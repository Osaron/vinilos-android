package co.edu.uniandes.vinilos.ui.artists

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.VinilosApplication
import co.edu.uniandes.vinilos.databinding.FragmentArtistDetailBinding
import co.edu.uniandes.vinilos.model.ArtistAlbum
import co.edu.uniandes.vinilos.model.ArtistDetail
import co.edu.uniandes.vinilos.model.ArtistType
import coil.load
import coil.transform.CircleCropTransformation
import kotlinx.coroutines.launch

class ArtistDetailFragment : Fragment(R.layout.fragment_artist_detail) {
    private val artistId: Int by lazy { requireArguments().getInt(ARG_ARTIST_ID).also { require(it > 0) } }
    private val artistType: ArtistType by lazy {
        requireNotNull(ArtistType.values().firstOrNull { it.name == requireArguments().getString(ARG_ARTIST_TYPE) })
    }
    private val viewModel: ArtistDetailViewModel by viewModels {
        ArtistDetailViewModel.Factory(
            artistId,
            artistType,
            (requireActivity().application as VinilosApplication).artistDetailRepository,
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentArtistDetailBinding.bind(view)
        binding.retryArtistDetail.setOnClickListener { viewModel.refresh() }
        binding.backArtistDetail.setOnClickListener { parentFragmentManager.popBackStack() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    binding.artistDetailLoading.isVisible = state.loading
                    binding.artistDetailContent.isVisible = !state.loading && state.detail != null
                    binding.artistDetailError.isVisible = !state.loading && state.failed
                    state.detail?.let { detail -> binding.render(detail) }
                }
            }
        }
    }

    private fun FragmentArtistDetailBinding.render(detail: ArtistDetail) {
        artistDetailTitle.text = detail.name
        artistDetailImage.contentDescription = getString(R.string.artist_image, detail.name)
        artistDetailImage.load(detail.image) {
            addHeader("User-Agent", "VinilosAndroid/1.0 (https://github.com/Osaron/vinilos-android)")
            placeholder(R.drawable.ic_artist)
            error(R.drawable.ic_artist)
            transformations(CircleCropTransformation())
        }
        artistDetailType.text = getString(
            if (detail.type == ArtistType.BAND) R.string.artist_band else R.string.artist_musician,
        )
        val date = if (detail.type == ArtistType.BAND) detail.creationDate else detail.birthDate
        artistDetailDate.isVisible = !date.isNullOrBlank()
        artistDetailDate.text = date?.take(10).orEmpty()
        artistDetailBiography.text = detail.description.ifBlank { getString(R.string.no_description) }
        artistAlbumCount.text = resources.getQuantityString(
            R.plurals.artist_detail_album_count,
            detail.albums.size,
            detail.albums.size,
        )
        artistDetailAlbums.removeAllViews()
        if (detail.albums.isEmpty()) {
            artistDetailAlbums.addView(TextView(requireContext()).apply {
                setText(R.string.artist_detail_no_albums)
                setPadding(0, resources.getDimensionPixelSize(R.dimen.detail_album_spacing), 0, 0)
            })
        } else {
            detail.albums.forEach { album -> artistDetailAlbums.addView(albumRow(album, artistDetailAlbums)) }
        }
    }

    private fun albumRow(album: ArtistAlbum, parent: LinearLayout): View = layoutInflater.inflate(
        R.layout.item_artist_detail_album,
        parent,
        false,
    ).apply {
        findViewById<TextView>(R.id.artist_detail_album_name).text = album.name
        findViewById<TextView>(R.id.artist_detail_album_metadata).text = listOfNotNull(
            album.releaseDate?.take(10)?.takeIf(String::isNotBlank),
            album.recordLabel?.takeIf(String::isNotBlank),
        ).joinToString(" · ").ifBlank { getString(R.string.not_available) }
        findViewById<ImageView>(R.id.artist_detail_album_cover).apply {
            contentDescription = getString(R.string.album_cover, album.name)
            load(album.cover) {
                addHeader("User-Agent", "VinilosAndroid/1.0 (https://github.com/Osaron/vinilos-android)")
                placeholder(R.drawable.ic_album)
                error(R.drawable.ic_album)
            }
        }
    }

    companion object {
        private const val ARG_ARTIST_ID = "artistId"
        private const val ARG_ARTIST_TYPE = "artistType"

        fun newInstance(artistId: Int, artistType: String) = ArtistDetailFragment().apply {
            arguments = bundleOf(ARG_ARTIST_ID to artistId, ARG_ARTIST_TYPE to artistType)
        }
    }
}
