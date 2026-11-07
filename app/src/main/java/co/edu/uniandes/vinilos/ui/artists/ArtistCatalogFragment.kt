package co.edu.uniandes.vinilos.ui.artists

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
import co.edu.uniandes.vinilos.databinding.FragmentArtistCatalogBinding
import kotlinx.coroutines.launch

class ArtistCatalogFragment : Fragment(R.layout.fragment_artist_catalog) {
    private val viewModel: ArtistCatalogViewModel by viewModels {
        ArtistCatalogViewModel.Factory((requireActivity().application as VinilosApplication).artistRepository)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentArtistCatalogBinding.bind(view)
        val adapter = ArtistAdapter { artist ->
            viewModel.select(artist.id, artist.type)
            parentFragmentManager.setFragmentResult(ARTIST_SELECTED, bundleOf(
                "artistId" to artist.id, "artistType" to artist.type.name, "artistName" to artist.name))
        }
        binding.artistList.layoutManager = LinearLayoutManager(requireContext())
        binding.artistList.adapter = adapter
        binding.searchArtists.setText(viewModel.state.value.query)
        binding.searchArtists.doAfterTextChanged { viewModel.search(it.toString()) }
        binding.retryArtists.setOnClickListener { viewModel.refresh() }
        binding.backToAlbums.setOnClickListener { parentFragmentManager.popBackStack() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    val artists = state.visibleArtists
                    binding.loadingArtists.isVisible = state.loading
                    binding.artistList.isVisible = !state.loading && !state.failed && artists.isNotEmpty()
                    binding.artistStatus.isVisible = !state.loading && (state.failed || artists.isEmpty())
                    binding.retryArtists.isVisible = !state.loading && state.failed
                    binding.artistStatus.setText(when {
                        state.failed -> R.string.artists_error
                        state.artists.isEmpty() -> R.string.artists_empty
                        else -> R.string.artists_no_results
                    })
                    binding.artistCount.text = resources.getQuantityString(R.plurals.artist_count, artists.size, artists.size)
                    binding.artistSelection.isVisible = !state.loading && !state.failed && state.selected != null
                    binding.artistSelection.text = state.selected?.let { getString(R.string.artist_selected, it.name) }.orEmpty()
                    adapter.submitList(artists)
                }
            }
        }
    }
    companion object { const val ARTIST_SELECTED = "artist_selected" }
}
