package co.edu.uniandes.vinilos.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface CollectorAlbumService {
    @GET("collectors/{collectorId}/albums")
    suspend fun getAlbums(@Path("collectorId") collectorId: Int): List<CollectorAlbumDto>

    @POST("collectors/{collectorId}/albums/{albumId}")
    suspend fun addAlbum(
        @Path("collectorId") collectorId: Int,
        @Path("albumId") albumId: Int,
        @Body request: AddCollectorAlbumRequest,
    ): CollectorAlbumDto
}

data class AddCollectorAlbumRequest(val price: Int, val status: String)

data class CollectorAlbumDto(
    val album: CollectorAlbumReferenceDto? = null,
    val collector: CollectorAlbumReferenceDto? = null,
)

data class CollectorAlbumReferenceDto(val id: Int? = null, val name: String? = null)

enum class CollectorAlbumStatus(val apiValue: String) {
    ACTIVE("Active"),
    INACTIVE("Inactive"),
}

class CollectorAlbumRepository(private val service: CollectorAlbumService) {
    suspend fun getAlbumIds(collectorId: Int): Set<Int> {
        require(collectorId > 0) { "Identificador de coleccionista inválido" }
        return onIo { service.getAlbums(collectorId) }.mapTo(mutableSetOf()) { relation ->
            val albumId = relation.album?.id
            require(albumId != null && albumId > 0) { "Relación sin álbum válido" }
            albumId
        }
    }

    suspend fun getAlbumNames(collectorId: Int): List<String> {
        require(collectorId > 0) { "Identificador de coleccionista inválido" }
        return onIo { service.getAlbums(collectorId) }.mapNotNull { it.album?.name?.takeIf(String::isNotBlank) }
    }

    suspend fun addAlbum(collectorId: Int, albumId: Int, price: Int, status: CollectorAlbumStatus) {
        require(collectorId > 0 && albumId > 0) { "Identificadores inválidos" }
        require(price >= 0) { "El precio debe ser un entero no negativo" }
        val relation = onIo { service.addAlbum(collectorId, albumId, AddCollectorAlbumRequest(price, status.apiValue)) }
        require(relation.album?.id == albumId && relation.collector?.id == collectorId) {
            "La confirmación no corresponde a la colección y el álbum solicitados"
        }
    }
}
