package co.edu.uniandes.vinilos

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.edu.uniandes.vinilos.data.AlbumRepository
import co.edu.uniandes.vinilos.data.ArtistRepository
import co.edu.uniandes.vinilos.model.Artist
import co.edu.uniandes.vinilos.model.ArtistType
import co.edu.uniandes.vinilos.ui.artists.ArtistCatalogFragment
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/** Repositorios controlados; no se presenta como prueba contra backend real. */
@RunWith(AndroidJUnit4::class)
class ArtistCatalogTest {
    private val band = Artist(7, ArtistType.BAND, "Café Tacvba", "", "")
    private val musician = Artist(7, ArtistType.MUSICIAN, "Celia Cruz", "", "")
    private val app get() = ApplicationProvider.getApplicationContext<VinilosApplication>()

    private fun withCatalog(repository: ArtistRepository, block: (ActivityScenario<MainActivity>) -> Unit) {
        val originalArtists = app.artistRepository
        val originalAlbums = app.albumRepository
        app.artistRepository = repository
        app.albumRepository = AlbumRepository { emptyList() }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                onView(withId(R.id.open_artists)).perform(click())
                block(scenario)
            }
        } finally {
            app.artistRepository = originalArtists
            app.albumRepository = originalAlbums
        }
    }

    @Test fun buscaSeleccionaConTipoYConservaAlRotar() = withCatalog(ArtistRepository { listOf(musician, band) }) { scenario ->
        onView(withId(R.id.search_artists)).perform(typeText("cafe"), closeSoftKeyboard())
        onView(withText("Café Tacvba")).check(matches(isDisplayed()))
        var selectedId = -1
        var selectedType = ""
        scenario.onActivity { activity ->
            activity.supportFragmentManager.setFragmentResultListener(ArtistCatalogFragment.ARTIST_SELECTED, activity) { _, data ->
                selectedId = data.getInt("artistId")
                selectedType = data.getString("artistType").orEmpty()
            }
        }
        onView(withId(R.id.artist_list)).perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(0, click()))
        scenario.onActivity {
            assertEquals(7, selectedId)
            assertEquals("BAND", selectedType)
        }
        scenario.recreate()
        onView(withId(R.id.search_artists)).check(matches(withText("cafe")))
        onView(withId(R.id.artist_selection)).check(matches(withText("Artista seleccionado: Café Tacvba")))
        onView(withId(R.id.back_to_albums)).perform(click())
        onView(withId(R.id.open_artists)).check(matches(isDisplayed()))
    }

    @Test fun informaCatalogoVacio() = withCatalog(ArtistRepository { emptyList() }) {
        onView(withText(R.string.artists_empty)).check(matches(isDisplayed()))
    }

    @Test fun informaBusquedaSinResultados() = withCatalog(ArtistRepository { listOf(band) }) {
        onView(withId(R.id.search_artists)).perform(typeText("zzz"), closeSoftKeyboard())
        onView(withText(R.string.artists_no_results)).check(matches(isDisplayed()))
    }

    @Test fun reintentaTrasFallo() {
        var calls = 0
        withCatalog(ArtistRepository { if (++calls == 1) throw IOException() else listOf(band) }) {
            onView(withText(R.string.artists_error)).check(matches(isDisplayed()))
            onView(withId(R.id.retry_artists)).perform(click())
            onView(withText("Café Tacvba")).check(matches(isDisplayed()))
        }
    }
}
