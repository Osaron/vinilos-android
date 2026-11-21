package co.edu.uniandes.vinilos.data

import co.edu.uniandes.vinilos.model.Artist
import co.edu.uniandes.vinilos.model.ArtistType
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** HU12: BackVynils guarda la banda en el músico y devuelve el músico asociado. */
interface BandMusicianService {
    @POST("bands/{bandId}/musicians/{musicianId}")
    suspend fun addMusician(
        @Path("bandId") bandId: Int,
        @Path("musicianId") musicianId: Int,
    ): BandMusicianResponseDto

    @GET("bands/{bandId}/musicians/")
    suspend fun getMembers(@Path("bandId") bandId: Int): List<ArtistDto>
}

data class BandMusicianResponseDto(
    val id: Int? = null,
    val name: String? = null,
    val image: String? = null,
    val description: String? = null,
    val band: AssociatedBandDto? = null,
)

data class AssociatedBandDto(val id: Int? = null)

interface BandMusicianRepository {
    suspend fun addMusician(bandId: Int, musicianId: Int): Artist
    suspend fun getMembers(bandId: Int): List<Artist>
}

/** Propaga HttpException e IOException para manejar 404 y fallos de red en la pantalla. */
class BandMusicianServiceAdapter(private val service: BandMusicianService) {
    suspend fun addMusician(bandId: Int, musicianId: Int): Artist {
        require(bandId > 0 && musicianId > 0) { "Identificadores inválidos" }
        val response = service.addMusician(bandId, musicianId)
        require(response.id == musicianId && response.band?.id == bandId) {
            "La asociación confirmada no corresponde a la banda y al músico solicitados"
        }
        return musician(response.id, response.name, response.image, response.description)
    }

    suspend fun getMembers(bandId: Int): List<Artist> {
        require(bandId > 0) { "Identificador de banda inválido" }
        return service.getMembers(bandId).map {
            musician(it.id, it.name, it.image, it.description)
        }
    }

    private fun musician(id: Int?, name: String?, image: String?, description: String?): Artist {
        require(id != null && id > 0 && !name.isNullOrBlank()) { "Respuesta de músico inválida" }
        return Artist(id, ArtistType.MUSICIAN, name.trim(), image.orEmpty(), description.orEmpty())
    }
}

class RemoteBandMusicianRepository(private val adapter: BandMusicianServiceAdapter) : BandMusicianRepository {
    override suspend fun addMusician(bandId: Int, musicianId: Int) = adapter.addMusician(bandId, musicianId)
    override suspend fun getMembers(bandId: Int) = adapter.getMembers(bandId)
}
