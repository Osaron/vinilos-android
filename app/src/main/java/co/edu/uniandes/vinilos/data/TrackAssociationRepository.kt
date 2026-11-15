package co.edu.uniandes.vinilos.data

import co.edu.uniandes.vinilos.model.NewTrack
import co.edu.uniandes.vinilos.model.Track

/**
 * Contrato de HU08 (#33) entre el flujo de envío (#34) y la capa de datos.
 *
 * [addTrack] devuelve la canción que el backend confirmó para [albumId] o lanza una
 * [TrackAssociationException] con el motivo. Las pruebas pueden usar un lambda como doble,
 * porque es una `fun interface`.
 */
fun interface TrackAssociationRepository {
    suspend fun addTrack(albumId: Int, track: NewTrack): Track

    /**
     * Canciones actuales del álbum. Sirve para revisar el resultado cuando se perdió la respuesta
     * de un envío ([TrackAssociationException.Reason.NETWORK]) antes de ofrecer un nuevo intento.
     */
    suspend fun getTracks(albumId: Int): List<Track> =
        throw UnsupportedOperationException("Consulta de canciones no disponible")
}

/** Error controlado de la asociación. [reason] permite a la UI elegir el mensaje y la acción. */
class TrackAssociationException(
    val reason: Reason,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    enum class Reason {
        /** HTTP 404: el álbum ya no existe. La UI debe pedir otro álbum. */
        ALBUM_NOT_FOUND,

        /** HTTP 400 u otro 4xx: el backend rechazó los datos. Se conserva el borrador. */
        REJECTED,

        /** HTTP 5xx: falla del servidor. Se puede reintentar. */
        SERVER,

        /**
         * Sin respuesta (sin red, tiempo agotado o conexión cortada). El resultado es incierto:
         * consultar [TrackAssociationRepository.getTracks] antes de reenviar.
         */
        NETWORK,

        /** El backend respondió 200 sin una canción válida para el álbum pedido. */
        INVALID_RESPONSE,
    }
}
