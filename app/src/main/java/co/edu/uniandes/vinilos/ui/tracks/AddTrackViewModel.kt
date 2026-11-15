package co.edu.uniandes.vinilos.ui.tracks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import co.edu.uniandes.vinilos.data.TrackAssociationException
import co.edu.uniandes.vinilos.data.TrackAssociationException.Reason
import co.edu.uniandes.vinilos.data.TrackAssociationRepository
import co.edu.uniandes.vinilos.model.NewTrack
import co.edu.uniandes.vinilos.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Resultado del último intento de guardar la canción. */
sealed interface AddTrackResult {
    /** Guardada: la vista vuelve al detalle de [albumId] y recarga sus canciones. */
    data class Saved(val albumId: Int, val track: Track) : AddTrackResult

    /** Falló sin guardar (rechazo, error del servidor o respuesta inválida). El borrador se conserva. */
    data object Failed : AddTrackResult

    /** El álbum dejó de existir: el coleccionista debe elegir otro antes de reintentar. */
    data object AlbumMissing : AddTrackResult

    /**
     * Se perdió la respuesta y el álbum ya tiene una canción con el mismo nombre y duración. Puede
     * ser la que se acaba de enviar o una anterior: la app no reenvía sola y deja decidir al usuario.
     */
    data class PossiblySaved(val albumId: Int, val match: Track) : AddTrackResult

    /** Se perdió la respuesta y tampoco se pudo consultar el álbum. Se informa antes de reintentar. */
    data object Unconfirmed : AddTrackResult
}

data class AddTrackState(
    /** Álbum del último envío, tomado de `TRACK_READY` (#31). Se conserva por ID. */
    val albumId: Int? = null,
    val submitting: Boolean = false,
    val result: AddTrackResult? = null,
) {
    val saved: Boolean get() = result is AddTrackResult.Saved
    val canSubmit: Boolean get() = !submitting && !saved
}

/**
 * Estados de envío, éxito y error de HU08 (#34).
 *
 * La selección del álbum (#31) y el formulario (#32) entregan `albumId` y canción con
 * [submit]; este ViewModel envía con el contrato de #33 ([TrackAssociationRepository]), evita
 * duplicados y decide qué mostrar después:
 * - Una sola solicitud a la vez y ninguna después de guardar, aunque se pulse varias veces.
 * - Ante un error el borrador queda en el formulario y se puede reintentar.
 * - [Reason.ALBUM_NOT_FOUND]: pide elegir otro álbum.
 * - [Reason.NETWORK]: la respuesta pudo perderse, así que consulta las canciones del álbum antes
 *   de ofrecer otro envío.
 */
class AddTrackViewModel(private val repository: TrackAssociationRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(AddTrackState())
    val state = mutableState.asStateFlow()

    fun submit(albumId: Int, track: NewTrack) {
        val current = mutableState.value
        if (!current.canSubmit || albumId <= 0) return
        mutableState.value = current.copy(albumId = albumId, submitting = true, result = null)
        viewModelScope.launch {
            val result = try {
                AddTrackResult.Saved(albumId, repository.addTrack(albumId, track))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: TrackAssociationException) {
                when (error.reason) {
                    Reason.ALBUM_NOT_FOUND -> AddTrackResult.AlbumMissing
                    Reason.NETWORK -> verifyAfterLostResponse(albumId, track)
                    Reason.REJECTED, Reason.SERVER, Reason.INVALID_RESPONSE -> AddTrackResult.Failed
                }
            } catch (_: Exception) {
                AddTrackResult.Failed
            }
            mutableState.value = mutableState.value.copy(submitting = false, result = result)
        }
    }

    /** Al elegir otro álbum se descarta el aviso anterior; el éxito no se descarta. */
    fun dismissResult() {
        val current = mutableState.value
        if (current.submitting || current.saved || current.result == null) return
        mutableState.value = current.copy(result = null)
    }

    private suspend fun verifyAfterLostResponse(albumId: Int, track: NewTrack): AddTrackResult = try {
        val match = repository.getTracks(albumId).firstOrNull {
            it.name.trim().equals(track.name.trim(), ignoreCase = true) && it.duration.trim() == track.duration.trim()
        }
        if (match != null) AddTrackResult.PossiblySaved(albumId, match) else AddTrackResult.Failed
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        AddTrackResult.Unconfirmed
    }

    class Factory(private val repository: TrackAssociationRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AddTrackViewModel(repository) as T
    }
}
