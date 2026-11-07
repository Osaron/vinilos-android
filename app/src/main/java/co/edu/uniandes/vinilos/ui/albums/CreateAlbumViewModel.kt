package co.edu.uniandes.vinilos.ui.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.edu.uniandes.vinilos.data.AlbumCreationRepository
import co.edu.uniandes.vinilos.model.Album
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

data class CreateAlbumState(
    val form: AlbumForm = AlbumForm(),
    /** Errores por campo. Solo se muestran después del primer intento de guardar. */
    val errors: Map<AlbumField, AlbumFieldError> = emptyMap(),
    val submitting: Boolean = false,
    /** El último envío falló (red o rechazo del backend); el formulario conserva los datos. */
    val failed: Boolean = false,
    /** Álbum creado por el backend; cuando no es null, la vista regresa al catálogo. */
    val createdAlbum: Album? = null,
) {
    val canSubmit: Boolean get() = !submitting && createdAlbum == null
}

/**
 * ViewModel del formulario de creación de álbum (HU07, #18 y #20).
 *
 * - Valida antes de enviar; con errores no se hace ninguna solicitud.
 * - Ignora nuevos intentos mientras hay un envío en curso o después de crear el álbum, para
 *   evitar álbumes duplicados por pulsaciones repetidas.
 * - Si el envío falla conserva lo escrito para que el coleccionista pueda reintentar.
 */
class CreateAlbumViewModel(
    private val repository: AlbumCreationRepository,
    private val today: () -> Calendar = { Calendar.getInstance() },
) : ViewModel() {
    private val mutableState = MutableStateFlow(CreateAlbumState())
    val state = mutableState.asStateFlow()
    private var showErrors = false

    fun updateForm(form: AlbumForm) {
        val current = mutableState.value
        if (!current.canSubmit) return
        mutableState.value = current.copy(
            form = form,
            failed = false,
            errors = if (showErrors) errorsOf(form) else emptyMap(),
        )
    }

    fun submit() {
        val current = mutableState.value
        if (!current.canSubmit) return
        showErrors = true
        when (val validation = AlbumFormValidator.validate(current.form, today())) {
            is AlbumValidation.Invalid ->
                mutableState.value = current.copy(errors = validation.errors, failed = false)
            is AlbumValidation.Valid -> {
                mutableState.value = current.copy(errors = emptyMap(), submitting = true, failed = false)
                viewModelScope.launch {
                    try {
                        val created = repository.createAlbum(validation.album)
                        mutableState.value = mutableState.value.copy(submitting = false, createdAlbum = created)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        mutableState.value = mutableState.value.copy(submitting = false, failed = true)
                    }
                }
            }
        }
    }

    private fun errorsOf(form: AlbumForm): Map<AlbumField, AlbumFieldError> =
        when (val validation = AlbumFormValidator.validate(form, today())) {
            is AlbumValidation.Invalid -> validation.errors
            is AlbumValidation.Valid -> emptyMap()
        }

    class Factory(private val repository: AlbumCreationRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CreateAlbumViewModel::class.java))
            return CreateAlbumViewModel(repository) as T
        }
    }
}
