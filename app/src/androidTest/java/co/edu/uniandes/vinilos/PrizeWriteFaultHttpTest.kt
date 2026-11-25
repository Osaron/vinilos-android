package co.edu.uniandes.vinilos

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.data.*
import co.edu.uniandes.vinilos.ui.prizes.PrizeCatalogFragment
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Errores HTTP controlados por un proxy; las escrituras de éxito van a BackVynils aislado real. */
@RunWith(AndroidJUnit4::class)
class PrizeWriteFaultHttpTest {
    private val app get() = ApplicationProvider.getApplicationContext<VinilosApplication>()
    private fun waitFor(activity: ActivityScenario<MainActivity>, condition: (MainActivity) -> Boolean) {
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (System.nanoTime() < until) {
            var ready = false; activity.onActivity { ready = condition(it) }
            if (ready) return
            Thread.sleep(50)
        }
        fail("No se alcanzó el estado HTTP esperado")
    }
    private fun capture(activity: ActivityScenario<MainActivity>, name: String) = activity.onActivity {
        val root = it.findViewById<View>(R.id.fragment_container)
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        File(it.getExternalFilesDir(null), "hu13-fase3").apply { mkdirs() }.resolve("$name.png").outputStream()
            .use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        bitmap.recycle()
    }
    private fun exercise(dropAfterCommit: Boolean) {
        val realUrl = InstrumentationRegistry.getArguments().getString("HU13_REAL_URL").orEmpty()
        assumeTrue("Requiere backend aislado", realUrl.isNotEmpty())
        require(URI(realUrl).host in setOf("10.0.2.2", "127.0.0.1", "localhost"))
        val postRequests = AtomicInteger(); val backendWrites = AtomicInteger()
        val forwarder = OkHttpClient.Builder().retryOnConnectionFailure(false).callTimeout(15, TimeUnit.SECONDS).build()
        val proxy = MockWebServer()
        proxy.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val post = request.method == "POST"
                if (post && postRequests.incrementAndGet() == 1 && !dropAfterCommit) {
                    return MockResponse().setResponseCode(400).setBody("{\"message\":\"Rechazo inyectado por proxy de prueba\"}")
                        .addHeader("Content-Type", "application/json")
                }
                val body = if (post) request.body.readUtf8().toRequestBody("application/json".toMediaType()) else null
                val actual = Request.Builder().url(realUrl.trimEnd('/') + request.path).method(request.method!!, body).build()
                return forwarder.newCall(actual).execute().use { response ->
                    if (post && response.isSuccessful) backendWrites.incrementAndGet()
                    val result = MockResponse().setResponseCode(response.code).setBody(response.body?.string().orEmpty())
                        .addHeader("Content-Type", "application/json")
                    if (post && dropAfterCommit) result.setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST) else result
                }
            }
        }
        proxy.start()
        val realRetrofit = Retrofit.Builder().baseUrl(realUrl).client(forwarder).addConverterFactory(GsonConverterFactory.create()).build()
        val real = RemotePrizeRepository(PrizeServiceAdapter(realRetrofit.create(PrizeService::class.java)))
        val proxyRetrofit = Retrofit.Builder().baseUrl(proxy.url("/"))
            .client(OkHttpClient.Builder().retryOnConnectionFailure(false).callTimeout(5, TimeUnit.SECONDS).build())
            .addConverterFactory(GsonConverterFactory.create()).build()
        val oldPrize = app.prizeRepository; val oldCollector = app.activeCollectorStore
        app.prizeRepository = RemotePrizeRepository(PrizeServiceAdapter(proxyRetrofit.create(PrizeService::class.java)))
        app.activeCollectorStore = InMemoryActiveCollectorStore(ActiveCollector(7, "Prueba aislada"))
        val mode = if (dropAfterCommit) "respuesta-perdida" else "rechazo-recuperable"
        val unique = "HU13-$mode-${System.currentTimeMillis()}"
        try {
            ActivityScenario.launch(MainActivity::class.java).use { activity ->
                activity.onActivity {
                    it.supportFragmentManager.beginTransaction().replace(R.id.fragment_container, PrizeCatalogFragment())
                        .addToBackStack("proxy_prize_catalog").commit()
                }
                waitFor(activity) { it.findViewById<View>(R.id.prize_create)?.isEnabled == true }
                onView(withId(R.id.prize_create)).perform(click())
                onView(withId(R.id.prize_name_input)).perform(replaceText(unique))
                onView(withId(R.id.prize_description_input)).perform(replaceText("Error controlado, escritura real"))
                onView(withId(R.id.prize_organization_input)).perform(replaceText("Vinilos pruebas aisladas"), closeSoftKeyboard())
                onView(withId(R.id.prize_save)).perform(scrollTo(), click())
                if (dropAfterCommit) {
                    waitFor(activity) { it.findViewById<View>(R.id.prize_review_catalog)?.visibility == View.VISIBLE }
                    capture(activity, "http-07-respuesta-perdida")
                    onView(withId(R.id.prize_review_catalog)).perform(scrollTo(), click())
                    waitFor(activity) { it.findViewById<View>(R.id.prize_back_draft)?.visibility == View.VISIBLE && it.findViewById<View>(R.id.prize_loading)?.visibility == View.GONE }
                    capture(activity, "http-08-premio-existente-tras-timeout")
                    onView(withId(R.id.prize_back_draft)).perform(click())
                    onView(withId(R.id.prize_name_input)).check(matches(withText(unique)))
                    activity.recreate()
                    onView(withId(R.id.prize_review_catalog)).check(matches(isDisplayed()))
                    assertEquals(1, postRequests.get())
                } else {
                    waitFor(activity) { it.findViewById<TextView>(R.id.create_prize_status)?.text.toString() == it.getString(R.string.hu13_rejected) }
                    onView(withId(R.id.prize_name_input)).check(matches(withText(unique)))
                    capture(activity, "http-06-rechazo-recuperable")
                    onView(withId(R.id.prize_save)).perform(scrollTo(), click())
                    waitFor(activity) { it.findViewById<View>(R.id.prize_create)?.isEnabled == true }
                    assertEquals(2, postRequests.get())
                }
                val saved = runBlocking { real.getPrizes() }.single { it.name == unique }
                assertEquals(1, backendWrites.get())
                val proof = JSONObject().put("mode", mode).put("faultSource", "MockWebServer proxy")
                    .put("backendBaseUrl", realUrl).put("name", unique).put("savedId", saved.id)
                    .put("clientPostRequests", postRequests.get()).put("successfulBackendWrites", backendWrites.get())
                    .put("draftKept", true)
                File(app.getExternalFilesDir(null), "hu13-fase3/$mode.json").writeText(proof.toString(2))
            }
        } finally { app.prizeRepository = oldPrize; app.activeCollectorStore = oldCollector; proxy.shutdown() }
    }
    @Test fun confirmed400KeepsDraftAndExplicitRetryWritesOnlyOnce() = exercise(false)
    @Test fun lostResponseAfterRealCommitNeverCreatesAgain() = exercise(true)
}
