package co.edu.uniandes.vinilos

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.data.*
import co.edu.uniandes.vinilos.model.*
import co.edu.uniandes.vinilos.ui.artists.ArtistDetailFragment
import java.io.File
import java.util.Collections
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Solo se ejecuta en el emulador dedicado con TalkBack instalado y habilitado. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 24)
@android.annotation.TargetApi(24)
class BandMusicianTalkBackTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = ApplicationProvider.getApplicationContext<VinilosApplication>()
    private lateinit var automation: UiAutomation
    private val events = Collections.synchronizedList(mutableListOf<String>())
    private val context get() = instrumentation.targetContext
    private fun id(name: String) = "co.edu.uniandes.vinilos:id/$name"

    @Test fun recorridoConTalkBackSeleccionaConfirmaYRegresa() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("hu12TalkBack") == "true")
        automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val manager = context.getSystemService(AccessibilityManager::class.java)
        assertTrue("TalkBack no está habilitado", manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == "com.android.talkback" })
        await("Exploración táctil habilitada") { manager.isTouchExplorationEnabled }
        automation.setOnAccessibilityEventListener { event ->
            if (event.eventType in listOf(AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED,
                    AccessibilityEvent.TYPE_VIEW_SELECTED, AccessibilityEvent.TYPE_ANNOUNCEMENT)) {
                events.add("event=${AccessibilityEvent.eventTypeToString(event.eventType)}; text=${event.text}; description=${event.contentDescription}")
            }
        }
        val originalAlbums = app.albumRepository
        val originalArtists = app.artistRepository
        val originalDetail = app.artistDetailRepository
        val originalBands = app.bandMusicianRepository
        val originalFavorites = app.favoriteArtistRepository
        val originalCollector = app.activeCollectorStore
        val members = mutableListOf<Artist>()
        var calls = 0
        val musician = Artist(101, ArtistType.MUSICIAN, "Músico de prueba TalkBack", "", "")
        app.albumRepository = AlbumRepository { emptyList() }
        app.artistRepository = ArtistRepository { listOf(musician) }
        app.artistDetailRepository = ArtistDetailRepository { type, value -> ArtistDetail(value, type, "Banda TalkBack", null, "Descripción de prueba") }
        app.bandMusicianRepository = object : BandMusicianRepository {
            override suspend fun getMembers(bandId: Int): List<Artist> = members.toList()
            override suspend fun addMusician(bandId: Int, musicianId: Int): Artist {
                assertEquals(200, bandId); assertEquals(101, musicianId)
                calls++; members.add(musician); return musician
            }
        }
        app.favoriteArtistRepository = object : FavoriteArtistRepository {
            override suspend fun getFavorites(collectorId: Int): List<FavoritePerformer> = emptyList()
            override suspend fun addFavorite(collectorId: Int, artistId: Int, type: ArtistType): FavoritePerformer = error("No corresponde a HU12")
        }
        app.activeCollectorStore = InMemoryActiveCollectorStore(ActiveCollector(100, "Coleccionista de prueba"))
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val expected = checkNotNull(InstrumentationRegistry.getArguments().getString("hu12FontScale")).toFloat()
                    assertEquals("Escala efectiva", expected, activity.resources.configuration.fontScale, 0.01f)
                    events.add("font_scale=${activity.resources.configuration.fontScale}")
                    activity.supportFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, ArtistDetailFragment.newInstance(200, ArtistType.BAND.name))
                        .commitNow()
                }
                await("Detalle de banda") { node(id("add_band_musician")) != null }
                val add = checkNotNull(node(id("add_band_musician")))
                add.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
                SystemClock.sleep(500)
                // Coloca el foco inicial. La activación y navegación siguientes usan gestos táctiles.
                checkNotNull(node(id("add_band_musician"))).performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
                SystemClock.sleep(500)
                capture("01-banda-foco")
                doubleTap()
                await("Selector abierto con doble toque") { node(id("musician_selection_band")) != null }
                focusBySwiping("Músico de prueba TalkBack")
                capture("02-musico-foco")
                doubleTap()
                await("Músico seleccionado") { allNodes().any { it.isCheckable && it.isChecked && label(it).contains("Músico de prueba TalkBack") } }
                capture("03-seleccionado")
                focusBySwiping("Agregar músico", id("musician_selection_confirm"))
                capture("04-confirmar-foco")
                doubleTap()
                await("Diálogo de confirmación") { node("android:id/button1") != null }
                focusBySwiping("Agregar músico", "android:id/button1")
                capture("05-dialogo-foco")
                doubleTap()
                await("Regreso con integrante") { node(id("band_members"))?.text?.contains("Músico de prueba TalkBack") == true }
                assertEquals(1, calls)
                capture("06-regreso-banda")
                events.add("result=PASS; association_calls=$calls; member=101; band=200")
            }
        } finally {
            File(context.filesDir, "hu12-talkback-recorrido.txt").writeText(synchronized(events) { events.toList() }.joinToString("\n")+"\n")
            app.albumRepository = originalAlbums
            app.artistRepository = originalArtists
            app.artistDetailRepository = originalDetail
            app.bandMusicianRepository = originalBands
            app.favoriteArtistRepository = originalFavorites
            app.activeCollectorStore = originalCollector
            automation.setOnAccessibilityEventListener(null)
        }
    }

    private fun label(node: AccessibilityNodeInfo) = "${node.text?.toString().orEmpty()} ${node.contentDescription?.toString().orEmpty()}"
    private fun allNodes(): List<AccessibilityNodeInfo> {
        val found = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null) return
            found.add(node)
            for (i in 0 until node.childCount) visit(node.getChild(i))
        }
        visit(automation.rootInActiveWindow)
        return found
    }
    private fun node(viewId: String) = automation.rootInActiveWindow?.findAccessibilityNodeInfosByViewId(viewId)?.firstOrNull()
    private fun focusBySwiping(text: String, viewId: String? = null) {
        repeat(18) {
            val focused = automation.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
            events.add("focus=$it; id=${focused?.viewIdResourceName}; label=${focused?.let(::label)}; checked=${focused?.isChecked}")
            if (focused != null && label(focused).contains(text, ignoreCase = true) &&
                (viewId == null || focused.viewIdResourceName == viewId)) return
            swipeRight()
            SystemClock.sleep(650)
        }
        capture("failure-foco")
        throw AssertionError("TalkBack no alcanzó $text, id=$viewId mediante deslizamientos")
    }
    private fun doubleTap() {
        val metrics = context.resources.displayMetrics
        val x = metrics.widthPixels * .5f; val y = metrics.heightPixels * .6f
        repeat(2) {
            val start = SystemClock.uptimeMillis()
            touch(MotionEvent.ACTION_DOWN, x, y, start)
            SystemClock.sleep(50)
            touch(MotionEvent.ACTION_UP, x, y, start)
            SystemClock.sleep(80)
        }
        SystemClock.sleep(700)
    }
    private fun swipeRight() {
        val metrics = context.resources.displayMetrics
        val y = metrics.heightPixels * .65f
        val start = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, metrics.widthPixels * .25f, y, start)
        for (i in 1..10) {
            SystemClock.sleep(15)
            touch(MotionEvent.ACTION_MOVE, metrics.widthPixels * (.25f + .05f * i), y, start)
        }
        touch(MotionEvent.ACTION_UP, metrics.widthPixels * .75f, y, start)
    }
    private fun touch(action: Int, x: Float, y: Float, start: Long) {
        val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        try { assertTrue(automation.injectInputEvent(event, true)) } finally { event.recycle() }
    }
    private fun capture(name: String) {
        val screenshot = checkNotNull(automation.takeScreenshot())
        File(context.filesDir, "hu12-talkback-$name.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }
    private fun await(reason: String, test: () -> Boolean) {
        val limit = SystemClock.elapsedRealtime() + 15_000
        while (!test()) {
            if (SystemClock.elapsedRealtime() > limit) throw AssertionError(reason)
            SystemClock.sleep(150)
        }
    }
}
