package co.edu.uniandes.vinilos

import co.edu.uniandes.vinilos.data.FakeTrackAssociationRepository
import co.edu.uniandes.vinilos.data.TrackAssociationException
import co.edu.uniandes.vinilos.data.TrackAssociationException.Reason
import co.edu.uniandes.vinilos.data.TrackAssociationRepository
import co.edu.uniandes.vinilos.model.NewTrack
import co.edu.uniandes.vinilos.model.Track
import co.edu.uniandes.vinilos.ui.tracks.AddTrackResult
import co.edu.uniandes.vinilos.ui.tracks.AddTrackViewModel
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

/** Estados de envío, éxito y error de HU08 (#34) con el contrato de #33, probados para #35. */
@OptIn(ExperimentalCoroutinesApi::class)
class AddTrackViewModelTest {
    private val track = NewTrack(name = "Decisiones", duration = "4:05")
    private val saved = Track(id = 301, name = "Decisiones", duration = "4:05")

    private fun withMain(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try { block() } finally { Dispatchers.resetMain() }
    }

    /** Doble programable para respuestas perdidas y consultas fallidas. */
    private class Repo(
        val add: suspend (Int, NewTrack) -> Track,
        val tracks: suspend (Int) -> List<Track> = { emptyList() },
    ) : TrackAssociationRepository {
        val requests = mutableListOf<Pair<Int, NewTrack>>()
        override suspend fun addTrack(albumId: Int, track: NewTrack): Track { requests += albumId to track; return add(albumId, track) }
        override suspend fun getTracks(albumId: Int): List<Track> = tracks(albumId)
    }

    private fun failure(reason: Reason) = TrackAssociationException(reason, "Fallo simulado: $reason")

    @Test fun `sin album valido no envia nada`() = withMain {
        val repo = FakeTrackAssociationRepository()
        val viewModel = AddTrackViewModel(repo)

        viewModel.submit(0, track)
        advanceUntilIdle()

        assertTrue(repo.requests.isEmpty())
        assertNull(viewModel.state.value.result)
    }

    @Test fun `guarda en el album elegido e indica cual detalle refrescar`() = withMain {
        val repo = FakeTrackAssociationRepository()
        val viewModel = AddTrackViewModel(repo)

        viewModel.submit(101, track)
        advanceUntilIdle()

        assertEquals(listOf(101 to track), repo.requests)
        val result = viewModel.state.value.result as AddTrackResult.Saved
        assertEquals(101, result.albumId)
        assertEquals("Decisiones", result.track.name)
        assertFalse(viewModel.state.value.submitting)
        assertEquals(listOf(result.track), repo.getTracks(101))
    }

