package co.edu.uniandes.vinilos.ui.bands

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.edu.uniandes.vinilos.data.ArtistRepository
import co.edu.uniandes.vinilos.data.BandMusicianRepository
import co.edu.uniandes.vinilos.model.Artist
import co.edu.uniandes.vinilos.model.ArtistType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

data class MusicianSelectionState(
    val loading: Boolean = true,
    val musicians: List<Artist> = emptyList(),
    val selectedId: Int? = null,
    val failed: Boolean = false,
    val submitting: Boolean = false,
    val saved: Boolean = false,
    val submitFailed: Boolean = false,
    val unconfirmed: Boolean = false,
) {
    val selected: Artist? get() = musicians.firstOrNull { it.id == selectedId }
}

class MusicianSelectionViewModel(
    private val bandId: Int,
    private val artists: ArtistRepository,
    private val bands: BandMusicianRepository,
    private val handle: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        MusicianSelectionState(selectedId = handle[KEY_SELECTED]),
    )
    val state = mutableState.asStateFlow()
    private var fetching = false

    init {
        require(bandId > 0) { "Identificador de banda inválido" }
        load()
    }

    fun load() {
        if (fetching || state.value.submitting || state.value.saved) return
        fetching = true
        mutableState.value = mutableState.value.copy(loading = true, failed = false)
        viewModelScope.launch {
            try {
                val members = bands.getMembers(bandId).map { it.id }.toSet()
                val available = artists.getArtists().filter {
                    it.type == ArtistType.MUSICIAN && it.id !in members
                }.distinctBy { it.id }
                val selected = mutableState.value.selectedId?.takeIf { id -> available.any { it.id == id } }
                handle[KEY_SELECTED] = selected
                mutableState.value = MusicianSelectionState(
                    loading = false, musicians = available, selectedId = selected,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(loading = false, failed = true)
            } finally {
                fetching = false
            }
        }
    }

    fun select(id: Int) {
        if (state.value.loading || state.value.failed || state.value.submitting || state.value.saved || state.value.musicians.none { it.id == id }) return
        handle[KEY_SELECTED] = id
        mutableState.value = mutableState.value.copy(selectedId = id, submitFailed = false, unconfirmed = false)
    }

    fun submit() {
        val current = state.value
        val musician = current.selected ?: return
        if (current.loading || current.failed || current.submitting || current.saved) return
        mutableState.value = current.copy(submitting = true, submitFailed = false, unconfirmed = false)
        viewModelScope.launch {
            try {
                // Consulta antes de reenviar si una respuesta previa pudo perderse.
                val existing = bands.getMembers(bandId)
                if (existing.none { it.id == musician.id }) bands.addMusician(bandId, musician.id)
                mutableState.value = state.value.copy(submitting = false, saved = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                val confirmed = try {
                    bands.getMembers(bandId).any { it.id == musician.id }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    false
                }
                mutableState.value = state.value.copy(
                    submitting = false, saved = confirmed, unconfirmed = !confirmed,
                )
            } catch (_: Exception) {
                mutableState.value = state.value.copy(submitting = false, submitFailed = true)
            }
        }
    }

    companion object { const val KEY_SELECTED = "hu12SelectedMusician" }
}
