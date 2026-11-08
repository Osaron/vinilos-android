package co.edu.uniandes.vinilos

import co.edu.uniandes.vinilos.data.ArtistDetailRepository
import co.edu.uniandes.vinilos.model.ArtistAlbum
import co.edu.uniandes.vinilos.model.ArtistDetail
import co.edu.uniandes.vinilos.model.ArtistType
import co.edu.uniandes.vinilos.ui.artists.ArtistDetailViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ArtistDetailViewModelTest {
    private val detail = ArtistDetail(
        100, ArtistType.MUSICIAN, "Rubén Blades", null, "Cantautor",
        birthDate = "1948-07-16",
        albums = listOf(ArtistAlbum(100, "Buscando América", null, "1984-08-01", "Elektra", "Salsa")),
    )

    @Test fun `consulta tipo e id recibidos y publica el contenido`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var requested: Pair<ArtistType, Int>? = null
            val repository = ArtistDetailRepository { type, id -> requested = type to id; detail }
            val viewModel = ArtistDetailViewModel(100, ArtistType.MUSICIAN, repository)
            assertTrue(viewModel.state.value.loading)
            advanceUntilIdle()
            assertEquals(ArtistType.MUSICIAN to 100, requested)
            assertEquals(detail, viewModel.state.value.detail)
            assertFalse(viewModel.state.value.failed)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `error se recupera mediante reintento explicito`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var calls = 0
            val repository = ArtistDetailRepository { _, _ ->
                if (++calls == 1) throw IOException("sin red")
                detail
            }
            val viewModel = ArtistDetailViewModel(100, ArtistType.MUSICIAN, repository)
            advanceUntilIdle()
            assertTrue(viewModel.state.value.failed)
            viewModel.refresh()
            advanceUntilIdle()
            assertEquals(2, calls)
            assertEquals(detail, viewModel.state.value.detail)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `ignora otro reintento mientras hay una consulta activa`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var calls = 0
            val pending = CompletableDeferred<ArtistDetail>()
            val viewModel = ArtistDetailViewModel(100, ArtistType.MUSICIAN) { _, _ ->
                calls++
                pending.await()
            }
            runCurrent()
            viewModel.refresh()
            runCurrent()
            assertEquals(1, calls)
            pending.complete(detail)
            advanceUntilIdle()
            assertEquals(detail, viewModel.state.value.detail)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `no convierte cancelacion en error visible`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = ArtistDetailRepository { _, _ -> throw CancellationException("cancelado") }
            val viewModel = ArtistDetailViewModel(100, ArtistType.MUSICIAN, repository)
            advanceUntilIdle()
            assertFalse(viewModel.state.value.failed)
            assertNull(viewModel.state.value.detail)
        } finally { Dispatchers.resetMain() }
    }
}
