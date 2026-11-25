package co.edu.uniandes.vinilos

import android.content.res.Configuration
import android.os.Process

/** Entrada exclusiva del APK de pruebas, invocada como shell en el AVD API21.
 * Usa el mismo cambio persistente que Settings de AOSP5, sin modificar la app.
 * https://android.googlesource.com/platform/packages/apps/Settings/+/android-5.0.1_r1/src/com/android/settings/DisplaySettings.java
 */
object PrizeLegacyFontScale {
    @JvmStatic fun main(args: Array<String>) {
        try { applyScale(args) } catch (failure: Throwable) {
            failure.printStackTrace(System.out)
            kotlin.system.exitProcess(1)
        }
    }
    private fun applyScale(args: Array<String>) {
        check(Process.myUid() == 2000 || Process.myUid() == 0) { "Solo shell del emulador de pruebas" }
        val scale = args.single().toFloat()
        check(scale in 0.85f..2.0f)
        val manager = Class.forName("android.app.ActivityManagerNative").getMethod("getDefault").invoke(null)
        val api = Class.forName("android.app.IActivityManager")
        val config = api.getMethod("getConfiguration").invoke(manager) as Configuration
        config.fontScale = scale
        api.getMethod("updatePersistentConfiguration", Configuration::class.java).invoke(manager, config)
        val applied = (api.getMethod("getConfiguration").invoke(manager) as Configuration).fontScale
        check(kotlin.math.abs(applied - scale) < 0.01f) { "No aplicó escala: $applied" }
        println("HU13_FONT_SCALE=$applied")
    }
}
