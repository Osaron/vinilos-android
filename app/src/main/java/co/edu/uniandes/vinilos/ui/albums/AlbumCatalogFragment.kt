package co.edu.uniandes.vinilos.ui.albums

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
import androidx.recyclerview.widget.GridLayoutManager
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.VinilosApplication
import co.edu.uniandes.vinilos.databinding.FragmentAlbumCatalogBinding
import kotlinx.coroutines.launch

class AlbumCatalogFragment : Fragment(R.layout.fragment_album_catalog) {
    private val viewModel: AlbumCatalogViewModel by viewModels {
        AlbumCatalogViewModel.Factory((requireActivity().application as VinilosApplication).albumRepository)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentAlbumCatalogBinding.bind(view)
        val albumAdapter = AlbumAdapter { album ->
            parentFragmentManager.setFragmentResult(ALBUM_SELECTED,
                bundleOf("albumId" to album.id, "albumName" to album.name))
        }
        binding.albumList.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.albumList.adapter = albumAdapter
        binding.searchAlbums.setText(viewModel.state.value.query)
        binding.searchAlbums.doAfterTextChanged { viewModel.search(it.toString()) }
        binding.retryButton.setOnClickListener { viewModel.refresh() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    val albums = state.visibleAlbums
                    binding.loadingIndicator.isVisible = state.loading
                    binding.albumList.isVisible = !state.loading && !state.failed && albums.isNotEmpty()
                    binding.statusMessage.isVisible = !state.loading && (state.failed || albums.isEmpty())
                    binding.retryButton.isVisible = !state.loading && state.failed
                    binding.statusMessage.setText(when {
                        state.failed -> R.string.catalog_error
                        state.albums.isEmpty() -> R.string.catalog_empty
                        else -> R.string.no_search_results
                    })
                    binding.albumCount.text = resources.getQuantityString(R.plurals.album_count, albums.size, albums.size)
                    albumAdapter.submitList(albums)
                }
            }
        }
    }

    companion object { const val ALBUM_SELECTED = "album_selected" }
}
