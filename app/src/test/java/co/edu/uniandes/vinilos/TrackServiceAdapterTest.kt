package co.edu.uniandes.vinilos

import co.edu.uniandes.vinilos.data.RemoteTrackAssociationRepository
import co.edu.uniandes.vinilos.data.TrackAssociationException
import co.edu.uniandes.vinilos.data.TrackAssociationException.Reason
import co.edu.uniandes.vinilos.data.TrackService
import co.edu.uniandes.vinilos.data.TrackServiceAdapter
import co.edu.uniandes.vinilos.model.NewTrack
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** Solicitud, respuesta y errores de POST /albums/{albumId}/tracks (HU08, #33). */
class TrackServiceAdapterTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    private fun repository() = RemoteTrackAssociationRepository(TrackServiceAdapter(
        Retrofit.Builder().baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().callTimeout(2, TimeUnit.SECONDS).retryOnConnectionFailure(false).build())
            .addConverterFactory(GsonConverterFactory.create()).build()
            .create(TrackService::class.java),
    ))

    @Test fun `envia POST al album elegido con nombre y duracion recortados`() = runTest {
        server.enqueue(MockResponse().setBody(savedBody(albumId = 100)))

        repository().addTrack(100, NewTrack("  Decisiones ", " 5:05 "))

        val request = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals("POST", request.method)
        assertEquals("/albums/100/tracks", request.path)
        assertTrue(request.getHeader("Content-Type").orEmpty().startsWith("application/json"))
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals("solo los campos del contrato", setOf("name", "duration"), body.keySet())
        assertEquals("Decisiones", body["name"].asString)
        assertEquals("5:05", body["duration"].asString)
    }

    @Test fun `transforma la respuesta 200 en la cancion guardada`() = runTest {
        server.enqueue(MockResponse().setBody(savedBody(albumId = 100)))

        val saved = repository().addTrack(100, NewTrack("Decisiones", "5:05"))

        assertEquals(301, saved.id)
        assertEquals("Decisiones", saved.name)
        assertEquals("5:05", saved.duration)
    }

    @Test fun `acepta una respuesta sin el album embebido`() = runTest {
        server.enqueue(MockResponse().setBody("""{"id":7,"name":"Decisiones","duration":"5:05"}"""))

        assertEquals(7, repository().addTrack(100, NewTrack("Decisiones", "5:05")).id)
    }

    @Test fun `rechaza la cancion asociada a otro album`() = runTest {
        server.enqueue(MockResponse().setBody(savedBody(albumId = 101)))

        assertReason(Reason.INVALID_RESPONSE) { repository().addTrack(100, NewTrack("Decisiones", "5:05")) }
    }

    @Test fun `rechaza una respuesta sin identificador`() = runTest {
        server.enqueue(MockResponse().setBody("""{"name":"Decisiones","duration":"5:05"}"""))

        assertReason(Reason.INVALID_RESPONSE) { repository().addTrack(100, NewTrack("Decisiones", "5:05")) }
    }

    @Test fun `rechaza un cuerpo que no es JSON de cancion`() = runTest {
        server.enqueue(MockResponse().setBody("[]"))

        assertReason(Reason.INVALID_RESPONSE) { repository().addTrack(100, NewTrack("Decisiones", "5:05")) }
    }

    @Test fun `el 404 indica que el album ya no existe`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404)
            .setBody("""{"statusCode":404,"message":"The album with the given id was not found"}"""))

        assertReason(Reason.ALBUM_NOT_FOUND) { repository().addTrack(999, NewTrack("Decisiones", "5:05")) }
    }

    @Test fun `el 400 se informa como rechazo y no se reintenta`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400)
            .setBody("""{"statusCode":400,"message":"ValidationError: \"duration\" is required"}"""))

        assertReason(Reason.REJECTED) { repository().addTrack(100, NewTrack("Decisiones", "5:05")) }
        assertEquals("una sola solicitud: no se crean duplicados", 1, server.requestCount)
    }

    @Test fun `el 5xx se informa como falla del servidor`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))

        assertReason(Reason.SERVER) { repository().addTrack(100, NewTrack("Decisiones", "5:05")) }
    }

    @Test fun `la conexion cortada se informa como resultado incierto sin reintentar`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))

        assertReason(Reason.NETWORK) { repository().addTrack(100, NewTrack("Decisiones", "5:05")) }
        assertEquals("OkHttp no debe reenviar el POST", 1, server.requestCount)
    }

    @Test fun `no envia nada con un album invalido o campos vacios`() = runTest {
        for ((albumId, track) in listOf(0 to NewTrack("Decisiones", "5:05"), 100 to NewTrack("  ", "5:05"), 100 to NewTrack("Decisiones", " "))) {
            try { repository().addTrack(albumId, track); fail("Debe rechazar $albumId $track") }
            catch (_: IllegalArgumentException) { }
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun `consulta las canciones del album para revisar un envio incierto`() = runTest {
        server.enqueue(MockResponse().setBody("""[{"id":1,"name":"Decisiones","duration":"5:05"},{"id":2,"name":" "}]"""))

        val tracks = repository().getTracks(100)

        assertEquals("/albums/100/tracks", requireNotNull(server.takeRequest(2, TimeUnit.SECONDS)).path)
        assertEquals(listOf("Decisiones"), tracks.map { it.name })
    }

    private suspend fun assertReason(expected: Reason, block: suspend () -> Unit) {
        try { block(); fail("Debe lanzar TrackAssociationException($expected)") }
        catch (error: TrackAssociationException) { assertEquals(expected, error.reason) }
    }

    private fun savedBody(albumId: Int) = """{
        "name":"Decisiones","duration":"5:05",
        "album":{"id":$albumId,"name":"Buscando América","cover":"https://example.org/c.jpg"},
        "id":301
    }"""
}
