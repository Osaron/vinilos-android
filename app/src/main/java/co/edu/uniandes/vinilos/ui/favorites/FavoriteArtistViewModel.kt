package co.edu.uniandes.vinilos.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.edu.uniandes.vinilos.data.ActiveCollector
import co.edu.uniandes.vinilos.data.FavoriteArtistRepository
import co.edu.uniandes.vinilos.model.ArtistType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/** Resultado del último intento de agregar el artista a favoritos. */
sealed interface FavoriteResult {
    /** Quedó en los favoritos de [collector]. */
    data class Added(val collector: ActiveCollector) : FavoriteResult

    /** Error del servidor o sin red y sin el artista en favoritos. Se puede reintentar. */
    data object Failed : FavoriteResult

    /** El artista o el coleccionista ya no existen (404). */
    data object NotFound : FavoriteResult

    /** Se perdió la respuesta y tampoco se pudo consultar la lista de favoritos. */
    data object Unconfirmed : FavoriteResult
}

data class FavoriteArtistState(
    val collector: ActiveCollector? = null,
    /** Consultando si el artista ya es favorito del coleccionista activo. */
    val checking: Boolean = false,
    /** `true` si ya está en sus favoritos; `null` si no se pudo saber. */
    val isFavorite: Boolean? = null,
    val submitting: Boolean = false,
    val result: FavoriteResult? = null,
) {
    /** Se puede agregar si no se sabe que ya es favorito: el backend no impide duplicados. */
    val canAdd: Boolean get() = !submitting && !checking && isFavorite != true
}

/**
 * HU10 (#87, #89): agrega el músico o la banda a los favoritos del coleccionista activo (#80).
 *
 * - Al cambiar el coleccionista consulta sus favoritos para no ofrecer agregar lo que ya está:
 *   BackVynils no impide duplicados, así que la regla la aplica la app.
 * - Una sola solicitud a la vez y ninguna cuando ya es favorito.
 * - Si se pierde la respuesta, consulta los favoritos antes de ofrecer otro intento.
 */
class FavoriteArtistViewModel(
    private val artistId: Int,
    private val type: ArtistType,
    private val repository: FavoriteArtistRepository,
    private val activeCollector: StateFlow<ActiveCollector?>,
) : ViewModel() {
    private val mutableState = MutableStateFlow(FavoriteArtistState())
    val state = mutableState.asStateFlow()
    private var checkJob: Job? = null

    /** Se pidió agregar mientras se consultaban los favoritos: se agrega al terminar la consulta. */
    private var pendingAdd = false

    init {
        viewModelScope.launch {
            activeCollector.collect { collector ->
                if (collector != mutableState.value.collector) onCollectorChanged(collector)
            }
        }
    }

    private fun onCollectorChanged(collector: ActiveCollector?) {
        checkJob?.cancel()
        if (mutableState.value.submitting) {
            // El envío en curso termina a nombre del coleccionista con el que empezó.
            mutableState.value = mutableState.value.copy(collector = collector)
            return
        }
        startChecking(collector)
    }

    private fun startChecking(collector: ActiveCollector?) {
        pendingAdd = false
        mutableState.value = FavoriteArtistState(collector = collector, checking = collector != null)
        if (collector != null) checkJob = viewModelScope.launch { check(collector) }
    }

    private suspend fun check(collector: ActiveCollector) {
        val isFavorite = try {
            repository.getFavorites(collector.id).any { it.id == artistId }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        if (mutableState.value.collector != collector) return
        mutableState.value = mutableState.value.copy(checking = false, isFavorite = isFavorite)
        if (pendingAdd) {
            pendingAdd = false
            if (mutableState.value.canAdd) submit(collector)
        }
    }

    /** Agrega el artista; devuelve `false` si falta elegir coleccionista (la vista abre el selector). */
    fun add(): Boolean {
        val active = activeCollector.value ?: return false
        if (active != mutableState.value.collector) onCollectorChanged(active)
        val current = mutableState.value
        when {
            current.checking -> pendingAdd = true
            current.canAdd -> submit(active)
        }
        return true
    }

    private fun submit(collector: ActiveCollector) {
        mutableState.value = mutableState.value.copy(submitting = true, result = null)
        viewModelScope.launch {
            val result = try {
                repository.addFavorite(collector.id, artistId, type)
                FavoriteResult.Added(collector)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: HttpException) {
                if (error.code() == 404) FavoriteResult.NotFound else FavoriteResult.Failed
            } catch (_: IOException) {
                verifyAfterLostResponse(collector)
            } catch (_: Exception) {
                FavoriteResult.Failed
            }
            val latest = mutableState.value
            if (latest.collector == collector) {
                mutableState.value = latest.copy(
                    submitting = false,
                    result = result,
                    isFavorite = if (result is FavoriteResult.Added) true else latest.isFavorite,
                )
            } else {
                // Se cambió de coleccionista mientras se enviaba: se consulta de nuevo para el nuevo.
                startChecking(latest.collector)
            }
        }
    }

    private suspend fun verifyAfterLostResponse(collector: ActiveCollector): FavoriteResult = try {
        if (repository.getFavorites(collector.id).any { it.id == artistId }) FavoriteResult.Added(collector) else FavoriteResult.Failed
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        FavoriteResult.Unconfirmed
    }

    class Factory(
        private val artistId: Int,
        private val type: ArtistType,
        private val repository: FavoriteArtistRepository,
        private val activeCollector: StateFlow<ActiveCollector?>,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            FavoriteArtistViewModel(artistId, type, repository, activeCollector) as T
    }
}
