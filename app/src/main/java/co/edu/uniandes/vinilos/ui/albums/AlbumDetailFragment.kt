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
import co.edu.uniandes.vinilos.ui.collectors.ActiveCollectorFragment
import co.edu.uniandes.vinilos.ui.comments.AlbumCommentsState
import co.edu.uniandes.vinilos.ui.comments.AlbumCommentsViewModel
import co.edu.uniandes.vinilos.ui.comments.CommentAlbumFragment
import co.edu.uniandes.vinilos.ui.tracks.AddTrackFragment
import co.edu.uniandes.vinilos.BuildConfig
import android.util.Log
import java.net.URI
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

    // HU09: comentarios del álbum, cargados aparte del detalle.
    private val commentsViewModel: AlbumCommentsViewModel by viewModels {
        AlbumCommentsViewModel.Factory(albumId, (requireActivity().application as VinilosApplication).commentRepository)
    }

    /** "+ Comentar" pidió elegir coleccionista (#80): al volver con uno se abre el formulario. */
    private var commentPending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        commentPending = savedInstanceState?.getBoolean(STATE_COMMENT_PENDING) ?: false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_COMMENT_PENDING, commentPending)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentAlbumDetailBinding.bind(view)
        bindComments(binding)
        binding.retryDetailButton.setOnClickListener { viewModel.refresh() }
        binding.addTrackButton.setOnClickListener {
            parentFragmentManager.setFragmentResult(OPEN_ADD_TRACK, bundleOf(ARG_ALBUM_ID to albumId))
        }
        // HU08 (#34): al volver de guardar se recargan las canciones; si se guardó en otro álbum, se abre ese.
        parentFragmentManager.setFragmentResultListener(AddTrackFragment.TRACK_SAVED, viewLifecycleOwner) { _, data ->
            val savedIn = data.getInt(AddTrackFragment.ALBUM_ID, -1)
            // Sin Toast: en API 21 su inflado falla con el tema de la app (ver CreateAlbumFragment).
            // La lista recargada es la confirmación visible; TalkBack la anuncia.
            data.getString(AddTrackFragment.TRACK_NAME)?.let { name ->
                view.announceForAccessibility(getString(R.string.add_track_saved, name))
            }
            when {
                savedIn == albumId -> viewModel.refresh()
                savedIn > 0 -> parentFragmentManager.beginTransaction()
                    .replace(id, newInstance(savedIn))
                    .addToBackStack("album_detail")
                    .commit()
            }
        }
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

    /** HU09 (#82, #83): sección de comentarios y acción "+ Comentar" con el coleccionista activo. */
    private fun bindComments(binding: FragmentAlbumDetailBinding) {
        binding.retryCommentsButton.setOnClickListener { commentsViewModel.refresh() }
        binding.addCommentButton.setOnClickListener {
            if ((requireActivity().application as VinilosApplication).activeCollectorStore.active.value != null) {
                openCommentForm()
            } else {
                commentPending = true
                parentFragmentManager.setFragmentResult(ActiveCollectorFragment.REQUEST, Bundle())
            }
        }
        parentFragmentManager.setFragmentResultListener(ActiveCollectorFragment.CHOSEN, viewLifecycleOwner) { _, _ ->
            if (commentPending) {
                commentPending = false
                openCommentForm()
            }
        }
        parentFragmentManager.setFragmentResultListener(CommentAlbumFragment.SAVED, viewLifecycleOwner) { _, data ->
            if (data.getInt(CommentAlbumFragment.ALBUM_ID, -1) != albumId) return@setFragmentResultListener
            commentsViewModel.refresh()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                commentsViewModel.state.collect { binding.renderComments(it) }
            }
        }
    }

    private fun openCommentForm() {
        parentFragmentManager.setFragmentResult(CommentAlbumFragment.OPEN, bundleOf(
            CommentAlbumFragment.ALBUM_ID to albumId,
            CommentAlbumFragment.ALBUM_NAME to viewModel.state.value.album?.name,
        ))
    }

    private fun FragmentAlbumDetailBinding.renderComments(state: AlbumCommentsState) {
        val status = when {
            state.loading && state.comments.isEmpty() -> getString(R.string.album_comments_loading)
            state.failed -> getString(R.string.album_comments_error)
            !state.loading && state.comments.isEmpty() -> getString(R.string.album_comments_empty)
            else -> null
        }
        detailCommentsStatus.isVisible = status != null
        detailCommentsStatus.text = status
        retryCommentsButton.isVisible = state.failed
        detailComments.isVisible = state.comments.isNotEmpty()
        detailComments.text = state.comments.joinToString("\n") {
            getString(R.string.album_comment_item, it.description, it.rating)
        }
    }

    private fun FragmentAlbumDetailBinding.render(album: AlbumDetail) {
        detailName.text = album.name
        detailCover.contentDescription = getString(R.string.album_cover, album.name)
        detailCover.load(album.cover) {
            addHeader("User-Agent", "VinilosAndroid/1.0 (https://github.com/Osaron/vinilos-android)")
            placeholder(R.drawable.ic_album)
            error(R.drawable.ic_album)
            listener(onError = { request, result ->
                if (BuildConfig.DEBUG) {
                    val host = runCatching { URI(request.data.toString()).host }.getOrNull()
                    Log.w("AlbumCover", "No se pudo cargar portada; host=$host; causa=${result.throwable.javaClass.simpleName}", result.throwable)
                }
            })
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
        private const val STATE_COMMENT_PENDING = "commentPending"

        /** HU08: abre el formulario con este álbum preseleccionado (clave `albumId`). */
        const val OPEN_ADD_TRACK = "open_add_track"
        fun newInstance(albumId: Int) = AlbumDetailFragment().apply {
            arguments = bundleOf(ARG_ALBUM_ID to albumId)
        }
    }
}
