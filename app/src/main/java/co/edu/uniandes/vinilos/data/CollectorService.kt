package co.edu.uniandes.vinilos.data

import retrofit2.http.GET

interface CollectorService {
    @GET("collectors") suspend fun getCollectors(): List<CollectorDto>
}

data class CollectorDto(
    val id: Int? = null,
    val name: String? = null,
    val email: String? = null,
    val telephone: String? = null,
)
