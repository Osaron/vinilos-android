package co.edu.uniandes.vinilos

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.DatePicker
import android.widget.RadioGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.PickerActions
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import co.edu.uniandes.vinilos.data.*
import co.edu.uniandes.vinilos.model.*
import co.edu.uniandes.vinilos.ui.artists.AssignPrizeFragment
import co.edu.uniandes.vinilos.ui.prizes.PrizeCatalogFragment
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okio.Buffer
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.net.URI
import java.util.Collections
import java.util.concurrent.TimeUnit

/** Opt-in: solo escribe en BackVynils aislado y accesible por loopback del host/emulador. */
@RunWith(AndroidJUnit4::class)
class PrizeBackendIntegrationTest {
    private val app get() = ApplicationProvider.getApplicationContext<VinilosApplication>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun waitFor(activity: ActivityScenario<MainActivity>, message: String, predicate: (MainActivity) -> Boolean) {
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(25)
        while (System.nanoTime() < until) {
            var ready = false
            activity.onActivity { ready = predicate(it) }
            if (ready) return
            Thread.sleep(50)
        }
        fail(message)
    }
    private fun capture(activity: ActivityScenario<MainActivity>, name: String) = activity.onActivity {
        val view = it.findViewById<View>(R.id.fragment_container)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(it.getExternalFilesDir(null), "hu13-fase3").apply { mkdirs() }.resolve("$name.png").outputStream()
            .use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        bitmap.recycle()
    }
    @Test fun createsOnceThenAssignsSamePrizeToMusicianAndBandWithoutChangingOtherArtists() {
        val url = InstrumentationRegistry.getArguments().getString("HU13_REAL_URL").orEmpty()
        assumeTrue("Requiere HU13_REAL_URL de backend aislado", url.isNotEmpty())
        require(URI(url).host in setOf("10.0.2.2", "127.0.0.1", "localhost"))
        val requests = Collections.synchronizedList(mutableListOf<JSONObject>())
        val client = OkHttpClient.Builder().retryOnConnectionFailure(false).callTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)
                val body = request.body?.let { value -> Buffer().also { value.writeTo(it) }.readUtf8() }
                requests += JSONObject().put("method", request.method).put("path", request.url.encodedPath)
                    .put("status", response.code).put("request", body ?: JSONObject.NULL)
                response
            }.build()
        val retrofit = Retrofit.Builder().baseUrl(url).client(client).addConverterFactory(GsonConverterFactory.create()).build()
        val prizes = RemotePrizeRepository(PrizeServiceAdapter(retrofit.create(PrizeService::class.java)))
        val awards = RemoteArtistPrizeRepository(ArtistPrizeServiceAdapter(retrofit.create(ArtistPrizeService::class.java)))
        val artists = retrofit.create(ArtistService::class.java)
        val musicianIds = runBlocking { artists.getMusicians().mapNotNull { it.id } }
        val bandIds = runBlocking { artists.getBands().mapNotNull { it.id } }
        val targets = musicianIds.map { ArtistType.MUSICIAN to it } + bandIds.map { ArtistType.BAND to it }
        fun snapshot() = runBlocking { targets.associateWith { (type, id) ->
            awards.getArtistPrizes(type, id).map { it.prizeId to it.date }.toSet()
        } }
        val before = snapshot()
        val beforeIds = runBlocking { prizes.getPrizes().map { it.id }.toSet() }
        val collectors = runBlocking { retrofit.create(CollectorService::class.java).getCollectors() }
        val collectorId = collectors.first().id!!
        val unique = "HU13-real-${System.currentTimeMillis()}"
        val gate = CompletableDeferred<Unit>()
        var createAttempts = 0
        val oldPrize = app.prizeRepository; val oldAwards = app.artistPrizeRepository; val oldCollector = app.activeCollectorStore
        app.prizeRepository = object : PrizeRepository {
            override suspend fun getPrizes() = prizes.getPrizes()
            override suspend fun createPrize(prize: NewPrize): Prize {
                createAttempts++; gate.await(); return prizes.createPrize(prize)
            }
        }
        app.artistPrizeRepository = awards
        app.activeCollectorStore = InMemoryActiveCollectorStore(ActiveCollector(collectorId, collectors.first().name!!))
        var createdId = -1
        try {
            ActivityScenario.launch(MainActivity::class.java).use { activity ->
                // Entrar desde el acceso real de Álbumes, no reemplazar el catálogo HU13 por un doble.
                waitFor(activity, "No cargó Álbumes") { it.findViewById<View>(R.id.loading_indicator)?.visibility != View.VISIBLE }
                onView(withId(R.id.open_prizes)).perform(scrollTo(), click())
                waitFor(activity, "No cargó el catálogo real") { it.findViewById<View>(R.id.prize_create)?.isEnabled == true }
                capture(activity, "real-01-catalogo")
                onView(withId(R.id.prize_create)).perform(click())
                onView(withId(R.id.prize_name_input)).perform(replaceText(unique))
                onView(withId(R.id.prize_description_input)).perform(replaceText("Integración real HU13-HU14"))
                onView(withId(R.id.prize_organization_input)).perform(replaceText("Vinilos pruebas aisladas"), closeSoftKeyboard())
                // Dos toques en el mismo turno de main; no consultar Espresso mientras gira el indicador.
                activity.onActivity {
                    it.findViewById<View>(R.id.prize_save).performClick()
                    it.findViewById<View>(R.id.prize_save).performClick()
                }
                waitFor(activity, "No comenzó el único envío") { createAttempts == 1 }
                activity.recreate()
                waitFor(activity, "Guardado no sobrevivió recreación") { it.findViewById<View>(R.id.prize_saving)?.visibility == View.VISIBLE }
                assertEquals(1, createAttempts)
                capture(activity, "real-02-recreacion-guardando")
                gate.complete(Unit)
                waitFor(activity, "No volvió al catálogo después del POST") { it.findViewById<View>(R.id.prize_create)?.isEnabled == true }
                val catalog = runBlocking { prizes.getPrizes() }
                val created = catalog.single { it.name == unique }
                createdId = created.id
                assertFalse(created.id in beforeIds)
                assertEquals("Integración real HU13-HU14", created.description)
                assertEquals("Vinilos pruebas aisladas", created.organization)
                assertEquals(1, requests.count { it.getString("method") == "POST" && it.getString("path") == "/prizes" })
                capture(activity, "real-03-creado")
                onView(withId(R.id.prize_back)).perform(click())
                onView(withId(R.id.open_prizes)).perform(scrollTo(), click())
                waitFor(activity, "No recargó catálogo al reabrir") { it.findViewById<View>(R.id.prize_create)?.isEnabled == true }
                assertEquals(created.id, runBlocking { prizes.getPrizes() }.single { it.name == unique }.id)
                capture(activity, "real-04-catalogo-reabierto")
                val expected = before.toMutableMap()
                for ((type, id) in listOf(ArtistType.MUSICIAN to musicianIds.first(), ArtistType.BAND to bandIds.first())) {
                    // Abrir la pantalla HU14 real con tipo/ID, sin simular su repositorio ni la asignación.
                    activity.onActivity {
                        it.supportFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, AssignPrizeFragment.newInstance(id, type.name, "Artista real $id"))
                            .addToBackStack("real_hu14_${type.name}").commit()
                    }
                    waitFor(activity, "No cargó selector HU14") { it.findViewById<View>(R.id.assign_prize_loading)?.visibility == View.GONE }
                    var matchingId = -1
                    activity.onActivity {
                        val group = it.findViewById<RadioGroup>(R.id.assign_prize_options)
                        val option = (0 until group.childCount).map(group::getChildAt).single { option -> option.tag == created.id }
                        matchingId = option.id
                    }
                    onView(withId(matchingId)).perform(scrollTo(), click())
                    onView(withId(R.id.assign_prize_pick_date)).perform(scrollTo(), click())
                    onView(withClassName(equalTo(DatePicker::class.java.name))).inRoot(isDialog())
                        .perform(PickerActions.setDate(2020, 5, 1))
                    onView(withText(R.string.hu14_date_accept)).inRoot(isDialog()).perform(click())
                    capture(activity, "real-05-${type.name}-premio-seleccionado")
                    onView(withId(R.id.assign_prize_confirm)).perform(scrollTo(), click())
                    waitFor(activity, "No terminó la asignación real") { it.findViewById<View>(R.id.prize_create)?.isEnabled == true }
                    expected[type to id] = expected.getValue(type to id) + (created.id to "2020-05-01")
                    assertEquals("No debe cambiar otro artista", expected, snapshot())
                }
                val report = JSONObject().put("baseUrl", url).put("prizeId", createdId).put("uniqueName", unique)
                    .put("postCreateCount", createAttempts).put("backendPostCount", requests.count { it.getString("method") == "POST" && it.getString("path") == "/prizes" })
                    .put("otherArtistsUnchanged", true).put("targetsVerified", targets.size).put("requests", JSONArray(requests.toList()))
                File(app.getExternalFilesDir(null), "hu13-fase3/REAL_HTTP.json").writeText(report.toString(2))
            }
        } finally {
            gate.complete(Unit)
            app.prizeRepository = oldPrize; app.artistPrizeRepository = oldAwards; app.activeCollectorStore = oldCollector
        }
    }
}
