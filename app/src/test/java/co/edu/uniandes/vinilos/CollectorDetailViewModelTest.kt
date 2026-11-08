package co.edu.uniandes.vinilos

import co.edu.uniandes.vinilos.data.CollectorDetailRepository
import co.edu.uniandes.vinilos.model.CollectorComment
import co.edu.uniandes.vinilos.model.CollectorDetail
import co.edu.uniandes.vinilos.model.FavoritePerformer
import co.edu.uniandes.vinilos.ui.collectors.CollectorDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CollectorDetailViewModelTest {
    private val detail = CollectorDetail(
        100, "Manolo Bellon", "manolo@example.org", "3001234567",
        listOf(CollectorComment(11, "Buen álbum", 5)),
        listOf(FavoritePerformer(7, "Rubén Blades", null, "Cantautor", "1948-07-16")),
    )

    @Test fun `consulta id y publica el detalle`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var requested = -1
            val viewModel = CollectorDetailViewModel(100, CollectorDetailRepository { requested = it; detail })
            assertTrue(viewModel.state.value.loading)
            advanceUntilIdle()
            assertEquals(100, requested)
            assertEquals(detail, viewModel.state.value.detail)
            assertFalse(viewModel.state.value.failed)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun `error se recupera con reintento explicito`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var calls = 0
            val viewModel = CollectorDetailViewModel(100, CollectorDetailRepository {
                if (++calls == 1) throw IOException("offline")
                detail
            })
            advanceUntilIdle()
            assertTrue(viewModel.state.value.failed)
            viewModel.refresh()
            advanceUntilIdle()
            assertEquals(2, calls)
            assertEquals(detail, viewModel.state.value.detail)
        } finally { Dispatchers.resetMain() }
    }
}
