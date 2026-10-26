package co.edu.uniandes.vinilos.data

import retrofit2.http.GET
import retrofit2.http.Path

/** Contrato GET /albums de BackVynils. Los campos no usados se ignoran. */
interface AlbumService {
    @GET("albums") suspend fun getAlbums(): List<AlbumDto>
    @GET("albums/{albumId}") suspend fun getAlbum(@Path("albumId") albumId: Int): AlbumDetailDto
}

data class PerformerDto(val name: String? = null)
data class AlbumDto(
    val id: Int? = null,
    val name: String? = null,
    val cover: String? = null,
    val genre: String? = null,
    val performers: List<PerformerDto>? = null,
)

data class TrackDto(
    val id: Int? = null,
    val name: String? = null,
    val duration: String? = null,
)

data class AlbumDetailDto(
    val id: Int? = null,
    val name: String? = null,
    val cover: String? = null,
    val releaseDate: String? = null,
    val description: String? = null,
    val genre: String? = null,
    val recordLabel: String? = null,
    val performers: List<PerformerDto>? = null,
    val tracks: List<TrackDto>? = null,
)
