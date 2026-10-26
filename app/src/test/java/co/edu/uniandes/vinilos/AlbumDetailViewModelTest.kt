package co.edu.uniandes.vinilos

import co.edu.uniandes.vinilos.data.AlbumRepository
import co.edu.uniandes.vinilos.model.Album
import co.edu.uniandes.vinilos.model.AlbumDetail
import co.edu.uniandes.vinilos.model.Track
import co.edu.uniandes.vinilos.ui.albums.AlbumDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumDetailViewModelTest {
    private val detail = AlbumDetail(
        7, "Re", "", "1994-01-01", "Álbum de estudio", "Rock", "Warner",
        listOf("Café Tacvba"), listOf(Track(1, "El aparato", "4:20")),
    )

    @Test fun `carga el detalle del album seleccionado`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var requestedId = -1
            val repository = object : AlbumRepository {
                override suspend fun getAlbums(): List<Album> = emptyList()
                override suspend fun getAlbum(albumId: Int): AlbumDetail {
                    requestedId = albumId
                    return detail
                }
            }
            val viewModel = AlbumDetailViewModel(7, repository)
            assertTrue(viewModel.state.value.loading)
            advanceUntilIdle()
            assertEquals(7, requestedId)
            assertEquals(detail, viewModel.state.value.album)
            assertFalse(viewModel.state.value.failed)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `muestra error y permite reintentar`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var calls = 0
            val repository = object : AlbumRepository {
                override suspend fun getAlbums(): List<Album> = emptyList()
                override suspend fun getAlbum(albumId: Int): AlbumDetail {
                    if (++calls == 1) throw IOException("sin red")
                    return detail
                }
            }
            val viewModel = AlbumDetailViewModel(7, repository)
            advanceUntilIdle()
            assertTrue(viewModel.state.value.failed)
            viewModel.refresh()
            advanceUntilIdle()
            assertEquals(2, calls)
            assertEquals(detail, viewModel.state.value.album)
        } finally { Dispatchers.resetMain() }
    }
}
