package co.edu.uniandes.vinilos

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.data.*
import co.edu.uniandes.vinilos.model.Prize
import co.edu.uniandes.vinilos.ui.prizes.PrizeCatalogFragment
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class PrizeQualityFlowTest {
    private val app get() = ApplicationProvider.getApplicationContext<VinilosApplication>()
    private class Repo : PrizeRepository {
        var empty = false; var offline = false; var writes = 0
        var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
        val records = mutableListOf(Prize(100, "Grammy Award", "NARAS", "Excelencia musical"))
        override suspend fun getPrizes(): List<Prize> {
            if (offline) throw IOException("fallo controlado de lectura")
            return if (empty) emptyList() else records.toList()
        }
        override suspend fun createPrize(prize: NewPrize): Prize {
            writes++; gate?.await(); return Prize(200, prize.name, prize.organization, prize.description).also { records += it }
        }
    }
    private fun exercise(repo: Repo = Repo(), block: (ActivityScenario<MainActivity>, Repo) -> Unit) {
        val old = app.prizeRepository; val albums = app.albumRepository; val active = app.activeCollectorStore
        app.prizeRepository = repo; app.albumRepository = AlbumRepository { emptyList() }
        app.activeCollectorStore = InMemoryActiveCollectorStore(ActiveCollector(7, "Prueba"))
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { it.supportFragmentManager.beginTransaction().replace(R.id.fragment_container, PrizeCatalogFragment()).addToBackStack("quality_catalog").commit() }
                block(scenario, repo)
            }
        } finally { app.prizeRepository = old; app.albumRepository = albums; app.activeCollectorStore = active }
    }
    private fun capture(scenario: ActivityScenario<MainActivity>, name: String) = scenario.onActivity {
        val root = it.findViewById<View>(R.id.fragment_container)
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        File(it.getExternalFilesDir(null), "hu13-fase3").apply { mkdirs() }.resolve("$name.png").outputStream()
            .use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) }
        bitmap.recycle()
    }
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    @Test fun emptyCatalogAndReadErrorRecoverWithoutWriting() {
        exercise(Repo().apply { empty = true }) { scenario, repo ->
            onView(withId(R.id.prize_status)).check(matches(withText(R.string.hu13_empty)))
            capture(scenario, "quality-01-vacio")
            assertEquals(0, repo.writes)
        }
        exercise(Repo().apply { offline = true }) { scenario, repo ->
            onView(withId(R.id.prize_status)).check(matches(withText(R.string.hu13_load_error)))
            capture(scenario, "quality-02-error-carga")
            repo.offline = false
            onView(withId(R.id.prize_retry)).perform(click())
            onView(withText("Grammy Award")).check(matches(isDisplayed()))
            assertEquals(0, repo.writes)
        }
    }
    @Test fun whitespaceAndSingleFieldEditsKeepOtherDraftFields() = exercise { scenario, repo ->
        onView(withId(R.id.prize_create)).perform(click())
        onView(withId(R.id.prize_name_input)).perform(replaceText("   "))
        onView(withId(R.id.prize_description_input)).perform(replaceText("Descripción que debe conservarse"))
        onView(withId(R.id.prize_organization_input)).perform(replaceText("Organización"), closeSoftKeyboard())
        onView(withId(R.id.prize_save)).perform(scrollTo(), click())
        onView(withId(R.id.prize_name_error)).check(matches(isDisplayed()))
        onView(withId(R.id.prize_name_input)).perform(replaceText("Nombre corregido"), closeSoftKeyboard())
        onView(withId(R.id.prize_description_input)).check(matches(withText("Descripción que debe conservarse")))
        scenario.recreate()
        onView(withId(R.id.prize_name_input)).check(matches(withText("Nombre corregido")))
        assertEquals(0, repo.writes)
    }
    @Test fun cancelRequiresConfirmationAndNeverSends() = exercise { _, repo ->
        onView(withId(R.id.prize_create)).perform(click())
        onView(withId(R.id.prize_name_input)).perform(replaceText("Borrador parcial"), closeSoftKeyboard())
        onView(withId(R.id.prize_cancel)).perform(scrollTo(), click())
        onView(withText(R.string.hu13_keep_editing)).inRoot(isDialog()).perform(click())
        onView(withId(R.id.prize_name_input)).check(matches(withText("Borrador parcial")))
        onView(withId(R.id.prize_cancel)).perform(scrollTo(), click())
        onView(withText(R.string.hu13_discard)).inRoot(isDialog()).perform(click())
        onView(withId(R.id.prize_create)).check(matches(isDisplayed()))
        assertEquals(0, repo.writes)
    }
    @Test fun actualRotationKeepsPartialCompleteAndInFlightDraftWithoutResend() = exercise { scenario, repo ->
        fun await(reason: String, condition: (MainActivity) -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + 10_000
            while (true) {
                var ready = false
                scenario.onActivity { ready = condition(it) }
                if (ready) return
                check(SystemClock.elapsedRealtime() < deadline) { reason }
                SystemClock.sleep(100)
            }
        }
        fun rotate(orientation: Int, expected: Int) {
            scenario.onActivity { it.requestedOrientation = orientation }
            await("No terminó la rotación") { it.resources.configuration.orientation == expected }
        }
        onView(withId(R.id.prize_create)).perform(click())
        onView(withId(R.id.prize_name_input)).perform(replaceText("Borrador rotado"), closeSoftKeyboard())
        rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)
        onView(withId(R.id.prize_name_input)).check(matches(withText("Borrador rotado")))
        onView(withId(R.id.prize_description_input)).check(matches(withText("")))
        scenario.onActivity {
            it.findViewById<android.widget.EditText>(R.id.prize_description_input).setText("Descripción conservada")
            it.findViewById<android.widget.EditText>(R.id.prize_organization_input).setText("Organización conservada")
        }
        rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)
        onView(withId(R.id.prize_description_input)).check(matches(withText("Descripción conservada")))
        onView(withId(R.id.prize_organization_input)).check(matches(withText("Organización conservada")))
        capture(scenario, "quality-03-borrador-rotado")
        repo.gate = kotlinx.coroutines.CompletableDeferred()
        scenario.onActivity { it.findViewById<View>(R.id.prize_save).performClick() }
        await("No empezó el envío") { repo.writes == 1 }
        rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)
        await("No restauró guardado") { it.findViewById<View>(R.id.prize_saving)?.visibility == View.VISIBLE }
        assertEquals(1, repo.writes)
        capture(scenario, "quality-04-rotacion-guardando")
        repo.gate!!.complete(Unit)
        await("No volvió al catálogo") { it.findViewById<View>(R.id.prize_create) != null }
        assertEquals(1, repo.writes)
    }
    private fun accessibleFlow(scale: Float, landscape: Boolean) {
        val previous = shell("settings get system font_scale").trim().toFloatOrNull() ?: 1f
        val report = JSONArray()
        val tag = "a11y-${(scale * 100).toInt()}-${if (landscape) "horizontal" else "vertical"}"
        val validator = AccessibilityValidator().setRunChecksFromRootView(false)
        validator.javaClass.methods.firstOrNull { it.name == "setCaptureScreenshots" }?.invoke(validator, true)
        validator.javaClass.methods.firstOrNull { it.name == "setThrowExceptionFor" }?.invoke(validator, null)
        try {
            shell("settings put system font_scale $scale"); SystemClock.sleep(500)
            exercise { scenario, _ ->
                if (landscape) {
                    scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                    val limit = SystemClock.elapsedRealtime() + 5000
                    var ready = false
                    while (!ready && SystemClock.elapsedRealtime() < limit) {
                        scenario.onActivity { ready = it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
                        SystemClock.sleep(50)
                    }
                    assertTrue(ready)
                }
                fun check(state: String) {
                    scenario.onActivity { activity ->
                        assertEquals(scale, activity.resources.configuration.fontScale, 0.02f)
                        val result = validator.checkAndReturnResults(activity.findViewById(R.id.fragment_container))
                        val counts = result.groupingBy { it.type.name }.eachCount()
                        report.put(JSONObject().put("state", state).put("counts", JSONObject(counts))
                            .put("findings", JSONArray(result.filter { it.type.name in setOf("ERROR", "WARNING") }.map {
                                JSONObject().put("type", it.type.name).put("viewId", it.view?.id)
                                    .put("message", it.getMessage(Locale.ROOT))
                            })))
                        assertTrue("$tag / $state: $result", result.none { it.type.name == "ERROR" })
                    }
                    capture(scenario, "$tag-$state")
                }
                check("catalogo")
                onView(withId(R.id.prize_create)).perform(click())
                check("formulario")
                onView(withId(R.id.prize_save)).perform(scrollTo(), click())
                onView(withId(R.id.prize_name_error)).check(matches(isDisplayed()))
                check("validacion")
                onView(withId(R.id.prize_name_input)).perform(scrollTo(), replaceText("Premio accesible"))
                onView(withId(R.id.prize_description_input)).perform(scrollTo(), replaceText("Descripción accesible"))
                onView(withId(R.id.prize_organization_input)).perform(scrollTo(), replaceText("Vinilos"), closeSoftKeyboard())
                onView(withId(R.id.prize_save)).perform(scrollTo(), click())
                onView(withId(R.id.prize_catalog_notice)).check(matches(isDisplayed()))
                check("creado")
            }
        } finally {
            shell("settings put system font_scale $previous")
            File(app.getExternalFilesDir(null), "hu13-fase3").apply { mkdirs() }.resolve("$tag.json").writeText(report.toString(2))
        }
    }
    @Test fun accessibilityNormalText() = accessibleFlow(1f, false)
    @Test fun accessibility150PercentText() = accessibleFlow(1.5f, false)
    @Test fun accessibility150PercentLandscape() = accessibleFlow(1.5f, true)
}
