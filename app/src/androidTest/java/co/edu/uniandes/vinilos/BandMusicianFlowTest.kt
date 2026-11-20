package co.edu.uniandes.vinilos

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.edu.uniandes.vinilos.data.*
import co.edu.uniandes.vinilos.model.*
import co.edu.uniandes.vinilos.ui.artists.ArtistDetailFragment
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BandMusicianFlowTest {
    private val app get() = ApplicationProvider.getApplicationContext<VinilosApplication>()
    private val musician = Artist(101, ArtistType.MUSICIAN, "Músico disponible", "", "")

    private class Bands : BandMusicianRepository {
        val members = mutableListOf<Artist>()
        var calls = 0
        var rejected = false
        override suspend fun getMembers(bandId: Int): List<Artist> = members.toList()
        override suspend fun addMusician(bandId: Int, musicianId: Int): Artist {
            assertEquals(200, bandId)
            assertEquals(101, musicianId)
            calls++
            if (rejected) throw IllegalStateException("rechazo controlado")
            return Artist(101, ArtistType.MUSICIAN, "Músico disponible", "", "").also { members.add(it) }
        }
    }

    private fun withBand(collector: Boolean = true, block: (ActivityScenario<MainActivity>, Bands) -> Unit) {
        val oldArtists = app.artistRepository
        val oldDetail = app.artistDetailRepository
        val oldBands = app.bandMusicianRepository
        val oldFavorites = app.favoriteArtistRepository
        app.favoriteArtistRepository = object : FavoriteArtistRepository {
            override suspend fun getFavorites(collectorId: Int): List<FavoritePerformer> = emptyList()
            override suspend fun addFavorite(collectorId: Int, artistId: Int, type: ArtistType): FavoritePerformer =
                error("HU12 no debe enviar favoritos")
        }
        val oldCollector = app.activeCollectorStore
        val bands = Bands()
        app.artistRepository = ArtistRepository { listOf(musician) }
        app.artistDetailRepository = ArtistDetailRepository { type, id ->
            ArtistDetail(id, type, "Banda de prueba", null, "Descripción")
        }
        app.bandMusicianRepository = bands
        app.activeCollectorStore = InMemoryActiveCollectorStore(
            if (collector) ActiveCollector(100, "Coleccionista de prueba") else null,
        )
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity {
                    it.supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, ArtistDetailFragment.newInstance(200, ArtistType.BAND.name))
                        .commitNow()
                }
                block(scenario, bands)
            }
        } finally {
            app.artistRepository = oldArtists
            app.artistDetailRepository = oldDetail
            app.bandMusicianRepository = oldBands
            app.favoriteArtistRepository = oldFavorites
            app.activeCollectorStore = oldCollector
        }
    }

    @Test fun visitanteNoVeLaAccion() = withBand(false) { _, bands ->
        onView(withId(R.id.artist_detail_title)).check(matches(withText("Banda de prueba")))
        onView(withId(R.id.add_band_musician)).check(matches(not(isDisplayed())))
        assertEquals(0, bands.calls)
    }

    @Test fun seleccionRotacionGuardadoYRefresco() = withBand { scenario, bands ->
        onView(withId(R.id.add_band_musician)).perform(scrollTo(), click())
        onView(withText("Músico disponible")).perform(click())
        onView(withId(R.id.musician_selection_band)).check(matches(withText("Banda de prueba · ID 200")))
        scenario.recreate()
        onView(withText("Músico disponible")).check(matches(isChecked()))
        onView(withId(R.id.musician_selection_confirm)).perform(scrollTo(), click())
        onView(withText(R.string.hu12_choose)).inRoot(isDialog()).perform(click())
        onView(withId(R.id.band_members)).check(matches(withText("Músico disponible")))
        assertEquals(1, bands.calls)
        onView(withId(R.id.add_band_musician)).perform(scrollTo(), click())
        onView(withId(R.id.musician_selection_empty)).check(matches(isDisplayed()))
    }

    @Test fun cancelarNoEnvia() = withBand { _, bands ->
        onView(withId(R.id.add_band_musician)).perform(scrollTo(), click())
        onView(withText("Músico disponible")).perform(click())
        pressBack()
        onView(withId(R.id.artist_detail_title)).check(matches(withText("Banda de prueba")))
        assertEquals(0, bands.calls)
    }
    @Test fun errorConservaSeleccionYReintentoGuarda() = withBand { _, bands ->
        bands.rejected = true
        onView(withId(R.id.add_band_musician)).perform(scrollTo(), click())
        onView(withText("Músico disponible")).perform(click())
        onView(withId(R.id.musician_selection_confirm)).perform(scrollTo(), click())
        onView(withText(R.string.hu12_choose)).inRoot(isDialog()).perform(click())
        onView(withId(R.id.musician_selection_status)).check(matches(withText(R.string.hu12_save_failed)))
        onView(withText("Músico disponible")).check(matches(isChecked()))
        bands.rejected = false
        onView(withId(R.id.musician_selection_confirm)).perform(scrollTo(), click())
        onView(withText(R.string.hu12_choose)).inRoot(isDialog()).perform(click())
        onView(withId(R.id.band_members)).check(matches(withText("Músico disponible")))
        assertEquals(2, bands.calls)
    }

    @Test fun cancelarConfirmacionConservaSeleccionSinEnviar() = withBand { _, bands ->
        onView(withId(R.id.add_band_musician)).perform(scrollTo(), click())
        onView(withText("Músico disponible")).perform(click())
        onView(withId(R.id.musician_selection_confirm)).perform(scrollTo(), click())
        onView(withText(R.string.hu12_cancel)).inRoot(isDialog()).perform(click())
        onView(withText("Músico disponible")).check(matches(isChecked()))
        assertEquals(0, bands.calls)
    }

}
