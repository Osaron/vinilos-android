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
import co.edu.uniandes.vinilos.ui.bands.MusicianSelectionFragment
import co.edu.uniandes.vinilos.ui.collectors.ActiveCollectorFragment
import co.edu.uniandes.vinilos.ui.collectors.CollectorCatalogFragment
import co.edu.uniandes.vinilos.ui.favorites.FavoriteArtistState
import co.edu.uniandes.vinilos.ui.favorites.FavoriteArtistViewModel
import co.edu.uniandes.vinilos.ui.favorites.FavoriteResult
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
            (requireActivity().application as VinilosApplication).bandMusicianRepository,
        )
    }

    // HU10: favoritos del coleccionista activo (#80).
    private val favoriteViewModel: FavoriteArtistViewModel by viewModels {
        val app = requireActivity().application as VinilosApplication
        FavoriteArtistViewModel.Factory(artistId, artistType, app.favoriteArtistRepository, app.activeCollectorStore.active)
    }

    /** "Agregar a favoritos" pidió elegir coleccionista: al volver con uno se agrega. */
    private var favoritePending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        favoritePending = savedInstanceState?.getBoolean(STATE_FAVORITE_PENDING) ?: false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_FAVORITE_PENDING, favoritePending)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentArtistDetailBinding.bind(view)
        val app = requireActivity().application as VinilosApplication
        parentFragmentManager.setFragmentResultListener(MusicianSelectionFragment.RESULT, viewLifecycleOwner) { _, data ->
            if (data.getInt(MusicianSelectionFragment.ARG_BAND_ID) == artistId) {
                binding.bandAssociationStatus.isVisible = true
                viewModel.refresh()
            }
        }
        binding.addBandMusician.setOnClickListener {
            if (artistType == ArtistType.BAND && app.activeCollectorStore.active.value != null &&
                parentFragmentManager.findFragmentById(R.id.fragment_container) is ArtistDetailFragment) {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, MusicianSelectionFragment.newInstance(artistId, viewModel.state.value.detail?.name.orEmpty()))
                    .addToBackStack("hu12_musician").commit()
                parentFragmentManager.executePendingTransactions()
            }
        }
        bindFavorite(binding)
        binding.retryArtistDetail.setOnClickListener { viewModel.refresh() }
        binding.backArtistDetail.setOnClickListener { parentFragmentManager.popBackStack() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    app.activeCollectorStore.active.collect { collector ->
                        binding.addBandMusician.isVisible = artistType == ArtistType.BAND && collector != null
                    }
                }
                viewModel.state.collect { state ->
                    binding.artistDetailLoading.isVisible = state.loading
                    binding.artistDetailContent.isVisible = !state.loading && state.detail != null
                    binding.artistDetailError.isVisible = !state.loading && state.failed
                    state.detail?.let { detail -> binding.render(detail) }
                    binding.bandMembersSection.isVisible = artistType == ArtistType.BAND
                    binding.bandMembers.text = when {
                        state.membersFailed -> getString(R.string.hu12_members_failed)
                        state.members.isEmpty() -> getString(R.string.hu12_no_members)
                        else -> state.members.joinToString("\n") { it.name }
                    }
                }
            }
        }
    }

    /** HU10 (#87, #88, #89): agregar a favoritos y ver los favoritos del coleccionista. */
    private fun bindFavorite(binding: FragmentArtistDetailBinding) {
        binding.addFavoriteButton.setOnClickListener {
            if (!favoriteViewModel.add()) requestCollector(pending = true)
        }
        binding.changeFavoriteCollector.setOnClickListener { requestCollector(pending = false) }
        binding.viewMyFavoritesButton.setOnClickListener {
            favoriteViewModel.state.value.collector?.let { collector ->
                parentFragmentManager.setFragmentResult(CollectorCatalogFragment.OPEN_DETAIL, bundleOf("collectorId" to collector.id))
            }
        }
        parentFragmentManager.setFragmentResultListener(ActiveCollectorFragment.CHOSEN, viewLifecycleOwner) { _, _ ->
            if (favoritePending) {
                favoritePending = false
                favoriteViewModel.add()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                favoriteViewModel.state.collect { binding.renderFavorite(it) }
            }
        }
    }

    private fun requestCollector(pending: Boolean) {
        favoritePending = pending
        parentFragmentManager.setFragmentResult(ActiveCollectorFragment.REQUEST, Bundle())
    }

    private fun FragmentArtistDetailBinding.renderFavorite(state: FavoriteArtistState) {
        val name = state.collector?.name
        addFavoriteButton.setText(if (state.isFavorite == true) R.string.favorite_already else R.string.favorite_add)
        addFavoriteButton.isEnabled = state.collector == null || state.canAdd
        changeFavoriteCollector.setText(if (name == null) R.string.favorite_choose_collector else R.string.favorite_change_collector)
        changeFavoriteCollector.isEnabled = !state.submitting
        favoriteStatus.text = when (val result = state.result) {
            is FavoriteResult.Added -> getString(R.string.favorite_added, result.collector.name)
            FavoriteResult.Failed -> getString(R.string.favorite_error)
            FavoriteResult.NotFound -> getString(R.string.favorite_not_found)
            FavoriteResult.Unconfirmed -> getString(R.string.favorite_unconfirmed)
            null -> when {
                name == null -> getString(R.string.favorite_no_collector)
                state.submitting -> getString(R.string.favorite_adding)
                state.checking -> getString(R.string.favorite_checking, name)
                state.isFavorite == true -> getString(R.string.favorite_is_favorite, name)
                else -> getString(R.string.favorite_collector, name)
            }
        }
        viewMyFavoritesButton.isVisible = name != null && (state.isFavorite == true || state.result == FavoriteResult.Unconfirmed)
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
        val metadata = listOfNotNull(
            album.releaseDate?.take(10)?.takeIf(String::isNotBlank),
            album.recordLabel?.takeIf(String::isNotBlank),
        ).joinToString(" · ").ifBlank { getString(R.string.not_available) }
        findViewById<TextView>(R.id.artist_detail_album_name).text = album.name
        findViewById<TextView>(R.id.artist_detail_album_metadata).text = metadata
        // TalkBack lee la fila completa ("Buscando América, 1984-08-01 · Elektra"): por separado, dos álbumes
        // con la misma fecha y sello se anunciaban igual y sin decir de cuál se trataba.
        isFocusable = true
        contentDescription = getString(R.string.artist_album_row, album.name, metadata)
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
        private const val STATE_FAVORITE_PENDING = "favoritePending"

        fun newInstance(artistId: Int, artistType: String) = ArtistDetailFragment().apply {
            arguments = bundleOf(ARG_ARTIST_ID to artistId, ARG_ARTIST_TYPE to artistType)
        }
    }
}
