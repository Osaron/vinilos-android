package co.edu.uniandes.vinilos.ui.collectors

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.VinilosApplication
import co.edu.uniandes.vinilos.databinding.FragmentCollectorDetailBinding
import co.edu.uniandes.vinilos.model.CollectorDetail
import co.edu.uniandes.vinilos.model.FavoritePerformer
import coil.load
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CollectorDetailFragment : Fragment(R.layout.fragment_collector_detail) {
    private val collectorId: Int by lazy { requireArguments().getInt(ARG_COLLECTOR_ID).also { require(it > 0) } }
    private val viewModel: CollectorDetailViewModel by viewModels {
        CollectorDetailViewModel.Factory(
            collectorId,
            (requireActivity().application as VinilosApplication).collectorDetailRepository,
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentCollectorDetailBinding.bind(view)
        val app = requireActivity().application as VinilosApplication
        binding.addCollectorAlbum.setOnClickListener {
            val detail = viewModel.state.value.detail ?: return@setOnClickListener
            if (app.activeCollectorStore.active.value?.id != collectorId) return@setOnClickListener
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, CollectorAlbumFragment.newInstance(collectorId, detail.name))
                .addToBackStack("hu11_album")
                .commit()
        }
        parentFragmentManager.setFragmentResultListener(CollectorAlbumFragment.RESULT, viewLifecycleOwner) { _, data ->
            if (data.getInt(CollectorAlbumFragment.COLLECTOR_ID) == collectorId) {
                Toast.makeText(requireContext(), R.string.hu11_saved, Toast.LENGTH_SHORT).show()
                viewModel.refresh()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                app.activeCollectorStore.active.collectLatest { active ->
                    binding.addCollectorAlbum.isVisible = active?.id == collectorId
                    binding.collectorAlbumList.isVisible = false
                    if (active?.id == collectorId) {
                        binding.collectorAlbumList.text = try {
                            val names = app.collectorAlbumRepository.getAlbumNames(collectorId)
                            val albums = names.joinToString("\n").ifEmpty { getString(R.string.hu11_no_albums) }
                            getString(R.string.hu11_collection, albums)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            getString(R.string.hu11_collection_failed)
                        }
                        binding.collectorAlbumList.isVisible = app.activeCollectorStore.active.value?.id == collectorId
                    }
                }
            }
        }
        binding.backCollectorDetail.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.retryCollectorDetail.setOnClickListener { viewModel.refresh() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    binding.collectorDetailLoading.isVisible = state.loading
                    binding.collectorDetailError.isVisible = state.failed
                    binding.collectorDetailContent.isVisible = !state.loading && !state.failed && state.detail != null
                    state.detail?.let { binding.render(it) }
                }
            }
        }
    }

    private fun FragmentCollectorDetailBinding.render(detail: CollectorDetail) {
        collectorDetailTitle.text = detail.name
        collectorDetailEmail.isVisible = detail.email.isNotBlank()
        collectorDetailEmail.text = detail.email
        collectorDetailTelephone.isVisible = detail.telephone.isNotBlank()
        collectorDetailTelephone.text = detail.telephone

        collectorDetailFavorites.removeAllViews()
        collectorDetailFavoritesEmpty.isVisible = detail.favoritePerformers.isEmpty()
        detail.favoritePerformers.forEach { collectorDetailFavorites.addView(favoriteRow(it)) }

        collectorDetailComments.removeAllViews()
        collectorDetailCommentsEmpty.isVisible = detail.comments.isEmpty()
        detail.comments.forEach { comment ->
            collectorDetailComments.addView(TextView(requireContext()).apply {
                text = comment.rating?.let { getString(R.string.collector_comment_with_rating, comment.description, it) }
                    ?: comment.description
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                textSize = 16f
                setPadding(0, 0, 0, resources.getDimensionPixelSize(R.dimen.detail_album_spacing))
            })
        }
    }

    private fun favoriteRow(performer: FavoritePerformer): View {
        val spacing = resources.getDimensionPixelSize(R.dimen.detail_album_spacing)
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            // Incluye la fecha y la descripción: la descripción de la fila reemplaza el texto que se ve.
            contentDescription = listOf(
                getString(R.string.collector_favorite_accessibility, performer.name),
                listOfNotNull(performer.date?.take(10), performer.description.takeIf(String::isNotBlank)).joinToString(" · "),
            ).filter(String::isNotBlank).joinToString(". ")
            setPadding(0, spacing / 2, 0, spacing / 2)
            val image = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(spacing * 3, spacing * 3)
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = getString(R.string.collector_favorite_image, performer.name)
                load(performer.image) {
                    addHeader("User-Agent", "VinilosAndroid/1.0 (https://github.com/Osaron/vinilos-android)")
                    placeholder(R.drawable.ic_artist)
                    error(R.drawable.ic_artist)
                }
            }
            addView(image)
            val textColumn = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(spacing, 0, 0, 0)
                addView(TextView(context).apply {
                    text = performer.name
                    textSize = 16f
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                })
                val summary = listOfNotNull(performer.date?.take(10), performer.description.takeIf(String::isNotBlank))
                    .joinToString(" · ")
                if (summary.isNotBlank()) addView(TextView(context).apply {
                    text = summary
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                    maxLines = 3
                })
            }
            addView(textColumn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    companion object {
        private const val ARG_COLLECTOR_ID = "collectorId"
        fun newInstance(collectorId: Int) = CollectorDetailFragment().apply {
            arguments = bundleOf(ARG_COLLECTOR_ID to collectorId)
        }
    }
}
