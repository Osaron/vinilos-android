package co.edu.uniandes.vinilos.ui.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.edu.uniandes.vinilos.data.AlbumRepository
import co.edu.uniandes.vinilos.model.AlbumDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AlbumDetailState(
    val loading: Boolean = true,
    val album: AlbumDetail? = null,
    val failed: Boolean = false,
)

class AlbumDetailViewModel(
    private val albumId: Int,
    private val repository: AlbumRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AlbumDetailState())
    val state = mutableState.asStateFlow()
    private var fetching = false

    init { refresh() }

    fun refresh() {
        if (fetching) return
        fetching = true
        mutableState.value = AlbumDetailState(loading = true)
        viewModelScope.launch {
            try {
                val album = repository.getAlbum(albumId)
                mutableState.value = AlbumDetailState(loading = false, album = album)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = AlbumDetailState(loading = false, failed = true)
            } finally {
                fetching = false
            }
        }
    }

    class Factory(
        private val albumId: Int,
        private val repository: AlbumRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AlbumDetailViewModel::class.java))
            return AlbumDetailViewModel(albumId, repository) as T
        }
    }
}
