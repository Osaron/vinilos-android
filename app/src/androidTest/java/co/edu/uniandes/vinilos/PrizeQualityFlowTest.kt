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
                onMain { it.supportFragmentManager.beginTransaction().replace(R.id.fragment_container, PrizeCatalogFragment()).addToBackStack("quality_catalog").commit() }
                awaitUi(scenario, "No terminó carga inicial") {
                    it.findViewById<View>(R.id.prize_loading)?.visibility == View.GONE
                }
                block(scenario, repo)
            }
        } finally { app.prizeRepository = old; app.albumRepository = albums; app.activeCollectorStore = active }
    }
    // ActivityScenario.onActivity espera idle y puede atascarse con el spinner de API21.
    private fun onMain(block: (MainActivity) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
        while (true) {
            var executed = false
            var failure: Throwable? = null
            instrumentation.runOnMainSync {
                val current = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                    .filterIsInstance<MainActivity>().firstOrNull()
                if (current != null) {
                    executed = true
                    try { block(current) } catch (error: Throwable) { failure = error }
                }
            }
            failure?.let { throw it }
            if (executed) return
            check(android.os.SystemClock.elapsedRealtime() < deadline) { "MainActivity no está reanudada" }
            android.os.SystemClock.sleep(50)
        }
    }
    private fun awaitUi(scenario: ActivityScenario<MainActivity>, reason: String, condition: (MainActivity) -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
        while (true) {
            var ready = false
            onMain { ready = condition(it) }
            if (ready) return
            check(android.os.SystemClock.elapsedRealtime() < deadline) { reason }
            android.os.SystemClock.sleep(50)
        }
    }
    private fun submitWithoutEspressoWaitingOnSpinner(scenario: ActivityScenario<MainActivity>) {
        onView(withId(R.id.prize_save)).perform(scrollTo())
        onMain { it.findViewById<View>(R.id.prize_save).performClick() }
    }
    private fun capture(scenario: ActivityScenario<MainActivity>, name: String) = onMain {
        val root = it.findViewById<View>(R.id.fragment_container)
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        File(it.getExternalFilesDir(null) ?: it.filesDir, "hu13-fase3").apply { mkdirs() }.resolve("$name.png").outputStream()
            .use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) }
        bitmap.recycle()
    }
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }

    @Test(timeout = 90_000) fun emptyCatalogAndReadErrorRecoverWithoutWriting() {
        exercise(Repo().apply { empty = true }) { scenario, repo ->
            onView(withId(R.id.prize_status)).check(matches(withText(R.string.hu13_empty)))
            capture(scenario, "quality-01-vacio")
            assertEquals(0, repo.writes)
        }
        exercise(Repo().apply { offline = true }) { scenario, repo ->
            onView(withId(R.id.prize_status)).check(matches(withText(R.string.hu13_load_error)))
            capture(scenario, "quality-02-error-carga")
            repo.offline = false
            onView(withId(R.id.prize_retry)).check(matches(isDisplayed()))
            onMain { it.findViewById<View>(R.id.prize_retry).performClick() }
            awaitUi(scenario, "No terminó reintento") {
                it.findViewById<View>(R.id.prize_loading)?.visibility == View.GONE &&
                    (it.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.prize_list)?.adapter?.itemCount ?: 0) > 0
            }
            onView(withText("Grammy Award")).check(matches(isDisplayed()))
            assertEquals(0, repo.writes)
        }
    }
    @Test(timeout = 90_000) fun whitespaceAndSingleFieldEditsKeepOtherDraftFields() = exercise { scenario, repo ->
        onView(withId(R.id.prize_create)).perform(click())
        onView(withId(R.id.prize_name_input)).perform(replaceText("   "))
        onView(withId(R.id.prize_description_input)).perform(replaceText("Descripción que debe conservarse"))
        onView(withId(R.id.prize_organization_input)).perform(replaceText("Organización"), closeSoftKeyboard())
        submitWithoutEspressoWaitingOnSpinner(scenario)
        awaitUi(scenario, "No presentó validación") { it.findViewById<View>(R.id.prize_name_error)?.visibility == View.VISIBLE }
        onView(withId(R.id.prize_name_error)).check(matches(isDisplayed()))
        onView(withId(R.id.prize_name_input)).perform(replaceText("Nombre corregido"), closeSoftKeyboard())
        onView(withId(R.id.prize_description_input)).check(matches(withText("Descripción que debe conservarse")))
        scenario.recreate()
        onView(withId(R.id.prize_name_input)).check(matches(withText("Nombre corregido")))
        assertEquals(0, repo.writes)
    }
    @Test(timeout = 90_000) fun cancelRequiresConfirmationAndNeverSends() = exercise { _, repo ->
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
    @Test(timeout = 90_000) fun actualRotationKeepsPartialCompleteAndInFlightDraftWithoutResend() = exercise { scenario, repo ->
        fun await(reason: String, condition: (MainActivity) -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + 10_000
            while (true) {
                var ready = false
                onMain { ready = condition(it) }
                if (ready) return
                check(SystemClock.elapsedRealtime() < deadline) { reason }
                SystemClock.sleep(100)
            }
        }
        fun rotate(orientation: Int, expected: Int) {
            onMain { it.requestedOrientation = orientation }
            await("No terminó la rotación") { it.resources.configuration.orientation == expected }
        }
        onView(withId(R.id.prize_create)).perform(click())
        onView(withId(R.id.prize_name_input)).perform(replaceText("Borrador rotado"), closeSoftKeyboard())
        rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)
        onView(withId(R.id.prize_name_input)).check(matches(withText("Borrador rotado")))
        onView(withId(R.id.prize_description_input)).check(matches(withText("")))
        onMain {
            it.findViewById<android.widget.EditText>(R.id.prize_description_input).setText("Descripción conservada")
            it.findViewById<android.widget.EditText>(R.id.prize_organization_input).setText("Organización conservada")
        }
        rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)
        onView(withId(R.id.prize_description_input)).check(matches(withText("Descripción conservada")))
        onView(withId(R.id.prize_organization_input)).check(matches(withText("Organización conservada")))
        capture(scenario, "quality-03-borrador-rotado")
        repo.gate = kotlinx.coroutines.CompletableDeferred()
        onMain { it.findViewById<View>(R.id.prize_save).performClick() }
        await("No empezó el envío") { repo.writes == 1 }
        rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)
        await("No restauró guardado") { it.findViewById<View>(R.id.prize_saving)?.visibility == View.VISIBLE }
        assertEquals(1, repo.writes)
        capture(scenario, "quality-04-rotacion-guardando")
        repo.gate!!.complete(Unit)
        await("No volvió al catálogo") { it.findViewById<View>(R.id.prize_create) != null }
        assertEquals(1, repo.writes)
    }
    private fun setFontScale(scale: Float) {
        if (kotlin.math.abs(app.resources.configuration.fontScale - scale) < 0.01f) return
        if (android.os.Build.VERSION.SDK_INT <= 25) {
            val targetApk = app.applicationInfo.sourceDir
            val testApk = InstrumentationRegistry.getInstrumentation().context.applicationInfo.sourceDir
            val result = shell("app_process -Djava.class.path=$targetApk:$testApk /system/bin co.edu.uniandes.vinilos.PrizeLegacyFontScale $scale")
            check(result.contains("HU13_FONT_SCALE=$scale")) { "No aplicó fuente AOSP5: $result" }
        } else shell("settings put system font_scale $scale")
        val deadline = SystemClock.elapsedRealtime() + 10_000
        var applied = false
        while (!applied && SystemClock.elapsedRealtime() < deadline) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                applied = kotlin.math.abs(app.resources.configuration.fontScale - scale) < 0.02f
            }
            if (!applied) SystemClock.sleep(100)
        }
        assertTrue("La configuración efectiva no cambió a $scale", applied)
    }
    private fun accessibleFlow(scale: Float, landscape: Boolean) {
        val previous = app.resources.configuration.fontScale
        val report = JSONArray()
        val tag = "a11y-${(scale * 100).toInt()}-${if (landscape) "horizontal" else "vertical"}"
        val validator = AccessibilityValidator().setRunChecksFromRootView(false)
        validator.javaClass.methods.firstOrNull { it.name == "setCaptureScreenshots" }?.invoke(validator, true)
        validator.javaClass.methods.firstOrNull { it.name == "setThrowExceptionFor" }?.invoke(validator, null)
        try {
            setFontScale(scale)
            exercise { scenario, _ ->
                if (landscape) {
                    onMain { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                    val limit = SystemClock.elapsedRealtime() + 5000
                    var ready = false
                    while (!ready && SystemClock.elapsedRealtime() < limit) {
                        onMain { ready = it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
                        SystemClock.sleep(50)
                    }
                    assertTrue(ready)
                }
                fun check(state: String) {
                    // ListAdapter publica el diff después del estado del ViewModel. Revisar una
                    // lista todavía sin hijos no representa el catálogo presentado al usuario.
                    val deadline = SystemClock.elapsedRealtime() + 10_000
                    var settled = false
                    while (!settled && SystemClock.elapsedRealtime() < deadline) {
                        onMain { activity ->
                            val root = activity.findViewById<View>(R.id.fragment_container)
                            val list = activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.prize_list)
                            settled = root.isLaidOut && !root.isLayoutRequested &&
                                (state !in setOf("catalogo", "creado") ||
                                    (list != null && (list.adapter?.itemCount ?: 0) > 0 && list.childCount > 0 && !list.isComputingLayout))
                        }
                        if (!settled) SystemClock.sleep(50)
                    }
                    assertTrue("No terminó de presentar $state", settled)
                    onMain { activity ->
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
                submitWithoutEspressoWaitingOnSpinner(scenario)
                awaitUi(scenario, "No presentó validación") { it.findViewById<View>(R.id.prize_name_error)?.visibility == View.VISIBLE }
                onView(withId(R.id.prize_name_error)).check(matches(isDisplayed()))
                check("validacion")
                onView(withId(R.id.prize_name_input)).perform(scrollTo(), replaceText("Premio accesible"))
                onView(withId(R.id.prize_description_input)).perform(scrollTo(), replaceText("Descripción accesible"))
                onView(withId(R.id.prize_organization_input)).perform(scrollTo(), replaceText("Vinilos"), closeSoftKeyboard())
                submitWithoutEspressoWaitingOnSpinner(scenario)
                awaitUi(scenario, "No confirmó creación") {
                    it.findViewById<View>(R.id.prize_catalog_notice)?.visibility == View.VISIBLE &&
                        it.findViewById<View>(R.id.prize_loading)?.visibility == View.GONE
                }
                onView(withId(R.id.prize_catalog_notice)).check(matches(isDisplayed()))
                check("creado")
            }
        } finally {
            setFontScale(previous)
            File(app.getExternalFilesDir(null) ?: app.filesDir, "hu13-fase3").apply { mkdirs() }.resolve("$tag.json").writeText(report.toString(2))
        }
    }
    @Test(timeout = 90_000) fun accessibilityNormalText() = accessibleFlow(1f, false)
    @Test(timeout = 90_000) fun accessibility150PercentText() = accessibleFlow(1.5f, false)
    @Test(timeout = 90_000) fun accessibility150PercentLandscape() = accessibleFlow(1.5f, true)
}
