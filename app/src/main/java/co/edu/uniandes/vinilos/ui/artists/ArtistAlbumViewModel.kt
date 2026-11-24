package co.edu.uniandes.vinilos.ui.artists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.edu.uniandes.vinilos.data.AlbumRepository
import co.edu.uniandes.vinilos.data.ArtistAlbumRepository
import co.edu.uniandes.vinilos.model.Album
import co.edu.uniandes.vinilos.model.ArtistType
import co.edu.uniandes.vinilos.ui.albums.picker.AlbumPicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

data class ArtistAlbumState(
    val loading: Boolean = true,
    /** Álbumes del catálogo que todavía no están en la discografía del artista. */
    val available: List<Album> = emptyList(),
    val query: String = "",
    val selectedId: Int? = null,
    val failed: Boolean = false,
    val submitting: Boolean = false,
    val saved: Boolean = false,
    val submitFailed: Boolean = false,
    val unconfirmed: Boolean = false,
) {
    val visible: List<Album> get() = AlbumPicker.filter(available, query)
    val selected: Album? get() = available.firstOrNull { it.id == selectedId }
    val canSubmit: Boolean get() = !loading && !failed && !submitting && !saved && selected != null
}

/** HU15: elegir un álbum del catálogo y agregarlo a la discografía de un músico o una banda. */
class ArtistAlbumViewModel(
    private val artistId: Int,
    private val artistType: ArtistType,
    private val albums: AlbumRepository,
    private val artistAlbums: ArtistAlbumRepository,
    private val handle: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        ArtistAlbumState(selectedId = handle[KEY_SELECTED], query = handle[KEY_QUERY] ?: ""),
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
                val associated = artistAlbums.getAlbumIds(artistType, artistId)
                val available = AlbumPicker.available(albums.getAlbums(), associated)
                val selected = state.value.selectedId?.takeIf { id -> available.any { it.id == id } }
                handle[KEY_SELECTED] = selected
                mutableState.value = state.value.copy(
                    loading = false, available = available, selectedId = selected, failed = false,
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

    fun setQuery(query: String) {
        if (query == state.value.query) return
        handle[KEY_QUERY] = query
        mutableState.value = state.value.copy(query = query)
    }

    fun select(albumId: Int) {
        val current = state.value
        if (current.loading || current.failed || current.submitting || current.saved) return
        if (current.available.none { it.id == albumId }) return
        handle[KEY_SELECTED] = albumId
        mutableState.value = current.copy(selectedId = albumId, submitFailed = false, unconfirmed = false)
    }

    fun submit() {
        val current = state.value
        val album = current.selected ?: return
        if (!current.canSubmit) return
        mutableState.value = current.copy(submitting = true, submitFailed = false, unconfirmed = false)
        viewModelScope.launch {
            try {
                // Si una respuesta anterior se perdió, el álbum puede estar asociado: se consulta antes
                // de volver a enviar para no duplicarlo.
                if (album.id !in artistAlbums.getAlbumIds(artistType, artistId)) {
                    artistAlbums.addAlbum(artistType, artistId, album.id)
                }
                mutableState.value = state.value.copy(submitting = false, saved = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                val confirmed = try {
                    album.id in artistAlbums.getAlbumIds(artistType, artistId)
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

    companion object {
        const val KEY_SELECTED = "hu15SelectedAlbum"
        const val KEY_QUERY = "hu15AlbumQuery"
    }
}
