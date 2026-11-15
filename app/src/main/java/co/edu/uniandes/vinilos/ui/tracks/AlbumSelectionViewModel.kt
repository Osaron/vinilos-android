package co.edu.uniandes.vinilos.ui.tracks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.edu.uniandes.vinilos.data.AlbumRepository
import co.edu.uniandes.vinilos.model.Album
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Estado del selector de álbum de HU08 (#31).
 *
 * [selectedAlbumId] es la fuente de verdad: se conserva aunque haya títulos repetidos y aunque
 * el catálogo todavía no cargue. [selectedAlbum] solo existe si ese ID está en el catálogo.
 */
data class AlbumSelectionState(
    val loading: Boolean = true,
    val albums: List<Album> = emptyList(),
    val selectedAlbumId: Int = NO_ALBUM,
    val failed: Boolean = false,
) {
    val selectedAlbum: Album? get() = albums.firstOrNull { it.id == selectedAlbumId }
    val empty: Boolean get() = !loading && !failed && albums.isEmpty()

    /** El ID guardado ya no aparece en el catálogo cargado: hay que elegir otro. */
    val selectionMissing: Boolean get() = !loading && !failed && albums.isNotEmpty() && selectedAlbum == null

    companion object { const val NO_ALBUM = 0 }
}

/**
 * Obtiene los álbumes disponibles y registra cuál recibirá la canción.
 *
 * El ID elegido vive en [SavedStateHandle], así que sobrevive a la rotación y a la muerte del
 * proceso. [initialAlbumId] es el álbum desde cuyo detalle se abrió el flujo.
 */
class AlbumSelectionViewModel(
    initialAlbumId: Int,
    private val repository: AlbumRepository,
    private val handle: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        AlbumSelectionState(selectedAlbumId = handle[KEY_SELECTED_ALBUM] ?: initialAlbumId.coerceAtLeast(0)),
    )
    val state = mutableState.asStateFlow()
    private var fetching = false

    init { load() }

    /** ID que debe usar el envío (#34), o `null` si no hay un álbum válido elegido. */
    val selectedAlbumIdForSubmit: Int? get() = state.value.selectedAlbum?.id

    fun load() {
        if (fetching) return
        fetching = true
        mutableState.value = mutableState.value.copy(loading = true, failed = false)
        viewModelScope.launch {
            try {
                val albums = repository.getAlbums()
                mutableState.value = mutableState.value.copy(loading = false, albums = albums)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(loading = false, failed = true)
            } finally {
                fetching = false
            }
        }
    }

    /** Cambia el destino. Ignora IDs que no estén en el catálogo cargado. */
    fun select(albumId: Int) {
        if (mutableState.value.albums.none { it.id == albumId }) return
        handle[KEY_SELECTED_ALBUM] = albumId
        mutableState.value = mutableState.value.copy(selectedAlbumId = albumId)
    }

    companion object {
        const val KEY_SELECTED_ALBUM = "selectedAlbumId"
    }
}
