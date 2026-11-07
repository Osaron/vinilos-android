package co.edu.uniandes.vinilos.data

import retrofit2.http.GET

/** BackVynils expone músicos y bandas por separado; no existe GET /artists. */
interface ArtistService {
    @GET("musicians") suspend fun getMusicians(): List<ArtistDto>
    @GET("bands") suspend fun getBands(): List<ArtistDto>
}

data class ArtistDto(
    val id: Int? = null,
    val name: String? = null,
    val image: String? = null,
    val description: String? = null,
)
