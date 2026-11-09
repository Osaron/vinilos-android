package co.edu.uniandes.vinilos

import co.edu.uniandes.vinilos.data.*
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

class CollectorServiceAdapterTest {
    @Test fun `consulta ruta y adapta datos sin exponer relaciones del transporte`() = runTest {
        withServer { server ->
            server.enqueue(MockResponse().setBody("""[{"id":7,"name":" Ana María ","email":"ana@example.org","telephone":"3001234567","comments":[],"favoritePerformers":[],"collectorAlbums":[]}]"""))
            val collector = repository(server).getCollectors().single()
            assertEquals("/collectors", server.takeRequest(2, TimeUnit.SECONDS)?.path)
            assertEquals(7, collector.id)
            assertEquals("Ana María", collector.name)
            assertEquals("ana@example.org", collector.email)
            assertEquals("3001234567", collector.telephone)
        }
    }
    @Test fun `normaliza campos opcionales y permite lista vacia`() = runTest {
        withServer { server ->
            server.enqueue(MockResponse().setBody("""[{"id":1,"name":"Ana","email":null}]"""))
            val repository = repository(server)
            val collector = repository.getCollectors().single()
            assertEquals("", collector.email)
            assertEquals("", collector.telephone)
            server.enqueue(MockResponse().setBody("[]"))
            assertTrue(repository.getCollectors().isEmpty())
        }
    }
    @Test fun `rechaza identidad invalida antes de navegar`() = runTest {
        for (body in listOf("""[{"name":"Ana"}]""", """[{"id":0,"name":"Ana"}]""", """[{"id":1,"name":" "}]""", """[{"id":1}]""")) {
            withServer { server ->
                server.enqueue(MockResponse().setBody(body))
                try { repository(server).getCollectors(); fail("Debe rechazar la identidad inválida") }
                catch (_: IllegalArgumentException) { }
            }
        }
    }
    @Test fun `propaga error http y permite recuperarse`() = runTest {
        withServer { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            val repository = repository(server)
            try { repository.getCollectors(); fail("Debe propagar HTTP 503") }
            catch (error: HttpException) { assertEquals(503, error.code()) }
            server.enqueue(MockResponse().setBody("[]"))
            assertTrue(repository.getCollectors().isEmpty())
        }
    }
    @Test fun `fallo de red no se convierte en catalogo vacio`() = runTest {
        withServer { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            try { repository(server).getCollectors(); fail("Debe propagar error de red") }
            catch (_: IOException) { }
        }
    }
    private suspend fun withServer(block: suspend (MockWebServer) -> Unit) {
        val server = MockWebServer()
        server.start()
        try { block(server) } finally { server.shutdown() }
    }
    private fun repository(server: MockWebServer) = RemoteCollectorRepository(CollectorServiceAdapter(
        Retrofit.Builder().baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().retryOnConnectionFailure(false).build())
            .addConverterFactory(GsonConverterFactory.create()).build().create(CollectorService::class.java)))
}
