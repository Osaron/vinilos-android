package co.edu.uniandes.vinilos.ui.albums

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.VinilosApplication
import co.edu.uniandes.vinilos.databinding.FragmentCreateAlbumBinding
import co.edu.uniandes.vinilos.model.Album
import co.edu.uniandes.vinilos.model.AlbumAllowedValues
import kotlinx.coroutines.launch

/**
 * Formulario de creación de álbum (HU07, #17) del flujo Coleccionista.
 *
 * La vista no valida ni envía por su cuenta: publica lo escrito con [CreateAlbumViewModel.updateForm],
 * pide el envío con [CreateAlbumViewModel.submit] y solo dibuja [CreateAlbumState]. Al crear el
 * álbum publica [ALBUM_CREATED] para que el catálogo se actualice y regresa.
 */
class CreateAlbumFragment : Fragment(R.layout.fragment_create_album) {
    private val viewModel: CreateAlbumViewModel by viewModels {
        CreateAlbumViewModel.Factory(
            (requireActivity().application as VinilosApplication).albumCreationRepository,
        )
    }
    private var creationHandled = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentCreateAlbumBinding.bind(view)
        val genres = listOf(getString(R.string.select_genre)) + AlbumAllowedValues.GENRES
        val recordLabels = listOf(getString(R.string.select_record_label)) + AlbumAllowedValues.RECORD_LABELS
        binding.genreInput.adapter = optionsAdapter(genres)
        binding.recordLabelInput.adapter = optionsAdapter(recordLabels)

        // Restaura lo que ya conoce el ViewModel antes de escuchar cambios.
        val form = viewModel.state.value.form
        binding.nameInput.setText(form.name)
        binding.coverInput.setText(form.coverUrl)
        binding.releaseDateInput.setText(form.releaseDate)
        binding.descriptionInput.setText(form.description)
        binding.genreInput.setSelection(genres.indexOf(form.genre).coerceAtLeast(0), false)
        binding.recordLabelInput.setSelection(recordLabels.indexOf(form.recordLabel).coerceAtLeast(0), false)

        val publish = { viewModel.updateForm(binding.readForm()) }
        listOf(binding.nameInput, binding.coverInput, binding.releaseDateInput, binding.descriptionInput)
            .forEach { input -> input.doAfterTextChanged { publish() } }
        binding.genreInput.onSelectionChanged(publish)
        binding.recordLabelInput.onSelectionChanged(publish)

        binding.saveButton.setOnClickListener {
            viewModel.updateForm(binding.readForm())
            viewModel.submit()
        }
        binding.cancelButton.setOnClickListener { close() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state -> binding.render(state) }
            }
        }
    }

    private fun FragmentCreateAlbumBinding.render(state: CreateAlbumState) {
        showError(nameError, state.errors[AlbumField.NAME])
        showError(coverError, state.errors[AlbumField.COVER])
        showError(releaseDateError, state.errors[AlbumField.RELEASE_DATE])
        showError(descriptionError, state.errors[AlbumField.DESCRIPTION])
        showError(genreError, state.errors[AlbumField.GENRE])
        showError(recordLabelError, state.errors[AlbumField.RECORD_LABEL])

        // Mientras se envía no se puede editar: el ViewModel ignora cambios y pulsaciones.
        val editable = state.canSubmit
        listOf(nameInput, coverInput, releaseDateInput, descriptionInput, genreInput, recordLabelInput,
            saveButton, cancelButton).forEach { it.isEnabled = editable }
        savingIndicator.isVisible = state.submitting
        createStatusMessage.isVisible = state.failed

        state.createdAlbum?.let { onCreated(it) }
    }

    private fun onCreated(album: Album) {
        if (creationHandled) return
        creationHandled = true
        parentFragmentManager.setFragmentResult(
            ALBUM_CREATED, bundleOf("albumId" to album.id, "albumName" to album.name),
        )
        Toast.makeText(requireContext(), getString(R.string.album_created, album.name), Toast.LENGTH_SHORT).show()
        close()
    }

    private fun close() {
        hideKeyboard()
        if (!parentFragmentManager.isStateSaved) parentFragmentManager.popBackStack()
    }

    private fun hideKeyboard() {
        val focused = view?.findFocus() ?: return
        (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(focused.windowToken, 0)
        focused.clearFocus()
    }

    private fun FragmentCreateAlbumBinding.readForm() = AlbumForm(
        name = nameInput.text.toString(),
        coverUrl = coverInput.text.toString(),
        releaseDate = releaseDateInput.text.toString(),
        description = descriptionInput.text.toString(),
        // La posición 0 es el texto guía; equivale a no haber elegido.
        genre = selectedOption(genreInput, AlbumAllowedValues.GENRES),
        recordLabel = selectedOption(recordLabelInput, AlbumAllowedValues.RECORD_LABELS),
    )

    private fun selectedOption(spinner: Spinner, options: List<String>): String =
        options.getOrNull(spinner.selectedItemPosition - 1).orEmpty()

    private fun showError(target: TextView, error: AlbumFieldError?) {
        target.isVisible = error != null
        if (error != null) target.setText(messageFor(error))
    }

    @StringRes private fun messageFor(error: AlbumFieldError): Int = when (error) {
        AlbumFieldError.REQUIRED -> R.string.error_required
        AlbumFieldError.INVALID_URL -> R.string.error_invalid_url
        AlbumFieldError.INVALID_DATE -> R.string.error_invalid_date
        AlbumFieldError.FUTURE_DATE -> R.string.error_future_date
        AlbumFieldError.NOT_ALLOWED -> R.string.error_not_allowed
    }

    private fun optionsAdapter(options: List<String>) =
        ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, options).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

    private fun Spinner.onSelectionChanged(action: () -> Unit) {
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = action()
            override fun onNothingSelected(parent: AdapterView<*>?) = action()
        }
    }

    companion object {
        /** Lo publica el catálogo para abrir el formulario (MainActivity hace la navegación). */
        const val OPEN_CREATE_ALBUM = "open_create_album"
        /** Lo publica este fragmento al crear el álbum; el catálogo recarga la lista. */
        const val ALBUM_CREATED = "album_created"
    }
}
