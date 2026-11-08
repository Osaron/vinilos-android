package co.edu.uniandes.vinilos.ui.collectors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.edu.uniandes.vinilos.data.CollectorDetailRepository
import co.edu.uniandes.vinilos.model.CollectorDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CollectorDetailState(
    val loading: Boolean = true,
    val detail: CollectorDetail? = null,
    val failed: Boolean = false,
)

class CollectorDetailViewModel(
    private val collectorId: Int,
    private val repository: CollectorDetailRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CollectorDetailState())
    val state: StateFlow<CollectorDetailState> = mutableState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            mutableState.value = CollectorDetailState(loading = true)
            try {
                mutableState.value = CollectorDetailState(loading = false, detail = repository.getCollectorDetail(collectorId))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = CollectorDetailState(loading = false, failed = true)
            }
        }
    }

    class Factory(private val collectorId: Int, private val repository: CollectorDetailRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CollectorDetailViewModel::class.java))
            return CollectorDetailViewModel(collectorId, repository) as T
        }
    }
}
