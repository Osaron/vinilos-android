package co.edu.uniandes.vinilos

import co.edu.uniandes.vinilos.data.ArtistAlbumService
import co.edu.uniandes.vinilos.data.ArtistAlbumServiceAdapter
import co.edu.uniandes.vinilos.data.RemoteArtistAlbumRepository
import co.edu.uniandes.vinilos.model.ArtistType
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/** HU15: contrato del Service Adapter de la discografía con MockWebServer. */
class ArtistAlbumServiceAdapterTest {
    private lateinit var server: MockWebServer

    @Before fun setup() { server = MockWebServer().apply { start() } }
    @After fun cleanup() { server.shutdown() }

    private fun repository() = RemoteArtistAlbumRepository(ArtistAlbumServiceAdapter(
        Retrofit.Builder().baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().callTimeout(1, TimeUnit.SECONDS).retryOnConnectionFailure(false).build())
            .addConverterFactory(GsonConverterFactory.create()).build()
            .create(ArtistAlbumService::class.java),
    ))

    @Test fun consultaLaDiscografiaPorLaRutaDelTipoDeArtista() = runTest {
        server.enqueue(MockResponse().setBody("""[{"id":100,"name":"Buscando América"},{"id":101},{"id":0},{}]"""))
        server.enqueue(MockResponse().setBody("""[{"id":102,"name":"A Night at the Opera"}]"""))
        assertEquals(setOf(100, 101), repository().getAlbumIds(ArtistType.MUSICIAN, 50))
        assertEquals("/musicians/50/albums/", server.takeRequest().path)
        assertEquals(setOf(102), repository().getAlbumIds(ArtistType.BAND, 60))
        assertEquals("/bands/60/albums/", server.takeRequest().path)
    }

    @Test fun asociaMusicoPorLaRutaDelAlbumSinCuerpo() = runTest {
        server.enqueue(MockResponse().setBody("""{"id":102,"name":"A Night","performers":[{"id":101},{"id":50}]}"""))
        repository().addAlbum(ArtistType.MUSICIAN, 50, 102)
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("POST", request.method)
        assertEquals("/albums/102/musicians/50", request.path)
        assertEquals(0L, request.body.size)
    }

    @Test fun asociaBandaPorLaRutaDelAlbumQueConservaOtrosInterpretes() = runTest {
        server.enqueue(MockResponse().setBody("""{"id":100,"performers":[{"id":100},{"id":60}]}"""))
        repository().addAlbum(ArtistType.BAND, 60, 100)
        assertEquals("/albums/100/bands/60", server.takeRequest().path)
        assertEquals(1, server.requestCount)
    }

    @Test fun aceptaUnaConfirmacionSinListaDeInterpretes() = runTest {
        server.enqueue(MockResponse().setBody("""{"id":102,"name":"A Night"}"""))
        repository().addAlbum(ArtistType.MUSICIAN, 50, 102)
    }

    @Test fun rechazaUnaConfirmacionDeOtroAlbumOSinElArtista() = runTest {
        for (body in listOf(
            """{"id":103,"performers":[{"id":50}]}""",
            """{"performers":[{"id":50}]}""",
            """{"id":102,"performers":[{"id":51}]}""",
        )) {
            server.enqueue(MockResponse().setBody(body))
            try {
                repository().addAlbum(ArtistType.MUSICIAN, 50, 102)
                fail("Debía rechazar la confirmación inválida: $body")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun rechazaIdentificadoresInvalidosSinEnviar() = runTest {
        for ((artist, album) in listOf(0 to 1, 1 to 0, -1 to 1)) {
            try {
                repository().addAlbum(ArtistType.BAND, artist, album)
                fail("Debía rechazar $artist/$album")
            } catch (_: IllegalArgumentException) { }
        }
        try {
            repository().getAlbumIds(ArtistType.MUSICIAN, 0)
            fail("Debía rechazar el artista 0")
        } catch (_: IllegalArgumentException) { }
        assertEquals(0, server.requestCount)
    }

    @Test fun propagaElErrorHttpParaQueLaPantallaLoInforme() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"The album with the given id was not found"}"""))
        try {
            repository().addAlbum(ArtistType.MUSICIAN, 50, 999)
            fail("Debía propagar el 404")
        } catch (error: HttpException) {
            assertEquals(404, error.code())
        }
    }

    @Test fun propagaLaFaltaDeRedSinReintentar() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        try {
            repository().addAlbum(ArtistType.MUSICIAN, 50, 102)
            fail("Debía propagar IOException")
        } catch (_: IOException) {
            assertEquals(1, server.requestCount)
        }
    }
}
