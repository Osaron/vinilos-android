package co.edu.uniandes.vinilos

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.edu.uniandes.vinilos.data.AlbumRepository
import co.edu.uniandes.vinilos.model.Album
import co.edu.uniandes.vinilos.model.AlbumDetail
import co.edu.uniandes.vinilos.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.hamcrest.Matchers.containsString
import java.io.IOException

/**
 * Escenarios E2E de HU02 (ESC-08 a ESC-12 de «Sprint 1 ‐ Artefactos de prueba»).
 * Usan un repositorio controlado; la validación contra el backend real es manual (ESC-13).
 */
@RunWith(AndroidJUnit4::class)
class AlbumDetailTest {
    private val app get() = ApplicationProvider.getApplicationContext<VinilosApplication>()
    private val album = Album(7, "Re", "", listOf("Café Tacvba"), "Rock")
    private val detail = AlbumDetail(
        7, "Re", "", "1994-01-01", "Álbum de estudio", "Rock", "Warner Music Mexico",
        listOf("Café Tacvba"), listOf(Track(1, "El aparato", "4:20")),
    )

    /** Ejecuta [block] con un repositorio cuyo detalle responde [onGetAlbum]; restaura el original. */
    private fun withRepository(onGetAlbum: (Int) -> AlbumDetail, block: () -> Unit) {
        val original = app.albumRepository
        app.albumRepository = object : AlbumRepository {
            override suspend fun getAlbums(): List<Album> = listOf(album)
            override suspend fun getAlbum(albumId: Int): AlbumDetail = onGetAlbum(albumId)
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { block() }
        } finally { app.albumRepository = original }
    }

    private fun abrirPrimerAlbum() {
        onView(withId(R.id.album_list)).perform(
            RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(0, click()),
        )
    }

    /** ESC-08: el detalle muestra toda la información del álbum seleccionado. */
    @Test fun muestraTodaLaInformacionDelAlbum() = withRepository({ detail }) {
        abrirPrimerAlbum()
        onView(withId(R.id.detail_cover)).check(matches(withContentDescription("Portada de Re")))
        onView(withId(R.id.detail_name)).check(matches(withText("Re")))
        onView(withId(R.id.detail_performers)).check(matches(withText("Café Tacvba")))
        // En pantallas pequeñas (p. ej. API 21) estos campos quedan bajo la portada.
        onView(withId(R.id.detail_release_date)).perform(scrollTo())
            .check(matches(withText("Lanzamiento: 1994-01-01")))
        onView(withId(R.id.detail_genre)).perform(scrollTo())
            .check(matches(withText("Género: Rock")))
        onView(withId(R.id.detail_record_label)).perform(scrollTo())
            .check(matches(withText("Sello discográfico: Warner Music Mexico")))
        onView(withId(R.id.detail_description)).perform(scrollTo())
            .check(matches(withText("Álbum de estudio")))
    }

    /** ESC-09 y ESC-11: lista los tracks del álbum correcto y Atrás regresa al catálogo. */
    @Test fun navegaAlDetalleCorrectoYRegresaAlCatalogo() {
        var requestedId = -1
        withRepository({ id -> requestedId = id; detail }) {
            abrirPrimerAlbum()
            onView(withId(R.id.detail_name)).check(matches(withText("Re")))
            onView(withId(R.id.detail_tracks))
                .perform(scrollTo())
                .check(matches(withText(containsString("El aparato (4:20)"))))
                .check(matches(isDisplayed()))
            assertEquals(7, requestedId)
            closeSoftKeyboard()
            pressBack()
            onView(withId(R.id.album_list)).check(matches(isDisplayed()))
        }
    }

    /** ESC-10: un álbum sin tracks se informa sin errores. */
    @Test fun informaCuandoElAlbumNoTieneTracks() =
        withRepository({ detail.copy(tracks = emptyList()) }) {
            abrirPrimerAlbum()
            onView(withId(R.id.detail_name)).check(matches(withText("Re")))
            onView(withId(R.id.detail_tracks)).perform(scrollTo())
                .check(matches(withText(R.string.no_tracks)))
        }

    /** ESC-12: si falla la consulta se muestra el error y Reintentar carga el detalle. */
    @Test fun muestraErrorYPermiteReintentar() {
        var attempts = 0
        withRepository({ if (++attempts == 1) throw IOException("sin red") else detail }) {
            abrirPrimerAlbum()
            onView(withText(R.string.detail_error)).check(matches(isDisplayed()))
            onView(withId(R.id.retry_detail_button)).perform(click())
            onView(withId(R.id.detail_name)).check(matches(withText("Re")))
            assertEquals(2, attempts)
        }
    }
}
