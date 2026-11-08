package co.edu.uniandes.vinilos

import android.app.Activity
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import co.edu.uniandes.vinilos.data.AlbumRepository
import co.edu.uniandes.vinilos.data.CollectorDetailRepository
import co.edu.uniandes.vinilos.data.CollectorRepository
import co.edu.uniandes.vinilos.model.Album
import co.edu.uniandes.vinilos.model.Collector
import co.edu.uniandes.vinilos.model.CollectorComment
import co.edu.uniandes.vinilos.model.CollectorDetail
import co.edu.uniandes.vinilos.model.FavoritePerformer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class CollectorDetailNavigationTest {
    private val app get() = ApplicationProvider.getApplicationContext<VinilosApplication>()
    private val collectors = listOf(
        Collector(100, "Manolo Bellon", "manollo@example.org", "3001234567"),
        Collector(101, "Jaime Monsalve", "jaime@example.org", "3101234567"),
    )
    private val detail = CollectorDetail(
        100, "Manolo Bellon", "manollo@example.org", "3001234567",
        listOf(CollectorComment(11, "Buen álbum", 5)),
        listOf(FavoritePerformer(7, "Rubén Blades", null, "Cantautor", "1948-07-16")),
    )

    @Test fun selecciona_id_abre_detalle_y_regresa_con_busqueda_conservada() {
        val requests = mutableListOf<Int>()
        withRepositories({ id -> requests += id; detail }) {
            openCatalog()
            onView(withId(R.id.search_collectors)).perform(replaceText("manolo"), closeSoftKeyboard())
            tapCollector(0)
            tap(R.id.open_collector_detail)
            waitText(R.id.collector_detail_title, "Manolo Bellon")
            onView(withText("Rubén Blades")).check(matches(isDisplayed()))
            onView(withText("Buen álbum · Calificación: 5/5")).check(matches(isDisplayed()))
            tap(R.id.back_collector_detail)
            onView(withId(R.id.search_collectors)).check(matches(withText("manolo")))
        }
        assertEquals(listOf(100), requests)
    }

    @Test fun maneja_fallo_y_permite_reintentar() {
        var calls = 0
        withRepositories({ if (++calls == 1) throw IOException("offline") else detail }) {
            openCatalog()
            tapCollector(0)
            tap(R.id.open_collector_detail)
            waitVisible(R.id.collector_detail_error)
            tap(R.id.retry_collector_detail)
            waitText(R.id.collector_detail_title, "Manolo Bellon")
        }
        assertEquals(2, calls)
    }

    private fun openCatalog() {
        tap(R.id.open_collectors)
        waitVisible(R.id.collector_list)
    }

    private fun withRepositories(detailCall: suspend (Int) -> CollectorDetail, block: () -> Unit) {
        val oldAlbums = app.albumRepository
        val oldCollectors = app.collectorRepository
        val oldDetails = app.collectorDetailRepository
        app.albumRepository = object : AlbumRepository { override suspend fun getAlbums(): List<Album> = emptyList() }
        app.collectorRepository = CollectorRepository { collectors }
        app.collectorDetailRepository = CollectorDetailRepository(detailCall)
        try { ActivityScenario.launch(MainActivity::class.java).use { block() } }
        finally {
            app.albumRepository = oldAlbums
            app.collectorRepository = oldCollectors
            app.collectorDetailRepository = oldDetails
        }
    }

    private fun tapCollector(position: Int) = onMain { activity ->
        val list = checkNotNull(activity.findViewById<RecyclerView>(R.id.collector_list))
        checkNotNull(list.findViewHolderForAdapterPosition(position)) { "No está visible el coleccionista $position" }
            .itemView.performClick()
    }

    private fun tap(id: Int) = onMain { activity ->
        checkNotNull(activity.findViewById<View>(id)) { "No existe la vista $id" }.performClick()
    }

    private fun waitVisible(id: Int) = waitUntil { isVisible(id) }

    private fun waitText(id: Int, expected: String) = waitUntil {
        var actual: CharSequence? = null
        onMain { actual = it.findViewById<TextView>(id)?.text }
        actual == expected
    }

    private fun isVisible(id: Int): Boolean {
        var visible = false
        onMain { visible = it.findViewById<View>(id)?.isShown == true }
        return visible
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (!condition()) {
            check(SystemClock.elapsedRealtime() <= deadline) { "La interfaz no llegó al estado esperado" }
            SystemClock.sleep(100)
        }
    }

    private fun onMain(action: (Activity) -> Unit) {
        var error: Throwable? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                val activity = checkNotNull(
                    ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull(),
                ) { "No hay una actividad en primer plano" }
                action(activity)
            } catch (failure: Throwable) { error = failure }
        }
        error?.let { throw it }
    }
}
