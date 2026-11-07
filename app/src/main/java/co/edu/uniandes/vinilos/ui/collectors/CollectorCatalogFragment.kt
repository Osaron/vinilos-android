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
import co.edu.uniandes.vinilos.databinding.FragmentCollectorCatalogBinding
import kotlinx.coroutines.launch

class CollectorCatalogFragment : Fragment(R.layout.fragment_collector_catalog) {
    private val viewModel: CollectorCatalogViewModel by viewModels {
        CollectorCatalogViewModel.Factory((requireActivity().application as VinilosApplication).collectorRepository)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentCollectorCatalogBinding.bind(view)
        val adapter = CollectorAdapter { collector ->
            viewModel.select(collector.id)
            parentFragmentManager.setFragmentResult(COLLECTOR_SELECTED, bundleOf(
                "collectorId" to collector.id, "collectorName" to collector.name))
        }
        binding.collectorList.layoutManager = LinearLayoutManager(requireContext())
        binding.collectorList.adapter = adapter
        binding.searchCollectors.setText(viewModel.state.value.query)
        binding.searchCollectors.doAfterTextChanged { viewModel.search(it.toString()) }
        binding.retryCollectors.setOnClickListener { viewModel.refresh() }
        binding.backToAlbums.setOnClickListener { parentFragmentManager.popBackStack() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    val collectors = state.visibleCollectors
                    binding.loadingCollectors.isVisible = state.loading
                    binding.collectorList.isVisible = !state.loading && !state.failed && collectors.isNotEmpty()
                    binding.collectorStatus.isVisible = !state.loading && (state.failed || collectors.isEmpty())
                    binding.retryCollectors.isVisible = !state.loading && state.failed
                    binding.collectorStatus.setText(when {
                        state.failed -> R.string.collectors_error
                        state.collectors.isEmpty() -> R.string.collectors_empty
                        else -> R.string.collectors_no_results
                    })
                    binding.collectorCount.text = resources.getQuantityString(R.plurals.collector_count, collectors.size, collectors.size)
                    binding.collectorSelection.isVisible = !state.loading && !state.failed && state.selected != null
                    binding.collectorSelection.text = state.selected?.let { getString(R.string.collector_selected, it.name) }.orEmpty()
                    adapter.submitList(collectors)
                }
            }
        }
    }
    companion object { const val COLLECTOR_SELECTED = "collector_selected" }
}