    @Test fun `mientras envia muestra el estado de guardado`() = withMain {
        val repo = FakeTrackAssociationRepository()
        val gate = repo.holdNext()
        val viewModel = AddTrackViewModel(repo)

        viewModel.submit(100, track)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.submitting)
        assertFalse(viewModel.state.value.canSubmit)
        assertNull(viewModel.state.value.result)

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.submitting)
        assertTrue(viewModel.state.value.saved)
    }

    @Test fun `pulsaciones repetidas y envios despues de guardar crean una sola cancion`() = withMain {
        val repo = FakeTrackAssociationRepository()
        val gate = repo.holdNext()
        val viewModel = AddTrackViewModel(repo)

        repeat(3) { viewModel.submit(100, track) }
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()
        viewModel.submit(100, track)
        viewModel.submit(101, track)
        advanceUntilIdle()

        assertEquals(1, repo.requests.size)
        assertEquals(1, repo.getTracks(100).size)
        assertTrue(repo.getTracks(101).isEmpty())
    }

    @Test fun `rechazo error del servidor o respuesta invalida permiten reintentar`() = withMain {
        for (reason in listOf(Reason.REJECTED, Reason.SERVER, Reason.INVALID_RESPONSE)) {
            val repo = FakeTrackAssociationRepository()
            val viewModel = AddTrackViewModel(repo)
            repo.failNext(reason)

            viewModel.submit(100, track)
            advanceUntilIdle()
            assertEquals(reason.name, AddTrackResult.Failed, viewModel.state.value.result)
            assertTrue(reason.name, viewModel.state.value.canSubmit)

            viewModel.submit(100, track)
            advanceUntilIdle()
            assertTrue(reason.name, viewModel.state.value.saved)
            assertEquals(reason.name, 2, repo.requests.size)
        }
    }

    @Test fun `un error inesperado tambien deja reintentar`() = withMain {
        var calls = 0
        val repo = Repo(add = { _, _ -> if (++calls == 1) throw IllegalStateException("inesperado") else saved })
        val viewModel = AddTrackViewModel(repo)

        viewModel.submit(100, track)
        advanceUntilIdle()
        assertEquals(AddTrackResult.Failed, viewModel.state.value.result)

        viewModel.submit(100, track)
        advanceUntilIdle()
        assertEquals(AddTrackResult.Saved(100, saved), viewModel.state.value.result)
    }

    @Test fun `si el album ya no existe pide elegir otro y guarda en el nuevo`() = withMain {
        val repo = FakeTrackAssociationRepository(existingAlbums = setOf(101))
        val viewModel = AddTrackViewModel(repo)

        viewModel.submit(100, track)
        advanceUntilIdle()
        assertEquals(AddTrackResult.AlbumMissing, viewModel.state.value.result)

        viewModel.dismissResult()
        assertNull(viewModel.state.value.result)
        viewModel.submit(101, track)
        advanceUntilIdle()

        assertEquals(listOf(100, 101), repo.requests.map { it.first })
        assertEquals(101, (viewModel.state.value.result as AddTrackResult.Saved).albumId)
    }

    @Test fun `descartar el aviso no borra un exito ni interrumpe un envio`() = withMain {
        val repo = FakeTrackAssociationRepository()
        val gate = repo.holdNext()
        val viewModel = AddTrackViewModel(repo)

        viewModel.submit(100, track)
        advanceUntilIdle()
        viewModel.dismissResult()
        assertTrue(viewModel.state.value.submitting)
        gate.complete(Unit)
        advanceUntilIdle()
        viewModel.dismissResult()

        assertTrue(viewModel.state.value.saved)
    }

    @Test fun `sin red y sin la cancion en el album se puede reintentar`() = withMain {
        val repo = Repo(add = { _, _ -> throw failure(Reason.NETWORK) }, tracks = { listOf(Track(1, "Otra", "3:00")) })
        val viewModel = AddTrackViewModel(repo)

        viewModel.submit(100, track)
        advanceUntilIdle()

        assertEquals(AddTrackResult.Failed, viewModel.state.value.result)
        assertTrue(viewModel.state.value.canSubmit)
    }

    @Test fun `respuesta perdida con la cancion ya en el album no reenvia sola`() = withMain {
        val existing = Track(301, "decisiones ", "4:05")
        val repo = Repo(add = { _, _ -> throw failure(Reason.NETWORK) }, tracks = { listOf(existing) })
        val viewModel = AddTrackViewModel(repo)

        viewModel.submit(100, track)
        advanceUntilIdle()

        assertEquals(AddTrackResult.PossiblySaved(100, existing), viewModel.state.value.result)
        assertEquals("no hay reintento automático", 1, repo.requests.size)
    }

    @Test fun `respuesta perdida sin poder consultar el album queda sin confirmar`() = withMain {
        val repo = Repo(add = { _, _ -> throw failure(Reason.NETWORK) }, tracks = { throw failure(Reason.NETWORK) })
        val viewModel = AddTrackViewModel(repo)

        viewModel.submit(100, track)
        advanceUntilIdle()

        assertEquals(AddTrackResult.Unconfirmed, viewModel.state.value.result)
        assertFalse(viewModel.state.value.submitting)
        assertTrue(viewModel.state.value.canSubmit)
    }
}
