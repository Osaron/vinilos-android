package co.edu.uniandes.vinilos.ui.tracks

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.VinilosApplication
import co.edu.uniandes.vinilos.databinding.FragmentAddTrackBinding
import co.edu.uniandes.vinilos.model.Album
import co.edu.uniandes.vinilos.model.NewTrack
import kotlinx.coroutines.launch

/**
 * Pantalla de HU08: selección del álbum (#31) y formulario de la canción (#32).
 *
 * Cuando el formulario es válido y hay un álbum elegido, publica [TRACK_READY] en el
 * `parentFragmentManager` con [ALBUM_ID], [TRACK_NAME] y [TRACK_DURATION]. El envío, sus estados
 * y el refresco del álbum pertenecen a #34.
 *
 * #34: este fragmento escucha [TRACK_READY] y envía con [AddTrackViewModel]. Mientras guarda
 * muestra el indicador y bloquea el botón y el cambio de álbum; ante un error conserva el
 * borrador y explica qué pasó. Al guardar vuelve al detalle y publica [TRACK_SAVED] con el
 * [ALBUM_ID] para que el detalle recargue sus canciones.
 */
class AddTrackFragment : Fragment(R.layout.fragment_add_track) {
    private val selection: AlbumSelectionViewModel by viewModels {
        viewModelFactory {
            initializer {
                AlbumSelectionViewModel(
                    requireArguments().getInt(ARG_ALBUM_ID),
                    (requireActivity().application as VinilosApplication).albumRepository,
                    createSavedStateHandle(),
                )
            }
        }
    }
    private val sending: AddTrackViewModel by viewModels {
        AddTrackViewModel.Factory((requireActivity().application as VinilosApplication).trackAssociationRepository)
    }
    private var dialog: AlertDialog? = null
    private var leaving = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentAddTrackBinding.bind(view)
        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.track_form_container, TrackFormFragment()).commitNow()
        }
        binding.changeAlbumButton.setOnClickListener { showAlbumChooser() }
        binding.retryAlbumsButton.setOnClickListener { selection.load() }
        binding.viewAlbumButton.setOnClickListener { sending.state.value.albumId?.let(::returnToAlbum) }
        parentFragmentManager.setFragmentResultListener(TRACK_READY, viewLifecycleOwner) { _, data ->
            sending.submit(
                data.getInt(ALBUM_ID),
                NewTrack(data.getString(TRACK_NAME).orEmpty(), data.getString(TRACK_DURATION).orEmpty()),
            )
        }
        childFragmentManager.setFragmentResultListener(TrackFormFragment.VALID_TRACK_RESULT, viewLifecycleOwner) { _, data ->
            val albumId = selection.selectedAlbumIdForSubmit
            if (albumId == null) {
                binding.albumStatus.isVisible = true
                binding.albumStatus.setText(R.string.add_track_choose_album_first)
                binding.changeAlbumButton.requestFocus()
                return@setFragmentResultListener
            }
            parentFragmentManager.setFragmentResult(TRACK_READY, bundleOf(
                ALBUM_ID to albumId,
                TRACK_NAME to data.getString(TrackFormFragment.TRACK_NAME),
                TRACK_DURATION to data.getString(TrackFormFragment.TRACK_DURATION),
            ))
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                selection.state.collect { binding.render(it) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sending.state.collect { binding.render(it) }
            }
        }
    }

    private fun FragmentAddTrackBinding.render(state: AddTrackState) {
        savingIndicator.isVisible = state.submitting
        changeAlbumButton.isEnabled = !state.submitting
        childFragmentManager.findFragmentById(R.id.track_form_container)?.view
            ?.findViewById<Button>(R.id.track_save_button)?.isEnabled = state.canSubmit
        val status = when (val result = state.result) {
            null -> if (state.submitting) getString(R.string.add_track_saving) else null
            is AddTrackResult.Saved -> getString(R.string.add_track_saved, result.track.name)
            AddTrackResult.Failed -> getString(R.string.add_track_save_error)
            AddTrackResult.AlbumMissing -> getString(R.string.add_track_save_album_missing)
            is AddTrackResult.PossiblySaved ->
                getString(R.string.add_track_possibly_saved, result.match.name, result.match.duration)
            AddTrackResult.Unconfirmed -> getString(R.string.add_track_unconfirmed)
        }
        trackStatus.isVisible = status != null
        trackStatus.text = status
        trackStatus.setTextColor(if (state.result == null || state.saved) NEUTRAL_COLOR else ERROR_COLOR)
        viewAlbumButton.isVisible = state.result is AddTrackResult.PossiblySaved || state.result is AddTrackResult.Unconfirmed
        when (val result = state.result) {
            is AddTrackResult.Saved -> returnToAlbum(result.albumId)
            // El álbum desapareció: se recarga la lista para que la selección pida elegir otro.
            AddTrackResult.AlbumMissing -> if (!selection.state.value.loading) selection.load()
            else -> Unit
        }
    }

    /** Vuelve al detalle (o al catálogo) y avisa qué álbum recargar. Solo una vez. */
    private fun returnToAlbum(albumId: Int) {
        if (leaving) return
        leaving = true
        val track = (sending.state.value.result as? AddTrackResult.Saved)?.track
        parentFragmentManager.setFragmentResult(TRACK_SAVED, bundleOf(ALBUM_ID to albumId, TRACK_NAME to track?.name))
        parentFragmentManager.popBackStack()
    }

    private fun FragmentAddTrackBinding.render(state: AlbumSelectionState) {
        albumLoading.isVisible = state.loading
        val album = state.selectedAlbum
        selectedAlbum.text = album?.let(::label) ?: getString(R.string.add_track_no_album_selected)
        changeAlbumButton.isVisible = !state.loading && !state.failed && !state.empty
        changeAlbumButton.setText(if (album == null) R.string.add_track_choose_album else R.string.add_track_change_album)
        retryAlbumsButton.isVisible = state.failed
        val status = when {
            state.loading -> R.string.add_track_loading_albums
            state.failed -> R.string.add_track_albums_error
            state.empty -> R.string.add_track_no_albums
            state.selectionMissing && state.selectedAlbumId != AlbumSelectionState.NO_ALBUM -> R.string.add_track_album_missing
            else -> null
        }
        albumStatus.isVisible = status != null
        status?.let(albumStatus::setText)
    }

    private fun showAlbumChooser() {
        val state = selection.state.value
        if (state.albums.isEmpty()) return
        val checked = state.albums.indexOfFirst { it.id == state.selectedAlbumId }
        dialog?.dismiss()
        dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.add_track_album_dialog_title)
            .setSingleChoiceItems(state.albums.map(::label).toTypedArray(), checked) { shown, which ->
                selection.select(state.albums[which].id)
                sending.dismissResult()
                shown.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun label(album: Album): String {
        val artist = album.performers.joinToString(", ")
        return if (artist.isBlank()) album.name else getString(R.string.add_track_album_with_artist, album.name, artist)
    }

    override fun onDestroyView() {
        dialog?.dismiss()
        dialog = null
        super.onDestroyView()
    }

    companion object {
        const val TRACK_READY = "track_ready_for_album"
        const val ALBUM_ID = "albumId"
        const val TRACK_NAME = TrackFormFragment.TRACK_NAME
        const val TRACK_DURATION = TrackFormFragment.TRACK_DURATION

        /** #34: canción guardada (o por revisar); lleva [ALBUM_ID] y, si se guardó, [TRACK_NAME]. */
        const val TRACK_SAVED = "track_saved_in_album"
        private const val NEUTRAL_COLOR = 0xFF222222.toInt()
        private const val ERROR_COLOR = 0xFFB00020.toInt()
        private const val ARG_ALBUM_ID = "albumId"

        /** [albumId] es el álbum preseleccionado; `0` abre el flujo sin selección. */
        fun newInstance(albumId: Int) = AddTrackFragment().apply {
            arguments = bundleOf(ARG_ALBUM_ID to albumId)
        }
    }
}
