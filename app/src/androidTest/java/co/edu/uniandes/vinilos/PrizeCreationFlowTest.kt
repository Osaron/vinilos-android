package co.edu.uniandes.vinilos

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.edu.uniandes.vinilos.data.*
import co.edu.uniandes.vinilos.model.Prize
import co.edu.uniandes.vinilos.model.Collector
import co.edu.uniandes.vinilos.ui.prizes.PrizeCatalogFragment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

/** Humo de fase 2; la matriz completa y CI API 21/29/34 se ejecutan en fase 3. */
@RunWith(AndroidJUnit4::class)
class PrizeCreationFlowTest {
    private val app get() = ApplicationProvider.getApplicationContext<VinilosApplication>()
    private class Repo : PrizeRepository {
        val prizes = mutableListOf(Prize(100, "Grammy Award", "NARAS", "Excelencia musical"))
        var calls = 0
        var uncertain = false
        override suspend fun getPrizes() = prizes.toList()
        override suspend fun createPrize(prize: NewPrize): Prize {
            calls++
            if (uncertain) throw IOException("respuesta perdida controlada")
            return Prize(123, prize.name, prize.organization, prize.description).also { prizes += it }
        }
    }
    private fun scenario(active: Boolean = true, block: (ActivityScenario<MainActivity>, Repo) -> Unit) {
        val oldPrize = app.prizeRepository; val oldAlbums = app.albumRepository; val oldCollector = app.activeCollectorStore
        val oldCollectors = app.collectorRepository
        val repo = Repo()
        app.prizeRepository = repo; app.albumRepository = AlbumRepository { emptyList() }
        app.activeCollectorStore = InMemoryActiveCollectorStore(if (active) ActiveCollector(7, "Hernán") else null)
        app.collectorRepository = CollectorRepository { listOf(Collector(7, "Coleccionista HU13", "", "")) }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { activity ->
                activity.onActivity {
                    it.supportFragmentManager.beginTransaction().replace(R.id.fragment_container, PrizeCatalogFragment())
                        .addToBackStack("test_prize_catalog").commit()
                    it.supportFragmentManager.executePendingTransactions()
                }
                awaitUi(activity, "No terminó carga inicial") {
                    it.findViewById<View>(R.id.prize_loading)?.visibility == View.GONE
                }
                block(activity, repo)
            }
        } finally {
            app.prizeRepository = oldPrize; app.albumRepository = oldAlbums; app.activeCollectorStore = oldCollector
            app.collectorRepository = oldCollectors
        }
    }
    private fun awaitUi(scenario: ActivityScenario<MainActivity>, reason: String, condition: (MainActivity) -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
        while (true) {
            var ready = false
            scenario.onActivity { ready = condition(it) }
            if (ready) return
            check(android.os.SystemClock.elapsedRealtime() < deadline) { reason }
            android.os.SystemClock.sleep(50)
        }
    }
    private fun submitWithoutEspressoWaitingOnSpinner(scenario: ActivityScenario<MainActivity>) {
        onView(withId(R.id.prize_save)).perform(scrollTo())
        scenario.onActivity { it.findViewById<View>(R.id.prize_save).performClick() }
    }
    private fun fill() {
        onView(withId(R.id.prize_name_input)).perform(replaceText("Premio Fase 2"))
        onView(withId(R.id.prize_description_input)).perform(replaceText("Reconocimiento de prueba"))
        onView(withId(R.id.prize_organization_input)).perform(replaceText("Vinilos"), closeSoftKeyboard())
    }
    private fun screenshot(activity: ActivityScenario<MainActivity>, name: String) {
        activity.onActivity {
            val root = it.findViewById<View>(R.id.fragment_container)
            val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(image))
            val directory = File(it.getExternalFilesDir(null) ?: it.filesDir, "hu13-fase2").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { out -> image.compress(Bitmap.CompressFormat.PNG, 100, out) }
            image.recycle()
        }
    }
    @Test(timeout = 90_000) fun invalidFieldsDoNotSendAndDraftSurvivesRotation() = scenario { activity, repo ->
        screenshot(activity, "01-catalogo")
        onView(withId(R.id.prize_create)).perform(click())
        submitWithoutEspressoWaitingOnSpinner(activity)
        awaitUi(activity, "No presentó validación") { it.findViewById<View>(R.id.prize_name_error)?.visibility == View.VISIBLE }
        onView(withId(R.id.prize_name_error)).check(matches(isDisplayed()))
        assertEquals(0, repo.calls); screenshot(activity, "02-validacion")
        fill(); activity.recreate()
        onView(withId(R.id.prize_name_input)).check(matches(withText("Premio Fase 2")))
        onView(withId(R.id.prize_organization_input)).check(matches(withText("Vinilos")))
        screenshot(activity, "03-borrador-rotado")
    }
    @Test(timeout = 90_000) fun confirmedCreationReturnsToRefreshedCatalog() = scenario { activity, repo ->
        onView(withId(R.id.prize_create)).perform(click()); fill()
        submitWithoutEspressoWaitingOnSpinner(activity)
        awaitUi(activity, "No confirmó creación") {
            it.findViewById<android.widget.TextView>(R.id.prize_catalog_notice)?.text?.contains("Premio Fase 2") == true &&
                it.findViewById<View>(R.id.prize_loading)?.visibility == View.GONE
        }
        onView(withText("Premio Fase 2")).check(matches(isDisplayed()))
        onView(withId(R.id.prize_catalog_notice)).check(matches(withText("Premio creado: Premio Fase 2.")))
        assertEquals(1, repo.calls); screenshot(activity, "04-creado")
    }
    @Test(timeout = 90_000) fun uncertainResponseCanReviewCatalogAndReturnWithoutResend() = scenario { activity, repo ->
        repo.uncertain = true
        onView(withId(R.id.prize_create)).perform(click()); fill()
        // El aviso incierto puede dejar el botón fuera del viewport en pantallas pequeñas.
        activity.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        submitWithoutEspressoWaitingOnSpinner(activity)
        awaitUi(activity, "No presentó respuesta incierta") { it.findViewById<View>(R.id.prize_review_catalog)?.visibility == View.VISIBLE }
        onView(withId(R.id.prize_review_catalog)).perform(scrollTo()).check(matches(isDisplayed()))
        activity.onActivity { it.findViewById<View>(R.id.prize_review_catalog).performClick() }
        awaitUi(activity, "No terminó catálogo de revisión") {
            it.findViewById<View>(R.id.prize_back_draft)?.visibility == View.VISIBLE &&
                it.findViewById<View>(R.id.prize_loading)?.visibility == View.GONE
        }
        onView(withId(R.id.prize_back_draft)).perform(click())
        onView(withId(R.id.prize_name_input)).check(matches(withText("Premio Fase 2")))
        onView(withId(R.id.prize_review_catalog)).perform(scrollTo()).check(matches(isDisplayed()))
        assertEquals(1, repo.calls); screenshot(activity, "05-incierto-borrador")
    }
    @Test(timeout = 90_000) fun choosingCollectorReturnsToCreationWithoutReentrantTransaction() = scenario(active = false) { _, repo ->
        onView(withId(R.id.prize_create)).perform(click())
        onView(withText("Coleccionista HU13")).perform(click())
        onView(withId(R.id.prize_name_input)).check(matches(isDisplayed()))
        assertEquals(7, app.activeCollectorStore.active.value?.id)
        assertEquals(0, repo.calls)
    }
}
