package co.edu.uniandes.vinilos

import co.edu.uniandes.vinilos.data.CollectorDetailServiceAdapter
import co.edu.uniandes.vinilos.data.CollectorService
import co.edu.uniandes.vinilos.data.RemoteCollectorDetailRepository
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class CollectorDetailServiceAdapterTest {
    @Test fun `consulta el id seleccionado y adapta relaciones reales`() = runTest {
        withServer { server ->
            server.enqueue(MockResponse().setBody("""{
                "id":100,"name":" Manolo Bellon ","email":"manollo@example.org","telephone":"3001234567",
                "comments":[{"id":11,"description":"Buen álbum","rating":5}],
                "favoritePerformers":[{"id":7,"name":"Rubén Blades","image":"https://example.org/ruben.png","description":"Cantautor","birthDate":"1948-07-16T00:00:00.000Z"}],
                "collectorAlbums":[{"id":12,"price":35,"status":"Active"}]
            }"""))
            val detail = repository(server).getCollectorDetail(100)
            assertEquals("/collectors/100", server.takeRequest(2, TimeUnit.SECONDS)?.path)
            assertEquals(100, detail.id)
            assertEquals("Manolo Bellon", detail.name)
            assertEquals("Buen álbum", detail.comments.single().description)
            assertEquals(5, detail.comments.single().rating)
            assertEquals("Rubén Blades", detail.favoritePerformers.single().name)
            assertEquals("1948-07-16T00:00:00.000Z", detail.favoritePerformers.single().date)
        }
    }

    @Test fun `normaliza relaciones opcionales vacias`() = runTest {
        withServer { server ->
            server.enqueue(MockResponse().setBody("""{"id":101,"name":"Jaime","email":null,"telephone":null}"""))
            val detail = repository(server).getCollectorDetail(101)
            assertEquals("", detail.email)
            assertEquals("", detail.telephone)
            assertTrue(detail.comments.isEmpty())
            assertTrue(detail.favoritePerformers.isEmpty())
        }
    }

    @Test fun `rechaza id distinto al solicitado`() = runTest {
        withServer { server ->
            server.enqueue(MockResponse().setBody("""{"id":101,"name":"Otro"}"""))
            try { repository(server).getCollectorDetail(100); fail("Debe rechazar otro coleccionista") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun `propaga el 404 del coleccionista`() = runTest {
        withServer { server ->
            server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"not found"}"""))
            try { repository(server).getCollectorDetail(999); fail("Debe propagar HTTP 404") }
            catch (error: HttpException) { assertEquals(404, error.code()) }
        }
    }

    private suspend fun withServer(block: suspend (MockWebServer) -> Unit) {
        val server = MockWebServer().apply { start() }
        try { block(server) } finally { server.shutdown() }
    }

    private fun repository(server: MockWebServer) = RemoteCollectorDetailRepository(
        CollectorDetailServiceAdapter(
            Retrofit.Builder().baseUrl(server.url("/"))
                .client(OkHttpClient.Builder().retryOnConnectionFailure(false).build())
                .addConverterFactory(GsonConverterFactory.create())
                .build().create(CollectorService::class.java),
        ),
    )
}
