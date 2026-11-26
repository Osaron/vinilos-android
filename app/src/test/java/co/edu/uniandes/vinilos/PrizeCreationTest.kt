package co.edu.uniandes.vinilos

import androidx.lifecycle.SavedStateHandle
import co.edu.uniandes.vinilos.data.*
import co.edu.uniandes.vinilos.model.Prize
import co.edu.uniandes.vinilos.ui.prizes.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PrizeCreationTest {
    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private val valid = PrizeDraft(" Premio ", " Descripción ", " Organización ")
    private val collector = MutableStateFlow<ActiveCollector?>(ActiveCollector(7, "Hernán"))
    private class Repo : PrizeRepository {
        var calls = 0
        var fail: Exception? = null
        var gate: CompletableDeferred<Unit>? = null
        var readFails = false
        var prizes = listOf(Prize(100, "Grammy", "NARAS", "Descripción"))
        var sent: NewPrize? = null
        override suspend fun getPrizes(): List<Prize> {
            if (readFails) throw IOException("sin red")
            return prizes
        }
        override suspend fun createPrize(prize: NewPrize): Prize {
            calls++; sent = prize; gate?.await(); fail?.let { throw it }
            return Prize(5, prize.name, prize.organization, prize.description)
        }
    }
    private fun rejected(code: Int) = HttpException(Response.error<Any>(code, "{}".toResponseBody("application/json".toMediaType())))

    @Test fun blanksDoNotSendAndAllFieldsHaveErrors() = runTest {
        val repo = Repo(); val vm = CreatePrizeViewModel(repo, collector, SavedStateHandle())
        vm.updateDraft(PrizeDraft("   ", "\n", "\t")); vm.submit(); advanceUntilIdle()
        assertEquals(3, vm.state.value.errors.size); assertEquals(0, repo.calls)
    }
    @Test fun noCollectorDoesNotSendEvenWithValidFields() = runTest {
        val repo = Repo(); val vm = CreatePrizeViewModel(repo, MutableStateFlow(null), SavedStateHandle())
        vm.updateDraft(valid); vm.submit(); advanceUntilIdle()
        assertTrue(vm.state.value.needsCollector); assertEquals(0, repo.calls)
    }
    @Test fun saveTrimsAndUsesOnlyThePrizeFields() = runTest {
        val repo = Repo(); val vm = CreatePrizeViewModel(repo, collector, SavedStateHandle())
        vm.updateDraft(valid); vm.submit(); advanceUntilIdle()
        assertEquals(NewPrize("Premio", "Descripción", "Organización"), repo.sent)
        assertEquals(5, vm.state.value.created?.id); assertFalse(vm.state.value.canSubmit)
        vm.submit(); advanceUntilIdle(); assertEquals(1, repo.calls)
    }
    @Test fun rapidDoubleTapAndEditsDuringSaveDoNotChangePayload() = runTest {
        val repo = Repo().apply { gate = CompletableDeferred() }
        val vm = CreatePrizeViewModel(repo, collector, SavedStateHandle())
        vm.updateDraft(valid); vm.submit(); vm.submit(); vm.updateDraft(PrizeDraft("Otro", "otro", "otra"))
        runCurrent(); assertEquals(1, repo.calls); assertEquals(valid, vm.state.value.draft)
        repo.gate!!.complete(Unit); advanceUntilIdle(); assertEquals("Premio", vm.state.value.created?.name)
    }
    @Test fun confirmed400KeepsDraftAndAllowsExplicitRetry() = runTest {
        val repo = Repo().apply { fail = rejected(400) }; val handle = SavedStateHandle()
        val vm = CreatePrizeViewModel(repo, collector, handle)
        vm.updateDraft(valid); vm.submit(); advanceUntilIdle()
        assertEquals(valid, vm.state.value.draft); assertEquals(PrizeSaveError.REJECTED, vm.state.value.error)
        assertTrue(vm.state.value.canSubmit); assertEquals(false, handle.get<Boolean>(CreatePrizeViewModel.WRITING))
        repo.fail = null; vm.submit(); advanceUntilIdle(); assertEquals(2, repo.calls); assertNotNull(vm.state.value.created)
    }
    @Test fun lostResponsePreservesDraftAndNeverBlindlyResends() = runTest {
        val repo = Repo().apply { fail = IOException("respuesta perdida") }; val handle = SavedStateHandle()
        val vm = CreatePrizeViewModel(repo, collector, handle)
        vm.updateDraft(valid); vm.submit(); advanceUntilIdle()
        vm.submit(); vm.updateDraft(PrizeDraft("otro", "otro", "otro")); advanceUntilIdle()
        assertEquals(1, repo.calls); assertEquals(valid, vm.state.value.draft)
        assertEquals(PrizeSaveError.UNCERTAIN, vm.state.value.error); assertFalse(vm.state.value.canSubmit)
        assertEquals(true, handle.get<Boolean>(CreatePrizeViewModel.WRITING))
    }
    @Test fun server500IsUncertainBecauseWriteMayHaveCommitted() = runTest {
        val repo = Repo().apply { fail = rejected(500) }; val vm = CreatePrizeViewModel(repo, collector, SavedStateHandle())
        vm.updateDraft(valid); vm.submit(); advanceUntilIdle()
        assertEquals(PrizeSaveError.UNCERTAIN, vm.state.value.error); assertFalse(vm.state.value.canSubmit)
    }
    @Test fun requestTimeout408AlsoBlocksResend() = runTest {
        val repo = Repo().apply { fail = rejected(408) }; val vm = CreatePrizeViewModel(repo, collector, SavedStateHandle())
        vm.updateDraft(valid); vm.submit(); advanceUntilIdle()
        assertEquals(PrizeSaveError.UNCERTAIN, vm.state.value.error)
    }
    @Test fun restoredPendingWriteDoesNotStartAnotherPost() = runTest {
        val repo = Repo(); val handle = SavedStateHandle(mapOf(CreatePrizeViewModel.NAME to "Borrador",
            CreatePrizeViewModel.DESCRIPTION to "Descripción", CreatePrizeViewModel.ORGANIZATION to "Organización",
            CreatePrizeViewModel.WRITING to true))
        val vm = CreatePrizeViewModel(repo, collector, handle); vm.submit(); advanceUntilIdle()
        assertEquals("Borrador", vm.state.value.draft.name); assertEquals(PrizeSaveError.UNCERTAIN, vm.state.value.error)
        assertEquals(0, repo.calls)
    }
    @Test fun draftRestoresFromSavedStateWithoutNetworkWrite() = runTest {
        val handle = SavedStateHandle(); val repo = Repo(); val first = CreatePrizeViewModel(repo, collector, handle)
        first.updateDraft(valid)
        val restored = CreatePrizeViewModel(repo, collector, handle)
        assertEquals(valid, restored.state.value.draft); assertEquals(0, repo.calls)
    }
    @Test fun confirmedSaveRestoresAsSavedAndCannotResend() = runTest {
        val handle = SavedStateHandle(); val repo = Repo(); val first = CreatePrizeViewModel(repo, collector, handle)
        first.updateDraft(valid); first.submit(); advanceUntilIdle()
        val restored = CreatePrizeViewModel(repo, collector, handle); restored.submit(); advanceUntilIdle()
        assertEquals(5, restored.state.value.created?.id); assertEquals(1, repo.calls)
    }
    @Test fun catalogLoadsEmptyAndRecoversFromReadFailure() = runTest {
        val repo = Repo().apply { readFails = true }; val vm = PrizeCatalogViewModel(repo)
        advanceUntilIdle(); assertTrue(vm.state.value.failed)
        repo.readFails = false; repo.prizes = emptyList(); vm.refresh(); advanceUntilIdle()
        assertFalse(vm.state.value.failed); assertTrue(vm.state.value.prizes.isEmpty())
    }
    @Test fun failedRefreshAfterCreationKeepsSuccessAndOnlyRepeatsGet() = runTest {
        val repo = Repo(); val vm = PrizeCatalogViewModel(repo); advanceUntilIdle()
        repo.readFails = true; vm.refresh("Premio creado"); advanceUntilIdle()
        assertTrue(vm.state.value.failed); assertEquals("Premio creado", vm.state.value.createdName)
        repo.readFails = false; vm.refresh(); advanceUntilIdle()
        assertEquals("Premio creado", vm.state.value.createdName); assertEquals(0, repo.calls)
    }
}
