package co.edu.uniandes.vinilos.ui.artists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.edu.uniandes.vinilos.data.ArtistDetailRepository
import co.edu.uniandes.vinilos.model.ArtistDetail
import co.edu.uniandes.vinilos.model.ArtistType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ArtistDetailState(
    val loading: Boolean = true,
    val detail: ArtistDetail? = null,
    val failed: Boolean = false,
)

class ArtistDetailViewModel(
    private val artistId: Int,
    private val artistType: ArtistType,
    private val repository: ArtistDetailRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ArtistDetailState())
    val state = mutableState.asStateFlow()
    private var fetching = false

    init {
        require(artistId > 0) { "Identificador de artista inválido" }
        refresh()
    }

    fun refresh() {
        if (fetching) return
        fetching = true
        mutableState.value = ArtistDetailState(loading = true)
        viewModelScope.launch {
            try {
                val detail = repository.getArtistDetail(artistType, artistId)
                mutableState.value = ArtistDetailState(loading = false, detail = detail)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = ArtistDetailState(loading = false, failed = true)
            } finally {
                fetching = false
            }
        }
    }

    class Factory(
        private val artistId: Int,
        private val artistType: ArtistType,
        private val repository: ArtistDetailRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ArtistDetailViewModel::class.java))
            return ArtistDetailViewModel(artistId, artistType, repository) as T
        }
    }
}
