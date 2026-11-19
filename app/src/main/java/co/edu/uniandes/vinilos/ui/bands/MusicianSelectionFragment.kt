package co.edu.uniandes.vinilos.ui.bands

import android.os.Bundle
import android.view.View
import android.widget.RadioButton
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

/** Selector de HU12. Publica la elección al flujo de guardado mediante FragmentResult. */
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
        binding.musicianSelectionCancel.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.musicianSelectionRetry.setOnClickListener { model.load() }
        binding.musicianSelectionConfirm.setOnClickListener {
            val state = model.state.value
            if (!state.loading && !state.failed) state.selected?.let { musician ->
                parentFragmentManager.setFragmentResult(
                    RESULT, bundleOf(ARG_BAND_ID to bandId, "musicianId" to musician.id),
                )
                parentFragmentManager.popBackStack()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { state ->
                    binding.musicianSelectionLoading.isVisible = state.loading
                    binding.musicianSelectionRetry.isVisible = state.failed
                    binding.musicianSelectionEmpty.isVisible =
                        !state.loading && !state.failed && state.musicians.isEmpty()
                    binding.musicianSelectionConfirm.isEnabled =
                        !state.loading && !state.failed && state.selected != null
                    binding.musicianSelectionOptions.isVisible = !state.loading && !state.failed
                    binding.musicianSelectionOptions.setOnCheckedChangeListener(null)
                    binding.musicianSelectionOptions.removeAllViews()
                    state.musicians.forEach { musician ->
                        binding.musicianSelectionOptions.addView(RadioButton(requireContext()).apply {
                            id = View.generateViewId()
                            text = musician.name
                            contentDescription = getString(R.string.hu12_select_musician, musician.name)
                            minHeight = (48 * resources.displayMetrics.density).toInt()
                            isChecked = musician.id == state.selectedId
                            setOnClickListener { model.select(musician.id) }
                        })
                    }
                }
            }
        }
    }

    companion object {
        const val RESULT = "hu12MusicianSelected"
        const val ARG_BAND_ID = "bandId"
        fun newInstance(bandId: Int) = MusicianSelectionFragment().apply {
            arguments = bundleOf(ARG_BAND_ID to bandId)
        }
    }
}
