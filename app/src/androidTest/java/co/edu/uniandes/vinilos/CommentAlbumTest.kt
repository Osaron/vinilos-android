package co.edu.uniandes.vinilos

import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.edu.uniandes.vinilos.data.ActiveCollector
import co.edu.uniandes.vinilos.data.ActiveCollectorStore
import co.edu.uniandes.vinilos.data.AlbumRepository
import co.edu.uniandes.vinilos.data.CollectorRepository
import co.edu.uniandes.vinilos.data.CommentRepository
import co.edu.uniandes.vinilos.data.InMemoryActiveCollectorStore
import co.edu.uniandes.vinilos.model.Album
import co.edu.uniandes.vinilos.model.AlbumComment
import co.edu.uniandes.vinilos.model.AlbumDetail
import co.edu.uniandes.vinilos.model.Collector
import co.edu.uniandes.vinilos.model.NewComment
import okhttp3.ResponseBody.Companion.toResponseBody
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.HttpException
import retrofit2.Response

/**
 * Escenarios E2E de HU09 (#84): comentar un álbum (ESC-36 a ESC-40). Dobles en memoria para el
 * catálogo, los coleccionistas y los comentarios; no dependen de la red.
 */
@RunWith(AndroidJUnit4::class)
class CommentAlbumTest {
    private val app get() = ApplicationProvider.getApplicationContext<VinilosApplication>()
    private val albums = listOf(
        Album(100, "Buscando América", "", listOf("Rubén Blades"), "Salsa"),
        Album(101, "Poeta del pueblo", "", listOf("Rubén Blades"), "Salsa"),
    )

    /** Comentarios en memoria por álbum. [failNext] simula un error HTTP en el siguiente envío. */
    private class Comments : CommentRepository {
        val byAlbum = mutableMapOf(100 to mutableListOf(AlbumComment(1, "Clásico de la salsa", 5, 7)))
        val requests = mutableListOf<Pair<Int, NewComment>>()
        var failNext: Int? = null
        private var nextId = 10
        override suspend fun getComments(albumId: Int): List<AlbumComment> = synchronized(this) { byAlbum[albumId].orEmpty().toList() }
        override suspend fun addComment(albumId: Int, comment: NewComment): AlbumComment = synchronized(this) {
            requests += albumId to comment
            failNext?.let { code ->
                failNext = null
                throw HttpException(Response.error<Any>(code, "".toResponseBody()))
            }
            AlbumComment(nextId++, comment.description, comment.rating, comment.collectorId)
                .also { byAlbum.getOrPut(albumId) { mutableListOf() } += it }
        }
    }

    private fun conComentarios(
        store: ActiveCollectorStore = InMemoryActiveCollectorStore(ActiveCollector(8, "Ana María")),
        block: (ActivityScenario<MainActivity>, Comments, ActiveCollectorStore) -> Unit,
    ) {
        val comments = Comments()
        val originalAlbums = app.albumRepository
        val originalComments = app.commentRepository
        val originalCollectors = app.collectorRepository
        val originalStore = app.activeCollectorStore
        app.albumRepository = object : AlbumRepository {
            override suspend fun getAlbums(): List<Album> = albums
            override suspend fun getAlbum(albumId: Int): AlbumDetail {
                val album = albums.first { it.id == albumId }
                return AlbumDetail(album.id, album.name, "", "1984-08-01", "", "Salsa", "Elektra", album.performers, emptyList())
            }
        }
        app.commentRepository = comments
        app.collectorRepository = CollectorRepository {
            listOf(Collector(7, "José Pérez", "", ""), Collector(8, "Ana María", "", ""))
        }
        app.activeCollectorStore = store
        try {
            ActivityScenario.launch(MainActivity::class.java).use { block(it, comments, store) }
        } finally {
            app.albumRepository = originalAlbums
            app.commentRepository = originalComments
            app.collectorRepository = originalCollectors
            app.activeCollectorStore = originalStore
        }
    }

    private fun abrirAlbum(posicion: Int) = onView(withId(R.id.album_list)).perform(
        RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(posicion, click()),
    )

    private fun escribir(texto: String, calificacion: Int) {
        onView(withId(R.id.comment_input)).perform(scrollTo(), replaceText(texto), closeSoftKeyboard())
        onView(withId(ratingId(calificacion))).perform(scrollTo(), click())
    }

