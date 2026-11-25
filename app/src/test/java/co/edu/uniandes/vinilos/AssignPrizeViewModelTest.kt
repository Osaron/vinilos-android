package co.edu.uniandes.vinilos

import androidx.lifecycle.SavedStateHandle
import co.edu.uniandes.vinilos.data.ArtistPrizeRepository
import co.edu.uniandes.vinilos.model.ArtistPrize
import co.edu.uniandes.vinilos.model.ArtistType
import co.edu.uniandes.vinilos.model.Prize
import co.edu.uniandes.vinilos.ui.artists.ArtistPrizesViewModel
import co.edu.uniandes.vinilos.ui.artists.AssignPrizeViewModel
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

/** HU14: selección de premio y fecha, y guardado sin duplicar entregas. */
@OptIn(ExperimentalCoroutinesApi::class)
class AssignPrizeViewModelTest {
    private val grammy = Prize(100, "Grammy Award", "NARAS")
    private val nuestro = Prize(101, "Premios lo Nuestro", "Univisión")

    private class Awards(var type: ArtistType = ArtistType.MUSICIAN, val catalog: List<Prize>) : ArtistPrizeRepository {
        var awarded = listOf(ArtistPrize(1, 100, "Grammy Award", "NARAS", "1978-12-10"))
        var failed = false
        var saves = 0
        var saveFailed = false
        var lostResponse = false
        var checkFailsAfterSave = false
        val sent = mutableListOf<Pair<Int, String>>()
        override suspend fun getPrizes(): List<Prize> {
            if (failed) throw IOException("sin red")
            return catalog
        }
        override suspend fun getArtistPrizes(type: ArtistType, artistId: Int): List<ArtistPrize> {
            check(type == this.type && artistId == 50)
            if (failed || (checkFailsAfterSave && saves > 0)) throw IOException("sin red")
            return awarded
        }
        override suspend fun assignPrize(type: ArtistType, artistId: Int, prizeId: Int, date: String) {
            check(type == this.type && artistId == 50)
            saves++
            if (saveFailed) throw IllegalStateException("rechazado")
            sent += prizeId to date
            awarded = awarded + ArtistPrize(100 + saves, prizeId, catalog.first { it.id == prizeId }.name, "", date)
            if (lostResponse) throw IOException("respuesta perdida")
        }
    }

    private fun scenario(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try { block() } finally { Dispatchers.resetMain() }
    }

    private fun awards(type: ArtistType = ArtistType.MUSICIAN) = Awards(type, listOf(grammy, nuestro))

    private fun model(repo: Awards = awards(), handle: SavedStateHandle = SavedStateHandle(), type: ArtistType = ArtistType.MUSICIAN) =
        AssignPrizeViewModel(50, type, repo, handle) { "2026-11-23" }

    @Test fun cargaLosPremiosYExigePremioYFecha() = scenario {
        val model = model()
        assertTrue(model.state.value.loading)
        advanceUntilIdle()
        assertEquals(listOf(100, 101), model.state.value.prizes.map { it.id })
        assertFalse(model.state.value.canSubmit)
        model.select(101)
        assertFalse("Falta la fecha", model.state.value.canSubmit)
        model.setDate("2020-05-01")
        assertTrue(model.state.value.canSubmit)
    }

    @Test fun laFechaSolaNoBasta() = scenario {
        val model = model()
        advanceUntilIdle()
        model.setDate("2020-05-01")
        assertFalse(model.state.value.canSubmit)
        model.submit()
        advanceUntilIdle()
        assertFalse(model.state.value.saved)
    }

    @Test fun asignaAUnMusicoConElPremioYLaFechaElegidos() = scenario {
        val repo = awards()
        val model = model(repo)
        advanceUntilIdle()
        model.select(101)
        model.setDate("2020-05-01")
        model.submit()
        assertTrue(model.state.value.submitting)
        advanceUntilIdle()
        assertTrue(model.state.value.saved)
        assertEquals(listOf(101 to "2020-05-01"), repo.sent)
    }

    @Test fun asignaAUnaBandaConservandoSuTipo() = scenario {
        val repo = awards(ArtistType.BAND)
        val model = model(repo, type = ArtistType.BAND)
        advanceUntilIdle()
        model.select(100)
        model.setDate("2021-11-23")
        model.submit()
        advanceUntilIdle()
        // El doble falla si el tipo o el ID no son los de la banda.
        assertTrue(model.state.value.saved)
        assertEquals(listOf(100 to "2021-11-23"), repo.sent)
    }

    @Test fun rechazaUnaFechaFuturaYConservaLaAnterior() = scenario {
        val model = model()
        advanceUntilIdle()
        model.setDate("2020-05-01")
        model.setDate("2026-11-24")
        assertTrue(model.state.value.futureDate)
        assertEquals("2020-05-01", model.state.value.date)
        model.setDate("2026-11-23")
        assertFalse(model.state.value.futureDate)
        assertEquals("2026-11-23", model.state.value.date)
    }

    @Test fun ignoraFechasMalFormadasYPremiosQueNoExisten() = scenario {
        val model = model()
        advanceUntilIdle()
        model.setDate("01/05/2020")
        model.select(999)
        assertNull(model.state.value.date)
        assertNull(model.state.value.selectedId)
    }

