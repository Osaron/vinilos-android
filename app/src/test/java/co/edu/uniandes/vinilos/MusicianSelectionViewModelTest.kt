package co.edu.uniandes.vinilos

import androidx.lifecycle.SavedStateHandle
import co.edu.uniandes.vinilos.data.ArtistRepository
import co.edu.uniandes.vinilos.data.BandMusicianRepository
import co.edu.uniandes.vinilos.model.Artist
import co.edu.uniandes.vinilos.model.ArtistType
import co.edu.uniandes.vinilos.ui.bands.MusicianSelectionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class MusicianSelectionViewModelTest {
    private fun musician(id: Int) = Artist(id, ArtistType.MUSICIAN, "Nombre repetido", "", "")
    private val catalog = listOf(musician(1), musician(2), musician(3),
        Artist(4, ArtistType.BAND, "Banda", "", ""))

    private class Bands : BandMusicianRepository {
        var members = listOf(1)
        var failed = false
        override suspend fun getMembers(bandId: Int): List<Artist> {
            check(bandId == 20)
            if (failed) throw IOException("sin red")
            return members.map { Artist(it, ArtistType.MUSICIAN, "Integrante", "", "") }
        }
        override suspend fun addMusician(bandId: Int, musicianId: Int): Artist =
            error("Seleccionar no debe asociar")
    }

    private fun scenario(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try { block() } finally { Dispatchers.resetMain() }
    }

    @Test fun excluyeIntegrantesYBandas() = scenario {
        val model = MusicianSelectionViewModel(20, ArtistRepository { catalog }, Bands(), SavedStateHandle())
        advanceUntilIdle()
        assertEquals(listOf(2, 3), model.state.value.musicians.map { it.id })
    }

    @Test fun conservaElIdAlRecrearConNombresRepetidos() = scenario {
        val handle = SavedStateHandle()
        val model = MusicianSelectionViewModel(20, ArtistRepository { catalog }, Bands(), handle)
        advanceUntilIdle()
        model.select(3)
        val restored = MusicianSelectionViewModel(20, ArtistRepository { catalog }, Bands(), handle)
        advanceUntilIdle()
        assertEquals(3, restored.state.value.selected?.id)
    }

    @Test fun ignoraLaSeleccionDeUnIntegranteOIdInexistente() = scenario {
        val model = MusicianSelectionViewModel(20, ArtistRepository { catalog }, Bands(), SavedStateHandle())
        advanceUntilIdle()
        model.select(1)
        model.select(999)
        assertNull(model.state.value.selected)
    }

    @Test fun limpiaLaSeleccionSiAhoraEsIntegrante() = scenario {
        val bands = Bands()
        val model = MusicianSelectionViewModel(20, ArtistRepository { catalog }, bands, SavedStateHandle())
        advanceUntilIdle()
        model.select(2)
        bands.members = listOf(1, 2)
        model.load()
        advanceUntilIdle()
        assertNull(model.state.value.selectedId)
        assertEquals(listOf(3), model.state.value.musicians.map { it.id })
    }

    @Test fun conservaElErrorYPermiteReintentar() = scenario {
        val bands = Bands().apply { failed = true }
        val model = MusicianSelectionViewModel(20, ArtistRepository { catalog }, bands, SavedStateHandle())
        advanceUntilIdle()
        assertTrue(model.state.value.failed)
        model.select(2)
        assertNull(model.state.value.selectedId)
        bands.failed = false
        model.load()
        advanceUntilIdle()
        assertFalse(model.state.value.failed)
        assertEquals(2, model.state.value.musicians.size)
    }

    @Test fun diferenciaCatalogoVacioDelFalloDeRed() = scenario {
        val model = MusicianSelectionViewModel(20, ArtistRepository { emptyList() }, Bands(), SavedStateHandle())
        advanceUntilIdle()
        assertFalse(model.state.value.failed)
        assertFalse(model.state.value.loading)
        assertTrue(model.state.value.musicians.isEmpty())
    }
}
