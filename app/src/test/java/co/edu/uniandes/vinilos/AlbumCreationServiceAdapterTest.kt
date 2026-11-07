package co.edu.uniandes.vinilos

import co.edu.uniandes.vinilos.data.AlbumCreationService
import co.edu.uniandes.vinilos.data.AlbumCreationServiceAdapter
import co.edu.uniandes.vinilos.data.RemoteAlbumCreationRepository
import co.edu.uniandes.vinilos.model.NewAlbum
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
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Solicitud, respuesta y errores de POST /albums (HU07, #19). */
class AlbumCreationServiceAdapterTest {
    private lateinit var server: MockWebServer

    private val newAlbum = NewAlbum(
        name = "Buscando América",
        cover = "https://i.pinimg.com/564x/aa/5f/ed/portada.jpg",
        releaseDate = "1984-08-01",
        description = "Buscando América es el primer álbum de Rubén Blades y Seis del Solar.",
        genre = "Salsa",
        recordLabel = "Elektra",
    )

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    private fun repository() = RemoteAlbumCreationRepository(AlbumCreationServiceAdapter(
        Retrofit.Builder().baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().callTimeout(2, TimeUnit.SECONDS).build())
            .addConverterFactory(GsonConverterFactory.create()).build()
            .create(AlbumCreationService::class.java),
    ))

    @Test fun `envia POST albums con los seis campos y la fecha ISO`() = runTest {
        server.enqueue(MockResponse().setBody(createdBody()))

        repository().createAlbum(newAlbum)

        val request = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals("POST", request.method)
        assertEquals("/albums", request.path)
        assertTrue(request.getHeader("Content-Type").orEmpty().startsWith("application/json"))
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals(setOf("name", "cover", "releaseDate", "description", "genre", "recordLabel"), body.keySet())
        assertEquals("Buscando América", body["name"].asString)
        assertEquals("https://i.pinimg.com/564x/aa/5f/ed/portada.jpg", body["cover"].asString)
        assertEquals("1984-08-01", body["releaseDate"].asString)
        assertEquals("Salsa", body["genre"].asString)
        assertEquals("Elektra", body["recordLabel"].asString)
    }

    @Test fun `transforma la respuesta 200 en el album creado`() = runTest {
        server.enqueue(MockResponse().setBody(createdBody()))

        val created = repository().createAlbum(newAlbum)

        assertEquals(101, created.id)
        assertEquals("Buscando América", created.name)
        assertEquals("https://i.pinimg.com/564x/aa/5f/ed/portada.jpg", created.cover)
        assertEquals("Salsa", created.genre)
        assertTrue("un álbum recién creado no tiene intérpretes", created.performers.isEmpty())
    }

    @Test fun `propaga el 400 del backend sin reintentar`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400)
            .setBody("""{"statusCode":400,"message":"\"genre\" must be one of [Classical, Salsa, Rock, Folk]"}"""))

        try { repository().createAlbum(newAlbum); fail("Debe propagar HTTP 400") }
        catch (error: HttpException) { assertEquals(400, error.code()) }
        assertEquals("una sola solicitud: no se crean duplicados", 1, server.requestCount)
    }

    @Test fun `propaga errores 5xx para que el formulario permita reintentar`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))

        try { repository().createAlbum(newAlbum); fail("Debe propagar HTTP 503") }
        catch (error: HttpException) { assertEquals(503, error.code()) }
    }

    @Test fun `propaga la falta de red como IOException`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        try { repository().createAlbum(newAlbum); fail("Debe propagar el fallo de red") }
        catch (_: IOException) { }
    }

    @Test fun `rechaza una respuesta sin identificador`() = runTest {
        server.enqueue(MockResponse().setBody("""{"name":"Buscando América"}"""))

        try { repository().createAlbum(newAlbum); fail("Debe rechazar la respuesta sin id") }
        catch (_: IllegalArgumentException) { }
    }

    private fun createdBody() = """{
        "id":101,"name":"Buscando América","cover":"https://i.pinimg.com/564x/aa/5f/ed/portada.jpg",
        "releaseDate":"1984-08-01T00:00:00.000Z",
        "description":"Buscando América es el primer álbum de Rubén Blades y Seis del Solar.",
        "genre":"Salsa","recordLabel":"Elektra"
    }"""
}
