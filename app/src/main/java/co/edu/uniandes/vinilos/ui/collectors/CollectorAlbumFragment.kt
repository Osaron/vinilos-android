package co.edu.uniandes.vinilos.ui.collectors

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.VinilosApplication
import co.edu.uniandes.vinilos.data.CollectorAlbumStatus
import co.edu.uniandes.vinilos.databinding.FragmentCollectorAlbumBinding
import co.edu.uniandes.vinilos.model.Album
import co.edu.uniandes.vinilos.ui.albums.picker.AlbumOptionsRenderer
import co.edu.uniandes.vinilos.ui.albums.picker.AlbumPicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class CollectorAlbumFragment : Fragment(R.layout.fragment_collector_album) {
    private var selectedId: Int? = null

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        selectedId?.let { outState.putInt(ALBUM_ID, it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentCollectorAlbumBinding.bind(view)
        val app = requireActivity().application as VinilosApplication
        val collectorId = requireArguments().getInt(COLLECTOR_ID)
        val picker = binding.collectorAlbumPicker
        var available = emptyList<Album>()
        var busy = false
        selectedId = savedInstanceState?.getInt(ALBUM_ID)?.takeIf { it > 0 } ?: selectedId
        binding.collectorAlbumContext.text = requireArguments().getString(COLLECTOR_NAME)
        fun mayEdit() = app.activeCollectorStore.active.value?.id == collectorId

        lateinit var renderer: AlbumOptionsRenderer
        fun render() {
            val visible = AlbumPicker.filter(available, picker.albumPickerSearch.text.toString())
            renderer.render(visible, selectedId, !busy)
            picker.albumPickerEmpty.isVisible = !busy && !picker.albumPickerRetry.isVisible && visible.isEmpty()
            val selected = available.firstOrNull { it.id == selectedId }
            binding.collectorAlbumSelected.isVisible = selected != null
            binding.collectorAlbumSelected.text = selected?.let(AlbumPicker::label).orEmpty()
            binding.collectorAlbumConfirm.isEnabled = !busy && selected != null && mayEdit()
            binding.collectorAlbumCancel.isEnabled = !busy
            binding.collectorAlbumPrice.isEnabled = !busy
            binding.collectorAlbumActive.isEnabled = !busy
            picker.albumPickerSearch.isEnabled = !busy
        }
        renderer = AlbumOptionsRenderer(picker.albumPickerOptions) { selectedId = it; render() }
        picker.albumPickerSearch.doAfterTextChanged { render() }
        binding.collectorAlbumCancel.setOnClickListener { parentFragmentManager.popBackStack() }

        fun load() {
            if (busy) return
            busy = true
            render()
            viewLifecycleOwner.lifecycleScope.launch {
                picker.albumPickerLoading.isVisible = true
                picker.albumPickerRetry.isVisible = false
                try {
                    available = AlbumPicker.available(
                        app.albumRepository.getAlbums(),
                        app.collectorAlbumRepository.getAlbumIds(collectorId),
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    picker.albumPickerRetry.isVisible = true
                } finally {
                    picker.albumPickerLoading.isVisible = false
                    busy = false
                    render()
                }
            }
        }
        picker.albumPickerRetry.setOnClickListener { load() }
        binding.collectorAlbumConfirm.setOnClickListener {
            val albumId = selectedId ?: return@setOnClickListener
            if (busy || !mayEdit()) return@setOnClickListener
            val price = binding.collectorAlbumPrice.text.toString().trim().toIntOrNull()
            if (price == null || price < 0) {
                binding.collectorAlbumPrice.error = getString(R.string.hu11_price_invalid)
                binding.collectorAlbumPrice.requestFocus()
                return@setOnClickListener
            }
            val status = if (binding.collectorAlbumActive.isChecked) {
                CollectorAlbumStatus.ACTIVE
            } else {
                CollectorAlbumStatus.INACTIVE
            }
            busy = true
            render()
            binding.collectorAlbumStatus.setText(R.string.hu11_saving)
            binding.collectorAlbumStatus.isVisible = true
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val repository = app.collectorAlbumRepository
                    if (albumId !in repository.getAlbumIds(collectorId)) {
                        if (!mayEdit()) return@launch
                        repository.addAlbum(collectorId, albumId, price, status)
                    }
                    parentFragmentManager.setFragmentResult(RESULT, bundleOf(COLLECTOR_ID to collectorId))
                    parentFragmentManager.popBackStack()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    binding.collectorAlbumStatus.setText(R.string.hu11_failed)
                } finally {
                    busy = false
                    render()
                }
            }
        }
        load()
    }

    companion object {
        const val RESULT = "hu11AlbumAdded"
        const val COLLECTOR_ID = "collectorId"
        private const val COLLECTOR_NAME = "collectorName"
        private const val ALBUM_ID = "albumId"

        fun newInstance(id: Int, name: String) = CollectorAlbumFragment().apply {
            arguments = bundleOf(COLLECTOR_ID to id, COLLECTOR_NAME to name)
        }
    }
}
