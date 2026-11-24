package co.edu.uniandes.vinilos

import androidx.lifecycle.SavedStateHandle
import co.edu.uniandes.vinilos.data.AlbumRepository
import co.edu.uniandes.vinilos.data.ArtistAlbumRepository
import co.edu.uniandes.vinilos.model.Album
import co.edu.uniandes.vinilos.model.ArtistType
import co.edu.uniandes.vinilos.ui.artists.ArtistAlbumViewModel
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

/** HU15: selección, exclusión y guardado de un álbum en la discografía de un artista. */
@OptIn(ExperimentalCoroutinesApi::class)
class ArtistAlbumViewModelTest {
    private val catalog = listOf(
        Album(100, "Buscando América", "", listOf("Rubén Blades"), "Salsa"),
        Album(101, "Poeta del pueblo", "", listOf("Rubén Blades"), "Salsa"),
        Album(102, "A Night at the Opera", "", listOf("Queen"), "Rock"),
        Album(103, "Mismo nombre", "", emptyList(), "Rock"),
        Album(104, "Mismo nombre", "", emptyList(), "Rock"),
    )

    private class Discography(var type: ArtistType = ArtistType.MUSICIAN) : ArtistAlbumRepository {
        var albums = setOf(100)
        var failed = false
        var saves = 0
        var saveFailed = false
        var lostResponse = false
        var checkFailsAfterSave = false
        override suspend fun getAlbumIds(type: ArtistType, artistId: Int): Set<Int> {
            check(type == this.type && artistId == 50)
            if (failed || (checkFailsAfterSave && saves > 0)) throw IOException("sin red")
            return albums
        }
        override suspend fun addAlbum(type: ArtistType, artistId: Int, albumId: Int) {
            check(type == this.type && artistId == 50)
            saves++
            if (saveFailed) throw IllegalStateException("rechazado")
            albums = albums + albumId
            if (lostResponse) throw IOException("respuesta perdida")
        }
    }

    private fun scenario(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try { block() } finally { Dispatchers.resetMain() }
    }

    private fun model(repo: Discography = Discography(), handle: SavedStateHandle = SavedStateHandle(), type: ArtistType = ArtistType.MUSICIAN) =
        ArtistAlbumViewModel(50, type, AlbumRepository { catalog }, repo, handle)

    @Test fun excluyeLosAlbumesQueYaEstanEnLaDiscografia() = scenario {
        val model = model()
        advanceUntilIdle()
        assertEquals(listOf(101, 102, 103, 104), model.state.value.available.map { it.id })
        assertFalse(model.state.value.canSubmit)
    }

    @Test fun usaLaRutaDeBandaParaUnaBanda() = scenario {
        val repo = Discography(ArtistType.BAND).apply { albums = setOf(102) }
        val model = model(repo, type = ArtistType.BAND)
        advanceUntilIdle()
        model.select(100)
        model.submit()
        advanceUntilIdle()
        assertTrue(model.state.value.saved)
        assertEquals(setOf(100, 102), repo.albums)
    }

    @Test fun filtraPorNombreOInterpreteYConservaLaSeleccionAunqueNoSeVea() = scenario {
        val model = model()
        advanceUntilIdle()
        model.select(102)
        model.setQuery("rubén")
        assertEquals(listOf(101), model.state.value.visible.map { it.id })
        assertEquals(102, model.state.value.selected?.id)
        assertTrue(model.state.value.canSubmit)
    }

    @Test fun conservaBusquedaYSeleccionPorIdAlRecrear() = scenario {
        val handle = SavedStateHandle()
        val first = model(handle = handle)
        advanceUntilIdle()
        first.setQuery("mismo")
        first.select(104)
        val restored = model(handle = handle)
        advanceUntilIdle()
        assertEquals("mismo", restored.state.value.query)
        assertEquals(104, restored.state.value.selected?.id)
    }

    @Test fun ignoraUnAlbumAsociadoOInexistente() = scenario {
        val model = model()
        advanceUntilIdle()
        model.select(100)
        model.select(999)
        assertNull(model.state.value.selected)
    }

    @Test fun limpiaLaSeleccionSiAlRecargarYaEstaAsociado() = scenario {
        val repo = Discography()
        val model = model(repo)
        advanceUntilIdle()
        model.select(101)
        repo.albums = setOf(100, 101)
        model.load()
        advanceUntilIdle()
        assertNull(model.state.value.selectedId)
    }

    @Test fun guardaUnaSolaVezYTerminaGuardado() = scenario {
        val repo = Discography()
        val model = model(repo)
        advanceUntilIdle()
        model.select(102)
        model.submit()
        model.submit()
        advanceUntilIdle()
        assertTrue(model.state.value.saved)
        assertEquals(1, repo.saves)
        assertEquals(setOf(100, 102), repo.albums)
    }

    @Test fun siYaQuedoAsociadoNoVuelveAEnviar() = scenario {
        val repo = Discography()
        val model = model(repo)
        advanceUntilIdle()
        model.select(102)
        repo.albums = setOf(100, 102)
        model.submit()
        advanceUntilIdle()
        assertTrue(model.state.value.saved)
        assertEquals(0, repo.saves)
    }

    @Test fun respuestaPerdidaSeConfirmaConsultandoLaDiscografia() = scenario {
        val repo = Discography().apply { lostResponse = true }
        val model = model(repo)
        advanceUntilIdle()
        model.select(102)
        model.submit()
        advanceUntilIdle()
        assertTrue(model.state.value.saved)
        assertEquals(1, repo.saves)
    }

    @Test fun sinConfirmacionQuedaPendienteYElReintentoNoDuplica() = scenario {
        val repo = Discography().apply { lostResponse = true; checkFailsAfterSave = true }
        val model = model(repo)
        advanceUntilIdle()
        model.select(102)
        model.submit()
        advanceUntilIdle()
        assertTrue(model.state.value.unconfirmed)
        assertFalse(model.state.value.saved)
        assertEquals(102, model.state.value.selectedId)
        repo.checkFailsAfterSave = false
        model.submit()
        advanceUntilIdle()
        assertTrue(model.state.value.saved)
        assertEquals(1, repo.saves)
    }

    @Test fun errorDelServidorConservaLaSeleccionYPermiteReintentar() = scenario {
        val repo = Discography().apply { saveFailed = true }
        val model = model(repo)
        advanceUntilIdle()
        model.select(102)
        model.submit()
        advanceUntilIdle()
        assertTrue(model.state.value.submitFailed)
        assertEquals(102, model.state.value.selectedId)
        repo.saveFailed = false
        model.submit()
        advanceUntilIdle()
        assertTrue(model.state.value.saved)
        assertEquals(2, repo.saves)
    }

    @Test fun errorAlCargarPermiteReintentar() = scenario {
        val repo = Discography().apply { failed = true }
        val model = model(repo)
        advanceUntilIdle()
        assertTrue(model.state.value.failed)
        model.select(101)
        assertNull(model.state.value.selectedId)
        repo.failed = false
        model.load()
        advanceUntilIdle()
        assertFalse(model.state.value.failed)
        assertEquals(4, model.state.value.available.size)
    }

    @Test fun noPermiteCambiarLaSeleccionMientrasGuarda() = scenario {
        val model = model()
        advanceUntilIdle()
        model.select(101)
        model.submit()
        model.select(102)
        advanceUntilIdle()
        assertEquals(101, model.state.value.selectedId)
    }
}
