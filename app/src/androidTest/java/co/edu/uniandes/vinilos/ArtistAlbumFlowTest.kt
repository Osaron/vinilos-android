package co.edu.uniandes.vinilos

import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.edu.uniandes.vinilos.data.ActiveCollector
import co.edu.uniandes.vinilos.data.AlbumRepository
import co.edu.uniandes.vinilos.data.ArtistAlbumRepository
import co.edu.uniandes.vinilos.data.ArtistDetailRepository
import co.edu.uniandes.vinilos.data.BandMusicianRepository
import co.edu.uniandes.vinilos.data.FavoriteArtistRepository
import co.edu.uniandes.vinilos.data.InMemoryActiveCollectorStore
import co.edu.uniandes.vinilos.model.Album
import co.edu.uniandes.vinilos.model.Artist
import co.edu.uniandes.vinilos.model.ArtistAlbum
import co.edu.uniandes.vinilos.model.ArtistDetail
import co.edu.uniandes.vinilos.model.ArtistType
import co.edu.uniandes.vinilos.model.FavoritePerformer
import co.edu.uniandes.vinilos.ui.artists.ArtistDetailFragment
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** HU15 · ESC-46 a ESC-52: agregar un álbum a la discografía de un músico o una banda. */
@RunWith(AndroidJUnit4::class)
class ArtistAlbumFlowTest {
    private val app get() = ApplicationProvider.getApplicationContext<VinilosApplication>()
    private val catalog = listOf(
        Album(100, "Buscando América", "", listOf("Rubén Blades"), "Salsa"),
        Album(101, "Poeta del pueblo", "", listOf("Rubén Blades"), "Salsa"),
        Album(102, "A Night at the Opera", "", listOf("Queen"), "Rock"),
    )

    /** Discografía en memoria del artista del detalle. */
    private class Discography(private val type: ArtistType, initial: Set<Int>) : ArtistAlbumRepository {
        val albums = initial.toMutableSet()
        val saved = mutableListOf<Triple<ArtistType, Int, Int>>()
        var rejected = false
        override suspend fun getAlbumIds(type: ArtistType, artistId: Int): Set<Int> {
            assertEquals(this.type, type)
            assertEquals(50, artistId)
            return albums.toSet()
        }
        override suspend fun addAlbum(type: ArtistType, artistId: Int, albumId: Int) {
            saved += Triple(type, artistId, albumId)
            if (rejected) throw IllegalStateException("rechazo controlado")
            albums += albumId
        }
    }

