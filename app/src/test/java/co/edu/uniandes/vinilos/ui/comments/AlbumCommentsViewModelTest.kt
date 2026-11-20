package co.edu.uniandes.vinilos.ui.comments

import co.edu.uniandes.vinilos.data.CommentRepository
import co.edu.uniandes.vinilos.model.AlbumComment
import co.edu.uniandes.vinilos.model.NewComment
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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** HU09 (#82): comentarios del detalle del álbum. */
@OptIn(ExperimentalCoroutinesApi::class)
class AlbumCommentsViewModelTest {
    private val comment = AlbumComment(1, "Gran álbum", 5, 8)

    private fun withMain(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try { block() } finally { Dispatchers.resetMain() }
    }

    private fun repo(list: suspend (Int) -> List<AlbumComment>) = object : CommentRepository {
        val asked = mutableListOf<Int>()
        override suspend fun getComments(albumId: Int): List<AlbumComment> { asked += albumId; return list(albumId) }
        override suspend fun addComment(albumId: Int, comment: NewComment) = error("no se usa")
    }

    @Test fun `carga los comentarios del album indicado`() = withMain {
        val repository = repo { listOf(comment) }
        val vm = AlbumCommentsViewModel(100, repository)
        assertTrue(vm.state.value.loading)

        advanceUntilIdle()

        assertEquals(listOf(100), repository.asked)
        assertEquals(listOf(comment), vm.state.value.comments)
        assertFalse(vm.state.value.loading)
    }

    @Test fun `si falla informa y al reintentar recupera`() = withMain {
        var calls = 0
        val vm = AlbumCommentsViewModel(100, repo { if (++calls == 1) throw IOException("sin red") else listOf(comment) })
        advanceUntilIdle()
        assertTrue(vm.state.value.failed)

        vm.refresh()
        advanceUntilIdle()

        assertFalse(vm.state.value.failed)
        assertEquals(listOf(comment), vm.state.value.comments)
    }

    @Test fun `al recargar conserva los comentarios mientras llega la respuesta`() = withMain {
        var comments = listOf(comment)
        val vm = AlbumCommentsViewModel(100, repo { comments })
        advanceUntilIdle()
        comments = listOf(comment, AlbumComment(2, "Otro", 4, 7))

        vm.refresh()
        assertEquals(listOf(comment), vm.state.value.comments)
        advanceUntilIdle()

        assertEquals(2, vm.state.value.comments.size)
    }
}
