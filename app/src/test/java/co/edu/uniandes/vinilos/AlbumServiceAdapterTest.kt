package co.edu.uniandes.vinilos

import co.edu.uniandes.vinilos.data.*
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class AlbumServiceAdapterTest {
    @Test fun `consulta albums y transforma la respuesta real de transporte`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("""[{"id":7,"name":"Re","cover":"https://example.org/re.jpg","genre":"Rock","performers":[{"name":"Café Tacvba"}]}]"""))
            val repository = repository(server)
            val result = repository.getAlbums().single()
            assertEquals("/albums", server.takeRequest(2, TimeUnit.SECONDS)?.path)
            assertEquals(7, result.id)
            assertEquals(listOf("Café Tacvba"), result.performers)
            assertEquals("Re", result.name)
        } finally { server.shutdown() }
    }

    @Test fun `rechaza registros sin id para no navegar al album incorrecto`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("""[{"name":"Sin id"}]"""))
            try { repository(server).getAlbums(); fail("Debe rechazar el contrato inválido") }
            catch (_: IllegalArgumentException) { }
        } finally { server.shutdown() }
    }

    @Test fun `propaga error http para que la vista permita reintentar`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(503))
            try { repository(server).getAlbums(); fail("Debe propagar HTTP 503") }
            catch (error: HttpException) { assertEquals(503, error.code()) }
        } finally { server.shutdown() }
    }

    private fun repository(server: MockWebServer) = RemoteAlbumRepository(AlbumServiceAdapter(
        Retrofit.Builder().baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create()).build().create(AlbumService::class.java)))
}
