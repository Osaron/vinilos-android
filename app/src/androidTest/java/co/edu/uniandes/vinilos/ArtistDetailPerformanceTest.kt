package co.edu.uniandes.vinilos

import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
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
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Medición diagnóstica repetible; no fija umbrales de rendimiento dependientes del emulador. */
@RunWith(AndroidJUnit4::class)
class ArtistDetailPerformanceTest {
    private val app get() = ApplicationProvider.getApplicationContext<VinilosApplication>()

    @Test fun diez_ciclos_catalogo_detalle_regreso_registran_tiempo_y_memoria() {
        val oldAlbums = app.albumRepository
        val oldArtists = app.artistRepository
        val oldDetails = app.artistDetailRepository
        app.albumRepository = object : AlbumRepository {
            override suspend fun getAlbums(): List<Album> = emptyList()
        }
        app.artistRepository = ArtistRepository {
            listOf(Artist(101, ArtistType.BAND, "Queen", "", "Rock"))
        }
        app.artistDetailRepository = ArtistDetailRepository { type, id ->
            ArtistDetail(
                id, type, "Queen", null, "Rock", creationDate = "1970-01-01",
                albums = listOf(
                    ArtistAlbum(1, "A Night at the Opera", null, "1975-11-21", "EMI", "Rock"),
                    ArtistAlbum(2, "News of the World", null, "1977-10-28", "EMI", "Rock"),
                ),
            )
        }

        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                onView(withId(R.id.open_artists)).perform(click())
                esperar { onView(withText("Queen")).check(matches(isDisplayed())) }
                val before = memoriaEstabilizada()
                val timesMs = mutableListOf<Long>()

                repeat(20) { cycle ->
                    val start = SystemClock.elapsedRealtimeNanos()
                    onView(withText("Queen")).perform(click())
                    esperar { onView(withId(R.id.artist_detail_title)).check(matches(withText("Queen"))) }
                    timesMs += (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000
                    onView(withId(R.id.back_artist_detail)).perform(click())
                    esperar { onView(withId(R.id.search_artists)).check(matches(isDisplayed())) }
                    if (cycle == 9) {
                        val middle = memoriaEstabilizada()
                        Log.i(
                            "HU04_PERFORMANCE",
                            "checkpoint=10; pss_kb=${middle.pssKb}; " +
                                "pss_delta_from_start_kb=${middle.pssKb - before.pssKb}; " +
                                "java_heap_kb=${middle.javaHeapKb}",
                        )
                    }
                }

                val after = memoriaEstabilizada()
                val firstTen = timesMs.take(10).sorted()
                val secondTen = timesMs.takeLast(10).sorted()
                val firstMedian = (firstTen[4] + firstTen[5]) / 2
                val secondMedian = (secondTen[4] + secondTen[5]) / 2
                Log.i(
                    "HU04_PERFORMANCE",
                    "cycles=20; first10_detail_visible_ms_p50=$firstMedian; " +
                        "second10_detail_visible_ms_p50=$secondMedian; " +
                        "pss_kb_before=${before.pssKb}; pss_kb_after=${after.pssKb}; " +
                        "pss_delta_kb=${after.pssKb - before.pssKb}; " +
                        "java_heap_kb_before=${before.javaHeapKb}; java_heap_kb_after=${after.javaHeapKb}; " +
                        "java_heap_delta_kb=${after.javaHeapKb - before.javaHeapKb}",
                )
            }
        } finally {
            app.albumRepository = oldAlbums
            app.artistRepository = oldArtists
            app.artistDetailRepository = oldDetails
        }
    }

    private fun memoriaEstabilizada(): MemorySnapshot {
        System.gc()
        Runtime.getRuntime().gc()
        SystemClock.sleep(300)
        val runtime = Runtime.getRuntime()
        return MemorySnapshot(Debug.getPss(), (runtime.totalMemory() - runtime.freeMemory()) / 1024)
    }

    private fun esperar(check: () -> Unit) {
        val end = SystemClock.elapsedRealtime() + 5_000
        while (true) {
            try {
                check()
                return
            } catch (error: AssertionError) {
                if (SystemClock.elapsedRealtime() > end) throw error
            } catch (error: androidx.test.espresso.NoMatchingViewException) {
                if (SystemClock.elapsedRealtime() > end) throw error
            }
            SystemClock.sleep(100)
        }
    }

    private data class MemorySnapshot(val pssKb: Long, val javaHeapKb: Long)
}
