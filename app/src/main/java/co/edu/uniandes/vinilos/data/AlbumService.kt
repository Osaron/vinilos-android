package co.edu.uniandes.vinilos.data

import retrofit2.http.GET

/** Contrato GET /albums de BackVynils. Los campos no usados se ignoran. */
interface AlbumService {
    @GET("albums") suspend fun getAlbums(): List<AlbumDto>
}

data class PerformerDto(val name: String? = null)
data class AlbumDto(
    val id: Int? = null,
    val name: String? = null,
    val cover: String? = null,
    val genre: String? = null,
    val performers: List<PerformerDto>? = null,
)
