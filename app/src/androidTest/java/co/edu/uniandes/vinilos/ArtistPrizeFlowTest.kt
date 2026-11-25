package co.edu.uniandes.vinilos

import android.view.View
import android.widget.DatePicker
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.PickerActions
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withClassName
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.edu.uniandes.vinilos.data.ActiveCollector
import co.edu.uniandes.vinilos.data.ActiveCollectorStore
import co.edu.uniandes.vinilos.data.AlbumRepository
import co.edu.uniandes.vinilos.data.ArtistAlbumRepository
import co.edu.uniandes.vinilos.data.ArtistDetailRepository
import co.edu.uniandes.vinilos.data.ArtistPrizeRepository
import co.edu.uniandes.vinilos.data.BandMusicianRepository
import co.edu.uniandes.vinilos.data.FavoriteArtistRepository
import co.edu.uniandes.vinilos.data.InMemoryActiveCollectorStore
import co.edu.uniandes.vinilos.model.Artist
import co.edu.uniandes.vinilos.model.ArtistDetail
import co.edu.uniandes.vinilos.model.ArtistPrize
import co.edu.uniandes.vinilos.model.ArtistType
import co.edu.uniandes.vinilos.model.FavoritePerformer
import co.edu.uniandes.vinilos.model.Prize
import co.edu.uniandes.vinilos.ui.artists.ArtistDetailFragment
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/** HU14 · ESC-53 a ESC-59: asignar un premio, con fecha, a un músico o una banda. */
@RunWith(AndroidJUnit4::class)
class ArtistPrizeFlowTest {
    private val app get() = ApplicationProvider.getApplicationContext<VinilosApplication>()
    private val catalog = listOf(
        Prize(100, "Grammy Award", "NARAS"),
        Prize(101, "Premios lo Nuestro", "Univisión"),
    )

    /** Premios en memoria del artista del detalle. Falla si llega otro tipo u otro ID. */
    private inner class Awards(private val type: ArtistType) : ArtistPrizeRepository {
        val awarded = mutableListOf(ArtistPrize(1, 100, "Grammy Award", "NARAS", "1978-12-10"))
        val saved = mutableListOf<List<Any>>()
        var rejected = false
        var offline = false
        override suspend fun getPrizes(): List<Prize> {
            if (offline) throw IOException("sin red")
            return catalog
        }
        override suspend fun getArtistPrizes(type: ArtistType, artistId: Int): List<ArtistPrize> {
            assertEquals(this.type, type)
            assertEquals(50, artistId)
            if (offline) throw IOException("sin red")
            return awarded.sortedByDescending { it.date }
        }
        override suspend fun assignPrize(type: ArtistType, artistId: Int, prizeId: Int, date: String) {
            saved += listOf(type, artistId, prizeId, date)
            if (rejected) throw IllegalStateException("rechazo controlado")
            val prize = catalog.first { it.id == prizeId }
            awarded += ArtistPrize(100 + saved.size, prizeId, prize.name, prize.organization, date)
        }
    }

