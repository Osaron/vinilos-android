package co.edu.uniandes.vinilos.ui.comments

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
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
import co.edu.uniandes.vinilos.databinding.FragmentCommentAlbumBinding
import co.edu.uniandes.vinilos.ui.collectors.ActiveCollectorFragment
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * HU09: comentar un álbum (#82, #83). Se abre publicando [OPEN] con [ALBUM_ID] (y opcionalmente
 * [ALBUM_NAME]). Al guardar vuelve al detalle y publica [SAVED] para que recargue los comentarios.
 */
class CommentAlbumFragment : Fragment(R.layout.fragment_comment_album) {
    private val app get() = requireActivity().application as VinilosApplication
    private val albumId: Int by lazy { requireArguments().getInt(ALBUM_ID).also { require(it > 0) } }
    private val viewModel: CommentAlbumViewModel by viewModels {
        viewModelFactory {
            initializer {
                CommentAlbumViewModel(albumId, app.commentRepository, app.activeCollectorStore.active, createSavedStateHandle())
            }
        }
    }
    private var leaving = false
    private val ratingButtons = mapOf(
        R.id.rating_1 to 1, R.id.rating_2 to 2, R.id.rating_3 to 3, R.id.rating_4 to 4, R.id.rating_5 to 5,
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentCommentAlbumBinding.bind(view)
        val albumName = requireArguments().getString(ALBUM_NAME)
        binding.commentAlbumContext.isVisible = !albumName.isNullOrBlank()
        binding.commentAlbumContext.text = albumName?.let { getString(R.string.comment_album_context, it) }
        if (savedInstanceState == null) {
            val state = viewModel.state.value
            binding.commentInput.setText(state.description)
            ratingButtons.entries.firstOrNull { it.value == state.rating }?.let { binding.ratingGroup.check(it.key) }
        }
        binding.commentInput.doAfterTextChanged { viewModel.updateDescription(it?.toString().orEmpty()) }
        binding.ratingGroup.setOnCheckedChangeListener { _, checkedId -> viewModel.updateRating(ratingButtons[checkedId]) }
        binding.changeCommentCollector.setOnClickListener {
            hideKeyboard()
            parentFragmentManager.setFragmentResult(ActiveCollectorFragment.REQUEST, Bundle())
        }
        parentFragmentManager.setFragmentResultListener(ActiveCollectorFragment.CHOSEN, viewLifecycleOwner) { _, _ ->
            viewModel.dismissResult()
        }
        binding.publishCommentButton.setOnClickListener {
            hideKeyboard()
            viewModel.submit()
        }
        binding.cancelCommentButton.setOnClickListener {
            hideKeyboard()
            if (!parentFragmentManager.isStateSaved) parentFragmentManager.popBackStack()
        }
        binding.viewCommentedAlbumButton.setOnClickListener { returnToAlbum(saved = false) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.combine(app.activeCollectorStore.active) { state, active -> state to active }
                    .collect { (state, active) -> binding.render(state, active?.name) }
            }
        }
    }

    private fun FragmentCommentAlbumBinding.render(state: CommentFormState, collectorName: String?) {
        commentCollector.text = collectorName?.let { getString(R.string.comment_as, it) } ?: getString(R.string.comment_no_collector)
        changeCommentCollector.setText(if (collectorName == null) R.string.comment_choose_collector else R.string.comment_change_collector)
        commentCollectorError.isVisible = state.errors.collector && collectorName == null
        commentDescriptionError.isVisible = state.errors.description
        commentRatingError.isVisible = state.errors.rating
        commentInput.isEnabled = !state.submitting
        ratingButtons.keys.forEach { root.findViewById<View>(it).isEnabled = !state.submitting }
        changeCommentCollector.isEnabled = !state.submitting
        publishCommentButton.isEnabled = state.canSubmit
        cancelCommentButton.isEnabled = !state.submitting
        val status = when (val result = state.result) {
            null -> if (state.submitting) getString(R.string.comment_publishing) else null
            is CommentResult.Saved -> getString(R.string.comment_saved)
            CommentResult.Failed -> getString(R.string.comment_error)
            CommentResult.Rejected -> getString(R.string.comment_rejected)
            CommentResult.NotFound -> getString(R.string.comment_not_found)
            is CommentResult.PossiblySaved -> getString(R.string.comment_possibly_saved)
            CommentResult.Unconfirmed -> getString(R.string.comment_unconfirmed)
        }
        commentStatus.isVisible = status != null
        commentStatus.text = status
        commentStatus.setTextColor(if (state.result == null || state.saved) NEUTRAL_COLOR else ERROR_COLOR)
        viewCommentedAlbumButton.isVisible = state.result is CommentResult.PossiblySaved || state.result is CommentResult.Unconfirmed
        if (state.result is CommentResult.Saved) returnToAlbum(saved = true)
    }

    /** Vuelve al detalle y le pide recargar los comentarios. Solo una vez. */
    private fun returnToAlbum(saved: Boolean) {
        if (leaving) return
        leaving = true
        // Sin Toast: en API 21 su inflado falla con el tema de la app. TalkBack anuncia el resultado.
        if (saved) view?.announceForAccessibility(getString(R.string.comment_saved))
        parentFragmentManager.setFragmentResult(SAVED, bundleOf(ALBUM_ID to albumId, WAS_SAVED to saved))
        if (!parentFragmentManager.isStateSaved) parentFragmentManager.popBackStack()
    }

    private fun hideKeyboard() {
        val focused = view?.findFocus() ?: return
        (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(focused.windowToken, 0)
    }

    companion object {
        /** Pide abrir el formulario (lo atiende MainActivity). Lleva [ALBUM_ID] y [ALBUM_NAME]. */
        const val OPEN = "open_comment_album"

        /** Se volvió del formulario; lleva [ALBUM_ID] y [WAS_SAVED]. */
        const val SAVED = "comment_album_saved"
        const val ALBUM_ID = "albumId"
        const val ALBUM_NAME = "albumName"
        const val WAS_SAVED = "wasSaved"
        private const val NEUTRAL_COLOR = 0xFF222222.toInt()
        private const val ERROR_COLOR = 0xFFB00020.toInt()

        fun newInstance(albumId: Int, albumName: String?) = CommentAlbumFragment().apply {
            arguments = bundleOf(ALBUM_ID to albumId, ALBUM_NAME to albumName)
        }
    }
}
