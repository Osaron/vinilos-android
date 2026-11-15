package co.edu.uniandes.vinilos

import androidx.lifecycle.SavedStateHandle
import co.edu.uniandes.vinilos.data.AlbumRepository
import co.edu.uniandes.vinilos.model.Album
import co.edu.uniandes.vinilos.ui.tracks.AlbumSelectionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Selección del álbum de destino de HU08 (#31). */
@OptIn(ExperimentalCoroutinesApi::class)
class AlbumSelectionViewModelTest {
    private val albums = listOf(
        Album(100, "Buscando América", "", listOf("Rubén Blades"), "Salsa"),
        Album(101, "Poeta del pueblo", "", listOf("Rubén Blades"), "Salsa"),
        // Mismo título que 100: la selección debe distinguirse por ID.
        Album(102, "Buscando América", "", listOf("Otro intérprete"), "Salsa"),
    )

    private fun scenario(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try { block() } finally { Dispatchers.resetMain() }
    }

    private fun repository(result: () -> List<Album>) = AlbumRepository { result() }

    @Test fun `abre con el album del detalle preseleccionado`() = scenario {
        val viewModel = AlbumSelectionViewModel(101, repository { albums }, SavedStateHandle())
        assertTrue(viewModel.state.value.loading)
        assertNull("sin catálogo no hay ID listo para enviar", viewModel.selectedAlbumIdForSubmit)

        advanceUntilIdle()

        assertEquals(albums, viewModel.state.value.albums)
        assertEquals("Poeta del pueblo", viewModel.state.value.selectedAlbum?.name)
        assertEquals(101, viewModel.selectedAlbumIdForSubmit)
    }

    @Test fun `cambia el album antes del envio y conserva el ID aunque el titulo se repita`() = scenario {
        val viewModel = AlbumSelectionViewModel(100, repository { albums }, SavedStateHandle())
        advanceUntilIdle()

        viewModel.select(102)

        assertEquals(102, viewModel.selectedAlbumIdForSubmit)
        assertEquals(listOf("Otro intérprete"), viewModel.state.value.selectedAlbum?.performers)
    }

    @Test fun `ignora un ID que no esta en el catalogo`() = scenario {
        val viewModel = AlbumSelectionViewModel(100, repository { albums }, SavedStateHandle())
        advanceUntilIdle()

        viewModel.select(999)

        assertEquals(100, viewModel.selectedAlbumIdForSubmit)
    }

    @Test fun `conserva la seleccion al recrear la pantalla`() = scenario {
        val handle = SavedStateHandle()
        val first = AlbumSelectionViewModel(100, repository { albums }, handle)
        advanceUntilIdle()
        first.select(101)

        // Rotación o muerte del proceso: el nuevo ViewModel recibe el mismo estado guardado.
        val restored = AlbumSelectionViewModel(100, repository { albums }, SavedStateHandle(mapOf(
            AlbumSelectionViewModel.KEY_SELECTED_ALBUM to handle.get<Int>(AlbumSelectionViewModel.KEY_SELECTED_ALBUM),
        )))
        assertEquals("el ID se conoce antes de recargar", 101, restored.state.value.selectedAlbumId)
        advanceUntilIdle()

        assertEquals(101, restored.selectedAlbumIdForSubmit)
    }

    @Test fun `si falla la carga permite reintentar sin perder el album elegido`() = scenario {
        var fail = true
        val viewModel = AlbumSelectionViewModel(101, repository { if (fail) throw IOException("sin red") else albums }, SavedStateHandle())
        advanceUntilIdle()

        assertTrue(viewModel.state.value.failed)
        assertNull(viewModel.selectedAlbumIdForSubmit)
        assertEquals(101, viewModel.state.value.selectedAlbumId)

        fail = false
        viewModel.load()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.failed)
        assertEquals(101, viewModel.selectedAlbumIdForSubmit)
    }

    @Test fun `informa catalogo vacio`() = scenario {
        val viewModel = AlbumSelectionViewModel(100, repository { emptyList() }, SavedStateHandle())
        advanceUntilIdle()

        assertTrue(viewModel.state.value.empty)
        assertNull(viewModel.selectedAlbumIdForSubmit)
    }

    @Test fun `pide elegir otro album si el preseleccionado ya no existe`() = scenario {
        val viewModel = AlbumSelectionViewModel(555, repository { albums }, SavedStateHandle())
        advanceUntilIdle()

        assertTrue(viewModel.state.value.selectionMissing)
        assertNull(viewModel.selectedAlbumIdForSubmit)
        viewModel.select(100)
        assertFalse(viewModel.state.value.selectionMissing)
        assertEquals(100, viewModel.selectedAlbumIdForSubmit)
    }
}
