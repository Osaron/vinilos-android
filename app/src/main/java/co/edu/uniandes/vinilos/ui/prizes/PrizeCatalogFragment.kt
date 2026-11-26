package co.edu.uniandes.vinilos.ui.prizes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.VinilosApplication
import co.edu.uniandes.vinilos.databinding.FragmentPrizeCatalogBinding
import co.edu.uniandes.vinilos.databinding.ItemPrizeBinding
import co.edu.uniandes.vinilos.model.Prize
import co.edu.uniandes.vinilos.ui.collectors.ActiveCollectorFragment
import kotlinx.coroutines.launch

class PrizeCatalogFragment : Fragment(R.layout.fragment_prize_catalog) {
    private val app get() = requireActivity().application as VinilosApplication
    private val reviewDraft get() = arguments?.getBoolean(REVIEW_DRAFT) == true
    private var pendingCreate = false
    private val model: PrizeCatalogViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PrizeCatalogViewModel(app.prizeRepository) as T
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentPrizeCatalogBinding.bind(view)
        pendingCreate = savedInstanceState?.getBoolean(PENDING_CREATE) ?: pendingCreate
        val adapter = PrizeAdapter()
        binding.prizeList.layoutManager = LinearLayoutManager(requireContext())
        binding.prizeList.adapter = adapter
        binding.prizeBack.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.prizeBackDraft.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.prizeRetry.setOnClickListener { model.refresh() }
        binding.prizeCreate.isVisible = !reviewDraft
        binding.prizeBackDraft.isVisible = reviewDraft
        binding.prizeCreate.setOnClickListener {
            if (app.activeCollectorStore.active.value == null) {
                pendingCreate = true
                parentFragmentManager.setFragmentResult(ActiveCollectorFragment.REQUEST, Bundle())
            } else openForm()
        }
        parentFragmentManager.setFragmentResultListener(ActiveCollectorFragment.CHOSEN, viewLifecycleOwner) { _, _ ->
            if (pendingCreate && app.activeCollectorStore.active.value != null) {
                pendingCreate = false
                openForm()
            }
        }
        parentFragmentManager.setFragmentResultListener(CreatePrizeFragment.CREATED, viewLifecycleOwner) { _, data ->
            model.refresh(data.getString(CreatePrizeFragment.CREATED_NAME))
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { state ->
                    val ready = !state.loading && !state.failed
                    binding.prizeLoading.isVisible = state.loading
                    binding.prizeList.isVisible = ready && state.prizes.isNotEmpty()
                    binding.prizeRetry.isVisible = state.failed
                    binding.prizeCreate.isEnabled = !state.loading && !state.failed
                    val status = when {
                        state.loading -> getString(R.string.hu13_loading)
                        state.failed -> getString(if (state.createdName == null) R.string.hu13_load_error else R.string.hu13_refresh_error)
                        state.prizes.isEmpty() -> getString(R.string.hu13_empty)
                        else -> ""
                    }
                    binding.prizeStatus.isVisible = status.isNotEmpty()
                    if (binding.prizeStatus.text.toString() != status) binding.prizeStatus.text = status
                    val notice = when {
                        reviewDraft -> getString(R.string.hu13_draft_kept)
                        state.createdName != null -> getString(R.string.hu13_created_name, state.createdName)
                        else -> ""
                    }
                    binding.prizeCatalogNotice.isVisible = notice.isNotEmpty()
                    if (binding.prizeCatalogNotice.text.toString() != notice) binding.prizeCatalogNotice.text = notice
                    adapter.submitList(state.prizes)
                }
            }
        }
    }

    private fun openForm() {
        parentFragmentManager.setFragmentResult(CreatePrizeFragment.OPEN, Bundle())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(PENDING_CREATE, pendingCreate)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        view?.findViewById<RecyclerView>(R.id.prize_list)?.adapter = null
        super.onDestroyView()
    }

    companion object {
        const val OPEN = "open_prize_catalog"
        const val REVIEW_DRAFT = "hu13ReviewDraft"
        private const val PENDING_CREATE = "hu13PendingCreate"
        fun newInstance(reviewDraft: Boolean = false) = PrizeCatalogFragment().apply {
            arguments = bundleOf(REVIEW_DRAFT to reviewDraft)
        }
    }
}

private class PrizeAdapter : ListAdapter<Prize, PrizeAdapter.Holder>(object : DiffUtil.ItemCallback<Prize>() {
    override fun areItemsTheSame(oldItem: Prize, newItem: Prize) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Prize, newItem: Prize) = oldItem == newItem
}) {
    class Holder(val binding: ItemPrizeBinding) : RecyclerView.ViewHolder(binding.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemPrizeBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val prize = getItem(position)
        holder.binding.apply {
            prizeName.text = prize.name
            prizeOrganization.text = prize.organization
            prizeDescription.text = prize.description
            prizeOrganization.isVisible = prize.organization.isNotBlank()
            prizeDescription.isVisible = prize.description.isNotBlank()
        }
    }
}
