package co.edu.uniandes.vinilos

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.data.ActiveCollector
import co.edu.uniandes.vinilos.data.AlbumCreationRepository
import co.edu.uniandes.vinilos.data.AlbumRepository
import co.edu.uniandes.vinilos.data.ArtistDetailRepository
import co.edu.uniandes.vinilos.data.ArtistRepository
import co.edu.uniandes.vinilos.data.BandMusicianRepository
import co.edu.uniandes.vinilos.data.CollectorDetailRepository
import co.edu.uniandes.vinilos.data.CollectorRepository
import co.edu.uniandes.vinilos.data.CommentRepository
import co.edu.uniandes.vinilos.data.FavoriteArtistRepository
import co.edu.uniandes.vinilos.data.InMemoryActiveCollectorStore
import co.edu.uniandes.vinilos.data.TrackAssociationRepository
import co.edu.uniandes.vinilos.model.Album
import co.edu.uniandes.vinilos.model.AlbumComment
import co.edu.uniandes.vinilos.model.AlbumDetail
import co.edu.uniandes.vinilos.model.Artist
import co.edu.uniandes.vinilos.model.ArtistAlbum
import co.edu.uniandes.vinilos.model.ArtistDetail
import co.edu.uniandes.vinilos.model.ArtistType
import co.edu.uniandes.vinilos.model.Collector
import co.edu.uniandes.vinilos.model.CollectorComment
import co.edu.uniandes.vinilos.model.CollectorDetail
import co.edu.uniandes.vinilos.model.FavoritePerformer
import co.edu.uniandes.vinilos.model.NewComment
import co.edu.uniandes.vinilos.model.Track
import co.edu.uniandes.vinilos.ui.albums.AlbumCatalogFragment
import co.edu.uniandes.vinilos.ui.albums.AlbumDetailFragment
import co.edu.uniandes.vinilos.ui.albums.CreateAlbumFragment
import co.edu.uniandes.vinilos.ui.artists.ArtistCatalogFragment
import co.edu.uniandes.vinilos.ui.collectors.ActiveCollectorFragment
import co.edu.uniandes.vinilos.ui.collectors.CollectorCatalogFragment
import co.edu.uniandes.vinilos.ui.comments.CommentAlbumFragment
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream
import java.io.IOException
import java.util.Locale

/**
 * Revisión automática de accesibilidad de todas las pantallas de Vinilos (HU01–HU10 y HU12) con
 * Accessibility Test Framework, el motor de Accessibility Scanner.
 *
 * Recorre cada pantalla con datos en memoria y la revisa completa (etiquetas, tamaño de toque,
 * elementos duplicados, texto que no cabe y, si el dispositivo permite capturar pantalla, contraste)
 * en cuatro condiciones: fuente normal, fuente al 130 %, orientación horizontal y estados de error,
 * vacío y validación. Cada resultado se registra con la etiqueta [TAG] por pantalla y condición.
 *
 * Por defecto la prueba falla si hay hallazgos de tipo ERROR. Con el argumento de instrumentación
 * `a11yInforme=true` solo registra los resultados, para levantar el inventario sin cortar la corrida.
 */
