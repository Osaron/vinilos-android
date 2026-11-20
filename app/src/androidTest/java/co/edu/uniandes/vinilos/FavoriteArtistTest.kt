package co.edu.uniandes.vinilos

import androidx.core.os.bundleOf
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.edu.uniandes.vinilos.data.ActiveCollector
import co.edu.uniandes.vinilos.data.ActiveCollectorStore
import co.edu.uniandes.vinilos.data.AlbumRepository
import co.edu.uniandes.vinilos.data.ArtistDetailRepository
import co.edu.uniandes.vinilos.data.CollectorDetailRepository
import co.edu.uniandes.vinilos.data.CollectorRepository
import co.edu.uniandes.vinilos.data.FavoriteArtistRepository
import co.edu.uniandes.vinilos.data.InMemoryActiveCollectorStore
import co.edu.uniandes.vinilos.model.ArtistDetail
import co.edu.uniandes.vinilos.model.ArtistType
import co.edu.uniandes.vinilos.model.Collector
import co.edu.uniandes.vinilos.model.CollectorDetail
import co.edu.uniandes.vinilos.model.FavoritePerformer
import co.edu.uniandes.vinilos.ui.artists.ArtistCatalogFragment
import okhttp3.ResponseBody.Companion.toResponseBody
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.HttpException
import retrofit2.Response

/**
 * Escenarios E2E de HU10 (#90): agregar artistas favoritos (ESC-41 a ESC-45). Dobles en memoria
 * para artistas, coleccionistas y favoritos; no dependen de la red.
 */
@RunWith(AndroidJUnit4::class)
class FavoriteArtistTest {
    private val app get() = ApplicationProvider.getApplicationContext<VinilosApplication>()
    private val jose = Collector(7, "José Pérez", "", "")
    private val ana = Collector(8, "Ana María", "", "")
    private val band = ArtistDetail(100, ArtistType.BAND, "Queen", null, "Banda británica", creationDate = "1970-01-01")

    /** Favoritos en memoria por coleccionista; [failNext] simula un error HTTP en el siguiente POST. */
    private inner class Backend : FavoriteArtistRepository {
        val favorites = mutableMapOf<Int, MutableList<Int>>()
        val requests = mutableListOf<Triple<Int, Int, ArtistType>>()
        var failNext: Int? = null
        override suspend fun getFavorites(collectorId: Int): List<FavoritePerformer> = synchronized(this) {
            favorites[collectorId].orEmpty().map { FavoritePerformer(it, band.name, null, band.description, band.creationDate) }
        }
        override suspend fun addFavorite(collectorId: Int, artistId: Int, type: ArtistType): FavoritePerformer = synchronized(this) {
            requests += Triple(collectorId, artistId, type)
            failNext?.let { code ->
                failNext = null
                throw HttpException(Response.error<Any>(code, "".toResponseBody()))
            }
            favorites.getOrPut(collectorId) { mutableListOf() } += artistId
            FavoritePerformer(artistId, band.name, null, band.description, band.creationDate)
        }
        val collectorDetail = CollectorDetailRepository { id ->
            val collector = listOf(jose, ana).first { it.id == id }
            CollectorDetail(collector.id, collector.name, "", "", emptyList(), getFavorites(id))
        }
    }

