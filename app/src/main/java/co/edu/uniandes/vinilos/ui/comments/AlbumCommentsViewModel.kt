package co.edu.uniandes.vinilos.ui.comments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.edu.uniandes.vinilos.data.CommentRepository
import co.edu.uniandes.vinilos.model.AlbumComment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AlbumCommentsState(
    val loading: Boolean = true,
    val comments: List<AlbumComment> = emptyList(),
    val failed: Boolean = false,
)

/**
 * Comentarios del detalle del álbum (HU09). Se cargan aparte del detalle (HU02), así un fallo en
 * los comentarios no oculta la información del álbum, y se recargan al volver de comentar.
 */
class AlbumCommentsViewModel(private val albumId: Int, private val repository: CommentRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(AlbumCommentsState())
    val state = mutableState.asStateFlow()
    private var fetching = false

    init { refresh() }

    fun refresh() {
        if (fetching) return
        fetching = true
        mutableState.value = mutableState.value.copy(loading = true, failed = false)
        viewModelScope.launch {
            try {
                mutableState.value = AlbumCommentsState(loading = false, comments = repository.getComments(albumId))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(loading = false, failed = true)
            } finally {
                fetching = false
            }
        }
    }

    class Factory(private val albumId: Int, private val repository: CommentRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AlbumCommentsViewModel(albumId, repository) as T
    }
}
