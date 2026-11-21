package co.edu.uniandes.vinilos

import co.edu.uniandes.vinilos.data.*
import co.edu.uniandes.vinilos.model.ArtistType
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

class BandMusicianServiceAdapterTest {
    private lateinit var server: MockWebServer

    @Before fun setup() { server = MockWebServer().apply { start() } }
    @After fun cleanup() { server.shutdown() }

    private fun repository() = RemoteBandMusicianRepository(BandMusicianServiceAdapter(
        Retrofit.Builder().baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().callTimeout(1, TimeUnit.SECONDS).retryOnConnectionFailure(false).build())
            .addConverterFactory(GsonConverterFactory.create()).build()
            .create(BandMusicianService::class.java),
    ))

    @Test fun asociaLaBandaYElMusicoCorrectosSinCuerpo() = runTest {
        server.enqueue(MockResponse().setBody("""{"id":101,"name":"Músico","band":{"id":200}}"""))
        val saved = repository().addMusician(200, 101)
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("POST", request.method)
        assertEquals("/bands/200/musicians/101", request.path)
        assertEquals(0L, request.body.size)
        assertEquals(101, saved.id)
        assertEquals(ArtistType.MUSICIAN, saved.type)
        assertEquals(1, server.requestCount)
    }

    @Test fun consultaIntegrantesParaFiltrarLaSeleccion() = runTest {
        server.enqueue(MockResponse().setBody("""[{"id":101,"name":"Músico"}]"""))
        assertEquals(101, repository().getMembers(200).single().id)
        assertEquals("/bands/200/musicians/", server.takeRequest().path)
    }

    @Test fun rechazaIdentificadoresInvalidosSinEnviar() = runTest {
        try {
            repository().addMusician(0, 101)
            fail("Debía rechazar el identificador")
        } catch (_: IllegalArgumentException) {
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun rechazaUnaBandaOUnMusicoDiferenteEnLaRespuesta() = runTest {
        for (body in listOf(
            """{"id":102,"name":"Otro","band":{"id":200}}""",
            """{"id":101,"name":"Músico","band":{"id":201}}""",
            """{"id":101,"name":"","band":{"id":200}}""",
            """{"id":101,"name":"Músico"}""",
        )) {
            server.enqueue(MockResponse().setBody(body))
            try {
                repository().addMusician(200, 101)
                fail("Debía rechazar la confirmación inválida")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun conservaElCodigoHttpParaLaPantalla() = runTest {
        for (code in listOf(400, 404, 500)) {
            server.enqueue(MockResponse().setResponseCode(code))
            try {
                repository().addMusician(200, 101)
                fail("Debía propagar el error HTTP")
            } catch (error: HttpException) {
                assertEquals(code, error.code())
            }
        }
        assertEquals(3, server.requestCount)
    }

    @Test fun propagaElFalloDeRedSinReintentar() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        try {
            repository().addMusician(200, 101)
            fail("Debía propagar el fallo de red")
        } catch (_: IOException) {
            assertEquals(1, server.requestCount)
        }
    }
}
