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
import co.edu.uniandes.vinilos.data.CollectorRepository
import co.edu.uniandes.vinilos.model.Collector
import co.edu.uniandes.vinilos.ui.collectors.CollectorCatalogFragment
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/** Repositorios controlados; no se presenta como prueba contra backend real. */
@RunWith(AndroidJUnit4::class)
class CollectorCatalogTest {
    private val jose = Collector(7, "José Pérez", "jose@example.org", "")
    private val ana = Collector(8, "Ana María", "", "")
    private val app get() = ApplicationProvider.getApplicationContext<VinilosApplication>()

    private fun withCatalog(repository: CollectorRepository, block: (ActivityScenario<MainActivity>) -> Unit) {
        val originalCollectors = app.collectorRepository
        val originalAlbums = app.albumRepository
        app.collectorRepository = repository
        app.albumRepository = AlbumRepository { emptyList() }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                onView(withId(R.id.open_collectors)).perform(scrollTo(), click())
                block(scenario)
            }
        } finally {
            app.collectorRepository = originalCollectors
            app.albumRepository = originalAlbums
        }
    }

    @Test fun buscaSeleccionaPorIdYConservaAlRotar() = withCatalog(CollectorRepository { listOf(ana, jose) }) { scenario ->
        onView(withId(R.id.search_collectors)).perform(typeText("jose"), closeSoftKeyboard())
        onView(withText("José Pérez")).check(matches(isDisplayed()))
        var selectedId = -1
        scenario.onActivity { activity ->
            activity.supportFragmentManager.setFragmentResultListener(CollectorCatalogFragment.COLLECTOR_SELECTED, activity) { _, data ->
                selectedId = data.getInt("collectorId")
            }
        }
        onView(withId(R.id.collector_list)).perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(0, click()))
        scenario.onActivity {
            assertEquals(7, selectedId)
        }
        scenario.recreate()
        onView(withId(R.id.search_collectors)).check(matches(withText("jose")))
        onView(withId(R.id.collector_selection)).check(matches(withText("Coleccionista seleccionado: José Pérez")))
        onView(withId(R.id.back_to_albums)).perform(click())
        onView(withId(R.id.open_collectors)).check(matches(isDisplayed()))
    }

    @Test fun informaCatalogoVacio() = withCatalog(CollectorRepository { emptyList() }) {
        onView(withText(R.string.collectors_empty)).check(matches(isDisplayed()))
    }

    @Test fun informaBusquedaSinResultados() = withCatalog(CollectorRepository { listOf(jose) }) {
        onView(withId(R.id.search_collectors)).perform(typeText("zzz"), closeSoftKeyboard())
        onView(withText(R.string.collectors_no_results)).check(matches(isDisplayed()))
    }

    @Test fun reintentaTrasFallo() {
        var calls = 0
        withCatalog(CollectorRepository { if (++calls == 1) throw IOException() else listOf(jose) }) {
            onView(withText(R.string.collectors_error)).check(matches(isDisplayed()))
            onView(withId(R.id.retry_collectors)).perform(click())
            onView(withText("José Pérez")).check(matches(isDisplayed()))
        }
    }
}
