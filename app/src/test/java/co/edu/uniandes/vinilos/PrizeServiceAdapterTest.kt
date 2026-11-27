package co.edu.uniandes.vinilos

import co.edu.uniandes.vinilos.data.*
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class PrizeServiceAdapterTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: PrizeServiceAdapter
    private val prize = NewPrize(" Premio ", " Descripción ", " Organización ")
    @Before fun setup() {
        server = MockWebServer(); server.start()
        adapter = PrizeServiceAdapter(Retrofit.Builder().baseUrl(server.url("/"))
            .client(okhttp3.OkHttpClient.Builder().retryOnConnectionFailure(false).callTimeout(2, TimeUnit.SECONDS).build())
            .addConverterFactory(GsonConverterFactory.create()).build().create(PrizeService::class.java))
    }
    @After fun cleanup() { server.shutdown() }
    private fun response(code: Int, body: String) = server.enqueue(MockResponse().setResponseCode(code).setBody(body)
        .addHeader("Content-Type", "application/json"))
    @Test fun createsWithExactThreeTrimmedFieldsAndAcceptsHttp200() = runBlocking {
        response(200, """{"id":8,"name":"Premio","description":"Descripción","organization":"Organización"}""")
        val saved = adapter.createPrize(prize)
        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("POST", request.method); assertEquals("/prizes", request.path)
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals(setOf("name", "description", "organization"), body.keySet())
        assertEquals("Premio", body["name"].asString); assertEquals("Descripción", saved.description)
        assertEquals(8, saved.id); assertEquals(1, server.requestCount)
    }
    @Test fun catalogRetainsDescriptionAndUsesSharedPrizeModel() = runBlocking {
        response(200, """[{"id":100,"name":"Grammy","organization":"NARAS","description":"Música","performerPrizes":[]}]""")
        val prizes = adapter.getPrizes()
        assertEquals("Música", prizes.single().description); assertEquals(100, prizes.single().id)
        assertEquals("/prizes", server.takeRequest().path)
    }
    @Test fun blankFieldsAreRejectedBeforeAnyHttp() = runBlocking {
        try { adapter.createPrize(prize.copy(description = "  ")); fail("Debe rechazar blancos") }
        catch (_: IllegalArgumentException) { assertEquals(0, server.requestCount) }
    }
    @Test fun invalidSuccessIsUncertainRatherThanPermissionToRetry() = runBlocking {
        response(200, """{"id":0,"name":"Premio","description":"Descripción","organization":"Organización"}""")
        try { adapter.createPrize(prize); fail("No hay confirmación válida") }
        catch (_: PrizeWriteUnconfirmed) { assertEquals(1, server.requestCount) }
    }
    @Test fun inconsistentResponseDoesNotReportSuccess() = runBlocking {
        response(200, """{"id":8,"name":"Otro","description":"Descripción","organization":"Organización"}""")
        try { adapter.createPrize(prize); fail("El premio no coincide") }
        catch (_: PrizeWriteUnconfirmed) { assertEquals(1, server.requestCount) }
    }
    @Test fun rejectionIsPropagatedWithoutAutomaticRetry() = runBlocking {
        response(400, "{}")
        try { adapter.createPrize(prize); fail("Debe propagar rechazo") }
        catch (error: HttpException) { assertEquals(400, error.code()); assertEquals(1, server.requestCount) }
    }
}