@RunWith(AndroidJUnit4::class)
class AppAccessibilityTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = ApplicationProvider.getApplicationContext<VinilosApplication>()
    private val soloInforme get() = InstrumentationRegistry.getArguments().getString("a11yInforme") == "true"
    private val errores = mutableListOf<String>()
    private val validator = AccessibilityValidator().setRunChecksFromRootView(false).also { validator ->
        // El contraste necesita captura de pantalla; se activa si esta versión del framework lo permite.
        runCatching {
            validator.javaClass.methods.firstOrNull { it.name == "setCaptureScreenshots" }?.invoke(validator, true)
        }
        // Se registran todos los hallazgos de cada pantalla en vez de cortar en el primer ERROR.
        runCatching {
            validator.javaClass.methods.firstOrNull { it.name == "setThrowExceptionFor" }?.invoke(validator, null)
        }
    }

    private val albums = listOf(
        Album(100, "Buscando América", "", listOf("Rubén Blades"), "Salsa"),
        Album(101, "Poeta del pueblo", "", listOf("Rubén Blades"), "Salsa"),
    )
    private val musician = Artist(50, ArtistType.MUSICIAN, "Rubén Blades", "", "Cantante panameño")
    private val band = Artist(100, ArtistType.BAND, "Queen", "", "Banda británica")
    private val collectors = listOf(Collector(7, "José Pérez", "jose@example.org", "3001234567"), Collector(8, "Ana María", "", ""))

    // ---------- Pruebas ----------

    @Test fun todasLasPantallasConFuenteNormal() = conFuente(1.0f) { recorrerTodas("fuente-100") }

    @Test fun todasLasPantallasConFuenteAmpliada() = conFuente(1.3f) { recorrerTodas("fuente-130") }

    @Test fun todasLasPantallasEnHorizontal() = conFuente(1.0f) { recorrerTodas("horizontal", horizontal = true) }

    @Test fun estadosDeErrorVacioYValidacion() = conFuente(1.0f) {
        // Catálogos y detalles con error de red.
        conDatos(fallar = true) { scenario ->
            esperar(R.id.retry_button)
            revisar(scenario, "HU01 catálogo de álbumes · error", "estados")
            irA(scenario, AlbumCatalogFragment.ALBUM_SELECTED, bundleOf("albumId" to 100), R.id.retry_detail_button)
            revisar(scenario, "HU02 detalle de álbum · error", "estados")
            irA(scenario, AlbumCatalogFragment.OPEN_ARTISTS, Bundle(), R.id.retry_artists)
            revisar(scenario, "HU03 catálogo de artistas · error", "estados")
            irA(scenario, ArtistCatalogFragment.ARTIST_SELECTED, bundleOf("artistId" to 50, "artistType" to "MUSICIAN"), R.id.retry_artist_detail)
            revisar(scenario, "HU04 detalle de artista · error", "estados")
            irA(scenario, AlbumCatalogFragment.OPEN_COLLECTORS, Bundle(), R.id.retry_collectors)
            revisar(scenario, "HU05 catálogo de coleccionistas · error", "estados")
            irA(scenario, CollectorCatalogFragment.OPEN_DETAIL, bundleOf("collectorId" to 7), R.id.retry_collector_detail)
            revisar(scenario, "HU06 detalle de coleccionista · error", "estados")
        }
        // Listas vacías.
        conDatos(vacio = true) { scenario ->
            esperar(R.id.status_message)
            revisar(scenario, "HU01 catálogo de álbumes · vacío", "estados")
            irA(scenario, AlbumCatalogFragment.OPEN_ARTISTS, Bundle(), R.id.artist_status)
            revisar(scenario, "HU03 catálogo de artistas · vacío", "estados")
            irA(scenario, AlbumCatalogFragment.OPEN_COLLECTORS, Bundle(), R.id.collector_status)
            revisar(scenario, "HU05 catálogo de coleccionistas · vacío", "estados")
        }
        // Formularios con errores de validación.
        conDatos { scenario ->
            esperar(R.id.album_list)
            irA(scenario, CreateAlbumFragment.OPEN_CREATE_ALBUM, Bundle(), R.id.save_button)
            onView(withId(R.id.save_button)).perform(scrollTo(), click())
            esperar(R.id.name_error)
            revisar(scenario, "HU07 crear álbum · errores de validación", "estados")
            irA(scenario, AlbumDetailFragment.OPEN_ADD_TRACK, bundleOf("albumId" to 100), R.id.track_save_button)
            onView(withId(R.id.track_save_button)).perform(scrollTo(), click())
            esperar(R.id.track_name_error)
            revisar(scenario, "HU08 agregar canción · errores de validación", "estados")
            irA(scenario, CommentAlbumFragment.OPEN, bundleOf(CommentAlbumFragment.ALBUM_ID to 100, CommentAlbumFragment.ALBUM_NAME to "Buscando América"), R.id.publish_comment_button)
            onView(withId(R.id.publish_comment_button)).perform(scrollTo(), click())
            esperar(R.id.comment_description_error)
            revisar(scenario, "HU09 comentar · errores de validación", "estados")
        }
        terminar()
    }

    // ---------- Recorrido ----------

    private fun recorrerTodas(condicion: String, horizontal: Boolean = false) {
        conDatos { scenario ->
            if (horizontal) {
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                SystemClock.sleep(1_000)
            }
            esperar(R.id.album_list)
            revisar(scenario, "HU01 catálogo de álbumes", condicion)
            irA(scenario, AlbumCatalogFragment.ALBUM_SELECTED, bundleOf("albumId" to 100), R.id.detail_name)
            revisar(scenario, "HU02 detalle de álbum + HU09 comentarios", condicion)
            irA(scenario, CreateAlbumFragment.OPEN_CREATE_ALBUM, Bundle(), R.id.create_album_title)
            revisar(scenario, "HU07 crear álbum", condicion)
            irA(scenario, AlbumDetailFragment.OPEN_ADD_TRACK, bundleOf("albumId" to 100), R.id.selected_album)
            revisar(scenario, "HU08 agregar canción", condicion)
            irA(scenario, CommentAlbumFragment.OPEN, bundleOf(CommentAlbumFragment.ALBUM_ID to 100, CommentAlbumFragment.ALBUM_NAME to "Buscando América"), R.id.comment_input)
            revisar(scenario, "HU09 comentar álbum", condicion)
            irA(scenario, ActiveCollectorFragment.REQUEST, Bundle(), R.id.active_collector_list)
            revisar(scenario, "HU09/HU10 seleccionar coleccionista", condicion)
            irA(scenario, AlbumCatalogFragment.OPEN_ARTISTS, Bundle(), R.id.artist_list)
            revisar(scenario, "HU03 catálogo de artistas", condicion)
            irA(scenario, ArtistCatalogFragment.ARTIST_SELECTED, bundleOf("artistId" to 50, "artistType" to "MUSICIAN"), R.id.artist_detail_title)
            revisar(scenario, "HU04 detalle de músico + HU10 favoritos", condicion)
            irA(scenario, ArtistCatalogFragment.ARTIST_SELECTED, bundleOf("artistId" to 100, "artistType" to "BAND"), R.id.artist_detail_title)
            revisar(scenario, "HU04 detalle de banda + HU10 favoritos + HU12 integrantes", condicion)
            onView(withId(R.id.add_band_musician)).perform(scrollTo(), click())
            esperar(R.id.musician_selection_band)
            revisar(scenario, "HU12 seleccionar músico", condicion)
            irA(scenario, AlbumCatalogFragment.OPEN_COLLECTORS, Bundle(), R.id.collector_list)
            revisar(scenario, "HU05 catálogo de coleccionistas", condicion)
            irA(scenario, CollectorCatalogFragment.OPEN_DETAIL, bundleOf("collectorId" to 7), R.id.collector_detail_title)
            revisar(scenario, "HU06 detalle de coleccionista", condicion)
        }
        terminar()
    }

    /** Vuelve al catálogo y pide abrir la pantalla con [key], como lo haría la pantalla de origen. */
    private fun irA(scenario: ActivityScenario<MainActivity>, key: String, data: Bundle, esperarId: Int) {
        scenario.onActivity { activity ->
            activity.supportFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            activity.supportFragmentManager.setFragmentResult(key, data)
        }
        esperar(esperarId)
    }

    private fun esperar(id: Int) = onView(withId(id)).check(matches(isDisplayed()))

    // ---------- Revisión ----------

    /** Revisa la pantalla completa visible en el contenedor principal. */
    private fun revisar(scenario: ActivityScenario<MainActivity>, pantalla: String, condicion: String) {
        scenario.onActivity { activity ->
            val root: View = activity.findViewById(R.id.fragment_container)
            try {
                val results = validator.checkAndReturnResults(root)
                val porTipo = results.groupingBy { it.type.name }.eachCount().toSortedMap()
                Log.i(TAG, "condicion=$condicion; pantalla=$pantalla; resultados=$porTipo")
                results.filter { it.type.name == "WARNING" || it.type.name == "ERROR" }.forEach {
                    // Un campo con etiqueta visible enlazada por labelFor se anuncia con el mismo texto que
                    // su etiqueta. Es el patrón recomendado: ocultar la etiqueta deja al campo sin nombre.
                    val aceptado = it.type.name == "WARNING" && (it.view as? EditText)?.let { campo -> tieneEtiqueta(root.rootView, campo.id) } == true
                    val tipo = if (aceptado) "ACEPTADO (labelFor)" else it.type.name
                    val detalle = "$tipo: ${vista(activity, it.view)} · ${it.getMessage(Locale.getDefault())}"
                    Log.i(TAG, "condicion=$condicion; pantalla=$pantalla; $detalle")
                    if (it.type.name == "ERROR") errores += "[$condicion] $pantalla: $detalle"
                }
            } catch (failure: Throwable) {
                // El framework lanza una excepción con el detalle cuando hay hallazgos de tipo ERROR.
                val detalle = failure.message.orEmpty().replace('\n', ' ')
                Log.e(TAG, "condicion=$condicion; pantalla=$pantalla; ERROR: $detalle")
                errores += "[$condicion] $pantalla: $detalle"
            }
        }
    }

    private fun tieneEtiqueta(view: View, id: Int): Boolean = id != View.NO_ID && (view.labelFor == id ||
        (view is ViewGroup && (0 until view.childCount).any { tieneEtiqueta(view.getChildAt(it), id) }))

    private fun vista(activity: Activity, view: View?): String =
        view?.takeIf { it.id != View.NO_ID }?.let { runCatching { activity.resources.getResourceEntryName(it.id) }.getOrNull() }
            ?: view?.javaClass?.simpleName.orEmpty()

    private fun terminar() {
        if (!soloInforme) assertTrue("Hallazgos de accesibilidad:\n" + errores.joinToString("\n"), errores.isEmpty())
    }

    // ---------- Condiciones del dispositivo ----------

    /** Cambia el tamaño de fuente del sistema durante la prueba y lo restaura al terminar. */
    private fun conFuente(escala: Float, block: () -> Unit) {
        val anterior = shell("settings get system font_scale").trim().toFloatOrNull() ?: 1.0f
        shell("settings put system font_scale $escala")
        SystemClock.sleep(500)
        try { block() } finally { shell("settings put system font_scale $anterior") }
    }

    private fun shell(command: String): String {
        val descriptor: ParcelFileDescriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return try {
            FileInputStream(descriptor.fileDescriptor).bufferedReader().use { it.readText() }
        } catch (_: IOException) { "" } finally { descriptor.close() }
    }

    // ---------- Datos en memoria ----------

    private fun conDatos(fallar: Boolean = false, vacio: Boolean = false, block: (ActivityScenario<MainActivity>) -> Unit) {
        fun <T> datos(lista: List<T>): List<T> = when {
            fallar -> throw IOException("sin red (simulado)")
            vacio -> emptyList()
            else -> lista
        }
        val originales = Originales(app)
        app.albumRepository = object : AlbumRepository {
            override suspend fun getAlbums(): List<Album> = datos(albums)
            override suspend fun getAlbum(albumId: Int): AlbumDetail {
                if (fallar) throw IOException("sin red (simulado)")
                val album = albums.first { it.id == albumId }
                return AlbumDetail(album.id, album.name, "", "1984-08-01", "Álbum de salsa", "Salsa", "Elektra",
                    album.performers, listOf(Track(1, "Decisiones", "4:05"), Track(2, "GDBD", "3:10")))
            }
        }
        app.albumCreationRepository = AlbumCreationRepository { error("La revisión de accesibilidad no crea álbumes") }
        app.artistRepository = ArtistRepository { datos(listOf(musician, band)) }
        app.artistDetailRepository = ArtistDetailRepository { type, id ->
            if (fallar) throw IOException("sin red (simulado)")
            if (type == ArtistType.BAND) ArtistDetail(id, type, band.name, null, band.description, creationDate = "1970-01-01",
                albums = listOf(ArtistAlbum(1, "A Night at the Opera", null, "1975-11-21", "EMI", "Rock")))
            else ArtistDetail(id, type, musician.name, null, musician.description, birthDate = "1948-07-16",
                albums = listOf(ArtistAlbum(100, "Buscando América", null, "1984-08-01", "Elektra", "Salsa")))
        }
        app.bandMusicianRepository = object : BandMusicianRepository {
            override suspend fun getMembers(bandId: Int): List<Artist> = listOf(Artist(51, ArtistType.MUSICIAN, "Freddie Mercury", "", ""))
            override suspend fun addMusician(bandId: Int, musicianId: Int): Artist = error("La revisión no asocia músicos")
        }
        app.collectorRepository = CollectorRepository { datos(collectors) }
        app.collectorDetailRepository = CollectorDetailRepository { id ->
            if (fallar) throw IOException("sin red (simulado)")
            val collector = collectors.first { it.id == id }
            CollectorDetail(collector.id, collector.name, collector.email, collector.telephone,
                listOf(CollectorComment(1, "Gran disco", 5)), listOf(FavoritePerformer(50, musician.name, null, "", null)))
        }
        app.commentRepository = object : CommentRepository {
            override suspend fun getComments(albumId: Int): List<AlbumComment> = datos(listOf(AlbumComment(1, "Clásico de la salsa", 5, 7)))
            override suspend fun addComment(albumId: Int, comment: NewComment): AlbumComment = error("La revisión no publica")
        }
        app.favoriteArtistRepository = object : FavoriteArtistRepository {
            override suspend fun getFavorites(collectorId: Int): List<FavoritePerformer> = emptyList()
            override suspend fun addFavorite(collectorId: Int, artistId: Int, type: ArtistType): FavoritePerformer = error("La revisión no agrega")
        }
        app.trackAssociationRepository = TrackAssociationRepository { _, _ -> error("La revisión no guarda canciones") }
        app.activeCollectorStore = InMemoryActiveCollectorStore(ActiveCollector(7, "José Pérez"))
        try {
            ActivityScenario.launch(MainActivity::class.java).use(block)
        } finally {
            originales.restaurar(app)
        }
    }

    /** Dependencias reales de la app, para restaurarlas al terminar. */
    private class Originales(app: VinilosApplication) {
        private val albums = app.albumRepository
        private val creation = app.albumCreationRepository
        private val artists = app.artistRepository
        private val artistDetails = app.artistDetailRepository
        private val bands = app.bandMusicianRepository
        private val collectors = app.collectorRepository
        private val collectorDetails = app.collectorDetailRepository
        private val comments = app.commentRepository
        private val favorites = app.favoriteArtistRepository
        private val tracks = app.trackAssociationRepository
        private val active = app.activeCollectorStore
        fun restaurar(app: VinilosApplication) {
            app.albumRepository = albums
            app.albumCreationRepository = creation
            app.artistRepository = artists
            app.artistDetailRepository = artistDetails
            app.bandMusicianRepository = bands
            app.collectorRepository = collectors
            app.collectorDetailRepository = collectorDetails
            app.commentRepository = comments
            app.favoriteArtistRepository = favorites
            app.trackAssociationRepository = tracks
            app.activeCollectorStore = active
        }
    }

    private companion object { const val TAG = "A11Y_APP" }
}
