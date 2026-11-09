package co.edu.uniandes.vinilos.data

import retrofit2.http.GET
import retrofit2.http.Path

interface CollectorService {
    @GET("collectors") suspend fun getCollectors(): List<CollectorDto>
    @GET("collectors/{collectorId}") suspend fun getCollector(@Path("collectorId") collectorId: Int): CollectorDetailDto
}

data class CollectorDto(
    val id: Int? = null,
    val name: String? = null,
    val email: String? = null,
    val telephone: String? = null,
)

data class CollectorDetailDto(
    val id: Int? = null,
    val name: String? = null,
    val email: String? = null,
    val telephone: String? = null,
    val comments: List<CollectorCommentDto>? = null,
    val favoritePerformers: List<FavoritePerformerDto>? = null,
)

data class CollectorCommentDto(val id: Int? = null, val description: String? = null, val rating: Int? = null)

data class FavoritePerformerDto(
    val id: Int? = null,
    val name: String? = null,
    val image: String? = null,
    val description: String? = null,
    val birthDate: String? = null,
    val creationDate: String? = null,
)