    private fun withArtist(
        type: ArtistType = ArtistType.MUSICIAN,
        collector: Boolean = true,
        block: (ActivityScenario<MainActivity>, Awards) -> Unit,
    ) {
        val old = listOf(app.albumRepository, app.artistDetailRepository, app.artistAlbumRepository,
            app.favoriteArtistRepository, app.bandMusicianRepository, app.activeCollectorStore, app.artistPrizeRepository)
        val awards = Awards(type)
        app.albumRepository = AlbumRepository { emptyList() }
        app.artistDetailRepository = ArtistDetailRepository { artistType, id ->
            ArtistDetail(id, artistType, "Artista de prueba", null, "Descripción")
        }
        app.artistAlbumRepository = object : ArtistAlbumRepository {
            override suspend fun getAlbumIds(type: ArtistType, artistId: Int): Set<Int> = emptySet()
            override suspend fun addAlbum(type: ArtistType, artistId: Int, albumId: Int) = error("HU14 no asocia álbumes")
        }
        app.favoriteArtistRepository = object : FavoriteArtistRepository {
            override suspend fun getFavorites(collectorId: Int): List<FavoritePerformer> = emptyList()
            override suspend fun addFavorite(collectorId: Int, artistId: Int, type: ArtistType): FavoritePerformer =
                error("HU14 no debe enviar favoritos")
        }
        app.bandMusicianRepository = object : BandMusicianRepository {
            override suspend fun getMembers(bandId: Int): List<Artist> = emptyList()
            override suspend fun addMusician(bandId: Int, musicianId: Int): Artist = error("HU14 no asocia músicos")
        }
        app.activeCollectorStore = InMemoryActiveCollectorStore(if (collector) ActiveCollector(7, "José Pérez") else null)
        app.artistPrizeRepository = awards
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity {
                    it.supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, ArtistDetailFragment.newInstance(50, type.name))
                        .commitNow()
                }
                block(scenario, awards)
            }
        } finally {
            app.albumRepository = old[0] as AlbumRepository
            app.artistDetailRepository = old[1] as ArtistDetailRepository
            app.artistAlbumRepository = old[2] as ArtistAlbumRepository
            app.favoriteArtistRepository = old[3] as FavoriteArtistRepository
            app.bandMusicianRepository = old[4] as BandMusicianRepository
            app.activeCollectorStore = old[5] as ActiveCollectorStore
            app.artistPrizeRepository = old[6] as ArtistPrizeRepository
        }
    }

    private fun abrirFormulario() {
        onView(withId(R.id.add_artist_prize)).perform(scrollTo(), click())
        onView(withId(R.id.assign_prize_title)).check(matches(isDisplayed()))
    }

    private fun elegirFecha(year: Int, month: Int, day: Int) {
        onView(withId(R.id.assign_prize_pick_date)).perform(scrollTo(), click())
        onView(withClassName(equalTo(DatePicker::class.java.name))).inRoot(isDialog())
            .perform(PickerActions.setDate(year, month, day))
        onView(withText(R.string.hu14_date_accept)).inRoot(isDialog()).perform(click())
    }

    private fun guardar() = onView(withId(R.id.assign_prize_confirm)).perform(scrollTo(), click())

    @Test fun esc53_sinColeccionistaActivoSeVenLosPremiosPeroNoLaAccion() = withArtist(collector = false) { _, awards ->
        onView(withId(R.id.artist_detail_title)).check(matches(withText("Artista de prueba")))
        onView(withText("Grammy Award · NARAS · 10/12/1978")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withId(R.id.add_artist_prize)).check(matches(not(isDisplayed())))
        assertTrue(awards.saved.isEmpty())
    }

    @Test fun esc54_asignaUnPremioAUnMusicoYConservaElBorradorAlRotar() = withArtist { scenario, awards ->
        abrirFormulario()
        onView(withId(R.id.assign_prize_context)).check(matches(withText("Artista de prueba · Músico")))
        onView(withId(R.id.assign_prize_status)).check(matches(withText(R.string.hu14_hint)))
        onView(withId(R.id.assign_prize_confirm)).check(matches(not(isEnabled())))
        onView(withText("Premios lo Nuestro · Univisión")).perform(scrollTo(), click())
        onView(withId(R.id.assign_prize_confirm)).check(matches(not(isEnabled())))
        elegirFecha(2020, 5, 1)
        scenario.recreate()
        onView(withText("Premios lo Nuestro · Univisión")).check(matches(isChecked()))
        onView(withId(R.id.assign_prize_date)).check(matches(withText("Fecha de entrega: 01/05/2020")))
        guardar()
        onView(withId(R.id.artist_detail_prize_status)).perform(scrollTo()).check(matches(withText(R.string.hu14_saved)))
        onView(withText("Premios lo Nuestro · Univisión · 01/05/2020")).perform(scrollTo()).check(matches(isDisplayed()))
        assertEquals(listOf(listOf(ArtistType.MUSICIAN, 50, 101, "2020-05-01")), awards.saved)
    }

    @Test fun esc55_asignaUnPremioAUnaBandaPorSuTipo() = withArtist(type = ArtistType.BAND) { _, awards ->
        abrirFormulario()
        onView(withId(R.id.assign_prize_context)).check(matches(withText("Artista de prueba · Banda")))
        onView(withText("Grammy Award · NARAS")).perform(scrollTo(), click())
        elegirFecha(1985, 2, 26)
        guardar()
        onView(withText("Grammy Award · NARAS · 26/02/1985")).perform(scrollTo()).check(matches(isDisplayed()))
        assertEquals(listOf(listOf(ArtistType.BAND, 50, 100, "1985-02-26")), awards.saved)
    }

    @Test fun esc56_unErrorConservaPremioYFechaYElReintentoGuarda() = withArtist { _, awards ->
        awards.rejected = true
        abrirFormulario()
        onView(withText("Premios lo Nuestro · Univisión")).perform(scrollTo(), click())
        elegirFecha(2020, 5, 1)
        guardar()
        onView(withId(R.id.assign_prize_status)).check(matches(withText(R.string.hu14_save_failed)))
        onView(withText("Premios lo Nuestro · Univisión")).check(matches(isChecked()))
        onView(withId(R.id.assign_prize_date)).check(matches(withText("Fecha de entrega: 01/05/2020")))
        awards.rejected = false
        guardar()
        onView(withText("Premios lo Nuestro · Univisión · 01/05/2020")).perform(scrollTo()).check(matches(isDisplayed()))
        assertEquals(2, awards.saved.size)
    }

    @Test fun esc57_noPermiteRepetirElMismoPremioConLaMismaFecha() = withArtist { _, awards ->
        abrirFormulario()
        onView(withText("Grammy Award · NARAS")).perform(scrollTo(), click())
        elegirFecha(1978, 12, 10)
        onView(withId(R.id.assign_prize_status)).check(matches(withText(R.string.hu14_duplicate)))
        onView(withId(R.id.assign_prize_confirm)).check(matches(not(isEnabled())))
        // El mismo premio en otra fecha es otra entrega y sí se permite.
        elegirFecha(1979, 12, 10)
        onView(withId(R.id.assign_prize_confirm)).check(matches(isEnabled()))
        assertTrue(awards.saved.isEmpty())
    }

    @Test fun esc58_unFalloDeRedAlCargarOfreceReintentar() = withArtist { _, awards ->
        awards.offline = true
        abrirFormulario()
        onView(withId(R.id.assign_prize_retry)).check(matches(isDisplayed()))
        onView(withText("Grammy Award · NARAS")).check(doesNotExist())
        onView(withId(R.id.assign_prize_confirm)).check(matches(not(isEnabled())))
        awards.offline = false
        onView(withId(R.id.assign_prize_retry)).perform(click())
        onView(withText("Grammy Award · NARAS")).perform(scrollTo()).check(matches(isDisplayed()))
        onView(withId(R.id.assign_prize_cancel)).perform(scrollTo(), click())
        onView(withId(R.id.artist_detail_title)).check(matches(isDisplayed()))
        assertTrue(awards.saved.isEmpty())
    }

    @Test fun esc59_accesibilidadDelFormularioYDeLosPremiosDelDetalle() = withArtist { scenario, _ ->
        scenario.onActivity { activity ->
            val row = activity.findViewById<android.view.ViewGroup>(R.id.artist_detail_prizes).getChildAt(0)
            assertEquals("Grammy Award, de NARAS, recibido el 10/12/1978", row.contentDescription.toString())
            AccessibilityValidator().setRunChecksFromRootView(false)
                .checkAndReturnResults(activity.findViewById(R.id.fragment_container))
        }
        abrirFormulario()
        onView(withText("Grammy Award · NARAS")).perform(scrollTo(), click())
        elegirFecha(2020, 5, 1)
        scenario.onActivity { activity ->
            val options = activity.findViewById<RadioGroup>(R.id.assign_prize_options)
            val density = activity.resources.displayMetrics.density
            val descriptions = (0 until options.childCount).map { options.getChildAt(it) as RadioButton }.map { radio ->
                assertTrue("Objetivo táctil de 48 dp", radio.height >= (48 * density).toInt() - 1)
                radio.contentDescription.toString()
            }
            assertEquals(
                listOf("Seleccionar premio Grammy Award, de NARAS", "Seleccionar premio Premios lo Nuestro, de Univisión"),
                descriptions,
            )
            listOf(R.id.assign_prize_date, R.id.assign_prize_status, R.id.assign_prize_empty).forEach {
                assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, activity.findViewById<View>(it).accessibilityLiveRegion)
            }
            val pickDate = activity.findViewById<View>(R.id.assign_prize_pick_date)
            assertTrue("Objetivo táctil de 48 dp", pickDate.height >= (48 * density).toInt() - 1)
            // Lanza una excepción si el motor de Accessibility Scanner encuentra un ERROR.
            AccessibilityValidator().setRunChecksFromRootView(false)
                .checkAndReturnResults(activity.findViewById(R.id.fragment_container))
        }
        onView(withId(R.id.assign_prize_pick_date)).check(matches(withText(R.string.hu14_date_change)))
    }
}
