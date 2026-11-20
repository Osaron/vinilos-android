package co.edu.uniandes.vinilos.ui.comments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.edu.uniandes.vinilos.data.ActiveCollector
import co.edu.uniandes.vinilos.data.CommentRepository
import co.edu.uniandes.vinilos.model.AlbumComment
import co.edu.uniandes.vinilos.model.NewComment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/** Resultado del último intento de publicar el comentario. */
sealed interface CommentResult {
    /** Guardado: la vista vuelve al detalle de [albumId] y recarga sus comentarios. */
    data class Saved(val albumId: Int, val comment: AlbumComment) : CommentResult

    /** Error del servidor o sin red y sin el comentario en el álbum. Se conserva lo escrito. */
    data object Failed : CommentResult

    /** El backend rechazó los datos (400). */
    data object Rejected : CommentResult

    /** El álbum o el coleccionista ya no existen (404). */
    data object NotFound : CommentResult

    /** Se perdió la respuesta y el álbum ya tiene este comentario del mismo coleccionista: no se reenvía solo. */
    data class PossiblySaved(val albumId: Int, val match: AlbumComment) : CommentResult

    /** Se perdió la respuesta y tampoco se pudo consultar el álbum. */
    data object Unconfirmed : CommentResult
}

data class CommentFormErrors(val description: Boolean = false, val rating: Boolean = false, val collector: Boolean = false) {
    val valid: Boolean get() = !description && !rating && !collector
}

data class CommentFormState(
    val description: String = "",
    /** Calificación de 1 a 5 elegida en la vista; `null` mientras no se elija. */
    val rating: Int? = null,
    val errors: CommentFormErrors = CommentFormErrors(),
    val submitting: Boolean = false,
    val result: CommentResult? = null,
) {
    val saved: Boolean get() = result is CommentResult.Saved
    val canSubmit: Boolean get() = !submitting && !saved
}

object CommentFormValidator {
    const val MIN_RATING = 1
    const val MAX_RATING = 5
    fun validate(description: String, rating: Int?, collector: ActiveCollector?) = CommentFormErrors(
        description = description.isBlank(),
        rating = rating == null || rating !in MIN_RATING..MAX_RATING,
        collector = collector == null,
    )
}

/**
 * Formulario, validación y envío del comentario de HU09 (#82, #83).
 *
 * - El borrador (texto y calificación) vive en [SavedStateHandle]: sobrevive a la rotación y a la
 *   muerte del proceso.
 * - El comentario se publica a nombre del coleccionista activo (#80), nunca de un ID fijo.
 * - Una sola solicitud a la vez y ninguna después de guardar, aunque se pulse varias veces.
 * - Ante un error se conserva lo escrito. Si se pierde la respuesta, consulta los comentarios del
 *   álbum antes de ofrecer otro envío, porque el comentario pudo haberse guardado.
 */
class CommentAlbumViewModel(
    private val albumId: Int,
    private val repository: CommentRepository,
    private val activeCollector: StateFlow<ActiveCollector?>,
    private val handle: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        CommentFormState(description = handle[KEY_DESCRIPTION] ?: "", rating = handle[KEY_RATING]),
    )
    val state = mutableState.asStateFlow()

    fun updateDescription(description: String) {
        if (description == mutableState.value.description) return
        handle[KEY_DESCRIPTION] = description
        val current = mutableState.value
        mutableState.value = current.copy(
            description = description,
            errors = current.errors.copy(description = current.errors.description && description.isBlank()),
        )
    }

    fun updateRating(rating: Int?) {
        handle[KEY_RATING] = rating
        val current = mutableState.value
        mutableState.value = current.copy(rating = rating, errors = current.errors.copy(rating = current.errors.rating && rating == null))
    }

    /** Al elegir otro coleccionista se descarta el aviso anterior; el éxito no se descarta. */
    fun dismissResult() {
        val current = mutableState.value
        if (current.submitting || current.saved) return
        mutableState.value = current.copy(result = null, errors = current.errors.copy(collector = false))
    }

    fun submit() {
        val current = mutableState.value
        if (!current.canSubmit) return
        val collector = activeCollector.value
        val errors = CommentFormValidator.validate(current.description, current.rating, collector)
        if (!errors.valid) {
            mutableState.value = current.copy(errors = errors, result = null)
            return
        }
        val comment = NewComment(current.description.trim(), checkNotNull(current.rating), checkNotNull(collector).id)
        mutableState.value = current.copy(errors = errors, submitting = true, result = null)
        viewModelScope.launch {
            val result = try {
                CommentResult.Saved(albumId, repository.addComment(albumId, comment))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: HttpException) {
                when (error.code()) {
                    400 -> CommentResult.Rejected
                    404 -> CommentResult.NotFound
                    else -> CommentResult.Failed
                }
            } catch (_: IOException) {
                verifyAfterLostResponse(comment)
            } catch (_: Exception) {
                CommentResult.Failed
            }
            mutableState.value = mutableState.value.copy(submitting = false, result = result)
        }
    }

    private suspend fun verifyAfterLostResponse(comment: NewComment): CommentResult = try {
        val match = repository.getComments(albumId).firstOrNull {
            it.description.trim().equals(comment.description, ignoreCase = true) &&
                it.rating == comment.rating &&
                (it.collectorId == null || it.collectorId == comment.collectorId)
        }
        if (match != null) CommentResult.PossiblySaved(albumId, match) else CommentResult.Failed
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        CommentResult.Unconfirmed
    }

    private companion object {
        const val KEY_DESCRIPTION = "comment_description"
        const val KEY_RATING = "comment_rating"
    }
}
