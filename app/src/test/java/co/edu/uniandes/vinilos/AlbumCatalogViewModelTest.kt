package co.edu.uniandes.vinilos

import co.edu.uniandes.vinilos.data.AlbumRepository
import co.edu.uniandes.vinilos.model.Album
import co.edu.uniandes.vinilos.ui.albums.AlbumCatalogViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumCatalogViewModelTest {
    private val album = Album(7, "Re", "", listOf("Café Tacvba"), "Rock")

    @Test fun `carga catalogo y busca por artista sin distinguir acentos`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = AlbumCatalogViewModel(AlbumRepository { listOf(album) })
            assertTrue(vm.state.value.loading)
            advanceUntilIdle()
            assertEquals(listOf(album), vm.state.value.visibleAlbums)
            vm.search("cafe")
            assertEquals(listOf(album), vm.state.value.visibleAlbums)
            vm.search("sin coincidencias")
            assertTrue(vm.state.value.visibleAlbums.isEmpty())
            assertFalse(vm.state.value.failed)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `error permite reintentar y no duplica solicitudes en curso`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var calls = 0
            val vm = AlbumCatalogViewModel(AlbumRepository {
                calls++
                if (calls == 1) throw IOException("sin red") else listOf(album)
            })
            vm.refresh()
            advanceUntilIdle()
            assertEquals(1, calls)
            assertTrue(vm.state.value.failed)
            vm.refresh()
            advanceUntilIdle()
            assertEquals(2, calls)
            assertFalse(vm.state.value.failed)
            assertEquals(listOf(album), vm.state.value.albums)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `catalogo vacio termina la carga sin convertirse en error`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = AlbumCatalogViewModel(AlbumRepository { emptyList() })
            advanceUntilIdle()
            assertFalse(vm.state.value.loading)
            assertFalse(vm.state.value.failed)
            assertTrue(vm.state.value.albums.isEmpty())
        } finally { Dispatchers.resetMain() }
    }
}
