package co.edu.uniandes.vinilos

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.core.os.bundleOf
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.edu.uniandes.vinilos.data.AlbumRepository
import co.edu.uniandes.vinilos.data.ArtistDetailRepository
import co.edu.uniandes.vinilos.data.ArtistRepository
import co.edu.uniandes.vinilos.model.Album
import co.edu.uniandes.vinilos.model.Artist
import co.edu.uniandes.vinilos.model.ArtistAlbum
import co.edu.uniandes.vinilos.model.ArtistDetail
import co.edu.uniandes.vinilos.model.ArtistType
import co.edu.uniandes.vinilos.ui.artists.ArtistCatalogFragment
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class ArtistDetailNavigationTest {
    private val app get() = ApplicationProvider.getApplicationContext<VinilosApplication>()
    private val artists = listOf(
        Artist(100, ArtistType.MUSICIAN, "Rubén Blades", "", "Cantautor"),
        Artist(101, ArtistType.BAND, "Queen", "", "Banda de rock"),
    )

    @Test fun abre_detalle_de_musico_y_vuelve_conservando_la_busqueda() {
        val calls = mutableListOf<Pair<ArtistType, Int>>()
        withRepositories(detail = { type, id ->
            calls += type to id
            ArtistDetail(id, type, "Rubén Blades", null, "Cantautor", albums = listOf(
                ArtistAlbum(1, "Buscando América", null, "1984-08-01", "Elektra", "Salsa"),
            ))
        }) {
            onView(withId(R.id.open_artists)).perform(click())
            esperar { onView(withText("Rubén Blades")).check(matches(isDisplayed())) }
            onView(withId(R.id.search_artists)).perform(replaceText("ruben"), closeSoftKeyboard())
            onView(withText("Rubén Blades")).perform(click())
            esperar { onView(withId(R.id.artist_detail_title)).check(matches(withText("Rubén Blades"))) }
            onView(withText("Buscando América")).check(matches(isDisplayed()))
            pressBack()
            onView(withId(R.id.search_artists)).check(matches(withText("ruben")))
            onView(withText("Rubén Blades")).check(matches(isDisplayed()))
        }
        assertEquals(listOf(ArtistType.MUSICIAN to 100), calls)
    }

    @Test fun abre_detalle_de_banda_usando_tipo_e_id_de_la_fila() {
        val calls = mutableListOf<Pair<ArtistType, Int>>()
        withRepositories(detail = { type, id ->
            calls += type to id
            ArtistDetail(id, type, "Queen", null, "Banda de rock", creationDate = "1970-01-01")
        }) {
            onView(withId(R.id.open_artists)).perform(click())
            esperar { onView(withText("Queen")).check(matches(isDisplayed())) }
            onView(withText("Queen")).perform(click())
            esperar { onView(withId(R.id.artist_detail_title)).check(matches(withText("Queen"))) }
            onView(withText("1970-01-01")).check(matches(isDisplayed()))
        }
        assertEquals(listOf(ArtistType.BAND to 101), calls)
    }

    @Test fun doble_seleccion_rapida_abre_un_solo_detalle_y_consulta_una_vez() {
        val calls = mutableListOf<Pair<ArtistType, Int>>()
        withRepositories(detail = { type, id ->
            calls += type to id
            ArtistDetail(id, type, "Queen", null, "Rock")
        }) {
            onView(withId(R.id.open_artists)).perform(click())
            esperar { onView(withText("Queen")).check(matches(isDisplayed())) }
            onActivity { activity ->
                val result = bundleOf("artistId" to 101, "artistType" to ArtistType.BAND.name)
                activity.supportFragmentManager.setFragmentResult(ArtistCatalogFragment.ARTIST_SELECTED, result)
                activity.supportFragmentManager.setFragmentResult(ArtistCatalogFragment.ARTIST_SELECTED, result)
            }
            esperar { onView(withId(R.id.artist_detail_title)).check(matches(withText("Queen"))) }
            onActivity { activity -> assertEquals(2, activity.supportFragmentManager.backStackEntryCount) }
        }
        assertEquals(listOf(ArtistType.BAND to 101), calls)
    }

    @Test fun muestra_carga_y_luego_el_detalle_de_la_respuesta() {
        val pending = CompletableDeferred<ArtistDetail>()
        withRepositories(detail = { type, id -> pending.await() }) {
            onView(withId(R.id.open_artists)).perform(click())
            esperar { onView(withText("Queen")).check(matches(isDisplayed())) }
            onView(withText("Queen")).perform(click())
            onView(withId(R.id.artist_detail_loading)).check(matches(isDisplayed()))
            SystemClock.sleep(6_000)
            pending.complete(ArtistDetail(101, ArtistType.BAND, "Queen", null, "Rock"))
            esperar { onView(withId(R.id.artist_detail_title)).check(matches(withText("Queen"))) }
        }
    }

    @Test fun error_de_detalle_permite_reintentar_y_recuperar() {
        var calls = 0
        withRepositories(detail = { type, id ->
            if (++calls == 1) throw IOException("sin red")
            ArtistDetail(101, ArtistType.BAND, "Queen", null, "Rock")
        }) {
            onView(withId(R.id.open_artists)).perform(click())
            esperar { onView(withText("Queen")).check(matches(isDisplayed())) }
            onView(withText("Queen")).perform(click())
            esperar { onView(withText(R.string.artist_detail_error)).check(matches(isDisplayed())) }
            onView(withId(R.id.retry_artist_detail)).perform(click())
            esperar { onView(withId(R.id.artist_detail_title)).check(matches(withText("Queen"))) }
        }
        assertEquals(2, calls)
    }

    @Test fun rotacion_conserva_el_detalle_sin_repetir_la_consulta() {
        val calls = mutableListOf<Pair<ArtistType, Int>>()
        withRepositories(detail = { type, id ->
            calls += type to id
            ArtistDetail(id, type, "Queen", null, "Rock")
        }) {
            onView(withId(R.id.open_artists)).perform(click())
            esperar { onView(withText("Queen")).check(matches(isDisplayed())) }
            onView(withText("Queen")).perform(click())
            esperar { onView(withId(R.id.artist_detail_title)).check(matches(withText("Queen"))) }
            recreate()
            esperar { onView(withId(R.id.artist_detail_title)).check(matches(withText("Queen"))) }
        }
        assertEquals(listOf(ArtistType.BAND to 101), calls)
    }

    private fun withRepositories(
        detail: suspend (ArtistType, Int) -> ArtistDetail,
        block: ActivityScenario<MainActivity>.() -> Unit,
    ) {
        val oldAlbums = app.albumRepository
        val oldArtists = app.artistRepository
        val oldDetails = app.artistDetailRepository
        app.albumRepository = object : AlbumRepository { override suspend fun getAlbums(): List<Album> = emptyList() }
        app.artistRepository = ArtistRepository { artists }
        app.artistDetailRepository = ArtistDetailRepository(detail)
        try { ActivityScenario.launch(MainActivity::class.java).use { it.block() } }
        finally {
            app.albumRepository = oldAlbums
            app.artistRepository = oldArtists
            app.artistDetailRepository = oldDetails
        }
    }

    private fun esperar(check: () -> Unit) {
        val end = SystemClock.elapsedRealtime() + 5_000
        while (true) {
            try { check(); return }
            catch (error: AssertionError) { if (SystemClock.elapsedRealtime() > end) throw error }
            catch (error: androidx.test.espresso.NoMatchingViewException) {
                if (SystemClock.elapsedRealtime() > end) throw error
            }
            SystemClock.sleep(100)
        }
    }
}
