package co.edu.uniandes.vinilos.ui.artists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.edu.uniandes.vinilos.data.ArtistRepository
import co.edu.uniandes.vinilos.model.Artist
import co.edu.uniandes.vinilos.model.ArtistType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.Locale

data class ArtistCatalogState(
    val loading: Boolean = true,
    val artists: List<Artist> = emptyList(),
    val query: String = "",
    val failed: Boolean = false,
    val selected: Artist? = null,
) {
    val visibleArtists: List<Artist> get() = artists.filter { it.name.searchKey().contains(query.searchKey()) }
}
private fun String.searchKey() = Normalizer.normalize(trim(), Normalizer.Form.NFD)
    .replace("\\p{M}+".toRegex(), "").lowercase(Locale.ROOT)

class ArtistCatalogViewModel(private val repository: ArtistRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(ArtistCatalogState())
    val state = mutableState.asStateFlow()
    private var fetching = false
    init { refresh() }
    fun search(query: String) { mutableState.value = mutableState.value.copy(query = query) }
    fun select(id: Int, type: ArtistType) {
        val artist = mutableState.value.artists.firstOrNull { it.id == id && it.type == type } ?: return
        mutableState.value = mutableState.value.copy(selected = artist)
    }
    fun refresh() {
        if (fetching) return
        fetching = true
        mutableState.value = mutableState.value.copy(loading = true, failed = false)
        viewModelScope.launch {
            try {
                val artists = repository.getArtists()
                val selected = mutableState.value.selected
                mutableState.value = mutableState.value.copy(loading = false, artists = artists,
                    selected = artists.firstOrNull { it.id == selected?.id && it.type == selected?.type })
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(loading = false, failed = true)
            } finally { fetching = false }
        }
    }
    class Factory(private val repository: ArtistRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ArtistCatalogViewModel::class.java))
            return ArtistCatalogViewModel(repository) as T
        }
    }
}
