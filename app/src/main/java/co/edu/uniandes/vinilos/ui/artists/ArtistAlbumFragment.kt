package co.edu.uniandes.vinilos.ui.artists

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.VinilosApplication
import co.edu.uniandes.vinilos.databinding.FragmentArtistAlbumBinding
import co.edu.uniandes.vinilos.model.ArtistType
import co.edu.uniandes.vinilos.ui.albums.picker.AlbumOptionsRenderer
import kotlinx.coroutines.launch

/** HU15: agrega un álbum del catálogo a la discografía de un músico o una banda. */
class ArtistAlbumFragment : Fragment(R.layout.fragment_artist_album) {
    private val artistId: Int by lazy { requireArguments().getInt(ARG_ARTIST_ID).also { require(it > 0) } }
    private val artistType: ArtistType by lazy {
        requireNotNull(ArtistType.values().firstOrNull { it.name == requireArguments().getString(ARG_ARTIST_TYPE) })
    }
    private val model: ArtistAlbumViewModel by viewModels {
        object : AbstractSavedStateViewModelFactory(this, arguments) {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(key: String, modelClass: Class<T>, handle: SavedStateHandle): T {
                val app = requireActivity().application as VinilosApplication
                return ArtistAlbumViewModel(artistId, artistType, app.albumRepository, app.artistAlbumRepository, handle) as T
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentArtistAlbumBinding.bind(view)
        val app = requireActivity().application as VinilosApplication
        val artistName = requireArguments().getString(ARG_ARTIST_NAME).orEmpty()
        val typeLabel = getString(if (artistType == ArtistType.BAND) R.string.artist_band else R.string.artist_musician)
        binding.artistAlbumContext.text = if (artistName.isBlank()) typeLabel else getString(R.string.hu15_context, artistName, typeLabel)
        val picker = binding.artistAlbumPicker
        if (picker.albumPickerSearch.text.isNullOrEmpty() && model.state.value.query.isNotEmpty()) {
            picker.albumPickerSearch.setText(model.state.value.query)
        }
        picker.albumPickerSearch.doAfterTextChanged { model.setQuery(it?.toString().orEmpty()) }
        picker.albumPickerRetry.setOnClickListener { model.load() }
        binding.artistAlbumCancel.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.artistAlbumConfirm.setOnClickListener {
            val album = model.state.value.selected ?: return@setOnClickListener
            if (app.activeCollectorStore.active.value == null) return@setOnClickListener
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.hu15_confirm_title)
                .setMessage(getString(R.string.hu15_confirm_message, album.name, artistName.ifBlank { typeLabel }))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.hu15_add) { _, _ ->
                    if (app.activeCollectorStore.active.value != null) model.submit()
                }.show()
        }
        val renderer = AlbumOptionsRenderer(picker.albumPickerOptions) { model.select(it) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { state ->
                    if (state.saved) {
                        parentFragmentManager.setFragmentResult(
                            RESULT, bundleOf(ARG_ARTIST_ID to artistId, ALBUM_ID to state.selectedId),
                        )
                        parentFragmentManager.popBackStack()
                        return@collect
                    }
                    picker.albumPickerLoading.isVisible = state.loading
                    picker.albumPickerRetry.isVisible = state.failed
                    picker.albumPickerSearch.isEnabled = !state.loading && !state.failed && !state.submitting
                    val visible = state.visible
                    picker.albumPickerEmpty.isVisible = !state.loading && !state.failed && visible.isEmpty()
                    picker.albumPickerEmpty.setText(
                        if (state.available.isEmpty()) R.string.hu15_empty else R.string.album_picker_no_results,
                    )
                    picker.albumPickerOptions.isVisible = !state.loading && !state.failed
                    renderer.render(visible, state.selectedId, enabled = !state.submitting)
                    binding.artistAlbumSelected.isVisible = state.selected != null
                    binding.artistAlbumSelected.text = state.selected?.let { getString(R.string.hu15_selected, it.name) }.orEmpty()
                    binding.artistAlbumStatus.isVisible = state.submitting || state.submitFailed || state.unconfirmed
                    binding.artistAlbumStatus.setText(when {
                        state.submitting -> R.string.hu15_saving
                        state.unconfirmed -> R.string.hu15_unconfirmed
                        else -> R.string.hu15_save_failed
                    })
                    binding.artistAlbumCancel.isEnabled = !state.submitting
                    binding.artistAlbumConfirm.isEnabled = state.canSubmit
                }
            }
        }
    }

    companion object {
        const val RESULT = "hu15AlbumAdded"
        const val ARG_ARTIST_ID = "artistId"
        const val ALBUM_ID = "albumId"
        private const val ARG_ARTIST_TYPE = "artistType"
        private const val ARG_ARTIST_NAME = "artistName"
        fun newInstance(artistId: Int, artistType: String, artistName: String = "") = ArtistAlbumFragment().apply {
            arguments = bundleOf(ARG_ARTIST_ID to artistId, ARG_ARTIST_TYPE to artistType, ARG_ARTIST_NAME to artistName)
        }
    }
}
