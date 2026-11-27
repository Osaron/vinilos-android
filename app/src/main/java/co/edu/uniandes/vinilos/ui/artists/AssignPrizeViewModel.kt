package co.edu.uniandes.vinilos.ui.artists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.edu.uniandes.vinilos.data.ArtistPrizeRepository
import co.edu.uniandes.vinilos.model.ArtistPrize
import co.edu.uniandes.vinilos.model.ArtistType
import co.edu.uniandes.vinilos.model.Prize
import co.edu.uniandes.vinilos.model.PrizeDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

data class AssignPrizeState(
    val loading: Boolean = true,
    val prizes: List<Prize> = emptyList(),
    /** Premios que el artista ya tiene; sirven para no registrar dos veces la misma entrega. */
    val awarded: List<ArtistPrize> = emptyList(),
    val selectedId: Int? = null,
    /** Fecha de entrega elegida, `yyyy-MM-dd`. */
    val date: String? = null,
    val futureDate: Boolean = false,
    val failed: Boolean = false,
    val submitting: Boolean = false,
    val saved: Boolean = false,
    val submitFailed: Boolean = false,
    val unconfirmed: Boolean = false,
) {
    val selected: Prize? get() = prizes.firstOrNull { it.id == selectedId }

    /** El artista ya tiene ese premio con esa misma fecha. */
    val duplicate: Boolean get() = selectedId != null && date != null &&
        awarded.any { it.prizeId == selectedId && it.date == date }

    val canSubmit: Boolean get() = !loading && !failed && !submitting && !saved &&
        selected != null && date != null && !duplicate
}

/** HU14: elegir un premio existente y la fecha en que lo recibió un músico o una banda. */
class AssignPrizeViewModel(
    private val artistId: Int,
    private val artistType: ArtistType,
    private val repository: ArtistPrizeRepository,
    private val handle: SavedStateHandle,
    private val today: () -> String = PrizeDate::today,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        AssignPrizeState(
            selectedId = handle[KEY_PRIZE],
            date = handle.get<String>(KEY_DATE)?.takeIf(PrizeDate::isIso),
        ),
    )
    val state = mutableState.asStateFlow()
    private var fetching = false

    init {
        require(artistId > 0) { "Identificador de artista inválido" }
        load()
    }

    fun load() {
        if (fetching || state.value.submitting || state.value.saved) return
        fetching = true
        mutableState.value = state.value.copy(loading = true, failed = false)
        viewModelScope.launch {
            try {
                val awarded = repository.getArtistPrizes(artistType, artistId)
                val prizes = repository.getPrizes()
                val selected = state.value.selectedId?.takeIf { id -> prizes.any { it.id == id } }
                handle[KEY_PRIZE] = selected
                mutableState.value = state.value.copy(
                    loading = false, prizes = prizes, awarded = awarded, selectedId = selected, failed = false,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = state.value.copy(loading = false, failed = true)
            } finally {
                fetching = false
            }
        }
    }

    fun select(prizeId: Int) {
        val current = state.value
        if (current.loading || current.failed || current.submitting || current.saved) return
        if (current.prizes.none { it.id == prizeId }) return
        handle[KEY_PRIZE] = prizeId
        mutableState.value = current.copy(selectedId = prizeId, submitFailed = false, unconfirmed = false)
    }

    /** [date] es `yyyy-MM-dd`. Una fecha futura se rechaza y se conserva la anterior. */
    fun setDate(date: String) {
        val current = state.value
        if (current.submitting || current.saved || !PrizeDate.isIso(date)) return
        if (date > today()) {
            mutableState.value = current.copy(futureDate = true)
            return
        }
        handle[KEY_DATE] = date
        mutableState.value = current.copy(date = date, futureDate = false, submitFailed = false, unconfirmed = false)
    }

    fun submit() {
        val current = state.value
        val prize = current.selected ?: return
        val date = current.date ?: return
        if (!current.canSubmit) return
        mutableState.value = current.copy(submitting = true, submitFailed = false, unconfirmed = false, futureDate = false)
        viewModelScope.launch {
            try {
                // El backend no evita repetidos: si una respuesta anterior se perdió, la entrega puede
                // existir. Se consulta antes de enviar para no duplicarla. La lectura usa la ruta del
                // tipo del artista, así que también detecta un ID que no es de ese tipo.
                if (!alreadyAwarded(prize.id, date)) {
                    repository.assignPrize(artistType, artistId, prize.id, date)
                }
                mutableState.value = state.value.copy(submitting = false, saved = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                val confirmed = try {
                    alreadyAwarded(prize.id, date)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    false
                }
                mutableState.value = state.value.copy(submitting = false, saved = confirmed, unconfirmed = !confirmed)
            } catch (_: Exception) {
                mutableState.value = state.value.copy(submitting = false, submitFailed = true)
            }
        }
    }

    private suspend fun alreadyAwarded(prizeId: Int, date: String): Boolean =
        repository.getArtistPrizes(artistType, artistId).any { it.prizeId == prizeId && it.date == date }

    companion object {
        const val KEY_PRIZE = "hu14SelectedPrize"
        const val KEY_DATE = "hu14PremiationDate"
    }
}

data class ArtistPrizesState(
    val loading: Boolean = true,
    val prizes: List<ArtistPrize> = emptyList(),
    val failed: Boolean = false,
)

/** HU14: premios que se muestran en el detalle del artista. Se carga aparte para no bloquear el detalle. */
class ArtistPrizesViewModel(
    private val artistId: Int,
    private val artistType: ArtistType,
    private val repository: ArtistPrizeRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ArtistPrizesState())
    val state = mutableState.asStateFlow()
    private var fetching = false

    init {
        require(artistId > 0) { "Identificador de artista inválido" }
        refresh()
    }

    fun refresh() {
        if (fetching) return
        fetching = true
        mutableState.value = state.value.copy(loading = true, failed = false)
        viewModelScope.launch {
            try {
                mutableState.value = ArtistPrizesState(loading = false, prizes = repository.getArtistPrizes(artistType, artistId))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = state.value.copy(loading = false, failed = true)
            } finally {
                fetching = false
            }
        }
    }

    class Factory(
        private val artistId: Int,
        private val artistType: ArtistType,
        private val repository: ArtistPrizeRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ArtistPrizesViewModel(artistId, artistType, repository) as T
    }
}
