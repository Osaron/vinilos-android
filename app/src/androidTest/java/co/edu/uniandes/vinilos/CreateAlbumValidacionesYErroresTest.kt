package co.edu.uniandes.vinilos

import android.app.Activity
import android.os.SystemClock
import android.view.View
import androidx.annotation.StringRes
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
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import co.edu.uniandes.vinilos.data.AlbumCreationRepository
import co.edu.uniandes.vinilos.data.AlbumRepository
import co.edu.uniandes.vinilos.model.Album
import co.edu.uniandes.vinilos.model.NewAlbum
import kotlinx.coroutines.CompletableDeferred
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.instanceOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Escenarios E2E de HU07 (#21): validaciones del formulario, errores del servicio y prevención de
 * envíos duplicados (ESC-22 a ESC-26 de «Sprint 2 ‐ Artefactos de prueba»).
 *
 * Complementan a [CreateAlbumNavigationTest], que cubre el camino feliz y cancelar. Usan dobles
 * de los repositorios para fijar las respuestas; la validación contra BackVynils real va aparte.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class CreateAlbumValidacionesYErroresTest {
    private val app get() = ApplicationProvider.getApplicationContext<VinilosApplication>()
    private val re = Album(7, "Re", "", listOf("Café Tacvba"), "Rock")

    private val valido = NewAlbum(
        name = "Buscando América",
        cover = "https://i.pinimg.com/564x/aa/5f/ed/portada.jpg",
        releaseDate = "1984-08-01",
        description = "Primer álbum de Rubén Blades y Seis del Solar.",
        genre = "Salsa",
        recordLabel = "Elektra",
    )

    private fun creadoDesde(nuevo: NewAlbum) = Album(101, nuevo.name, nuevo.cover, emptyList(), nuevo.genre)

    /** ESC-22: con el formulario vacío, Guardar marca los seis campos como obligatorios y no envía nada. */
    @Test
    fun esc22_formularioVacioMuestraCamposObligatoriosYNoEnvia() {
        val creacion = DobleCreacionAlbum { error("No debe enviarse") }
        conDobles(CatalogoEnMemoria(listOf(re)), creacion) {
            Formulario.abrir()

            Formulario.guardar()

            listOf(
                R.id.name_error, R.id.cover_error, R.id.release_date_error,
                R.id.description_error, R.id.genre_error, R.id.record_label_error,
            ).forEach { Formulario.muestraError(it, R.string.error_required) }
            onView(withId(R.id.create_album_title)).check(matches(isDisplayed()))
        }
        assertTrue(creacion.solicitudes.isEmpty())
    }

    /** ESC-23: portada sin http(s), fecha inexistente y fecha futura muestran su mensaje y no se envía nada. */
    @Test
    fun esc23_datosConFormatoInvalidoMuestranSuMensajeYNoSeEnvian() {
        val creacion = DobleCreacionAlbum { error("No debe enviarse") }
        conDobles(CatalogoEnMemoria(listOf(re)), creacion) {
            Formulario.abrir()
            Formulario.llenar(valido.copy(cover = "portada.jpg"), fecha = "31/02/2020")

            Formulario.guardar()

            Formulario.muestraError(R.id.cover_error, R.string.error_invalid_url)
            Formulario.muestraError(R.id.release_date_error, R.string.error_invalid_date)
            Formulario.sinError(R.id.name_error)

            Formulario.escribir(R.id.release_date_input, "01/01/2099")
            Formulario.guardar()

            Formulario.muestraError(R.id.release_date_error, R.string.error_future_date)
        }
        assertTrue(creacion.solicitudes.isEmpty())
    }

    /** ESC-24: al corregir los campos los mensajes desaparecen y el álbum se crea con los datos corregidos. */
    @Test
    fun esc24_alCorregirLosErroresSeCreaElAlbum() {
        val catalogo = CatalogoEnMemoria(listOf(re))
        val creacion = DobleCreacionAlbum { creadoDesde(it) }.alCrear { catalogo.agregar(it) }
        conDobles(catalogo, creacion) {
            Formulario.abrir()
            Formulario.llenar(valido.copy(name = "", cover = "portada.jpg"))
            Formulario.guardar()
            Formulario.muestraError(R.id.name_error, R.string.error_required)
            Formulario.muestraError(R.id.cover_error, R.string.error_invalid_url)

            Formulario.escribir(R.id.name_input, valido.name)
            Formulario.escribir(R.id.cover_input, valido.cover)
            Formulario.sinError(R.id.name_error)
            Formulario.sinError(R.id.cover_error)
            Formulario.guardar()

            Catalogo.muestraAlbum(valido.name)
        }
        assertEquals(listOf(valido), creacion.solicitudes)
    }

    /** ESC-25: si el servicio falla (sin red o 400), se informa, se conservan los datos y se puede reintentar. */
    @Test
    fun esc25_siElServicioFallaSeConservanLosDatosYSePuedeReintentar() {
        val catalogo = CatalogoEnMemoria(listOf(re))
        val intentos = AtomicInteger()
        val creacion = DobleCreacionAlbum { nuevo ->
            if (intentos.incrementAndGet() == 1) throw IOException("sin red") else creadoDesde(nuevo)
        }.alCrear { catalogo.agregar(it) }

        conDobles(catalogo, creacion) {
            Formulario.abrir()
            Formulario.llenar(valido)
            Formulario.guardar()

            esperar {
                onView(withId(R.id.create_status_message))
                    .check(matches(allOf(withEffectiveVisibility(Visibility.VISIBLE), withText(R.string.create_album_error))))
            }
            onView(withId(R.id.create_album_title)).check(matches(isDisplayed()))
            onView(withId(R.id.name_input)).check(matches(withText(valido.name)))
            onView(withId(R.id.description_input)).check(matches(withText(valido.description)))

            Formulario.guardar()

            Catalogo.muestraAlbum(valido.name)
            onView(withId(R.id.create_album_title)).check(doesNotExist())
        }
        assertEquals(listOf(valido, valido), creacion.solicitudes)
    }

    /**
     * ESC-26: mientras se guarda, el formulario queda bloqueado y varias pulsaciones crean un solo
     * álbum. Mientras el indicador de guardado está animando no se usa Espresso (ver [estaVisible]).
     */
    @Test
    fun esc26_variasPulsacionesMientrasGuardaCreanUnSoloAlbum() {
        val catalogo = CatalogoEnMemoria(listOf(re))
        val respuesta = CompletableDeferred<Album>()
        val creacion = DobleCreacionAlbum { respuesta.await() }.alCrear { catalogo.agregar(it) }

        conDobles(catalogo, creacion) {
            Formulario.abrir()
            Formulario.llenar(valido)

            enHiloPrincipal { actividad ->
                val guardar = actividad.findViewById<View>(R.id.save_button)
                repeat(3) { guardar.performClick() }
            }

            esperar { assertTrue(estaVisible(R.id.saving_indicator)) }
            assertFalse(estaHabilitada(R.id.save_button))
            assertFalse(estaHabilitada(R.id.name_input))
            enHiloPrincipal { it.findViewById<View>(R.id.save_button).performClick() }
            assertEquals(1, creacion.solicitudes.size)

            respuesta.complete(creadoDesde(valido))

            esperar { assertFalse(estaVisible(R.id.saving_indicator)) }
            Catalogo.muestraAlbum(valido.name)
        }
        assertEquals(listOf(valido), creacion.solicitudes)
    }

    // ---------------------------------------------------------------------------------------
    // Páginas (Page Object)
    // ---------------------------------------------------------------------------------------

    private object Catalogo {
        fun muestraAlbum(nombre: String) = esperar {
            onView(allOf(withId(R.id.album_name), withText(nombre))).check(matches(isDisplayed()))
        }
    }

    private object Formulario {
        fun abrir() {
            Catalogo.muestraAlbum("Re")
            onView(withId(R.id.add_album)).perform(click())
            onView(withId(R.id.create_album_title)).check(matches(isDisplayed()))
        }

        /** Llena el formulario con [album]; [fecha] se escribe tal cual (dd/MM/yyyy). */
        fun llenar(album: NewAlbum, fecha: String = "01/08/1984") {
            escribir(R.id.name_input, album.name)
            escribir(R.id.cover_input, album.cover)
            escribir(R.id.release_date_input, fecha)
            escribir(R.id.description_input, album.description)
            elegir(R.id.genre_input, album.genre)
            elegir(R.id.record_label_input, album.recordLabel)
        }

        fun escribir(id: Int, texto: String) {
            onView(withId(id)).perform(scrollTo(), replaceText(texto), closeSoftKeyboard())
        }

        fun guardar() {
            onView(withId(R.id.save_button)).perform(scrollTo(), click())
        }

        fun muestraError(id: Int, @StringRes mensaje: Int) = esperar {
            onView(withId(id)).check(matches(allOf(withEffectiveVisibility(Visibility.VISIBLE), withText(mensaje))))
        }

        fun sinError(id: Int) = esperar {
            onView(withId(id)).check(matches(withEffectiveVisibility(Visibility.GONE)))
        }

        private fun elegir(id: Int, opcion: String) {
            onView(withId(id)).perform(scrollTo(), click())
            onData(allOf(`is`(instanceOf(String::class.java)), `is`(opcion))).perform(click())
        }
    }

    // ---------------------------------------------------------------------------------------
    // Utilidades
    // ---------------------------------------------------------------------------------------

    private class CatalogoEnMemoria(iniciales: List<Album>) : AlbumRepository {
        private val albums = CopyOnWriteArrayList(iniciales)
        fun agregar(album: Album) { albums += album }
        override suspend fun getAlbums(): List<Album> = albums.toList()
    }

    /** Lanza la app con los dobles y restaura los repositorios originales al terminar. */
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

    /**
     * Consulta la vista en el hilo principal con `runOnMainSync`, que no espera a que la app esté
     * inactiva: en Android 5 (API 21) un indicador de carga animado puede dejar a Espresso
     * esperando sin fin (mismo caso de ESC-19, PR #58).
     */
    private fun estaVisible(id: Int): Boolean {
        var visible = false
        enHiloPrincipal { visible = it.findViewById<View>(id)?.isShown == true }
        return visible
    }

    private fun estaHabilitada(id: Int): Boolean {
        var habilitada = false
        enHiloPrincipal { habilitada = it.findViewById<View>(id)?.isEnabled == true }
        return habilitada
    }

    /** Ejecuta [accion] en el hilo principal sobre la actividad reanudada, sin esperar inactividad. */
    private fun enHiloPrincipal(accion: (Activity) -> Unit) {
        var error: Throwable? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                val actividad = checkNotNull(
                    ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED).firstOrNull(),
                ) { "No hay una actividad en primer plano" }
                accion(actividad)
            } catch (e: Throwable) {
                error = e
            }
        }
        error?.let { throw it }
    }

    private companion object {
        /** Reintenta [verificacion] hasta que pase o se agote el tiempo (la lista se actualiza en segundo plano). */
        fun esperar(tiempoMaximoMs: Long = 5_000, verificacion: () -> Unit) {
            val limite = SystemClock.elapsedRealtime() + tiempoMaximoMs
            while (true) {
                try {
                    verificacion()
                    return
                } catch (error: AssertionError) {
                    if (SystemClock.elapsedRealtime() > limite) throw error
                } catch (error: NoMatchingViewException) {
                    if (SystemClock.elapsedRealtime() > limite) throw error
                }
                SystemClock.sleep(100)
            }
        }
    }
}
