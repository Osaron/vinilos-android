package co.edu.uniandes.vinilos

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.data.*
import co.edu.uniandes.vinilos.model.Prize
import co.edu.uniandes.vinilos.ui.prizes.PrizeCatalogFragment
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in de herramientas instaladas en el emulador; no se confunde ATF con Scanner nativo. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 28)
class PrizeScannerTest {
    // Scanner evalúa controles y contraste reales; los datos del recorrido son un doble declarado.
    private val app get() = ApplicationProvider.getApplicationContext<VinilosApplication>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var automation: UiAutomation
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
        .bufferedReader().use { it.readText() }
    private val directory get() = File(app.getExternalFilesDir(null) ?: app.filesDir, "hu13-fase3").apply { mkdirs() }
    private fun capture(name: String) {
        val image = checkNotNull(automation.takeScreenshot())
        directory.resolve("$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
    private fun inventory(name: String) {
        val list = JSONArray()
        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null) return
            val bounds = Rect(); node.getBoundsInScreen(bounds)
            list.put(JSONObject().put("package", node.packageName).put("text", node.text).put("description", node.contentDescription)
                .put("viewId", node.viewIdResourceName).put("clickable", node.isClickable).put("bounds", bounds.toShortString()))
            for (i in 0 until node.childCount) visit(node.getChild(i))
        }
        automation.windows.forEach { visit(it.root) }
        directory.resolve("$name.json").writeText(list.toString(2))
    }
    private fun hasText(expected: String): Boolean {
        fun find(node: AccessibilityNodeInfo?): Boolean {
            if (node == null) return false
            if (node.text?.toString() == expected) return true
            return (0 until node.childCount).any { find(node.getChild(it)) }
        }
        return automation.windows.any { find(it.root) }
    }
    private fun clickNode(id: String) {
        fun find(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.viewIdResourceName == id) return node
            for (i in 0 until node.childCount) find(node.getChild(i))?.let { return it }
            return null
        }
        val node = automation.windows.firstNotNullOfOrNull { find(it.root) }
        checkNotNull(node) { "No existe $id" }
        check(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }
    @Test fun scannerNativeFourStates() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("hu13Scanner") == "true")
        automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val info = automation.serviceInfo
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = info
        val previous = shell("settings get secure enabled_accessibility_services").trim()
        val enabled = shell("settings get secure accessibility_enabled").trim()
        val oldPrize = app.prizeRepository; val oldAlbums = app.albumRepository; val oldCollector = app.activeCollectorStore
        app.albumRepository = AlbumRepository { emptyList() }
        app.prizeRepository = object : PrizeRepository {
            override suspend fun getPrizes() = listOf(Prize(100, "Grammy Award", "NARAS", "Reconocimiento musical"))
            override suspend fun createPrize(prize: NewPrize) = Prize(200, prize.name, prize.organization, prize.description)
        }
        app.activeCollectorStore = InMemoryActiveCollectorStore(ActiveCollector(7, "Prueba"))
        try {
            shell("settings put secure enabled_accessibility_services com.google.android.apps.accessibility.auditor/.ScannerService")
            shell("settings put secure accessibility_enabled 1")
            SystemClock.sleep(1500)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { it.supportFragmentManager.beginTransaction().replace(R.id.fragment_container, PrizeCatalogFragment()).commit() }
                SystemClock.sleep(700)
                fun scan(state: String) {
                    capture("scanner-$state-antes")
                    clickNode("com.google.android.apps.accessibility.auditor:id/check_button")
                    SystemClock.sleep(300)
                    clickNode("com.google.android.apps.accessibility.auditor:id/snapshot_button_container")
                    val deadline = SystemClock.elapsedRealtime() + 20_000
                    while (!hasText("No suggestions") && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(200)
                    capture("scanner-$state-resultado")
                    inventory("scanner-$state-resultado")
                    check(hasText("No suggestions")) { "Scanner reportó sugerencias o no terminó: $state" }
                    shell("input keyevent KEYCODE_BACK")
                    SystemClock.sleep(700)
                }
                scan("catalogo")
                scenario.onActivity { it.findViewById<android.view.View>(R.id.prize_create).performClick() }
                SystemClock.sleep(700)
                scan("formulario")
                scenario.onActivity { it.findViewById<android.view.View>(R.id.prize_save).performClick() }
                SystemClock.sleep(700)
                scan("validacion")
                scenario.onActivity {
                    it.findViewById<android.widget.EditText>(R.id.prize_name_input).setText("Premio Scanner")
                    it.findViewById<android.widget.EditText>(R.id.prize_description_input).setText("Descripción de prueba")
                    it.findViewById<android.widget.EditText>(R.id.prize_organization_input).setText("Organización Scanner")
                    it.findViewById<android.view.View>(R.id.prize_save).performClick()
                }
                SystemClock.sleep(700)
                scan("creado")
                shell("am start -n com.google.android.apps.accessibility.auditor/.ui.ScannerSettingsActivity")
                SystemClock.sleep(700)
                capture("scanner-configuracion")
                inventory("scanner-configuracion")
            }
        } finally {
            if (previous == "null" || previous.isEmpty()) shell("settings delete secure enabled_accessibility_services")
            else shell("settings put secure enabled_accessibility_services $previous")
            shell("settings put secure accessibility_enabled ${if (enabled == "null") "0" else enabled}")
            app.prizeRepository = oldPrize; app.albumRepository = oldAlbums; app.activeCollectorStore = oldCollector
        }
    }
}
