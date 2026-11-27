package co.edu.uniandes.vinilos

import co.edu.uniandes.vinilos.data.AlbumDetailDto
import co.edu.uniandes.vinilos.data.AlbumDto
import co.edu.uniandes.vinilos.data.AlbumService
import co.edu.uniandes.vinilos.data.AlbumServiceAdapter
import co.edu.uniandes.vinilos.data.DataDispatchers
import co.edu.uniandes.vinilos.data.RemoteAlbumRepository
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Los repositorios remotos ejecutan la red y el mapeo en el dispatcher de E/S, no en el hilo que llama. */
class DataDispatchersTest {
    private lateinit var original: CoroutineDispatcher
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "vinilos-io-test") }

    @Before fun setUp() {
        original = DataDispatchers.io
        DataDispatchers.io = executor.asCoroutineDispatcher()
    }

    @After fun tearDown() {
        DataDispatchers.io = original
        executor.shutdownNow()
    }

    @Test fun remoteRepositoryCallsServiceOnIoDispatcher() = runTest {
        var serviceThread = ""
        val service = object : AlbumService {
            override suspend fun getAlbums(): List<AlbumDto> {
                serviceThread = Thread.currentThread().name
                return listOf(AlbumDto(id = 1, name = "Buscando América"))
            }
            override suspend fun getAlbum(albumId: Int): AlbumDetailDto = AlbumDetailDto(id = albumId, name = "A")
        }

        val albums = RemoteAlbumRepository(AlbumServiceAdapter(service)).getAlbums()

        assertEquals("vinilos-io-test", serviceThread)
        assertEquals(listOf("Buscando América"), albums.map { it.name })
    }
}
