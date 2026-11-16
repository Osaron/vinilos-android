package co.edu.uniandes.vinilos

import co.edu.uniandes.vinilos.data.ArtistDetailService
import co.edu.uniandes.vinilos.data.ArtistDetailServiceAdapter
import co.edu.uniandes.vinilos.data.CollectorDetailServiceAdapter
import co.edu.uniandes.vinilos.data.CollectorService
import co.edu.uniandes.vinilos.model.ArtistType
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class DetailMappingDispatcherTest {
    private class RecordingDispatcher : CoroutineDispatcher() {
        val calls = AtomicInteger()
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            calls.incrementAndGet()
            Dispatchers.Default.dispatch(context, block)
        }
    }

    @Test fun detailAdaptersUseConfiguredDispatcherForMapping() = runTest {
        MockWebServer().use { server ->
            server.start()
            val retrofit = Retrofit.Builder().baseUrl(server.url("/"))
                .addConverterFactory(GsonConverterFactory.create()).build()
            val dispatcher = RecordingDispatcher()
            server.enqueue(MockResponse().setBody("""{"id":100,"name":"Musico"}"""))
            server.enqueue(MockResponse().setBody("""{"id":101,"name":"Coleccionista"}"""))
            val artist = ArtistDetailServiceAdapter(retrofit.create(ArtistDetailService::class.java), dispatcher)
                .getArtistDetail(ArtistType.MUSICIAN, 100)
            val collector = CollectorDetailServiceAdapter(retrofit.create(CollectorService::class.java), dispatcher)
                .getCollectorDetail(101)
            assertEquals("Musico", artist.name)
            assertEquals("Coleccionista", collector.name)
            assertTrue("La adaptación de ambos detalles se despacha", dispatcher.calls.get() >= 2)
        }
    }
}
