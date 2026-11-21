package co.edu.uniandes.vinilos.ui.comments

import androidx.lifecycle.SavedStateHandle
import co.edu.uniandes.vinilos.data.ActiveCollector
import co.edu.uniandes.vinilos.data.CommentRepository
import co.edu.uniandes.vinilos.model.AlbumComment
import co.edu.uniandes.vinilos.model.NewComment
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** HU09 (#82, #83): formulario, validación y estados del envío del comentario. Pruebas de #84. */
@OptIn(ExperimentalCoroutinesApi::class)
class CommentAlbumViewModelTest {
    private val ana = ActiveCollector(8, "Ana María")
    private val active = MutableStateFlow<ActiveCollector?>(ana)

    private fun withMain(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try { block() } finally { Dispatchers.resetMain() }
    }

    private class Repo(
        val add: suspend (Int, NewComment) -> AlbumComment = { _, c -> AlbumComment(50, c.description, c.rating, c.collectorId) },
        val list: suspend (Int) -> List<AlbumComment> = { emptyList() },
    ) : CommentRepository {
        val requests = mutableListOf<Pair<Int, NewComment>>()
        override suspend fun getComments(albumId: Int) = list(albumId)
        override suspend fun addComment(albumId: Int, comment: NewComment): AlbumComment {
            requests += albumId to comment
            return add(albumId, comment)
        }
    }

    private fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))

    private fun viewModel(repo: CommentRepository, handle: SavedStateHandle = SavedStateHandle()) =
        CommentAlbumViewModel(100, repo, active, handle)

    private fun CommentAlbumViewModel.fill(text: String = "  Una joya de la salsa  ", rating: Int? = 5) {
        updateDescription(text)
        updateRating(rating)
    }

    @Test fun `sin texto ni calificacion no envia y marca ambos campos`() = withMain {
        val repo = Repo()
        val vm = viewModel(repo)
        vm.fill(text = "   ", rating = null)

        vm.submit()
        advanceUntilIdle()

        assertTrue(repo.requests.isEmpty())
        assertTrue(vm.state.value.errors.description)
        assertTrue(vm.state.value.errors.rating)
        assertFalse(vm.state.value.errors.collector)
    }

    @Test fun `al corregir un campo su error desaparece`() = withMain {
        val vm = viewModel(Repo())
        vm.submit()
        vm.updateDescription("Buen disco")
        vm.updateRating(4)

        assertFalse(vm.state.value.errors.description)
        assertFalse(vm.state.value.errors.rating)
    }

    @Test fun `sin coleccionista activo no envia y lo pide`() = withMain {
        active.value = null
        val repo = Repo()
        val vm = viewModel(repo)
        vm.fill()

        vm.submit()
        advanceUntilIdle()

        assertTrue(repo.requests.isEmpty())
        assertTrue(vm.state.value.errors.collector)
    }

    @Test fun `publica una vez a nombre del coleccionista activo con el texto recortado`() = withMain {
        val repo = Repo()
        val vm = viewModel(repo)
        vm.fill()

        vm.submit()
        advanceUntilIdle()

        assertEquals(listOf(100 to NewComment("Una joya de la salsa", 5, 8)), repo.requests)
        val result = vm.state.value.result as CommentResult.Saved
        assertEquals(100, result.albumId)
        assertFalse(vm.state.value.submitting)
    }

    @Test fun `cambiar de coleccionista antes de publicar usa el nuevo`() = withMain {
        val repo = Repo()
        val vm = viewModel(repo)
        vm.fill()
        active.value = ActiveCollector(7, "José Pérez")

        vm.submit()
        advanceUntilIdle()

        assertEquals(7, repo.requests.single().second.collectorId)
    }

    @Test fun `pulsaciones repetidas y envios despues de guardar crean un solo comentario`() = withMain {
        val response = CompletableDeferred<AlbumComment>()
        val repo = Repo(add = { _, _ -> response.await() })
        val vm = viewModel(repo)
        vm.fill()

        repeat(3) { vm.submit() }
        advanceUntilIdle()
        assertTrue(vm.state.value.submitting)
        assertFalse(vm.state.value.canSubmit)
        response.complete(AlbumComment(50, "Una joya de la salsa", 5, 8))
        advanceUntilIdle()
        vm.submit()
        advanceUntilIdle()

        assertEquals(1, repo.requests.size)
        assertTrue(vm.state.value.saved)
    }

    @Test fun `errores del backend conservan el borrador y permiten reintentar`() = withMain {
        val cases = listOf(400 to CommentResult.Rejected, 404 to CommentResult.NotFound, 500 to CommentResult.Failed)
        for ((code, expected) in cases) {
            var calls = 0
            val repo = Repo(add = { _, c -> if (++calls == 1) throw http(code) else AlbumComment(51, c.description, c.rating, c.collectorId) })
            val vm = viewModel(repo)
            vm.fill()

            vm.submit()
            advanceUntilIdle()
            assertEquals("HTTP $code", expected, vm.state.value.result)
            assertEquals("  Una joya de la salsa  ", vm.state.value.description)
            assertEquals(5, vm.state.value.rating)
            assertTrue(vm.state.value.canSubmit)

            vm.submit()
            advanceUntilIdle()
            assertTrue("HTTP $code", vm.state.value.saved)
        }
    }

    @Test fun `sin red y sin el comentario en el album se puede reintentar`() = withMain {
        val repo = Repo(add = { _, _ -> throw IOException("sin red") }, list = { listOf(AlbumComment(1, "Otro", 3, 9)) })
        val vm = viewModel(repo)
        vm.fill()

        vm.submit()
        advanceUntilIdle()

        assertEquals(CommentResult.Failed, vm.state.value.result)
        assertTrue(vm.state.value.canSubmit)
    }

    @Test fun `respuesta perdida con el comentario ya guardado no reenvia sola`() = withMain {
        val existing = AlbumComment(77, "una joya de la salsa", 5, 8)
        val repo = Repo(add = { _, _ -> throw IOException("respuesta perdida") }, list = { listOf(existing) })
        val vm = viewModel(repo)
        vm.fill()

        vm.submit()
        advanceUntilIdle()

        assertEquals(CommentResult.PossiblySaved(100, existing), vm.state.value.result)
        assertEquals(1, repo.requests.size)
    }

    @Test fun `el mismo texto de otro coleccionista no cuenta como guardado`() = withMain {
        val repo = Repo(add = { _, _ -> throw IOException("respuesta perdida") }, list = { listOf(AlbumComment(77, "Una joya de la salsa", 5, 9)) })
        val vm = viewModel(repo)
        vm.fill()

        vm.submit()
        advanceUntilIdle()

        assertEquals(CommentResult.Failed, vm.state.value.result)
    }

    @Test fun `respuesta perdida sin poder consultar el album queda sin confirmar`() = withMain {
        val repo = Repo(add = { _, _ -> throw IOException("respuesta perdida") }, list = { throw IOException("sin red") })
        val vm = viewModel(repo)
        vm.fill()

        vm.submit()
        advanceUntilIdle()

        assertEquals(CommentResult.Unconfirmed, vm.state.value.result)
        assertFalse(vm.state.value.submitting)
    }

    @Test fun `el borrador sobrevive a la recreacion del ViewModel`() = withMain {
        val handle = SavedStateHandle()
        viewModel(Repo(), handle).fill(text = "Borrador", rating = 3)

        val restored = viewModel(Repo(), handle)

        assertEquals("Borrador", restored.state.value.description)
        assertEquals(3, restored.state.value.rating)
        assertNull(restored.state.value.result)
    }

    @Test fun `descartar el aviso no borra un exito`() = withMain {
        val vm = viewModel(Repo())
        vm.fill()
        vm.submit()
        advanceUntilIdle()

        vm.dismissResult()

        assertTrue(vm.state.value.saved)
    }
}
