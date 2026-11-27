package co.edu.uniandes.vinilos

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withSubstring
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.data.ActiveCollector
import co.edu.uniandes.vinilos.data.InMemoryActiveCollectorStore
import co.edu.uniandes.vinilos.ui.albums.picker.AlbumPicker
import co.edu.uniandes.vinilos.ui.collectors.CollectorDetailFragment
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CollectorAlbumBackendRealTest {
    @Test fun guardaDesdeElDetalleYRecargaLaColeccionActiva() {
        assumeTrue(
            "Requiere un BackVynils efímero y datos de prueba",
            InstrumentationRegistry.getArguments().getString("collectorAlbumBackendReal") == "true",
        )
        val app = ApplicationProvider.getApplicationContext<VinilosApplication>()
        val (owner, other) = runBlocking { app.collectorRepository.getCollectors().take(2) }
        val originalStore = app.activeCollectorStore
        val (album, otherBefore) = runBlocking {
            val existing = app.collectorAlbumRepository.getAlbumIds(owner.id)
            val candidate = app.albumRepository.getAlbums().first { it.id !in existing }
            candidate to app.collectorAlbumRepository.getAlbumIds(other.id)
        }
        app.activeCollectorStore = InMemoryActiveCollectorStore(ActiveCollector(owner.id, owner.name))
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, CollectorDetailFragment.newInstance(owner.id))
                        .commitNow()
                }
                await { onView(withId(R.id.add_collector_album)).check(matches(isDisplayed())) }
                onView(withId(R.id.add_collector_album)).perform(scrollTo(), click())
                await { onView(withText(AlbumPicker.label(album))).check(matches(withText(AlbumPicker.label(album)))) }
                onView(withText(AlbumPicker.label(album))).perform(scrollTo(), click())
                onView(withId(R.id.collector_album_price)).perform(scrollTo(), replaceText("41"), closeSoftKeyboard())
                onView(withId(R.id.collector_album_confirm)).perform(scrollTo(), click())
                await { onView(withId(R.id.collector_album_list)).check(matches(withSubstring(album.name))) }
            }
            runBlocking { assertEquals(otherBefore, app.collectorAlbumRepository.getAlbumIds(other.id)) }
        } finally {
            app.activeCollectorStore = originalStore
        }
    }

    private fun await(assertion: () -> Unit) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (true) {
            try {
                assertion()
                return
            } catch (error: NoMatchingViewException) {
                if (SystemClock.elapsedRealtime() >= deadline) throw error
            } catch (error: AssertionError) {
                if (SystemClock.elapsedRealtime() >= deadline) throw error
            }
            SystemClock.sleep(150)
        }
    }
}
