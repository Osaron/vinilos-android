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
import co.edu.uniandes.vinilos.ui.artists.ArtistCatalogFragment
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
class ArtistBackendRealTest {
    private data class Expected(val id: Int, val name: String, val type: String)

    @Before fun requireRealBackend() {
        assumeTrue("Requiere backendReal=true y un BackVynils accesible",
            InstrumentationRegistry.getArguments().getString("backendReal") == "true")
    }

    @Test fun listadoYSeleccionCoincidenConMusicosYBandasReales() {
        val expected = read("musicians", "MUSICIAN") + read("bands", "BAND")
        assertTrue("La base de prueba debe contener músicos y bandas", expected.any { it.type == "MUSICIAN" } && expected.any { it.type == "BAND" })
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.open_artists)).perform(click())
            val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
            waitFor {
                onView(withId(R.id.artist_count)).check(matches(withText(
                    resources.getQuantityString(R.plurals.artist_count, expected.size, expected.size))))
                onView(withId(R.id.artist_list)).check(matches(isDisplayed()))
            }
            expected.forEachIndexed { position, artist ->
                waitFor {
                    onView(withId(R.id.artist_list)).perform(RecyclerViewActions.scrollToPosition<RecyclerView.ViewHolder>(position))
                    onView(allOf(withId(R.id.artist_name), withText(artist.name))).check(matches(isDisplayed()))
                }
            }
            var selectedId = -1
            var selectedType = ""
            scenario.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.artist_list)
                assertEquals(expected.size, list.adapter!!.itemCount)
                activity.supportFragmentManager.setFragmentResultListener(ArtistCatalogFragment.ARTIST_SELECTED, activity) { _, data ->
                    selectedId = data.getInt("artistId")
                    selectedType = data.getString("artistType").orEmpty()
                }
            }
            // Comprueba selección de ambos tipos con los IDs que existen en la base real.
            listOf("MUSICIAN", "BAND").forEach { type ->
                val position = expected.indexOfFirst { it.type == type }
                onView(withId(R.id.artist_list)).perform(
                    RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(position, click()))
                scenario.onActivity {
                    assertEquals(expected[position].id, selectedId)
                    assertEquals(type, selectedType)
                }
            }
            val name = expected.first().name
            onView(withId(R.id.search_artists)).perform(replaceText(name), closeSoftKeyboard())
            waitFor { onView(allOf(withId(R.id.artist_name), withText(name))).check(matches(isDisplayed())) }
            scenario.recreate()
            onView(withId(R.id.search_artists)).check(matches(withText(name)))
        }
    }

    private fun read(path: String, type: String): List<Expected> {
        val connection = URL(BuildConfig.API_BASE_URL + path).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        try {
            assertEquals("GET $path", 200, connection.responseCode)
            val json = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
            return (0 until json.length()).map {
                val artist = json.getJSONObject(it)
                Expected(artist.getInt("id"), artist.getString("name").trim(), type)
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
