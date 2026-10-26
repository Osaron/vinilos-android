package co.edu.uniandes.vinilos.ui.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.edu.uniandes.vinilos.data.AlbumRepository
import co.edu.uniandes.vinilos.model.Album
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.Normalizer

data class AlbumCatalogState(
    val loading: Boolean = true,
    val albums: List<Album> = emptyList(),
    val query: String = "",
    val failed: Boolean = false,
) {
    val visibleAlbums: List<Album> get() = albums.filter {
        (it.name + " " + it.performers.joinToString(" ")).searchKey().contains(query.searchKey())
    }
}

private fun String.searchKey() = Normalizer.normalize(trim(), Normalizer.Form.NFD)
    .replace("\\p{M}+".toRegex(), "").lowercase(java.util.Locale.ROOT)

class AlbumCatalogViewModel(private val repository: AlbumRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(AlbumCatalogState())
    val state = mutableState.asStateFlow()
    private var fetching = false

    init { refresh() }

    fun search(query: String) { mutableState.value = mutableState.value.copy(query = query) }

    fun refresh() {
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
            } finally { fetching = false }
        }
    }

    class Factory(private val repository: AlbumRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AlbumCatalogViewModel::class.java))
            return AlbumCatalogViewModel(repository) as T
        }
    }
}
