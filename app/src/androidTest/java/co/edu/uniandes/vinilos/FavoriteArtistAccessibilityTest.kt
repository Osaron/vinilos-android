package co.edu.uniandes.vinilos

import android.util.Log
import android.view.View
import androidx.core.os.bundleOf
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.edu.uniandes.vinilos.data.ActiveCollector
import co.edu.uniandes.vinilos.data.ActiveCollectorStore
import co.edu.uniandes.vinilos.data.AlbumRepository
import co.edu.uniandes.vinilos.data.ArtistDetailRepository
import co.edu.uniandes.vinilos.data.BandMusicianRepository
import co.edu.uniandes.vinilos.data.CollectorDetailRepository
import co.edu.uniandes.vinilos.data.CollectorRepository
import co.edu.uniandes.vinilos.data.FavoriteArtistRepository
import co.edu.uniandes.vinilos.data.InMemoryActiveCollectorStore
import co.edu.uniandes.vinilos.model.Artist
import co.edu.uniandes.vinilos.model.ArtistDetail
import co.edu.uniandes.vinilos.model.ArtistType
import co.edu.uniandes.vinilos.model.Collector
import co.edu.uniandes.vinilos.model.CollectorDetail
import co.edu.uniandes.vinilos.model.FavoritePerformer
import co.edu.uniandes.vinilos.ui.artists.ArtistCatalogFragment
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.HttpException
import retrofit2.Response
import java.util.Locale

/**
 * Revisión automática de accesibilidad de HU10 y del selector de coleccionista (#80) con
 * Accessibility Test Framework: etiquetas, tamaño mínimo de toque (48 dp), contraste y elementos
 * duplicados. Se revisan solo las vistas de estas historias, en cada estado del recorrido, para no
 * mezclar hallazgos de otras pantallas. Un hallazgo de tipo ERROR hace fallar la prueba; el resumen
 * por tipo y cualquier advertencia se registran con la etiqueta [TAG]. Las revisiones que necesitan
 * captura de pantalla (contraste) no corren aquí: se cubren con Accessibility Scanner en el emulador.
 */
@RunWith(AndroidJUnit4::class)
class FavoriteArtistAccessibilityTest {
    private val app get() = ApplicationProvider.getApplicationContext<VinilosApplication>()
    private val validator = AccessibilityValidator().setRunChecksFromRootView(false)
    private val band = ArtistDetail(100, ArtistType.BAND, "Queen", null, "Banda británica", creationDate = "1970-01-01")
    private val collectors = listOf(Collector(7, "José Pérez", "jose@example.org", ""), Collector(8, "Ana María", "", ""))

    /** Favoritos en memoria; [failNext] simula un error HTTP en el siguiente POST. */
    private inner class Favorites : FavoriteArtistRepository {
        val byCollector = mutableMapOf<Int, MutableList<Int>>()
        var failNext: Int? = null
        override suspend fun getFavorites(collectorId: Int): List<FavoritePerformer> = synchronized(this) {
            byCollector[collectorId].orEmpty().map { FavoritePerformer(it, band.name, null, band.description, band.creationDate) }
        }
        override suspend fun addFavorite(collectorId: Int, artistId: Int, type: ArtistType): FavoritePerformer = synchronized(this) {
            failNext?.let { failNext = null; throw HttpException(Response.error<Any>(it, "".toResponseBody())) }
            byCollector.getOrPut(collectorId) { mutableListOf() } += artistId
            FavoritePerformer(artistId, band.name, null, band.description, band.creationDate)
        }
    }

