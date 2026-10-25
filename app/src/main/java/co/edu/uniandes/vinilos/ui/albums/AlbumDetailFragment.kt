package co.edu.uniandes.vinilos.ui.albums

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.VinilosApplication
import co.edu.uniandes.vinilos.databinding.FragmentAlbumDetailBinding
import co.edu.uniandes.vinilos.model.AlbumDetail
import coil.load
import kotlinx.coroutines.launch

class AlbumDetailFragment : Fragment(R.layout.fragment_album_detail) {
    private val albumId: Int by lazy {
        requireArguments().getInt(ARG_ALBUM_ID).also { require(it > 0) }
    }
    private val viewModel: AlbumDetailViewModel by viewModels {
        AlbumDetailViewModel.Factory(
            albumId,
            (requireActivity().application as VinilosApplication).albumRepository,
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentAlbumDetailBinding.bind(view)
        binding.retryDetailButton.setOnClickListener { viewModel.refresh() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    binding.detailLoading.isVisible = state.loading
                    binding.detailContent.isVisible = !state.loading && state.album != null
                    binding.detailErrorGroup.isVisible = !state.loading && state.failed
                    state.album?.let { binding.render(it) }
                }
            }
        }
    }

    private fun FragmentAlbumDetailBinding.render(album: AlbumDetail) {
        detailName.text = album.name
        detailCover.contentDescription = getString(R.string.album_cover, album.name)
        detailCover.load(album.cover) {
            addHeader("User-Agent", "VinilosAndroid/1.0 (https://github.com/Osaron/vinilos-android)")
            placeholder(R.drawable.ic_album)
            error(R.drawable.ic_album)
        }
        detailPerformers.text = album.performers.joinToString(", ")
            .ifBlank { getString(R.string.unknown_artist) }
        detailGenre.text = getString(
            R.string.genre_value,
            album.genre.ifBlank { getString(R.string.not_available) },
        )
        detailRecordLabel.text = getString(
            R.string.record_label_value,
            album.recordLabel.ifBlank { getString(R.string.not_available) },
        )
        detailReleaseDate.text = getString(
            R.string.release_date_value,
            album.releaseDate.take(10).ifBlank { getString(R.string.not_available) },
        )
        detailDescription.text = album.description.ifBlank { getString(R.string.no_description) }
        detailTracks.text = if (album.tracks.isEmpty()) {
            getString(R.string.no_tracks)
        } else {
            album.tracks.joinToString("\n") { track ->
                if (track.duration.isBlank()) "• ${track.name}"
                else "• ${track.name} (${track.duration})"
            }
        }
    }

    companion object {
        private const val ARG_ALBUM_ID = "albumId"
        fun newInstance(albumId: Int) = AlbumDetailFragment().apply {
            arguments = bundleOf(ARG_ALBUM_ID to albumId)
        }
    }
}
