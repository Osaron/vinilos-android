package co.edu.uniandes.vinilos

import co.edu.uniandes.vinilos.data.ArtistRepository
import co.edu.uniandes.vinilos.model.Artist
import co.edu.uniandes.vinilos.model.ArtistType
import co.edu.uniandes.vinilos.ui.artists.ArtistCatalogViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ArtistCatalogViewModelTest {
    private val musician = Artist(7, ArtistType.MUSICIAN, "Celia Cruz", "", "")
    private val band = Artist(7, ArtistType.BAND, "Café Tacvba", "", "")

    @Test fun `carga y busca sin distinguir tildes o mayusculas`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = ArtistCatalogViewModel(ArtistRepository { listOf(musician, band) })
            assertTrue(vm.state.value.loading)
            advanceUntilIdle()
            assertEquals(2, vm.state.value.visibleArtists.size)
            vm.search(" CAFE ")
            assertEquals(listOf(band), vm.state.value.visibleArtists)
            vm.search("zzz")
            assertTrue(vm.state.value.visibleArtists.isEmpty())
            assertFalse(vm.state.value.failed)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `catalogo vacio no es error`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = ArtistCatalogViewModel(ArtistRepository { emptyList() })
            advanceUntilIdle()
            assertFalse(vm.state.value.loading)
            assertFalse(vm.state.value.failed)
            assertTrue(vm.state.value.artists.isEmpty())
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `error permite reintento sin duplicar peticiones en curso`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var calls = 0
            val vm = ArtistCatalogViewModel(ArtistRepository {
                calls++
                if (calls == 1) throw IOException() else listOf(band)
            })
            vm.refresh()
            advanceUntilIdle()
            assertEquals(1, calls)
            assertTrue(vm.state.value.failed)
            vm.refresh()
            assertTrue(vm.state.value.loading)
            assertFalse(vm.state.value.failed)
            vm.refresh()
            advanceUntilIdle()
            assertEquals(2, calls)
            assertEquals(listOf(band), vm.state.value.artists)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `seleccion conserva tipo cuando los identificadores coinciden`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = ArtistCatalogViewModel(ArtistRepository { listOf(musician, band) })
            advanceUntilIdle()
            vm.select(7, ArtistType.BAND)
            assertEquals(band, vm.state.value.selected)
            vm.select(99, ArtistType.MUSICIAN)
            assertEquals(band, vm.state.value.selected)
            vm.refresh()
            advanceUntilIdle()
            assertEquals(band, vm.state.value.selected)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `elimina seleccion si desaparece del catalogo`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var artists = listOf(band)
            val vm = ArtistCatalogViewModel(ArtistRepository { artists })
            advanceUntilIdle()
            vm.select(7, ArtistType.BAND)
            artists = emptyList()
            vm.refresh()
            advanceUntilIdle()
            assertNull(vm.state.value.selected)
        } finally { Dispatchers.resetMain() }
    }
}
