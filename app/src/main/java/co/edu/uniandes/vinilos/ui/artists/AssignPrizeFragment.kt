package co.edu.uniandes.vinilos.ui.artists

import android.app.DatePickerDialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
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
import co.edu.uniandes.vinilos.databinding.FragmentAssignPrizeBinding
import co.edu.uniandes.vinilos.model.ArtistType
import co.edu.uniandes.vinilos.model.Prize
import co.edu.uniandes.vinilos.model.PrizeDate
import kotlinx.coroutines.launch

/** HU14: asigna un premio existente, con su fecha de entrega, a un músico o una banda. */
class AssignPrizeFragment : Fragment(R.layout.fragment_assign_prize) {
    private val artistId: Int by lazy { requireArguments().getInt(ARG_ARTIST_ID).also { require(it > 0) } }
    private val artistType: ArtistType by lazy {
        requireNotNull(ArtistType.values().firstOrNull { it.name == requireArguments().getString(ARG_ARTIST_TYPE) })
    }
    private val model: AssignPrizeViewModel by viewModels {
        object : AbstractSavedStateViewModelFactory(this, arguments) {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(key: String, modelClass: Class<T>, handle: SavedStateHandle): T {
                val app = requireActivity().application as VinilosApplication
                return AssignPrizeViewModel(artistId, artistType, app.artistPrizeRepository, handle) as T
            }
        }
    }
    private var dateDialog: DatePickerDialog? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentAssignPrizeBinding.bind(view)
        val app = requireActivity().application as VinilosApplication
        val artistName = requireArguments().getString(ARG_ARTIST_NAME).orEmpty()
        val typeLabel = getString(if (artistType == ArtistType.BAND) R.string.artist_band else R.string.artist_musician)
        binding.assignPrizeContext.text = if (artistName.isBlank()) typeLabel else getString(R.string.hu14_context, artistName, typeLabel)
        binding.assignPrizeRetry.setOnClickListener { model.load() }
        binding.assignPrizeCancel.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.assignPrizePickDate.setOnClickListener { showDatePicker() }
        binding.assignPrizeConfirm.setOnClickListener {
            if (app.activeCollectorStore.active.value != null) model.submit()
        }
        val renderer = PrizeOptionsRenderer(binding.assignPrizeOptions) { model.select(it) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { state ->
                    if (state.saved) {
                        parentFragmentManager.setFragmentResult(RESULT, bundleOf(ARG_ARTIST_ID to artistId))
                        parentFragmentManager.popBackStack()
                        return@collect
                    }
                    val ready = !state.loading && !state.failed
                    binding.assignPrizeLoading.isVisible = state.loading
                    binding.assignPrizeRetry.isVisible = state.failed
                    binding.assignPrizeEmpty.isVisible = ready && state.prizes.isEmpty()
                    binding.assignPrizeOptions.isVisible = ready
                    renderer.render(state.prizes, state.selectedId, enabled = !state.submitting)
                    binding.assignPrizeDate.text = state.date
                        ?.let { getString(R.string.hu14_date_value, PrizeDate.display(it)) }
                        ?: getString(R.string.hu14_date_none)
                    binding.assignPrizePickDate.setText(if (state.date == null) R.string.hu14_date_choose else R.string.hu14_date_change)
                    binding.assignPrizePickDate.isEnabled = !state.submitting
                    val status = when {
                        state.submitting -> R.string.hu14_saving
                        state.unconfirmed -> R.string.hu14_unconfirmed
                        state.submitFailed -> R.string.hu14_save_failed
                        state.futureDate -> R.string.hu14_future_date
                        state.duplicate -> R.string.hu14_duplicate
                        ready && state.prizes.isNotEmpty() && (state.selected == null || state.date == null) -> R.string.hu14_hint
                        else -> null
                    }
                    binding.assignPrizeStatus.isVisible = status != null
                    // Solo se cambia el texto si es otro: TalkBack lo anuncia una vez y no en cada emisión.
                    val text = status?.let(::getString).orEmpty()
                    if (binding.assignPrizeStatus.text.toString() != text) binding.assignPrizeStatus.text = text
                    binding.assignPrizeCancel.isEnabled = !state.submitting
                    binding.assignPrizeConfirm.isEnabled = state.canSubmit
                }
            }
        }
    }

    private fun showDatePicker() {
        if (dateDialog?.isShowing == true) return
        val (year, month, day) = PrizeDate.parts(model.state.value.date ?: PrizeDate.today())
        dateDialog = DatePickerDialog(
            requireContext(),
            { _, pickedYear, pickedMonth, pickedDay -> model.setDate(PrizeDate.iso(pickedYear, pickedMonth + 1, pickedDay)) },
            year, month - 1, day,
        ).apply {
            // Un premio no se puede haber entregado en el futuro.
            datePicker.maxDate = System.currentTimeMillis()
            // Botones en español aunque el dispositivo esté en otro idioma, como el resto de la app.
            setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.hu14_date_accept), this)
            setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.cancel), this)
            show()
        }
    }

    override fun onDestroyView() {
        dateDialog?.dismiss()
        dateDialog = null
        super.onDestroyView()
    }

    companion object {
        const val RESULT = "hu14PrizeAssigned"
        const val ARG_ARTIST_ID = "artistId"
        private const val ARG_ARTIST_TYPE = "artistType"
        private const val ARG_ARTIST_NAME = "artistName"
        fun newInstance(artistId: Int, artistType: String, artistName: String = "") = AssignPrizeFragment().apply {
            arguments = bundleOf(ARG_ARTIST_ID to artistId, ARG_ARTIST_TYPE to artistType, ARG_ARTIST_NAME to artistName)
        }
    }
}

/**
 * Pinta los premios en un [RadioGroup]. Conserva las vistas cuando la lista no cambia para no perder
 * el foco de TalkBack al seleccionar (mismo patrón que HU12 y HU15).
 */
private class PrizeOptionsRenderer(private val group: RadioGroup, private val onSelect: (Int) -> Unit) {
    fun render(prizes: List<Prize>, selectedId: Int?, enabled: Boolean) {
        val context = group.context
        val currentIds = (0 until group.childCount).map { group.getChildAt(it).tag }
        if (currentIds != prizes.map { it.id }) {
            group.removeAllViews()
            prizes.forEach { prize ->
                group.addView(RadioButton(context).apply {
                    id = View.generateViewId()
                    tag = prize.id
                    minHeight = (48 * resources.displayMetrics.density).toInt()
                    setOnClickListener { onSelect(prize.id) }
                })
            }
        }
        prizes.forEachIndexed { index, prize ->
            (group.getChildAt(index) as RadioButton).apply {
                text = if (prize.organization.isEmpty()) prize.name else "${prize.name} · ${prize.organization}"
                contentDescription = if (prize.organization.isEmpty()) {
                    context.getString(R.string.hu14_option, prize.name)
                } else {
                    context.getString(R.string.hu14_option_organization, prize.name, prize.organization)
                }
                isChecked = prize.id == selectedId
                isEnabled = enabled
            }
        }
    }
}
