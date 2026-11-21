package co.edu.uniandes.vinilos.ui.bands

import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
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
import co.edu.uniandes.vinilos.databinding.FragmentMusicianSelectionBinding
import kotlinx.coroutines.launch

/** Selector y guardado de HU12. Publica la asociación confirmada mediante FragmentResult. */
class MusicianSelectionFragment : Fragment(R.layout.fragment_musician_selection) {
    private val bandId: Int by lazy { requireArguments().getInt(ARG_BAND_ID).also { require(it > 0) } }
    private val model: MusicianSelectionViewModel by viewModels {
        object : AbstractSavedStateViewModelFactory(this, arguments) {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(key: String, modelClass: Class<T>, handle: SavedStateHandle): T {
                val app = requireActivity().application as VinilosApplication
                return MusicianSelectionViewModel(bandId, app.artistRepository, app.bandMusicianRepository, handle) as T
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentMusicianSelectionBinding.bind(view)
        val bandName = requireArguments().getString(ARG_BAND_NAME).orEmpty()
        binding.musicianSelectionBand.text = if (bandName.isBlank()) {
            getString(R.string.hu12_band_id, bandId)
        } else {
            getString(R.string.hu12_band_context, bandName, bandId)
        }
        binding.musicianSelectionCancel.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.musicianSelectionRetry.setOnClickListener { model.load() }
        binding.musicianSelectionConfirm.setOnClickListener {
            val musician = model.state.value.selected ?: return@setOnClickListener
            val app = requireActivity().application as VinilosApplication
            if (app.activeCollectorStore.active.value == null) return@setOnClickListener
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.hu12_confirm_title)
                .setMessage(getString(R.string.hu12_confirm_message, musician.name))
                .setNegativeButton(R.string.hu12_cancel, null)
                .setPositiveButton(R.string.hu12_choose) { _, _ ->
                    if (app.activeCollectorStore.active.value != null) model.submit()
                }.show()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { state ->
                    if (state.saved) {
                        parentFragmentManager.setFragmentResult(
                            RESULT, bundleOf(ARG_BAND_ID to bandId, "musicianId" to state.selectedId),
                        )
                        parentFragmentManager.popBackStack()
                        return@collect
                    }
                    binding.musicianSelectionCancel.isEnabled = !state.submitting
                    binding.musicianSelectionStatus.isVisible =
                        state.submitting || state.submitFailed || state.unconfirmed
                    binding.musicianSelectionStatus.setText(when {
                        state.submitting -> R.string.hu12_saving
                        state.unconfirmed -> R.string.hu12_unconfirmed
                        else -> R.string.hu12_save_failed
                    })
                    binding.musicianSelectionLoading.isVisible = state.loading
                    binding.musicianSelectionRetry.isVisible = state.failed
                    binding.musicianSelectionEmpty.isVisible =
                        !state.loading && !state.failed && state.musicians.isEmpty()
                    binding.musicianSelectionConfirm.isEnabled =
                        !state.loading && !state.failed && !state.submitting && state.selected != null
                    binding.musicianSelectionOptions.isVisible = !state.loading && !state.failed
                    val options = binding.musicianSelectionOptions
                    val currentIds = (0 until options.childCount).map { options.getChildAt(it).tag }
                    val incomingIds = state.musicians.map { it.id }
                    // Conservar las vistas evita perder el foco de TalkBack al seleccionar.
                    if (currentIds != incomingIds) {
                        options.removeAllViews()
                        state.musicians.forEach { musician ->
                            options.addView(RadioButton(requireContext()).apply {
                                id = View.generateViewId()
                                tag = musician.id
                                minHeight = (48 * resources.displayMetrics.density).toInt()
                                setOnClickListener { model.select(musician.id) }
                            })
                        }
                    }
                    state.musicians.forEachIndexed { index, musician ->
                        (options.getChildAt(index) as RadioButton).apply {
                            text = musician.name
                            contentDescription = getString(R.string.hu12_select_musician, musician.name)
                            isChecked = musician.id == state.selectedId
                            isEnabled = !state.submitting
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val RESULT = "hu12MusicianSelected"
        const val ARG_BAND_ID = "bandId"
        private const val ARG_BAND_NAME = "bandName"
        fun newInstance(bandId: Int, bandName: String = "") = MusicianSelectionFragment().apply {
            arguments = bundleOf(ARG_BAND_ID to bandId, ARG_BAND_NAME to bandName)
        }
    }
}
