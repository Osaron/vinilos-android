package co.edu.uniandes.vinilos.ui.prizes

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
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
import co.edu.uniandes.vinilos.databinding.FragmentCreatePrizeBinding
import co.edu.uniandes.vinilos.ui.collectors.ActiveCollectorFragment
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class CreatePrizeFragment : Fragment(R.layout.fragment_create_prize) {
    private val app get() = requireActivity().application as VinilosApplication
    private val model: CreatePrizeViewModel by viewModels {
        object : AbstractSavedStateViewModelFactory(this, arguments) {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(key: String, modelClass: Class<T>, handle: SavedStateHandle): T =
                CreatePrizeViewModel(app.prizeRepository, app.activeCollectorStore.active, handle) as T
        }
    }
    private var dialog: AlertDialog? = null
    private var leaving = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentCreatePrizeBinding.bind(view)
        // Al volver del catálogo de revisión el mismo ViewModel sigue conservando el borrador.
        if (savedInstanceState == null) model.state.value.draft.let {
            binding.prizeNameInput.setText(it.name)
            binding.prizeDescriptionInput.setText(it.description)
            binding.prizeOrganizationInput.setText(it.organization)
        }
        fun update() = model.updateDraft(PrizeDraft(binding.prizeNameInput.text.toString(),
            binding.prizeDescriptionInput.text.toString(), binding.prizeOrganizationInput.text.toString()))
        binding.prizeNameInput.doAfterTextChanged { update() }
        binding.prizeDescriptionInput.doAfterTextChanged { update() }
        binding.prizeOrganizationInput.doAfterTextChanged { update() }
        binding.prizeSave.setOnClickListener {
            hideKeyboard()
            model.submit()
            val first = model.state.value.errors.firstOrNull()
            val invalid = when (first) {
                PrizeField.NAME -> binding.prizeNameInput to R.string.hu13_required_name
                PrizeField.DESCRIPTION -> binding.prizeDescriptionInput to R.string.hu13_required_description
                PrizeField.ORGANIZATION -> binding.prizeOrganizationInput to R.string.hu13_required_organization
                null -> null
            }
            invalid?.let { (field, message) -> field.requestFocus(); field.announceForAccessibility(getString(message)) }
        }
        binding.prizeCancel.setOnClickListener { requestLeave() }
        binding.createPrizeBack.setOnClickListener { requestLeave() }
        binding.prizeReviewCatalog.setOnClickListener {
            hideKeyboard()
            parentFragmentManager.setFragmentResult(PrizeCatalogFragment.OPEN,
                bundleOf(PrizeCatalogFragment.REVIEW_DRAFT to true))
        }
        binding.prizeChooseCollector.setOnClickListener {
            parentFragmentManager.setFragmentResult(ActiveCollectorFragment.REQUEST, Bundle())
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = requestLeave()
        })
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.combine(app.activeCollectorStore.active) { state, collector -> state to collector }.collect { (state, collector) ->
                    if (state.created != null) {
                        if (!leaving && !parentFragmentManager.isStateSaved) {
                            leaving = true
                            hideKeyboard()
                            view.announceForAccessibility(getString(R.string.hu13_saved))
                            parentFragmentManager.setFragmentResult(CREATED, bundleOf(CREATED_ID to state.created.id, CREATED_NAME to state.created.name))
                            parentFragmentManager.popBackStack()
                        }
                        return@collect
                    }
                    binding.prizeNameError.isVisible = PrizeField.NAME in state.errors
                    binding.prizeDescriptionError.isVisible = PrizeField.DESCRIPTION in state.errors
                    binding.prizeOrganizationError.isVisible = PrizeField.ORGANIZATION in state.errors
                    binding.prizeCollectorError.isVisible = state.needsCollector && collector == null
                    binding.prizeChooseCollector.isVisible = collector == null
                    binding.prizeChooseCollector.isEnabled = !state.submitting
                    binding.prizeNameInput.isEnabled = state.canEdit
                    binding.prizeDescriptionInput.isEnabled = state.canEdit
                    binding.prizeOrganizationInput.isEnabled = state.canEdit
                    binding.prizeSave.isVisible = state.error != PrizeSaveError.UNCERTAIN
                    binding.prizeSave.isEnabled = state.canSubmit
                    binding.prizeCancel.isEnabled = !state.submitting
                    binding.createPrizeBack.isEnabled = !state.submitting
                    binding.prizeReviewCatalog.isVisible = state.error == PrizeSaveError.UNCERTAIN
                    binding.prizeSaving.isVisible = state.submitting
                    val status = when {
                        state.submitting -> R.string.hu13_saving
                        state.error == PrizeSaveError.REJECTED -> R.string.hu13_rejected
                        state.error == PrizeSaveError.UNCERTAIN -> R.string.hu13_uncertain
                        else -> null
                    }?.let(::getString).orEmpty()
                    binding.createPrizeStatus.isVisible = status.isNotEmpty()
                    if (binding.createPrizeStatus.text.toString() != status) binding.createPrizeStatus.text = status
                }
            }
        }
    }

    private fun requestLeave() {
        val state = model.state.value
        if (state.submitting || parentFragmentManager.isStateSaved || leaving) return
        hideKeyboard()
        if (!state.draft.hasContent) {
            parentFragmentManager.popBackStack()
        } else if (dialog?.isShowing != true) {
            dialog = AlertDialog.Builder(requireContext())
                .setTitle(R.string.hu13_discard_title)
                .setMessage(if (state.error == PrizeSaveError.UNCERTAIN) R.string.hu13_discard_uncertain else R.string.hu13_discard_message)
                .setNegativeButton(R.string.hu13_keep_editing, null)
                .setPositiveButton(R.string.hu13_discard) { _, _ ->
                    if (!parentFragmentManager.isStateSaved) parentFragmentManager.popBackStack()
                }.show()
        }
    }

    private fun hideKeyboard() {
        val focused = view?.findFocus() ?: return
        (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(focused.windowToken, 0)
    }

    override fun onDestroyView() {
        dialog?.dismiss(); dialog = null
        super.onDestroyView()
    }

    companion object {
        const val OPEN = "open_create_prize"
        const val CREATED = "hu13PrizeCreated"
        const val CREATED_ID = "prizeId"
        const val CREATED_NAME = "prizeName"
    }
}
