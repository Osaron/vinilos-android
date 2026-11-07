package co.edu.uniandes.vinilos

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import co.edu.uniandes.vinilos.data.AlbumCreationRepository
import co.edu.uniandes.vinilos.data.AlbumRepository
import co.edu.uniandes.vinilos.model.Album
import co.edu.uniandes.vinilos.model.NewAlbum
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.instanceOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Navegación base del flujo Coleccionista para HU07 (#17 y #19): catálogo → formulario →
 * creación → catálogo actualizado. Los escenarios de validación, red y duplicados son de #21.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class CreateAlbumNavigationTest {
    private val app get() = ApplicationProvider.getApplicationContext<VinilosApplication>()
    private val re = Album(7, "Re", "", listOf("Café Tacvba"), "Rock")

    @Test
    fun creaUnAlbumDesdeElCatalogoYVuelveConLaListaActualizada() {
        val catalogo = CatalogoEnMemoria(listOf(re))
        val creacion = DobleCreacionAlbum { nuevo -> Album(101, nuevo.name, nuevo.cover, emptyList(), nuevo.genre) }
            .alCrear { catalogo.agregar(it) }

        conDobles(catalogo, creacion) {
            esperar { onView(allOf(withId(R.id.album_name), withText("Re"))).check(matches(isDisplayed())) }
            onView(withId(R.id.add_album)).perform(click())

            onView(withId(R.id.create_album_title)).check(matches(isDisplayed()))
            escribir(R.id.name_input, "Buscando América")
            escribir(R.id.cover_input, "https://i.pinimg.com/564x/aa/5f/ed/portada.jpg")
            escribir(R.id.release_date_input, "01/08/1984")
            escribir(R.id.description_input, "Primer álbum de Rubén Blades y Seis del Solar.")
            elegir(R.id.genre_input, "Salsa")
            elegir(R.id.record_label_input, "Elektra")
            onView(withId(R.id.save_button)).perform(scrollTo(), click())

            esperar { onView(allOf(withId(R.id.album_name), withText("Buscando América"))).check(matches(isDisplayed())) }
            onView(withId(R.id.create_album_title)).check(doesNotExist())
        }

        assertEquals(
            listOf(NewAlbum("Buscando América", "https://i.pinimg.com/564x/aa/5f/ed/portada.jpg", "1984-08-01",
                "Primer álbum de Rubén Blades y Seis del Solar.", "Salsa", "Elektra")),
            creacion.solicitudes,
        )
    }

    @Test
    fun cancelarVuelveAlCatalogoSinEnviar() {
        val creacion = DobleCreacionAlbum { error("No debe enviarse") }
        conDobles(CatalogoEnMemoria(listOf(re)), creacion) {
            esperar { onView(withId(R.id.add_album)).check(matches(isDisplayed())) }
            onView(withId(R.id.add_album)).perform(click())
            escribir(R.id.name_input, "Borrador")
            onView(withId(R.id.cancel_button)).perform(scrollTo(), click())

            esperar { onView(allOf(withId(R.id.album_name), withText("Re"))).check(matches(isDisplayed())) }
            onView(withId(R.id.add_album)).perform(click())
            onView(withId(R.id.create_album_title)).check(matches(isDisplayed()))
        }
        assertTrue(creacion.solicitudes.isEmpty())
    }

    // ---------------------------------------------------------------------------------------

    private fun escribir(id: Int, texto: String) =
        onView(withId(id)).perform(scrollTo(), replaceText(texto), closeSoftKeyboard())

    private fun elegir(id: Int, opcion: String) {
        onView(withId(id)).perform(scrollTo(), click())
        onData(allOf(`is`(instanceOf(String::class.java)), `is`(opcion))).perform(click())
    }

    private fun conDobles(catalogo: AlbumRepository, creacion: AlbumCreationRepository, bloque: () -> Unit) {
        val catalogoOriginal = app.albumRepository
        val creacionOriginal = app.albumCreationRepository
        app.albumRepository = catalogo
        app.albumCreationRepository = creacion
        try {
            ActivityScenario.launch(MainActivity::class.java).use { bloque() }
        } finally {
            app.albumRepository = catalogoOriginal
            app.albumCreationRepository = creacionOriginal
        }
    }

    private class CatalogoEnMemoria(iniciales: List<Album>) : AlbumRepository {
        private val albums = CopyOnWriteArrayList(iniciales)
        fun agregar(album: Album) { albums += album }
        override suspend fun getAlbums(): List<Album> = albums.toList()
    }

    private companion object {
        fun esperar(tiempoMaximoMs: Long = 5_000, verificacion: () -> Unit) {
            val limite = SystemClock.elapsedRealtime() + tiempoMaximoMs
            while (true) {
                try { verificacion(); return }
                catch (error: AssertionError) { if (SystemClock.elapsedRealtime() > limite) throw error }
                catch (error: NoMatchingViewException) { if (SystemClock.elapsedRealtime() > limite) throw error }
                SystemClock.sleep(100)
            }
        }
    }
}

/**
 * Doble de prueba de [AlbumCreationRepository] para Espresso (HU07). Registra cada [NewAlbum]
 * recibido y responde con [respuesta]; puede lanzar una excepción para simular 400 o falta de red.
 */
class DobleCreacionAlbum(private val respuesta: suspend (NewAlbum) -> Album) : AlbumCreationRepository {
    val solicitudes: MutableList<NewAlbum> = CopyOnWriteArrayList()
    private var despuesDeCrear: (Album) -> Unit = {}

    fun alCrear(accion: (Album) -> Unit) = apply { despuesDeCrear = accion }

    override suspend fun createAlbum(album: NewAlbum): Album {
        solicitudes += album
        return respuesta(album).also(despuesDeCrear)
    }
}