    private fun publicar() = onView(withId(R.id.publish_comment_button)).perform(scrollTo(), click())

    private fun ratingId(valor: Int) = listOf(R.id.rating_1, R.id.rating_2, R.id.rating_3, R.id.rating_4, R.id.rating_5)[valor - 1]

    /** ESC-36: el detalle muestra los comentarios del álbum o avisa que no tiene. */
    @Test fun esc36_elDetalleMuestraLosComentarios() = conComentarios { _, _, _ ->
        abrirAlbum(0)
        onView(withId(R.id.detail_comments)).check(matches(withText(containsString("Clásico de la salsa · Calificación: 5/5"))))
    }

    /** ESC-37: sin coleccionista, "+ Comentar" lo pide, abre el formulario y publica a su nombre. */
    @Test fun esc37_pideColeccionistaYPublicaASuNombre() = conComentarios(InMemoryActiveCollectorStore()) { _, comments, store ->
        abrirAlbum(1)
        onView(withId(R.id.detail_comments_status)).check(matches(withText(R.string.album_comments_empty)))
        onView(withId(R.id.add_comment_button)).perform(scrollTo(), click())

        onView(withText("José Pérez")).perform(click())

        onView(withId(R.id.comment_collector)).check(matches(withText(app.getString(R.string.comment_as, "José Pérez"))))
        onView(withId(R.id.comment_album_context)).check(matches(withText(app.getString(R.string.comment_album_context, "Poeta del pueblo"))))
        escribir("  Letras que no envejecen  ", 4)
        publicar()

        onView(withId(R.id.detail_name)).check(matches(withText("Poeta del pueblo")))
        onView(withId(R.id.detail_comments)).check(matches(withText(containsString("Letras que no envejecen · Calificación: 4/5"))))
        assertEquals(listOf(101 to NewComment("Letras que no envejecen", 4, 7)), comments.requests)
        assertEquals(7, store.active.value?.id)
    }

    /** ESC-38: sin texto ni calificación no se envía y se explica cada campo. */
    @Test fun esc38_validaAntesDeEnviar() = conComentarios { _, comments, _ ->
        abrirAlbum(0)
        onView(withId(R.id.add_comment_button)).perform(scrollTo(), click())
        onView(withId(R.id.comment_input)).perform(scrollTo(), replaceText("   "), closeSoftKeyboard())

        publicar()

        onView(withId(R.id.comment_description_error)).check(matches(isDisplayed()))
        onView(withId(R.id.comment_rating_error)).perform(scrollTo()).check(matches(isDisplayed()))
        assertTrue(comments.requests.isEmpty())
    }

    /** ESC-39: un error del servidor conserva lo escrito, lo explica y permite reintentar. */
    @Test fun esc39_errorDelServidorPermiteReintentar() = conComentarios { _, comments, _ ->
        comments.failNext = 500
        abrirAlbum(0)
        onView(withId(R.id.add_comment_button)).perform(scrollTo(), click())
        escribir("Imprescindible", 5)
        publicar()

        onView(withId(R.id.comment_status)).perform(scrollTo()).check(matches(withText(R.string.comment_error)))
        onView(withId(R.id.comment_input)).check(matches(withText("Imprescindible")))
        onView(withId(R.id.rating_5)).check(matches(isChecked()))

        publicar()

        onView(withId(R.id.detail_comments)).check(matches(withText(containsString("Imprescindible · Calificación: 5/5"))))
        assertEquals(2, comments.requests.size)
    }

    /** ESC-40: el borrador sobrevive a la rotación y Cancelar vuelve sin publicar. */
    @Test fun esc40_rotarConservaElBorradorYCancelarNoPublica() = conComentarios { scenario, comments, _ ->
        abrirAlbum(0)
        onView(withId(R.id.add_comment_button)).perform(scrollTo(), click())
        escribir("Borrador sin terminar", 3)

        scenario.recreate()

        onView(withId(R.id.comment_input)).check(matches(withText("Borrador sin terminar")))
        onView(withId(R.id.rating_3)).check(matches(isChecked()))
        onView(withId(R.id.cancel_comment_button)).perform(scrollTo(), click())
        onView(withId(R.id.detail_name)).check(matches(withText("Buscando América")))
        onView(withId(R.id.detail_comments)).check(matches(withText(not(containsString("Borrador sin terminar")))))
        assertTrue(comments.requests.isEmpty())
    }
}
