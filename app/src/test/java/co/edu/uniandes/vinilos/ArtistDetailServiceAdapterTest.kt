package co.edu.uniandes.vinilos

import co.edu.uniandes.vinilos.data.*
import co.edu.uniandes.vinilos.model.ArtistType
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
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

class ArtistDetailServiceAdapterTest {
    @Test fun `consulta detalle de musico y adapta albumes y fecha`() = runTest {
        withServer { server ->
            server.enqueue(MockResponse().setBody("""{
                "id":100,"name":" Rubén Blades ","image":"https://example.org/ruben.jpg",
                "description":"Cantautor","birthDate":"1948-07-16T00:00:00.000Z",
                "albums":[{"id":100,"name":"Buscando América","cover":"https://example.org/a.jpg",
                "releaseDate":"1984-08-01T00:00:00.000Z","recordLabel":"Elektra","genre":"Salsa"}]
            }"""))
            val detail = repository(server).getArtistDetail(ArtistType.MUSICIAN, 100)
            assertEquals("/musicians/100", server.takeRequest(2, TimeUnit.SECONDS)?.path)
            assertEquals(ArtistType.MUSICIAN, detail.type)
            assertEquals("Rubén Blades", detail.name)
            assertEquals("1948-07-16T00:00:00.000Z", detail.birthDate)
            assertEquals("Elektra", detail.albums.single().recordLabel)
        }
    }

    @Test fun `consulta detalle de banda por su endpoint especifico`() = runTest {
        withServer { server ->
            server.enqueue(MockResponse().setBody("""{
                "id":101,"name":"Queen","creationDate":"1970-01-01T00:00:00.000Z",
                "albums":[],"musicians":[]
            }"""))
            val detail = repository(server).getArtistDetail(ArtistType.BAND, 101)
            assertEquals("/bands/101", server.takeRequest(2, TimeUnit.SECONDS)?.path)
            assertEquals(ArtistType.BAND, detail.type)
            assertEquals("1970-01-01T00:00:00.000Z", detail.creationDate)
            assertTrue(detail.albums.isEmpty())
        }
    }

    @Test fun `rechaza detalle con identidad distinta`() = runTest {
        withServer { server ->
            server.enqueue(MockResponse().setBody("""{"id":101,"name":"Queen"}"""))
            try { repository(server).getArtistDetail(ArtistType.MUSICIAN, 100); fail("Debe rechazar identidad distinta") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun `propaga error http y desconexion`() = runTest {
        withServer { server ->
            server.enqueue(MockResponse().setResponseCode(404))
            try { repository(server).getArtistDetail(ArtistType.MUSICIAN, 999); fail("Debe propagar HTTP 404") }
            catch (error: HttpException) { assertEquals(404, error.code()) }
        }
        withServer { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            try { repository(server).getArtistDetail(ArtistType.BAND, 101); fail("Debe propagar HTTP 503") }
            catch (error: HttpException) { assertEquals(503, error.code()) }
        }
        withServer { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            try { repository(server).getArtistDetail(ArtistType.MUSICIAN, 100); fail("Debe propagar desconexión") }
            catch (_: IOException) { }
        }
    }

    private suspend fun withServer(block: suspend (MockWebServer) -> Unit) {
        val server = MockWebServer()
        server.start()
        try { block(server) } finally { server.shutdown() }
    }

    private fun repository(server: MockWebServer) = RemoteArtistDetailRepository(
        ArtistDetailServiceAdapter(
            Retrofit.Builder().baseUrl(server.url("/"))
                .client(OkHttpClient.Builder().retryOnConnectionFailure(false).build())
                .addConverterFactory(GsonConverterFactory.create())
                .build().create(ArtistDetailService::class.java)
        )
    )
}
