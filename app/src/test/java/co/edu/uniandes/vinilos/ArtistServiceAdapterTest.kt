package co.edu.uniandes.vinilos

import co.edu.uniandes.vinilos.data.*
import co.edu.uniandes.vinilos.model.ArtistType
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

class ArtistServiceAdapterTest {
    @Test fun `combina musicos y bandas conservando tipo e id`() = runTest {
        withServer { server ->
            server.enqueue(MockResponse().setBody("""[{"id":7,"name":"  Celia Cruz  ","image":"https://example.org/celia.jpg","description":"Cantante","birthDate":"1925-10-21","albums":[]}]"""))
            server.enqueue(MockResponse().setBody("""[{"id":7,"name":"Queen","image":null,"description":null,"creationDate":"1970-01-01"}]"""))
            val artists = repository(server).getArtists()
            assertEquals("/musicians", server.takeRequest(2, TimeUnit.SECONDS)?.path)
            assertEquals("/bands", server.takeRequest(2, TimeUnit.SECONDS)?.path)
            assertEquals(listOf(ArtistType.MUSICIAN, ArtistType.BAND), artists.map { it.type })
            assertEquals(listOf(7, 7), artists.map { it.id })
            assertEquals("Celia Cruz", artists.first().name)
            assertEquals("https://example.org/celia.jpg", artists.first().image)
            assertEquals("Cantante", artists.first().description)
            assertEquals("", artists.last().image)
            assertEquals("", artists.last().description)
        }
    }

    @Test fun `dos respuestas vacias producen catalogo vacio`() = runTest {
        withServer { server ->
            repeat(2) { server.enqueue(MockResponse().setBody("[]")) }
            assertTrue(repository(server).getArtists().isEmpty())
        }
    }

    @Test fun `bandas siguen disponibles cuando no hay musicos`() = runTest {
        withServer { server ->
            server.enqueue(MockResponse().setBody("[]"))
            server.enqueue(MockResponse().setBody("""[{"id":2,"name":"Queen"}]"""))
            assertEquals(ArtistType.BAND, repository(server).getArtists().single().type)
        }
    }

    @Test fun `rechaza identidades invalidas para no navegar a otro artista`() = runTest {
        for (body in listOf("""[{"name":"Sin id"}]""", """[{"id":0,"name":"Inválido"}]""", """[{"id":1,"name":"   "}]""", """[{"id":1}]""")) {
            withServer { server ->
                server.enqueue(MockResponse().setBody(body))
                try { repository(server).getArtists(); fail("Debe rechazar identidad inválida") }
                catch (_: IllegalArgumentException) { }
            }
        }
    }

    @Test fun `fallo en bandas no publica un listado parcial de musicos`() = runTest {
        withServer { server ->
            server.enqueue(MockResponse().setBody("""[{"id":1,"name":"Celia"}]"""))
            server.enqueue(MockResponse().setResponseCode(503))
            try { repository(server).getArtists(); fail("Debe propagar HTTP 503") }
            catch (error: HttpException) { assertEquals(503, error.code()) }
        }
    }

    @Test fun `fallo http de musicos permite reintentar la consulta completa`() = runTest {
        withServer { server ->
            server.enqueue(MockResponse().setResponseCode(500))
            val repository = repository(server)
            try { repository.getArtists(); fail("Debe propagar HTTP 500") }
            catch (error: HttpException) { assertEquals(500, error.code()) }
            server.enqueue(MockResponse().setBody("[]"))
            server.enqueue(MockResponse().setBody("[]"))
            assertTrue(repository.getArtists().isEmpty())
        }
    }

    @Test fun `propaga corte de red sin convertirlo en catalogo vacio`() = runTest {
        withServer { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            try { repository(server).getArtists(); fail("Debe propagar error de red") }
            catch (_: IOException) { }
        }
    }

    private suspend fun withServer(block: suspend (MockWebServer) -> Unit) {
        val server = MockWebServer()
        server.start()
        try { block(server) } finally { server.shutdown() }
    }

    private fun repository(server: MockWebServer) = RemoteArtistRepository(ArtistServiceAdapter(
        Retrofit.Builder().baseUrl(server.url("/"))
            .client(okhttp3.OkHttpClient.Builder().retryOnConnectionFailure(false).build())
            .addConverterFactory(GsonConverterFactory.create()).build().create(ArtistService::class.java)))
}