    @Test fun bloqueaElMismoPremioConLaMismaFecha() = scenario {
        val repo = awards()
        val model = model(repo)
        advanceUntilIdle()
        model.select(100)
        model.setDate("1978-12-10")
        assertTrue(model.state.value.duplicate)
        assertFalse(model.state.value.canSubmit)
        model.submit()
        advanceUntilIdle()
        assertEquals(0, repo.saves)
        // El mismo premio en otra fecha sí es una entrega distinta.
        model.setDate("1985-02-26")
        assertFalse(model.state.value.duplicate)
        assertTrue(model.state.value.canSubmit)
    }

    @Test fun unDobleToqueEnviaUnaSolaVez() = scenario {
        val repo = awards()
        val model = model(repo)
        advanceUntilIdle()
        model.select(101)
        model.setDate("2020-05-01")
        model.submit()
        model.submit()
        advanceUntilIdle()
        model.submit()
        advanceUntilIdle()
        assertEquals(1, repo.saves)
    }

    @Test fun unRechazoConservaElBorradorYElReintentoGuarda() = scenario {
        val repo = awards().apply { saveFailed = true }
        val model = model(repo)
        advanceUntilIdle()
        model.select(101)
        model.setDate("2020-05-01")
        model.submit()
        advanceUntilIdle()
        assertTrue(model.state.value.submitFailed)
        assertEquals(101, model.state.value.selectedId)
        assertEquals("2020-05-01", model.state.value.date)
        repo.saveFailed = false
        model.submit()
        advanceUntilIdle()
        assertTrue(model.state.value.saved)
        assertEquals(listOf(101 to "2020-05-01"), repo.sent)
    }

    @Test fun siSePierdeLaRespuestaConfirmaLeyendoAlArtista() = scenario {
        val repo = awards().apply { lostResponse = true }
        val model = model(repo)
        advanceUntilIdle()
        model.select(101)
        model.setDate("2020-05-01")
        model.submit()
        advanceUntilIdle()
        assertTrue(model.state.value.saved)
        assertFalse(model.state.value.unconfirmed)
        assertEquals(1, repo.saves)
    }

    @Test fun siNoPuedeConfirmarElReintentoNoDuplicaLaEntrega() = scenario {
        val repo = awards().apply { lostResponse = true; checkFailsAfterSave = true }
        val model = model(repo)
        advanceUntilIdle()
        model.select(101)
        model.setDate("2020-05-01")
        model.submit()
        advanceUntilIdle()
        assertTrue(model.state.value.unconfirmed)
        assertFalse(model.state.value.saved)
        assertEquals(101, model.state.value.selectedId)
        // Vuelve la red: el reintento encuentra la entrega y no la envía otra vez.
        repo.checkFailsAfterSave = false
        model.submit()
        advanceUntilIdle()
        assertTrue(model.state.value.saved)
        assertEquals(1, repo.saves)
    }

    @Test fun unErrorDeCargaPermiteReintentar() = scenario {
        val repo = awards().apply { failed = true }
        val model = model(repo)
        advanceUntilIdle()
        assertTrue(model.state.value.failed)
        assertFalse(model.state.value.canSubmit)
        repo.failed = false
        model.load()
        advanceUntilIdle()
        assertFalse(model.state.value.failed)
        assertEquals(2, model.state.value.prizes.size)
    }

    @Test fun conservaPremioYFechaAlRecrear() = scenario {
        val handle = SavedStateHandle()
        val first = model(handle = handle)
        advanceUntilIdle()
        first.select(101)
        first.setDate("2020-05-01")
        val recreated = model(handle = handle)
        assertEquals(101, recreated.state.value.selectedId)
        assertEquals("2020-05-01", recreated.state.value.date)
        advanceUntilIdle()
        assertEquals(nuestro, recreated.state.value.selected)
        assertTrue(recreated.state.value.canSubmit)
    }

    @Test fun descartaUnPremioGuardadoQueYaNoExiste() = scenario {
        val handle = SavedStateHandle(mapOf(AssignPrizeViewModel.KEY_PRIZE to 999, AssignPrizeViewModel.KEY_DATE to "basura"))
        val model = model(handle = handle)
        advanceUntilIdle()
        assertNull(model.state.value.selectedId)
        assertNull(model.state.value.date)
    }

    @Test fun elDetalleMuestraLosPremiosYSeRecargaTrasAsignar() = scenario {
        val repo = awards()
        val detail = ArtistPrizesViewModel(50, ArtistType.MUSICIAN, repo)
        assertTrue(detail.state.value.loading)
        advanceUntilIdle()
        assertEquals(listOf("1978-12-10"), detail.state.value.prizes.map { it.date })
        repo.assignPrize(ArtistType.MUSICIAN, 50, 101, "2020-05-01")
        detail.refresh()
        advanceUntilIdle()
        assertEquals(2, detail.state.value.prizes.size)
        repo.failed = true
        detail.refresh()
        advanceUntilIdle()
        assertTrue(detail.state.value.failed)
    }
}
