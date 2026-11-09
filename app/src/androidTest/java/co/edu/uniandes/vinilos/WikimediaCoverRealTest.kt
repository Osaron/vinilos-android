package co.edu.uniandes.vinilos

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.request.ErrorResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Red externa explícita: se omite en la regresión controlada. */
@RunWith(AndroidJUnit4::class)
class WikimediaCoverRealTest {
    @Test fun downloadsAndDecodesCoverOverVerifiedHttps() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("coverReal") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val request = ImageRequest.Builder(context)
            .data("https://upload.wikimedia.org/wikipedia/en/4/4d/Queen_A_Night_At_The_Opera.png")
            .addHeader("User-Agent", "VinilosAndroid/1.0 (https://github.com/Osaron/vinilos-android)")
            .size(300, 300)
            .build()
        val result = context.imageLoader.execute(request)
        val reason = (result as? ErrorResult)?.throwable?.stackTraceToString().orEmpty()
        assertTrue("La portada real debe descargarse y decodificarse: $reason", result is SuccessResult)
    }
}
