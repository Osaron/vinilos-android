package co.edu.uniandes.vinilos.ui.collectors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.edu.uniandes.vinilos.data.CollectorRepository
import co.edu.uniandes.vinilos.model.Collector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.Locale

data class CollectorCatalogState(
    val loading: Boolean = true,
    val collectors: List<Collector> = emptyList(),
    val query: String = "",
    val failed: Boolean = false,
    val selected: Collector? = null,
) {
    val visibleCollectors: List<Collector> get() = collectors.filter { it.name.searchKey().contains(query.searchKey()) }
}
private fun String.searchKey() = Normalizer.normalize(trim(), Normalizer.Form.NFD)
    .replace("\\p{M}+".toRegex(), "").lowercase(Locale.ROOT)

class CollectorCatalogViewModel(private val repository: CollectorRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(CollectorCatalogState())
    val state = mutableState.asStateFlow()
    private var fetching = false
    init { refresh() }
    fun search(query: String) { mutableState.value = mutableState.value.copy(query = query) }
    fun select(id: Int) {
        val collector = mutableState.value.collectors.firstOrNull { it.id == id } ?: return
        mutableState.value = mutableState.value.copy(selected = collector)
    }
    fun refresh() {
        if (fetching) return
        fetching = true
        mutableState.value = mutableState.value.copy(loading = true, failed = false)
        viewModelScope.launch {
            try {
                val collectors = repository.getCollectors()
                val selected = mutableState.value.selected
                mutableState.value = mutableState.value.copy(loading = false, collectors = collectors,
                    selected = collectors.firstOrNull { it.id == selected?.id })
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) {
                mutableState.value = mutableState.value.copy(loading = false, failed = true)
            } finally { fetching = false }
        }
    }
    class Factory(private val repository: CollectorRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CollectorCatalogViewModel::class.java))
            return CollectorCatalogViewModel(repository) as T
        }
    }
}
