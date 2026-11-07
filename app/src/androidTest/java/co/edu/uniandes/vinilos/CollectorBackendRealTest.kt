package co.edu.uniandes.vinilos

import android.os.SystemClock
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.PerformException
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.ui.collectors.CollectorCatalogFragment
import org.hamcrest.Matchers.allOf
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/** Lee BackVynils por HTTP como oráculo independiente. No sustituye repositorios. */
@RunWith(AndroidJUnit4::class)
class CollectorBackendRealTest {
    private data class Expected(val id: Int, val name: String, val email: String)

    @Before fun requireRealBackend() {
        assumeTrue("Requiere backendReal=true y un BackVynils accesible",
            InstrumentationRegistry.getArguments().getString("backendReal") == "true")
    }

    @Test fun listadoYSeleccionCoincidenConColeccionistasReales() {
        val expected = read()
        assertTrue("La base de prueba debe contener coleccionistas", expected.isNotEmpty())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.open_collectors)).perform(scrollTo(), click())
            val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
            waitFor {
                onView(withId(R.id.collector_count)).check(matches(withText(
                    resources.getQuantityString(R.plurals.collector_count, expected.size, expected.size))))
                onView(withId(R.id.collector_list)).check(matches(isDisplayed()))
            }
            capture("coleccionistas-listado.png")
            expected.forEachIndexed { position, collector ->
                waitFor {
                    onView(withId(R.id.collector_list)).perform(RecyclerViewActions.scrollToPosition<RecyclerView.ViewHolder>(position))
                    onView(allOf(withId(R.id.collector_name), withText(collector.name))).check(matches(isDisplayed()))
                    val subtitle = collector.email.ifBlank { resources.getString(R.string.collector_member) }
                    onView(allOf(withId(R.id.collector_subtitle), withText(subtitle))).check(matches(isDisplayed()))
                }
            }
            var selectedId = -1
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.collector_list)
                assertEquals(expected.size, list.adapter!!.itemCount)
                activity.supportFragmentManager.setFragmentResultListener(CollectorCatalogFragment.COLLECTOR_SELECTED, activity) { _, data ->
                    selectedId = data.getInt("collectorId")
                }
            }
            expected.forEachIndexed { position, collector ->
                onView(withId(R.id.collector_list)).perform(
                    RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(position, click()))
                scenario.onActivity { assertEquals(collector.id, selectedId) }
            }
            val name = expected.first().name
            onView(withId(R.id.search_collectors)).perform(replaceText(name), closeSoftKeyboard())
            waitFor { onView(allOf(withId(R.id.collector_name), withText(name))).check(matches(isDisplayed())) }
            capture("coleccionistas-busqueda.png")
            scenario.recreate()
            onView(withId(R.id.search_collectors)).check(matches(withText(name)))
        }
    }

    private fun capture(name: String) {
        // Download permanece disponible después de que Gradle desinstale la app.
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val descriptor = automation.executeShellCommand("screencap -p /sdcard/Download/$name")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private fun read(): List<Expected> {
        val path = "collectors"
        val connection = URL(BuildConfig.API_BASE_URL + path).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        try {
            assertEquals("GET $path", 200, connection.responseCode)
            val json = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
            return (0 until json.length()).map {
                val collector = json.getJSONObject(it)
                Expected(collector.getInt("id"), collector.getString("name").trim(), if (collector.isNull("email")) "" else collector.optString("email"))
            }
        } finally { connection.disconnect() }
    }

    private fun waitFor(assertion: () -> Unit) {
        val deadline = SystemClock.elapsedRealtime() + 45000
        while (true) {
            try { assertion(); return }
            catch (error: AssertionError) { if (SystemClock.elapsedRealtime() >= deadline) throw error }
            catch (error: NoMatchingViewException) { if (SystemClock.elapsedRealtime() >= deadline) throw error }
            catch (error: PerformException) { if (SystemClock.elapsedRealtime() >= deadline) throw error }
            SystemClock.sleep(200)
        }
    }
}