    private fun conArtista(
        store: ActiveCollectorStore = InMemoryActiveCollectorStore(ActiveCollector(8, "Ana María")),
        block: (ActivityScenario<MainActivity>, Backend, ActiveCollectorStore) -> Unit,
    ) {
        val backend = Backend()
        val albums = app.albumRepository
        val oldBands = app.bandMusicianRepository
        app.bandMusicianRepository = object : co.edu.uniandes.vinilos.data.BandMusicianRepository {
            override suspend fun getMembers(bandId: Int): List<co.edu.uniandes.vinilos.model.Artist> = emptyList()
            override suspend fun addMusician(bandId: Int, musicianId: Int): co.edu.uniandes.vinilos.model.Artist =
                error("HU10 no debe asociar músicos")
        }
        val artists = app.artistDetailRepository
        val favorites = app.favoriteArtistRepository
        val collectors = app.collectorRepository
        val collectorDetails = app.collectorDetailRepository
        val active = app.activeCollectorStore
        app.albumRepository = AlbumRepository { emptyList() }
        app.artistDetailRepository = ArtistDetailRepository { _, _ -> band }
        app.favoriteArtistRepository = backend
        app.collectorRepository = CollectorRepository { listOf(jose, ana) }
        app.collectorDetailRepository = backend.collectorDetail
        app.activeCollectorStore = store
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity {
                    it.supportFragmentManager.setFragmentResult(ArtistCatalogFragment.ARTIST_SELECTED,
                        bundleOf("artistId" to band.id, "artistType" to band.type.name))
                }
                onView(withId(R.id.artist_detail_title)).check(matches(withText("Queen")))
                block(scenario, backend, store)
            }
        } finally {
            app.albumRepository = albums
            app.bandMusicianRepository = oldBands
            app.artistDetailRepository = artists
            app.favoriteArtistRepository = favorites
            app.collectorRepository = collectors
            app.collectorDetailRepository = collectorDetails
            app.activeCollectorStore = active
        }
    }

    private fun agregar() = onView(withId(R.id.add_favorite_button)).perform(scrollTo(), click())

    /** ESC-41: agrega la banda a los favoritos del coleccionista activo y no permite duplicarla. */
    @Test fun esc41_agregaYNoPermiteDuplicar() = conArtista { _, backend, _ ->
        onView(withId(R.id.favorite_status)).check(matches(withText(app.getString(R.string.favorite_collector, "Ana María"))))

        agregar()

        onView(withId(R.id.favorite_status)).check(matches(withText(app.getString(R.string.favorite_added, "Ana María"))))
        onView(withId(R.id.add_favorite_button)).check(matches(withText(R.string.favorite_already)))
        onView(withId(R.id.add_favorite_button)).check(matches(not(isEnabled())))
        assertEquals(listOf(Triple(8, 100, ArtistType.BAND)), backend.requests)
    }

    /** ESC-42: sin coleccionista, "Agregar a favoritos" lo pide y agrega a su nombre al elegirlo. */
    @Test fun esc42_pideColeccionistaYAgregaASuNombre() = conArtista(InMemoryActiveCollectorStore()) { _, backend, store ->
        onView(withId(R.id.favorite_status)).check(matches(withText(R.string.favorite_no_collector)))

        agregar()
        onView(withText("José Pérez")).perform(click())

        onView(withId(R.id.favorite_status)).perform(scrollTo())
            .check(matches(withText(app.getString(R.string.favorite_added, "José Pérez"))))
        assertEquals(listOf(Triple(7, 100, ArtistType.BAND)), backend.requests)
        assertEquals(7, store.active.value?.id)
    }

    /** ESC-43: si ya es favorito del coleccionista activo, se indica y no se ofrece agregarlo. */
    @Test fun esc43_yaEsFavoritoNoSeOfrece() {
        conArtista { _, backend, _ ->
            onView(withId(R.id.add_favorite_button)).check(matches(withText(R.string.favorite_add)))
            synchronized(backend) { backend.favorites[7] = mutableListOf(100) }
            // Cambiar al coleccionista que ya la tiene vuelve a consultar sus favoritos.
            onView(withId(R.id.change_favorite_collector)).perform(scrollTo(), click())
            onView(withText("José Pérez")).perform(click())

            onView(withId(R.id.favorite_status)).perform(scrollTo())
                .check(matches(withText(app.getString(R.string.favorite_is_favorite, "José Pérez"))))
            onView(withId(R.id.add_favorite_button)).check(matches(not(isEnabled())))
            assertTrue(backend.requests.isEmpty())
        }
    }

    /** ESC-44: un error del servidor se explica y se puede reintentar. */
    @Test fun esc44_errorDelServidorPermiteReintentar() = conArtista { _, backend, _ ->
        backend.failNext = 500
        agregar()
        onView(withId(R.id.favorite_status)).check(matches(withText(R.string.favorite_error)))
        onView(withId(R.id.add_favorite_button)).check(matches(isEnabled()))

        agregar()

        onView(withId(R.id.favorite_status)).check(matches(withText(app.getString(R.string.favorite_added, "Ana María"))))
        assertEquals(2, backend.requests.size)
    }

    /** ESC-45: "Ver mis favoritos" abre el detalle del coleccionista con el artista agregado (#88). */
    @Test fun esc45_elFavoritoApareceEnElDetalleDelColeccionista() = conArtista { _, _, _ ->
        agregar()
        onView(withId(R.id.view_my_favorites_button)).perform(scrollTo(), click())

        onView(withId(R.id.collector_detail_title)).check(matches(withText("Ana María")))
        onView(withContentDescription(app.getString(R.string.collector_favorite_accessibility, "Queen")))
            .perform(scrollTo()).check(matches(isDisplayed()))
    }
}
