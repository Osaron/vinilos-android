package co.edu.uniandes.vinilos.ui.collectors

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.VinilosApplication
import co.edu.uniandes.vinilos.data.ActiveCollector
import co.edu.uniandes.vinilos.databinding.FragmentActiveCollectorBinding
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * "Seleccionar coleccionista" (#80), compartida por HU09 y HU10. Reutiliza el listado de HU05
 * ([CollectorCatalogViewModel] y [CollectorAdapter]) sin cambiarlo.
 *
 * Se abre publicando [REQUEST] en el `supportFragmentManager`. Al tocar un coleccionista se guarda
 * como activo, se vuelve a la pantalla anterior y se publica [CHOSEN] con [COLLECTOR_ID] y
 * [COLLECTOR_NAME], para que esa pantalla continúe la acción que pidió (comentar o agregar favorito).
 */
class ActiveCollectorFragment : Fragment(R.layout.fragment_active_collector) {
    private val app get() = requireActivity().application as VinilosApplication
    private val viewModel: CollectorCatalogViewModel by viewModels {
        CollectorCatalogViewModel.Factory(app.collectorRepository)
    }
    private var leaving = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentActiveCollectorBinding.bind(view)
        val store = app.activeCollectorStore
        val adapter = CollectorAdapter { collector -> choose(ActiveCollector(collector.id, collector.name)) }
        binding.activeCollectorList.layoutManager = LinearLayoutManager(requireContext())
        binding.activeCollectorList.adapter = adapter
        if (savedInstanceState == null) binding.searchActiveCollector.setText(viewModel.state.value.query)
        binding.searchActiveCollector.doAfterTextChanged { viewModel.search(it.toString()) }
        binding.retryActiveCollectors.setOnClickListener { viewModel.refresh() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.combine(store.active) { state, active -> state to active }.collect { (state, active) ->
                    val collectors = state.visibleCollectors
                    val ready = !state.loading && !state.failed
                    // Si el coleccionista guardado ya no existe en el backend, se olvida y se pide otro.
                    if (ready && active != null && state.collectors.none { it.id == active.id }) {
                        store.clear()
                    }
                    binding.currentActiveCollector.text = active?.let { getString(R.string.active_collector_current, it.name) }
                        ?: getString(R.string.active_collector_none)
                    binding.loadingActiveCollectors.isVisible = state.loading
                    binding.activeCollectorList.isVisible = ready && collectors.isNotEmpty()
                    binding.activeCollectorStatus.isVisible = !state.loading && (state.failed || collectors.isEmpty())
                    binding.retryActiveCollectors.isVisible = !state.loading && state.failed
                    binding.activeCollectorStatus.setText(when {
                        state.failed -> R.string.collectors_error
                        state.collectors.isEmpty() -> R.string.collectors_empty
                        else -> R.string.collectors_no_results
                    })
                    adapter.submitList(collectors)
                }
            }
        }
    }

    private fun choose(collector: ActiveCollector) {
        if (leaving) return
        leaving = true
        app.activeCollectorStore.choose(collector)
        view?.announceForAccessibility(getString(R.string.active_collector_current, collector.name))
        parentFragmentManager.setFragmentResult(CHOSEN, bundleOf(COLLECTOR_ID to collector.id, COLLECTOR_NAME to collector.name))
        if (!parentFragmentManager.isStateSaved) parentFragmentManager.popBackStack()
    }

    companion object {
        /** Pide abrir esta pantalla (lo atiende MainActivity). */
        const val REQUEST = "request_active_collector"

        /** El usuario eligió coleccionista; lleva [COLLECTOR_ID] y [COLLECTOR_NAME]. */
        const val CHOSEN = "active_collector_chosen"
        const val COLLECTOR_ID = "collectorId"
        const val COLLECTOR_NAME = "collectorName"
    }
}
