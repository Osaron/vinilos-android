package co.edu.uniandes.vinilos

import android.os.Bundle
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.edu.uniandes.vinilos.data.ActiveCollector
import co.edu.uniandes.vinilos.data.ActiveCollectorStore
import co.edu.uniandes.vinilos.data.AlbumRepository
import co.edu.uniandes.vinilos.data.CollectorRepository
import co.edu.uniandes.vinilos.data.InMemoryActiveCollectorStore
import co.edu.uniandes.vinilos.model.Collector
import co.edu.uniandes.vinilos.ui.albums.AlbumCatalogFragment
import co.edu.uniandes.vinilos.ui.collectors.ActiveCollectorFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Escenarios E2E de #80 (ESC-32 a ESC-35): elegir el coleccionista activo que usarán HU09 y HU10.
 * Repositorios en memoria; no dependen de la red.
 */
@RunWith(AndroidJUnit4::class)
class ActiveCollectorSelectionTest {
    private val jose = Collector(7, "José Pérez", "jose@example.org", "")
    private val ana = Collector(8, "Ana María", "", "")
    private val app get() = ApplicationProvider.getApplicationContext<VinilosApplication>()

    private fun conSelector(
        repository: CollectorRepository,
        store: ActiveCollectorStore = InMemoryActiveCollectorStore(),
        block: (ActivityScenario<MainActivity>, ActiveCollectorStore) -> Unit,
    ) {
        val collectors = app.collectorRepository
        val albums = app.albumRepository
        val active = app.activeCollectorStore
        app.collectorRepository = repository
        app.albumRepository = AlbumRepository { emptyList() }
        app.activeCollectorStore = store
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { it.supportFragmentManager.setFragmentResult(ActiveCollectorFragment.REQUEST, Bundle()) }
                block(scenario, store)
            }
        } finally {
            app.collectorRepository = collectors
            app.albumRepository = albums
            app.activeCollectorStore = active
        }
    }

    private fun texto(id: Int, vararg args: Any) = app.getString(id, *args)

    /** Comprueba que se volvió a la pantalla que pidió el selector (aquí, el catálogo de álbumes). */
    private fun volvioAlCatalogo(scenario: ActivityScenario<MainActivity>) = scenario.onActivity { activity ->
        assertTrue(activity.supportFragmentManager.findFragmentById(R.id.fragment_container) is AlbumCatalogFragment)
    }

    /** ESC-32: sin coleccionista elegido, elegir uno lo guarda y vuelve a la pantalla que lo pidió. */
    @Test fun esc32_eligeUnColeccionistaYVuelve() = conSelector(CollectorRepository { listOf(jose, ana) }) { scenario, store ->
        var elegido: String? = null
        scenario.onActivity { activity ->
            activity.supportFragmentManager.setFragmentResultListener(ActiveCollectorFragment.CHOSEN, activity) { _, data ->
                elegido = "${data.getInt(ActiveCollectorFragment.COLLECTOR_ID)}/${data.getString(ActiveCollectorFragment.COLLECTOR_NAME)}"
            }
        }
        onView(withId(R.id.current_active_collector)).check(matches(withText(texto(R.string.active_collector_none))))

        onView(withId(R.id.active_collector_list)).perform(
            RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(1, click()),
        )

        volvioAlCatalogo(scenario)
        assertEquals(ActiveCollector(8, "Ana María"), store.active.value)
        assertEquals("8/Ana María", elegido)
    }

    /** ESC-33: muestra el coleccionista actual, conserva la búsqueda al rotar y permite cambiarlo. */
    @Test fun esc33_cambiaElColeccionistaYConservaLaBusquedaAlRotar() = conSelector(
        CollectorRepository { listOf(jose, ana) }, InMemoryActiveCollectorStore(ActiveCollector(8, "Ana María")),
    ) { scenario, store ->
        onView(withId(R.id.current_active_collector)).check(matches(withText(texto(R.string.active_collector_current, "Ana María"))))
        onView(withId(R.id.search_active_collector)).perform(typeText("jose"), closeSoftKeyboard())
        scenario.recreate()
        onView(withId(R.id.search_active_collector)).check(matches(withText("jose")))
        onView(withText("José Pérez")).check(matches(isDisplayed()))

        onView(withId(R.id.active_collector_list)).perform(
            RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(0, click()),
        )

        assertEquals(ActiveCollector(7, "José Pérez"), store.active.value)
        volvioAlCatalogo(scenario)
    }

    /** ESC-34: si el listado falla se informa y se puede reintentar; Atrás no cambia nada. */
    @Test fun esc34_errorAlCargarPermiteReintentar() {
        var llamadas = 0
        conSelector(CollectorRepository { if (++llamadas == 1) throw IOException("sin red") else listOf(jose) }) { scenario, store ->
            onView(withId(R.id.active_collector_status)).check(matches(withText(R.string.collectors_error)))
            onView(withId(R.id.retry_active_collectors)).perform(click())
            onView(withText("José Pérez")).check(matches(isDisplayed()))

            pressBack()

            volvioAlCatalogo(scenario)
            assertNull(store.active.value)
        }
    }

    /** ESC-35: un coleccionista guardado que ya no existe en el backend se olvida y se pide otro. */
    @Test fun esc35_coleccionistaGuardadoQueYaNoExisteSeOlvida() = conSelector(
        CollectorRepository { listOf(jose, ana) }, InMemoryActiveCollectorStore(ActiveCollector(99, "Coleccionista eliminado")),
    ) { _, store ->
        onView(withId(R.id.current_active_collector)).check(matches(withText(texto(R.string.active_collector_none))))
        assertNull(store.active.value)
    }
}
