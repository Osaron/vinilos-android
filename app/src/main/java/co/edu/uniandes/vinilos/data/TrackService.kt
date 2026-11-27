package co.edu.uniandes.vinilos.data

import co.edu.uniandes.vinilos.data.TrackAssociationException.Reason
import co.edu.uniandes.vinilos.model.NewTrack
import co.edu.uniandes.vinilos.model.Track
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.io.IOException

/**
 * Contrato de canciones de BackVynils (HU08, #33), commit 7178f83 de `track.controller.ts`.
 *
 * - `POST /albums/{albumId}/tracks` con `{"name","duration"}` (Joi: ambos cadenas obligatorias).
 *   Responde **200** con la canción guardada, su `id` generado y el `album` asociado; **404** si
 *   el álbum no existe y **400** si falta un campo.
 * - `GET /albums/{albumId}/tracks` devuelve las canciones del álbum o 404.
 */
interface TrackService {
    @POST("albums/{albumId}/tracks")
    suspend fun addTrack(@Path("albumId") albumId: Int, @Body track: AddTrackRequestDto): TrackResponseDto

    @GET("albums/{albumId}/tracks")
    suspend fun getTracks(@Path("albumId") albumId: Int): List<TrackDto>
}

/** Cuerpo exacto que espera POST /albums/{albumId}/tracks. */
data class AddTrackRequestDto(val name: String, val duration: String)

/** Respuesta de POST: la canción guardada y el álbum al que quedó asociada. */
data class TrackResponseDto(
    val id: Int? = null,
    val name: String? = null,
    val duration: String? = null,
    val album: AlbumDto? = null,
)

/**
 * Service Adapter de HU08: traduce [NewTrack] al DTO de transporte, valida que la respuesta
 * corresponda al álbum pedido y convierte los fallos de red y HTTP en [TrackAssociationException],
 * sin que los DTO ni `HttpException` lleguen a la UI. Envía una sola solicitud por llamada.
 */
class TrackServiceAdapter(private val service: TrackService) {
    suspend fun addTrack(albumId: Int, track: NewTrack): Track {
        require(albumId > 0) { "Identificador de álbum inválido" }
        val name = track.name.trim()
        val duration = track.duration.trim()
        require(name.isNotEmpty() && duration.isNotEmpty()) { "La canción necesita nombre y duración" }

        val dto = call { service.addTrack(albumId, AddTrackRequestDto(name, duration)) }
        val id = dto.id ?: invalid("El backend no devolvió el identificador de la canción")
        val savedName = dto.name?.trim().orEmpty()
        if (id <= 0 || savedName.isEmpty()) invalid("Respuesta de canción inválida")
        val savedAlbumId = dto.album?.id
        if (savedAlbumId != null && savedAlbumId != albumId) {
            invalid("La canción quedó asociada al álbum $savedAlbumId en lugar de $albumId")
        }
        return Track(id, savedName, dto.duration?.trim().orEmpty())
    }

    suspend fun getTracks(albumId: Int): List<Track> {
        require(albumId > 0) { "Identificador de álbum inválido" }
        return call { service.getTracks(albumId) }.mapNotNull { dto ->
            val name = dto.name?.trim().orEmpty()
            if (name.isEmpty()) null else Track(dto.id, name, dto.duration?.trim().orEmpty())
        }
    }

    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: HttpException) {
        val reason = when (error.code()) {
            404 -> Reason.ALBUM_NOT_FOUND
            in 500..599 -> Reason.SERVER
            else -> Reason.REJECTED
        }
        throw TrackAssociationException(reason, "HTTP ${error.code()} del servicio de canciones", error)
    } catch (error: IOException) {
        throw TrackAssociationException(Reason.NETWORK, "Sin respuesta del servicio de canciones", error)
    } catch (error: RuntimeException) {
        // JSON con forma inesperada (Gson lanza JsonParseException/IllegalStateException).
        throw TrackAssociationException(Reason.INVALID_RESPONSE, "Respuesta ilegible del servicio de canciones", error)
    }

    private fun invalid(message: String): Nothing =
        throw TrackAssociationException(Reason.INVALID_RESPONSE, message)
}

/** Implementación real del contrato de HU08. */
class RemoteTrackAssociationRepository(
    private val adapter: TrackServiceAdapter,
) : TrackAssociationRepository {
    override suspend fun addTrack(albumId: Int, track: NewTrack): Track = onIo { adapter.addTrack(albumId, track) }
    override suspend fun getTracks(albumId: Int): List<Track> = onIo { adapter.getTracks(albumId) }
}
