package co.edu.uniandes.vinilos.data

import retrofit2.http.GET
import retrofit2.http.Path

/** Endpoints de detalle que expone BackVynils para cada tipo de performer. */
interface ArtistDetailService {
    @GET("musicians/{musicianId}")
    suspend fun getMusician(@Path("musicianId") musicianId: Int): ArtistDetailDto

    @GET("bands/{bandId}")
    suspend fun getBand(@Path("bandId") bandId: Int): ArtistDetailDto
}

data class ArtistDetailDto(
    val id: Int? = null,
    val name: String? = null,
    val image: String? = null,
    val description: String? = null,
    val birthDate: String? = null,
    val creationDate: String? = null,
    val albums: List<ArtistAlbumDto>? = null,
)

data class ArtistAlbumDto(
    val id: Int? = null,
    val name: String? = null,
    val cover: String? = null,
    val releaseDate: String? = null,
    val recordLabel: String? = null,
    val genre: String? = null,
)
