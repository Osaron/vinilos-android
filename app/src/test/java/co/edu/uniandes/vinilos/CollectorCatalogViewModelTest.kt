package co.edu.uniandes.vinilos

import co.edu.uniandes.vinilos.data.CollectorRepository
import co.edu.uniandes.vinilos.model.Collector
import co.edu.uniandes.vinilos.ui.collectors.CollectorCatalogViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CollectorCatalogViewModelTest {
    private val ana = Collector(7, "Ana María", "ana@example.org", "3001234567")
    private val jose = Collector(8, "José Pérez", "", "")

    @Test fun `carga y busca sin distinguir tildes o mayusculas`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = CollectorCatalogViewModel(CollectorRepository { listOf(ana, jose) })
            assertTrue(vm.state.value.loading)
            advanceUntilIdle()
            assertEquals(2, vm.state.value.visibleCollectors.size)
            vm.search(" JOSE ")
            assertEquals(listOf(jose), vm.state.value.visibleCollectors)
            vm.search("zzz")
            assertTrue(vm.state.value.visibleCollectors.isEmpty())
            assertFalse(vm.state.value.failed)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `catalogo vacio no es error`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = CollectorCatalogViewModel(CollectorRepository { emptyList() })
            advanceUntilIdle()
            assertFalse(vm.state.value.loading)
            assertFalse(vm.state.value.failed)
            assertTrue(vm.state.value.collectors.isEmpty())
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `error permite reintento sin duplicar peticiones en curso`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var calls = 0
            val vm = CollectorCatalogViewModel(CollectorRepository {
                calls++
                if (calls == 1) throw IOException() else listOf(jose)
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
            assertEquals(listOf(jose), vm.state.value.collectors)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `seleccion conserva el id y rechaza uno inexistente`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = CollectorCatalogViewModel(CollectorRepository { listOf(ana, jose) })
            advanceUntilIdle()
            vm.select(8)
            assertEquals(jose, vm.state.value.selected)
            vm.select(99)
            assertEquals(jose, vm.state.value.selected)
            vm.refresh()
            advanceUntilIdle()
            assertEquals(jose, vm.state.value.selected)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `elimina seleccion si desaparece del catalogo`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var collectors = listOf(jose)
            val vm = CollectorCatalogViewModel(CollectorRepository { collectors })
            advanceUntilIdle()
            vm.select(8)
            collectors = emptyList()
            vm.refresh()
            advanceUntilIdle()
            assertNull(vm.state.value.selected)
        } finally { Dispatchers.resetMain() }
    }
}
