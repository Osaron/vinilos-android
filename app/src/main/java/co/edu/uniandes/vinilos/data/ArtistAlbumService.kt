package co.edu.uniandes.vinilos.data

import co.edu.uniandes.vinilos.model.ArtistType
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * HU15: discografía de un músico o una banda.
 *
 * Para asociar se usan las rutas del lado del álbum (`/albums/{albumId}/musicians|bands/{id}`), que
 * agregan el intérprete conservando los que ya tiene el álbum. La ruta `/bands/{id}/albums/{albumId}`
 * de BackVynils reemplaza a todos los intérpretes del álbum y borraría, por ejemplo, al músico que ya
 * lo tenía en su discografía.
 */
interface ArtistAlbumService {
    @GET("musicians/{musicianId}/albums/")
    suspend fun getMusicianAlbums(@Path("musicianId") musicianId: Int): List<ArtistAlbumRefDto>

    @GET("bands/{bandId}/albums/")
    suspend fun getBandAlbums(@Path("bandId") bandId: Int): List<ArtistAlbumRefDto>

    @POST("albums/{albumId}/musicians/{musicianId}")
    suspend fun addMusician(@Path("albumId") albumId: Int, @Path("musicianId") musicianId: Int): AlbumPerformersDto

    @POST("albums/{albumId}/bands/{bandId}")
    suspend fun addBand(@Path("albumId") albumId: Int, @Path("bandId") bandId: Int): AlbumPerformersDto
}

data class ArtistAlbumRefDto(val id: Int? = null, val name: String? = null)

data class AlbumPerformersDto(
    val id: Int? = null,
    val name: String? = null,
    val performers: List<PerformerRefDto>? = null,
)

data class PerformerRefDto(val id: Int? = null, val name: String? = null)

interface ArtistAlbumRepository {
    /** Identificadores de los álbumes que ya están en la discografía del artista. */
    suspend fun getAlbumIds(type: ArtistType, artistId: Int): Set<Int>

    /** Agrega el álbum a la discografía. Propaga HttpException e IOException para que la UI decida. */
    suspend fun addAlbum(type: ArtistType, artistId: Int, albumId: Int)
}

/** Service Adapter: valida identificadores y la confirmación del backend; no expone DTOs a la UI. */
class ArtistAlbumServiceAdapter(private val service: ArtistAlbumService) {
    suspend fun getAlbumIds(type: ArtistType, artistId: Int): Set<Int> {
        require(artistId > 0) { "Identificador de artista inválido" }
        val albums = when (type) {
            ArtistType.MUSICIAN -> service.getMusicianAlbums(artistId)
            ArtistType.BAND -> service.getBandAlbums(artistId)
        }
        return albums.mapNotNull { album -> album.id?.takeIf { it > 0 } }.toSet()
    }

    suspend fun addAlbum(type: ArtistType, artistId: Int, albumId: Int) {
        require(artistId > 0 && albumId > 0) { "Identificadores inválidos" }
        val response = when (type) {
            ArtistType.MUSICIAN -> service.addMusician(albumId, artistId)
            ArtistType.BAND -> service.addBand(albumId, artistId)
        }
        require(response.id == albumId) { "La confirmación no corresponde al álbum solicitado" }
        val performers = response.performers
        require(performers == null || performers.any { it.id == artistId }) {
            "La confirmación no incluye al artista solicitado"
        }
    }
}

class RemoteArtistAlbumRepository(private val adapter: ArtistAlbumServiceAdapter) : ArtistAlbumRepository {
    override suspend fun getAlbumIds(type: ArtistType, artistId: Int) = onIo { adapter.getAlbumIds(type, artistId) }
    override suspend fun addAlbum(type: ArtistType, artistId: Int, albumId: Int) = onIo { adapter.addAlbum(type, artistId, albumId) }
}
