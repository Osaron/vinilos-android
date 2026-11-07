package co.edu.uniandes.vinilos.data

import co.edu.uniandes.vinilos.model.Artist
import co.edu.uniandes.vinilos.model.ArtistType

fun interface ArtistRepository {
    suspend fun getArtists(): List<Artist>
}

/** Solo publica un catálogo completo. Un fallo no se convierte en lista vacía. */
class ArtistServiceAdapter(private val service: ArtistService) {
    suspend fun getArtists(): List<Artist> {
        val musicians = service.getMusicians().map { it.toArtist(ArtistType.MUSICIAN) }
        val bands = service.getBands().map { it.toArtist(ArtistType.BAND) }
        return musicians + bands
    }

    private fun ArtistDto.toArtist(type: ArtistType): Artist {
        val artistId = requireNotNull(id) { "Artista sin identificador" }
        val artistName = name?.trim().orEmpty()
        require(artistId > 0 && artistName.isNotEmpty()) { "Artista inválido" }
        return Artist(artistId, type, artistName, image.orEmpty(), description.orEmpty())
    }
}

class RemoteArtistRepository(private val adapter: ArtistServiceAdapter) : ArtistRepository {
    override suspend fun getArtists(): List<Artist> = adapter.getArtists()
}