    private fun withArtist(
        type: ArtistType = ArtistType.MUSICIAN,
        collector: Boolean = true,
        initial: Set<Int> = setOf(100),
        block: (ActivityScenario<MainActivity>, Discography) -> Unit,
    ) {
        val old = listOf(app.albumRepository, app.artistDetailRepository, app.artistAlbumRepository,
            app.favoriteArtistRepository, app.bandMusicianRepository, app.activeCollectorStore)
        val discography = Discography(type, initial)
        app.albumRepository = AlbumRepository { catalog }
        app.artistDetailRepository = ArtistDetailRepository { artistType, id ->
            ArtistDetail(id, artistType, "Artista de prueba", null, "Descripción",
                albums = catalog.filter { it.id in discography.albums }.map {
                    ArtistAlbum(it.id, it.name, null, "1984-08-01", "Elektra", it.genre)
                })
        }
        app.artistAlbumRepository = discography
        app.favoriteArtistRepository = object : FavoriteArtistRepository {
            override suspend fun getFavorites(collectorId: Int): List<FavoritePerformer> = emptyList()
            override suspend fun addFavorite(collectorId: Int, artistId: Int, type: ArtistType): FavoritePerformer =
                error("HU15 no debe enviar favoritos")
        }
        app.bandMusicianRepository = object : BandMusicianRepository {
            override suspend fun getMembers(bandId: Int): List<Artist> = emptyList()
            override suspend fun addMusician(bandId: Int, musicianId: Int): Artist = error("HU15 no asocia músicos")
        }
        app.activeCollectorStore = InMemoryActiveCollectorStore(if (collector) ActiveCollector(7, "José Pérez") else null)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity {
                    it.supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, ArtistDetailFragment.newInstance(50, type.name))
                        .commitNow()
                }
                block(scenario, discography)
            }
        } finally {
            app.albumRepository = old[0] as AlbumRepository
            app.artistDetailRepository = old[1] as ArtistDetailRepository
            app.artistAlbumRepository = old[2] as ArtistAlbumRepository
            app.favoriteArtistRepository = old[3] as FavoriteArtistRepository
            app.bandMusicianRepository = old[4] as BandMusicianRepository
            app.activeCollectorStore = old[5] as co.edu.uniandes.vinilos.data.ActiveCollectorStore
        }
    }

    private fun abrirSelector() {
        onView(withId(R.id.add_artist_album)).perform(scrollTo(), click())
        onView(withId(R.id.artist_album_title)).check(matches(isDisplayed()))
    }

    private fun confirmar() {
        onView(withId(R.id.artist_album_confirm)).perform(scrollTo(), click())
        onView(withText(R.string.hu15_add)).inRoot(isDialog()).perform(click())
    }

    private fun albumEnDiscografia(name: String) =
        onView(allOf(withId(R.id.artist_detail_album_name), withText(name)))

    @Test fun esc46_sinColeccionistaActivoNoSeOfreceLaAccion() = withArtist(collector = false) { _, discography ->
        onView(withId(R.id.artist_detail_title)).check(matches(withText("Artista de prueba")))
        onView(withId(R.id.add_artist_album)).check(matches(not(isDisplayed())))
        assertTrue(discography.saved.isEmpty())
    }

    @Test fun esc47_agregaUnAlbumAUnMusicoConBusquedaYRotacion() = withArtist { scenario, discography ->
        abrirSelector()
        onView(withId(R.id.artist_album_context)).check(matches(withText("Artista de prueba · Músico")))
        // Ya asociado: no se ofrece.
        onView(withText("Buscando América · Rubén Blades")).check(doesNotExist())
        onView(withId(R.id.album_picker_search)).perform(replaceText("queen"), closeSoftKeyboard())
        onView(withText("Poeta del pueblo · Rubén Blades")).check(doesNotExist())
        onView(withText("A Night at the Opera · Queen")).perform(click())
        scenario.recreate()
        onView(withId(R.id.album_picker_search)).check(matches(withText("queen")))
        onView(withText("A Night at the Opera · Queen")).check(matches(isChecked()))
        onView(withId(R.id.artist_album_selected)).check(matches(withText("Álbum elegido: A Night at the Opera")))
        confirmar()
        onView(withId(R.id.artist_detail_album_status)).check(matches(withText(R.string.hu15_saved)))
        albumEnDiscografia("A Night at the Opera").perform(scrollTo()).check(matches(isDisplayed()))
        assertEquals(listOf(Triple(ArtistType.MUSICIAN, 50, 102)), discography.saved)
    }

    @Test fun esc48_agregaUnAlbumAUnaBandaPorSuTipo() = withArtist(type = ArtistType.BAND, initial = setOf(102)) { _, discography ->
        abrirSelector()
        onView(withId(R.id.artist_album_context)).check(matches(withText("Artista de prueba · Banda")))
        onView(withText("Buscando América · Rubén Blades")).perform(click())
        confirmar()
        albumEnDiscografia("Buscando América").perform(scrollTo()).check(matches(isDisplayed()))
        assertEquals(listOf(Triple(ArtistType.BAND, 50, 100)), discography.saved)
    }

    @Test fun esc49_unErrorConservaLaSeleccionYElReintentoGuarda() = withArtist { _, discography ->
        discography.rejected = true
        abrirSelector()
        onView(withText("Poeta del pueblo · Rubén Blades")).perform(click())
        confirmar()
        onView(withId(R.id.artist_album_status)).check(matches(withText(R.string.hu15_save_failed)))
        onView(withText("Poeta del pueblo · Rubén Blades")).check(matches(isChecked()))
        discography.rejected = false
        confirmar()
        albumEnDiscografia("Poeta del pueblo").perform(scrollTo()).check(matches(isDisplayed()))
        assertEquals(2, discography.saved.size)
    }

    @Test fun esc50_cancelarLaConfirmacionNoEnviaYEstaEnEspanol() = withArtist { _, discography ->
        abrirSelector()
        onView(withText("Poeta del pueblo · Rubén Blades")).perform(click())
        onView(withId(R.id.artist_album_confirm)).perform(scrollTo(), click())
        onView(withText("Cancelar")).inRoot(isDialog()).perform(click())
        onView(withText("Poeta del pueblo · Rubén Blades")).check(matches(isChecked()))
        assertTrue(discography.saved.isEmpty())
    }

    @Test fun esc51_siTodoYaEstaAsociadoLoInformaYNoPermiteConfirmar() = withArtist(initial = setOf(100, 101, 102)) { _, discography ->
        abrirSelector()
        onView(withId(R.id.album_picker_empty)).check(matches(withText(R.string.hu15_empty)))
        onView(withId(R.id.artist_album_confirm)).check(matches(not(androidx.test.espresso.matcher.ViewMatchers.isEnabled())))
        assertTrue(discography.saved.isEmpty())
    }

    @Test fun esc52_accesibilidadDelSelector() = withArtist { scenario, _ ->
        abrirSelector()
        onView(withId(R.id.album_picker_search)).perform(replaceText("zzz"), closeSoftKeyboard())
        onView(withId(R.id.album_picker_empty)).check(matches(withText(R.string.album_picker_no_results)))
        onView(withId(R.id.album_picker_search)).perform(replaceText(""), closeSoftKeyboard())
        onView(withText("Poeta del pueblo · Rubén Blades")).perform(click())
        scenario.onActivity { activity ->
            val options = activity.findViewById<RadioGroup>(R.id.album_picker_options)
            val density = activity.resources.displayMetrics.density
            (0 until options.childCount).map { options.getChildAt(it) as RadioButton }.forEach { radio ->
                val description = radio.contentDescription.toString()
                assertTrue("Incluye nombre e intérprete: $description", description.contains(radio.text.split(" · ").first()) && description.contains("Rubén Blades|Queen".toRegex()))
                assertTrue("Objetivo táctil de 48 dp", radio.height >= (48 * density).toInt() - 1)
            }
            assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, activity.findViewById<View>(R.id.artist_album_selected).accessibilityLiveRegion)
            assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, activity.findViewById<View>(R.id.artist_album_status).accessibilityLiveRegion)
            // Lanza una excepción si el motor de Accessibility Scanner encuentra un ERROR.
            AccessibilityValidator().setRunChecksFromRootView(false)
                .checkAndReturnResults(activity.findViewById(R.id.fragment_container))
        }
        onView(withId(R.id.artist_album_selected)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
    }
}