    private fun conArtista(active: ActiveCollector?, block: (ActivityScenario<MainActivity>, Favorites) -> Unit) {
        val favorites = Favorites()
        val albums = app.albumRepository
        val details = app.artistDetailRepository
        val oldFavorites = app.favoriteArtistRepository
        val oldCollectors = app.collectorRepository
        val collectorDetails = app.collectorDetailRepository
        val bands = app.bandMusicianRepository
        val store = app.activeCollectorStore
        app.albumRepository = AlbumRepository { emptyList() }
        app.artistDetailRepository = ArtistDetailRepository { _, _ -> band }
        app.favoriteArtistRepository = favorites
        app.collectorRepository = CollectorRepository { collectors }
        app.collectorDetailRepository = CollectorDetailRepository { id ->
            val collector = collectors.first { it.id == id }
            CollectorDetail(collector.id, collector.name, collector.email, "", emptyList(), favorites.getFavorites(id))
        }
        app.bandMusicianRepository = object : BandMusicianRepository {
            override suspend fun getMembers(bandId: Int): List<Artist> = emptyList()
            override suspend fun addMusician(bandId: Int, musicianId: Int): Artist = error("HU10 no asocia músicos")
        }
        app.activeCollectorStore = InMemoryActiveCollectorStore(active)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity {
                    it.supportFragmentManager.setFragmentResult(ArtistCatalogFragment.ARTIST_SELECTED,
                        bundleOf("artistId" to band.id, "artistType" to band.type.name))
                }
                onView(withId(R.id.artist_detail_title)).check(matches(withText("Queen")))
                block(scenario, favorites)
            }
        } finally {
            app.albumRepository = albums
            app.artistDetailRepository = details
            app.favoriteArtistRepository = oldFavorites
            app.collectorRepository = oldCollectors
            app.collectorDetailRepository = collectorDetails
            app.bandMusicianRepository = bands
            app.activeCollectorStore = store
        }
    }

    /** Revisa las vistas visibles indicadas (y sus descendientes) en el estado actual. */
    private fun revisar(scenario: ActivityScenario<MainActivity>, estado: String, vararg ids: Int) {
        scenario.onActivity { activity ->
            ids.map { id -> checkNotNull(activity.findViewById<View>(id)) { "No existe la vista $id" } }
                .filter { it.isShown }
                .forEach { view ->
                    // Lanza una excepción con el detalle si hay hallazgos de tipo ERROR.
                    val results = validator.checkAndReturnResults(view)
                    val name = activity.resources.getResourceEntryName(view.id)
                    // Resumen por tipo (ERROR, WARNING, INFO y NOT_RUN, es decir, revisiones que no
                    // aplican a la vista o que necesitan captura de pantalla, como el contraste).
                    val porTipo = results.groupingBy { it.type.name }.eachCount().toSortedMap()
                    Log.i(TAG, "estado=$estado; vista=$name; resultados=$porTipo")
                    results.filter { it.type.name != "NOT_RUN" }.forEach {
                        Log.i(TAG, "estado=$estado; vista=$name; ${it.type}: ${it.getMessage(Locale.getDefault())}")
                    }
                }
        }
    }

    private val favoriteViews = intArrayOf(R.id.add_favorite_button, R.id.favorite_status,
        R.id.change_favorite_collector, R.id.view_my_favorites_button)

    @Test fun agregarFavoritoEsAccesibleEnCadaEstado() = conArtista(ActiveCollector(8, "Ana María")) { scenario, favorites ->
        revisar(scenario, "inicial", *favoriteViews)
        // El estado se anuncia solo con TalkBack, sin mover el foco.
        scenario.onActivity {
            assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, it.findViewById<View>(R.id.favorite_status).accessibilityLiveRegion)
        }

        favorites.failNext = 500
        onView(withId(R.id.add_favorite_button)).perform(scrollTo(), click())
        onView(withId(R.id.favorite_status)).check(matches(withText(R.string.favorite_error)))
        revisar(scenario, "error", *favoriteViews)

        onView(withId(R.id.add_favorite_button)).perform(scrollTo(), click())
        onView(withId(R.id.favorite_status)).check(matches(withText(app.getString(R.string.favorite_added, "Ana María"))))
        revisar(scenario, "agregado", *favoriteViews)
        scenario.onActivity { assertFalse(it.findViewById<View>(R.id.add_favorite_button).isEnabled) }
    }

    @Test fun selectorDeColeccionistaEsAccesible() = conArtista(null) { scenario, _ ->
        revisar(scenario, "sin-coleccionista", *favoriteViews)
        onView(withId(R.id.add_favorite_button)).perform(scrollTo(), click())
        onView(withText("José Pérez")).check(matches(withText("José Pérez")))
        revisar(scenario, "selector", R.id.current_active_collector, R.id.search_active_collector, R.id.active_collector_list)
        scenario.onActivity { activity ->
            val list = activity.findViewById<RecyclerView>(R.id.active_collector_list)
            val row = checkNotNull(list.findViewHolderForAdapterPosition(0)).itemView
            assertEquals(activity.getString(R.string.select_collector, "José Pérez", "jose@example.org"), row.contentDescription)
        }
    }

    private companion object { const val TAG = "HU10_ACCESSIBILITY" }
}
