package co.edu.uniandes.vinilos.data

import co.edu.uniandes.vinilos.data.TrackAssociationException.Reason
import co.edu.uniandes.vinilos.model.NewTrack
import co.edu.uniandes.vinilos.model.Track
import kotlinx.coroutines.CompletableDeferred

/**
 * Doble de prueba de HU08 (#33) para el formulario (#32) y los estados de envío (#34/#35).
 *
 * Registra cada solicitud con su `albumId`, guarda las canciones por álbum y permite programar
 * el siguiente resultado: [failNext] simula un error del backend y [holdNext] deja la solicitud
 * pendiente hasta completar el `CompletableDeferred`, útil para probar el doble toque.
 */
class FakeTrackAssociationRepository(
    private val existingAlbums: Set<Int> = setOf(100, 101),
) : TrackAssociationRepository {
    val requests = mutableListOf<Pair<Int, NewTrack>>()
    private val tracksByAlbum = mutableMapOf<Int, MutableList<Track>>()
    private var nextFailure: Reason? = null
    private var nextGate: CompletableDeferred<Unit>? = null
    private var nextId = 1

    fun failNext(reason: Reason) { nextFailure = reason }
    fun holdNext(): CompletableDeferred<Unit> = CompletableDeferred<Unit>().also { nextGate = it }

    override suspend fun addTrack(albumId: Int, track: NewTrack): Track {
        requests += albumId to track
        nextGate?.also { nextGate = null }?.await()
        nextFailure?.let { nextFailure = null; throw TrackAssociationException(it, "Fallo simulado: $it") }
        if (albumId !in existingAlbums) throw TrackAssociationException(Reason.ALBUM_NOT_FOUND, "Álbum $albumId no existe")
        return Track(nextId++, track.name.trim(), track.duration.trim())
            .also { tracksByAlbum.getOrPut(albumId) { mutableListOf() } += it }
    }

    override suspend fun getTracks(albumId: Int): List<Track> {
        if (albumId !in existingAlbums) throw TrackAssociationException(Reason.ALBUM_NOT_FOUND, "Álbum $albumId no existe")
        return tracksByAlbum[albumId].orEmpty().toList()
    }
}
