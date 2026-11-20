package co.edu.uniandes.vinilos

import android.graphics.Bitmap
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.RadioButton
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
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

    private fun withBand(collector: Boolean = true, artist: Artist = musician, block: (ActivityScenario<MainActivity>, Bands) -> Unit) {
        val oldArtists = app.artistRepository
        val oldDetail = app.artistDetailRepository
        val oldBands = app.bandMusicianRepository
        val oldCollector = app.activeCollectorStore
        val bands = Bands()
        app.artistRepository = ArtistRepository { listOf(artist) }
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

    @Test fun accesibilidadEtiquetasTamanoTactilYFuenteAmpliada() = withBand { scenario, _ ->
        onView(withId(R.id.add_band_musician)).perform(scrollTo(), click())
        onView(withText("Músico disponible")).perform(click())
        scenario.onActivity { activity ->
            InstrumentationRegistry.getArguments().getString("hu12FontScale")?.toFloat()?.let { expected ->
                assertEquals("Android no aplicó la escala solicitada", expected, activity.resources.configuration.fontScale, 0.01f)
            }
            val options = activity.findViewById<android.widget.RadioGroup>(R.id.musician_selection_options)
            val radio = options.getChildAt(0) as RadioButton
            assertTrue(radio.contentDescription.toString().contains("Músico disponible"))
            assertTrue(radio.isChecked)
            val minimum = 48 * activity.resources.displayMetrics.density
            assertTrue("Objetivo táctil del músico menor de 48 dp", radio.height >= minimum - 1)
            listOf(R.id.musician_selection_cancel, R.id.musician_selection_confirm).forEach { id ->
                val button = activity.findViewById<TextView>(id)
                assertTrue("Botón sin etiqueta", button.text.isNotBlank())
                assertTrue("Botón menor de 48 dp", button.height >= minimum - 1)
            }
            val status = activity.findViewById<View>(R.id.musician_selection_status)
            assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, status.accessibilityLiveRegion)
            Log.i("HU12_QUALITY", "api=${Build.VERSION.SDK_INT}; font_scale=${activity.resources.configuration.fontScale}; labels=true; touch_targets_48dp=true; live_region=polite")
        }
        capture("selector-accesibilidad")
    }

    @Test fun exploracionNombreLargoSinSeleccionYRegreso() {
        val longName = "María de los Ángeles Rodríguez y su agrupación de música latinoamericana"
        withBand(artist = musician.copy(name = longName)) { scenario, bands ->
            onView(withId(R.id.add_band_musician)).perform(scrollTo(), click())
            onView(withId(R.id.musician_selection_confirm)).check(matches(not(isEnabled())))
            onView(withText(longName)).perform(scrollTo(), click())
            scenario.onActivity { activity ->
                val radio = activity.findViewById<android.widget.RadioGroup>(R.id.musician_selection_options).getChildAt(0) as RadioButton
                val layout = checkNotNull(radio.layout)
                assertTrue("Nombre truncado", (0 until layout.lineCount).all { layout.getEllipsisCount(it) == 0 })
            }
            capture("nombre-largo")
            onView(withId(R.id.musician_selection_cancel)).perform(scrollTo(), click())
            onView(withId(R.id.artist_detail_title)).check(matches(withText("Banda de prueba")))
            assertEquals(0, bands.calls)
        }
    }

    @Test fun diezCiclosSelectorRegresoRegistranTiempoYMemoria() = withBand { _, bands ->
        val beforePss = Debug.getPss()
        val times = mutableListOf<Long>()
        repeat(10) {
            val start = SystemClock.elapsedRealtime()
            onView(withId(R.id.add_band_musician)).perform(scrollTo(), click())
            onView(withText("Músico disponible")).check(matches(isDisplayed()))
            times += SystemClock.elapsedRealtime() - start
            pressBack()
            onView(withId(R.id.artist_detail_title)).check(matches(withText("Banda de prueba")))
        }
        val sorted = times.sorted()
        val message = "api=${Build.VERSION.SDK_INT}; cycles=10; open_ms=$times; p50_ms=${(sorted[4]+sorted[5])/2}; max_ms=${sorted.last()}; pss_before_kb=$beforePss; pss_after_kb=${Debug.getPss()}; post_calls=${bands.calls}"
        Log.i("HU12_PERFORMANCE", message)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, "hu12-performance-api-${Build.VERSION.SDK_INT}.txt").writeText(message)
        assertEquals(0, bands.calls)
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val scale = context.resources.configuration.fontScale.toString().replace('.', '_')
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(context.filesDir, "hu12-$name-api-${Build.VERSION.SDK_INT}-font-$scale.png")
            .outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }

}
