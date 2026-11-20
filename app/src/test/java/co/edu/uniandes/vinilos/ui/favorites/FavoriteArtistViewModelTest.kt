package co.edu.uniandes.vinilos.ui.favorites

import co.edu.uniandes.vinilos.data.ActiveCollector
import co.edu.uniandes.vinilos.data.FavoriteArtistRepository
import co.edu.uniandes.vinilos.model.ArtistType
import co.edu.uniandes.vinilos.model.FavoritePerformer
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

/** HU10 (#87, #89): agregar un artista a los favoritos del coleccionista activo. Pruebas de #90. */
@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteArtistViewModelTest {
    private val ana = ActiveCollector(8, "Ana María")
    private val jose = ActiveCollector(7, "José Pérez")
    private val active = MutableStateFlow<ActiveCollector?>(ana)

    private fun withMain(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try { block() } finally { Dispatchers.resetMain() }
    }

    /** Favoritos en memoria por coleccionista; [add] y [list] permiten simular fallos y esperas. */
    private class Repo(
        val favorites: MutableMap<Int, MutableList<Int>> = mutableMapOf(),
        var add: (suspend (Int, Int, ArtistType) -> Unit)? = null,
        var list: (suspend (Int) -> Unit)? = null,
    ) : FavoriteArtistRepository {
        val requests = mutableListOf<Triple<Int, Int, ArtistType>>()
        override suspend fun getFavorites(collectorId: Int): List<FavoritePerformer> {
            list?.invoke(collectorId)
            return favorites[collectorId].orEmpty().map { FavoritePerformer(it, "Artista $it", null, "", null) }
        }
        override suspend fun addFavorite(collectorId: Int, artistId: Int, type: ArtistType): FavoritePerformer {
            requests += Triple(collectorId, artistId, type)
            add?.invoke(collectorId, artistId, type)
            favorites.getOrPut(collectorId) { mutableListOf() } += artistId
            return FavoritePerformer(artistId, "Artista $artistId", null, "", null)
        }
    }

    private fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))

    private fun viewModel(repo: Repo, type: ArtistType = ArtistType.BAND) = FavoriteArtistViewModel(100, type, repo, active)

    @Test fun `sin coleccionista no envia y pide elegir uno`() = withMain {
        active.value = null
        val repo = Repo()
        val vm = viewModel(repo)
        advanceUntilIdle()

        assertFalse(vm.add())
        advanceUntilIdle()

        assertTrue(repo.requests.isEmpty())
        assertNull(vm.state.value.collector)
    }

    @Test fun `consulta si ya es favorito del coleccionista activo`() = withMain {
        val vm = viewModel(Repo(mutableMapOf(8 to mutableListOf(100))))
        advanceUntilIdle()

        assertEquals(ana, vm.state.value.collector)
        assertEquals(true, vm.state.value.isFavorite)
        assertFalse(vm.state.value.canAdd)
    }

    @Test fun `agrega una banda por su ruta a nombre del coleccionista activo`() = withMain {
        val repo = Repo()
        val vm = viewModel(repo, ArtistType.BAND)
        advanceUntilIdle()

        assertTrue(vm.add())
        advanceUntilIdle()

        assertEquals(listOf(Triple(8, 100, ArtistType.BAND)), repo.requests)
        assertEquals(FavoriteResult.Added(ana), vm.state.value.result)
        assertEquals(true, vm.state.value.isFavorite)
    }

    @Test fun `un musico se agrega con su tipo`() = withMain {
        val repo = Repo()
        val vm = viewModel(repo, ArtistType.MUSICIAN)
        advanceUntilIdle()
        vm.add()
        advanceUntilIdle()

        assertEquals(ArtistType.MUSICIAN, repo.requests.single().third)
    }

    @Test fun `si ya es favorito no lo agrega de nuevo`() = withMain {
        val repo = Repo(mutableMapOf(8 to mutableListOf(100)))
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.add()
        advanceUntilIdle()

        assertTrue(repo.requests.isEmpty())
    }

    @Test fun `pulsaciones repetidas crean un solo favorito`() = withMain {
        val gate = CompletableDeferred<Unit>()
        val repo = Repo(add = { _, _, _ -> gate.await() })
        val vm = viewModel(repo)
        advanceUntilIdle()

        repeat(3) { vm.add() }
        advanceUntilIdle()
        assertTrue(vm.state.value.submitting)
        gate.complete(Unit)
        advanceUntilIdle()
        vm.add()
        advanceUntilIdle()

        assertEquals(1, repo.requests.size)
    }

    @Test fun `agregar mientras se consulta espera la consulta y no duplica`() = withMain {
        val gate = CompletableDeferred<Unit>()
        val repo = Repo(mutableMapOf(8 to mutableListOf(100)), list = { gate.await() })
        val vm = viewModel(repo)
        advanceUntilIdle()
        assertTrue(vm.state.value.checking)

        vm.add()
        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue("ya era favorito", repo.requests.isEmpty())
        assertEquals(true, vm.state.value.isFavorite)
    }

    @Test fun `si la consulta falla igual permite agregar`() = withMain {
        var fail = true
        val repo = Repo(list = { if (fail) throw IOException("sin red") })
        val vm = viewModel(repo)
        advanceUntilIdle()
        assertNull(vm.state.value.isFavorite)
        fail = false

        vm.add()
        advanceUntilIdle()

        assertEquals(FavoriteResult.Added(ana), vm.state.value.result)
    }

    @Test fun `errores del backend permiten reintentar`() = withMain {
        for ((code, expected) in listOf(404 to FavoriteResult.NotFound, 500 to FavoriteResult.Failed)) {
            var calls = 0
            val repo = Repo(add = { _, _, _ -> if (++calls == 1) throw http(code) })
            val vm = viewModel(repo)
            advanceUntilIdle()

            vm.add()
            advanceUntilIdle()
            assertEquals("HTTP $code", expected, vm.state.value.result)
            assertTrue(vm.state.value.canAdd)

            vm.add()
            advanceUntilIdle()
            assertEquals("HTTP $code", FavoriteResult.Added(ana), vm.state.value.result)
        }
    }

    @Test fun `respuesta perdida con el artista ya en favoritos cuenta como agregado`() = withMain {
        val repo = Repo()
        repo.add = { collectorId, artistId, _ ->
            repo.favorites.getOrPut(collectorId) { mutableListOf() } += artistId
            throw IOException("respuesta perdida")
        }
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.add()
        advanceUntilIdle()

        assertEquals(FavoriteResult.Added(ana), vm.state.value.result)
        assertEquals(1, repo.requests.size)
    }

    @Test fun `sin red y sin el artista en favoritos se puede reintentar`() = withMain {
        val repo = Repo(add = { _, _, _ -> throw IOException("sin red") })
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.add()
        advanceUntilIdle()

        assertEquals(FavoriteResult.Failed, vm.state.value.result)
        assertTrue(vm.state.value.canAdd)
    }

    @Test fun `respuesta perdida sin poder consultar queda sin confirmar`() = withMain {
        var failList = false
        val repo = Repo(add = { _, _, _ -> failList = true; throw IOException("respuesta perdida") }, list = { if (failList) throw IOException("sin red") })
        val vm = viewModel(repo)
        advanceUntilIdle()

        vm.add()
        advanceUntilIdle()

        assertEquals(FavoriteResult.Unconfirmed, vm.state.value.result)
        assertFalse(vm.state.value.submitting)
    }

    @Test fun `al cambiar de coleccionista se consulta de nuevo y se olvida el aviso anterior`() = withMain {
        val repo = Repo(mutableMapOf(7 to mutableListOf(100)))
        val vm = viewModel(repo)
        advanceUntilIdle()
        vm.add()
        advanceUntilIdle()
        assertEquals(FavoriteResult.Added(ana), vm.state.value.result)

        active.value = jose
        advanceUntilIdle()

        assertEquals(jose, vm.state.value.collector)
        assertNull(vm.state.value.result)
        assertEquals(true, vm.state.value.isFavorite)
    }

    @Test fun `al elegir coleccionista despues de pedirlo se puede agregar de inmediato`() = withMain {
        active.value = null
        val repo = Repo()
        val vm = viewModel(repo)
        advanceUntilIdle()
        assertFalse(vm.add())

        active.value = jose
        assertTrue(vm.add())
        advanceUntilIdle()

        assertEquals(listOf(Triple(7, 100, ArtistType.BAND)), repo.requests)
    }
}
