package co.edu.uniandes.vinilos

import android.os.SystemClock
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import co.edu.uniandes.vinilos.data.AlbumRepository
import co.edu.uniandes.vinilos.model.Album
import co.edu.uniandes.vinilos.model.AlbumDetail
import co.edu.uniandes.vinilos.model.Track
import kotlinx.coroutines.CompletableDeferred
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Escenarios E2E de estados y navegación de HU01 y HU02 (ESC-14 a ESC-21 de
 * «Sprint 1 ‐ Artefactos de prueba»).
 *
 * Usan un repositorio controlado para fijar los datos y los tiempos de respuesta de cada
 * escenario. La validación contra BackVynils real se hace en una suite separada.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class AlbumEstadosYNavegacionTest {

    private val app get() = ApplicationProvider.getApplicationContext<VinilosApplication>()

    private val re = Album(7, "Re", "", listOf("Café Tacvba"), "Rock")
    private val buscandoAmerica = Album(21, "Buscando América", "", listOf("Rubén Blades Bellido de Luna"), "Salsa")
    private val nightAtTheOpera = Album(35, "A Night at the Opera", "", listOf("Queen"), "Rock")
    private val catalogo = listOf(re, buscandoAmerica, nightAtTheOpera)

    /** ESC-14: mientras llega el catálogo solo se ve el indicador de carga; luego aparecen los álbumes. */
    @Test
    fun esc14_muestraCargandoMientrasLlegaElCatalogo() {
        val respuesta = CompletableDeferred<List<Album>>()
        conRepositorio(RepositorioControlado(albums = { respuesta.await() })) { escenario ->
            esperar { assertTrue(escenario.estaVisible(R.id.loading_indicator)) }
            assertFalse(escenario.estaVisible(R.id.album_list))
            assertFalse(escenario.estaVisible(R.id.status_message))
            assertFalse(escenario.estaVisible(R.id.retry_button))

            respuesta.complete(listOf(re))

            Catalogo.muestraAlbum("Re")
            assertFalse(escenario.estaVisible(R.id.loading_indicator))
        }
    }

    /** ESC-15: la búsqueda encuentra por artista, sin distinguir tildes ni mayúsculas. */
    @Test
    fun esc15_buscaPorArtistaSinTildesNiMayusculas() =
        conRepositorio(RepositorioControlado(albums = { catalogo })) {
            Catalogo.muestraAlbum("Re")
            Catalogo.tieneContador("3 álbumes")

            Catalogo.buscar("ruben")
            Catalogo.muestraAlbum("Buscando América")
            Catalogo.noMuestraAlbum("Re")
            Catalogo.noMuestraAlbum("A Night at the Opera")
            Catalogo.tieneContador("1 álbum")

            Catalogo.buscar("CAFE")
            Catalogo.muestraAlbum("Re")
            Catalogo.noMuestraAlbum("Buscando América")

            Catalogo.buscar("")
            Catalogo.tieneContador("3 álbumes")
        }

    /** ESC-16: entre varios álbumes se abre el detalle del álbum tocado, con su id. */
    @Test
    fun esc16_abreElDetalleDelAlbumCorrectoEntreVarios() {
        val repositorio = RepositorioControlado(albums = { catalogo })
        conRepositorio(repositorio) {
            Catalogo.muestraAlbum("A Night at the Opera")
            Catalogo.abrirAlbumEnPosicion(2)

            Detalle.muestraNombre("A Night at the Opera")
            assertEquals(listOf(35), repositorio.idsSolicitados)
        }
    }

    /** ESC-17: al volver del detalle el catálogo conserva la búsqueda y el filtro. */
    @Test
    fun esc17_alVolverDelDetalleSeConservaLaBusqueda() =
        conRepositorio(RepositorioControlado(albums = { catalogo })) {
            Catalogo.muestraAlbum("Re")
            Catalogo.buscar("queen")
            Catalogo.muestraAlbum("A Night at the Opera")
            Catalogo.noMuestraAlbum("Re")

            Catalogo.abrirAlbumEnPosicion(0)
            Detalle.muestraNombre("A Night at the Opera")
            closeSoftKeyboard()
            pressBack()

            onView(withId(R.id.search_albums)).check(matches(withText("queen")))
            Catalogo.muestraAlbum("A Night at the Opera")
            Catalogo.noMuestraAlbum("Re")
            Catalogo.tieneContador("1 álbum")
        }

    /** ESC-18: al rotar en el detalle se conserva la información sin volver a consultar el backend. */
    @Test
    fun esc18_elDetalleSeConservaAlRotar() {
        val repositorio = RepositorioControlado(albums = { catalogo })
        conRepositorio(repositorio) { escenario ->
            Catalogo.muestraAlbum("Re")
            Catalogo.abrirAlbumEnPosicion(0)
            Detalle.muestraNombre("Re")

            escenario.recreate()

            Detalle.muestraNombre("Re")
            assertEquals(listOf(7), repositorio.idsSolicitados)
        }
    }

    /** ESC-19: mientras llega el detalle solo se ve su indicador de carga; luego aparece la información. */
    @Test
    fun esc19_muestraCargandoMientrasLlegaElDetalle() {
        val respuesta = CompletableDeferred<AlbumDetail>()
        val repositorio = RepositorioControlado(albums = { catalogo }, detalle = { respuesta.await() })
        conRepositorio(repositorio) { escenario ->
            Catalogo.muestraAlbum("Re")
            Catalogo.abrirAlbumEnPosicion(0)

            esperar { assertTrue(escenario.estaVisible(R.id.detail_loading)) }
            assertFalse(escenario.estaVisible(R.id.detail_content))
            assertFalse(escenario.estaVisible(R.id.detail_error_group))

            respuesta.complete(detalleDe(re))

            Detalle.muestraNombre("Re")
            assertFalse(escenario.estaVisible(R.id.detail_loading))
        }
    }

    /** ESC-20: desde el error del detalle, Atrás regresa al catálogo. */
    @Test
    fun esc20_desdeElErrorDelDetalleAtrasRegresaAlCatalogo() =
        conRepositorio(RepositorioControlado(albums = { catalogo }, detalle = { throw IOException("sin red") })) {
            Catalogo.muestraAlbum("Re")
            Catalogo.abrirAlbumEnPosicion(0)
            esperar { onView(withText(R.string.detail_error)).check(matches(isDisplayed())) }

            closeSoftKeyboard()
            pressBack()

            onView(withId(R.id.album_list)).check(matches(isDisplayed()))
            Catalogo.muestraAlbum("Re")
        }

    /** ESC-21 (BUG-01, #46): un álbum sin intérpretes muestra «Artista no disponible» en la tarjeta y en el detalle. */
    @Test
    fun esc21_albumSinInterpretesMuestraArtistaNoDisponible() {
        val sinInterpretes = Album(42, "Album sin artista", "", emptyList(), "Rock")
        conRepositorio(RepositorioControlado(albums = { listOf(sinInterpretes) })) {
            val artistaNoDisponible = app.getString(R.string.unknown_artist)

            Catalogo.muestraAlbum("Album sin artista")
            esperar { onView(withId(R.id.album_artist)).check(matches(withText(artistaNoDisponible))) }

            Catalogo.abrirAlbumEnPosicion(0)
            Detalle.muestraNombre("Album sin artista")
            onView(withId(R.id.detail_performers)).check(matches(withText(artistaNoDisponible)))
        }
    }

    // ---------------------------------------------------------------------------------------
    // Páginas (Page Object): agrupan las interacciones de cada pantalla.
    // ---------------------------------------------------------------------------------------

    private object Catalogo {
        fun buscar(texto: String) {
            onView(withId(R.id.search_albums))
                .perform(ViewActions.replaceText(texto), ViewActions.closeSoftKeyboard())
        }

        fun abrirAlbumEnPosicion(posicion: Int) {
            onView(withId(R.id.album_list)).perform(
                RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(posicion, ViewActions.click()),
            )
        }

        fun muestraAlbum(nombre: String) = esperar {
            onView(allOf(withId(R.id.album_name), withText(nombre))).check(matches(isDisplayed()))
        }

        fun noMuestraAlbum(nombre: String) = esperar {
            onView(allOf(withId(R.id.album_name), withText(nombre))).check(doesNotExist())
        }

        fun tieneContador(texto: String) = esperar {
            onView(withId(R.id.album_count)).check(matches(withText(texto)))
        }
    }

    private object Detalle {
        fun muestraNombre(nombre: String) = esperar {
            onView(withId(R.id.detail_name)).check(matches(withText(nombre)))
        }
    }

    // ---------------------------------------------------------------------------------------
    // Utilidades
    // ---------------------------------------------------------------------------------------

    /** Repositorio controlado: fija los datos de cada escenario y registra los ids consultados. */
    private class RepositorioControlado(
        private val albums: suspend () -> List<Album>,
        /** Si es null, el detalle se construye a partir del álbum del catálogo con ese id. */
        private val detalle: (suspend (Int) -> AlbumDetail)? = null,
    ) : AlbumRepository {
        val idsSolicitados: MutableList<Int> = CopyOnWriteArrayList()

        @Volatile
        private var albumsEntregados: List<Album> = emptyList()

        override suspend fun getAlbums(): List<Album> = albums().also { albumsEntregados = it }

        override suspend fun getAlbum(albumId: Int): AlbumDetail {
            idsSolicitados += albumId
            return detalle?.invoke(albumId) ?: detalleDe(albumsEntregados.first { it.id == albumId })
        }
    }

    /** Lanza la app con [repositorio] y restaura el repositorio original al terminar. */
    private fun conRepositorio(
        repositorio: AlbumRepository,
        bloque: (ActivityScenario<MainActivity>) -> Unit,
    ) {
        val original = app.albumRepository
        app.albumRepository = repositorio
        try {
            ActivityScenario.launch(MainActivity::class.java).use { bloque(it) }
        } finally {
            app.albumRepository = original
        }
    }

    /**
     * Indica si la vista está visible en pantalla. Se consulta directamente en la actividad para
     * no depender de la sincronización de Espresso mientras hay un indicador de carga activo.
     */
    private fun ActivityScenario<MainActivity>.estaVisible(id: Int): Boolean {
        var visible = false
        onActivity { actividad -> visible = actividad.findViewById<View>(id)?.isShown == true }
        return visible
    }

    private companion object {
        fun detalleDe(album: Album) = AlbumDetail(
            id = album.id,
            name = album.name,
            cover = "",
            releaseDate = "1994-01-01",
            description = "Descripción de ${album.name}",
            genre = album.genre,
            recordLabel = "Sello de prueba",
            performers = album.performers,
            tracks = listOf(Track(1, "Canción de ${album.name}", "3:00")),
        )

        /**
         * Reintenta [verificacion] hasta que pase o se agote el tiempo. La lista del catálogo
         * calcula sus cambios en un hilo de fondo (ListAdapter), que Espresso no espera.
         */
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
