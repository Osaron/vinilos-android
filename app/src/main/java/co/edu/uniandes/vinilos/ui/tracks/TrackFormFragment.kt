package co.edu.uniandes.vinilos.ui.tracks

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.databinding.FragmentTrackFormBinding

/** Form component: the parent owns album selection and processes valid submissions. */
class TrackFormFragment : Fragment(R.layout.fragment_track_form) {
    private val draft: TrackFormViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentTrackFormBinding.bind(view)
        binding.trackNameInput.setText(draft.form.name)
        binding.trackDurationInput.setText(draft.form.duration)
        val renderErrors = {
            val errors = TrackFormValidator.validate(draft.form)
            binding.trackNameError.isVisible = draft.showErrors && errors.name
            binding.trackDurationError.isVisible = draft.showErrors && errors.duration
        }
        val update = {
            draft.update(binding.trackNameInput.text.toString(), binding.trackDurationInput.text.toString())
            renderErrors()
        }
        binding.trackNameInput.doAfterTextChanged { update() }
        binding.trackDurationInput.doAfterTextChanged { update() }
        renderErrors()
        binding.trackSaveButton.setOnClickListener {
            update()
            val errors = draft.validate()
            binding.trackNameError.isVisible = errors.name
            binding.trackDurationError.isVisible = errors.duration
            when {
                errors.name -> binding.trackNameInput.requestFocus()
                errors.duration -> binding.trackDurationInput.requestFocus()
                else -> draft.validated()?.let { form ->
                    parentFragmentManager.setFragmentResult(VALID_TRACK_RESULT, bundleOf(
                        TRACK_NAME to form.name, TRACK_DURATION to form.duration,
                    ))
                }
            }
        }
    }

    companion object {
        const val VALID_TRACK_RESULT = "valid_track_form"
        const val TRACK_NAME = "track_name"
        const val TRACK_DURATION = "track_duration"
    }
}
