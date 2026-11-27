package co.edu.uniandes.vinilos.data

import co.edu.uniandes.vinilos.model.ArtistType
import co.edu.uniandes.vinilos.model.FavoritePerformer
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Contrato de artistas favoritos de BackVynils (HU10, #86).
 *
 * Músicos y bandas usan rutas distintas para asociarse al coleccionista; ambas responden 200 con
 * el artista asociado y 404 si el coleccionista o el artista no existen. El backend no impide
 * agregar dos veces el mismo artista: esa regla la aplica la app antes de enviar.
 */
interface FavoriteArtistService {
    @GET("collectors/{collectorId}/performers")
    suspend fun getFavorites(@Path("collectorId") collectorId: Int): List<FavoritePerformerDto>

    @POST("collectors/{collectorId}/musicians/{musicianId}")
    suspend fun addMusician(
        @Path("collectorId") collectorId: Int,
        @Path("musicianId") musicianId: Int,
    ): FavoritePerformerDto

    @POST("collectors/{collectorId}/bands/{bandId}")
    suspend fun addBand(@Path("collectorId") collectorId: Int, @Path("bandId") bandId: Int): FavoritePerformerDto
}

/** Favoritos del coleccionista activo, sin exponer Retrofit ni DTO a la UI. */
interface FavoriteArtistRepository {
    suspend fun getFavorites(collectorId: Int): List<FavoritePerformer>
    suspend fun addFavorite(collectorId: Int, artistId: Int, type: ArtistType): FavoritePerformer
}

/**
 * Service Adapter de favoritos. Elige la ruta según el tipo de artista y propaga los errores
 * (`HttpException` para 404/5xx, `IOException` sin red) para que la pantalla permita reintentar.
 * Nunca reintenta el POST por su cuenta.
 */
class FavoriteArtistServiceAdapter(private val service: FavoriteArtistService) {
    suspend fun getFavorites(collectorId: Int): List<FavoritePerformer> =
        service.getFavorites(collectorId).mapNotNull { it.toModel() }

    suspend fun addFavorite(collectorId: Int, artistId: Int, type: ArtistType): FavoritePerformer {
        val dto = when (type) {
            ArtistType.MUSICIAN -> service.addMusician(collectorId, artistId)
            ArtistType.BAND -> service.addBand(collectorId, artistId)
        }
        val favorite = requireNotNull(dto.toModel()) { "Respuesta de favorito inválida" }
        require(favorite.id == artistId) { "El backend asoció un artista distinto al solicitado" }
        return favorite
    }

    private fun FavoritePerformerDto.toModel(): FavoritePerformer? {
        val performerId = id?.takeIf { it > 0 } ?: return null
        val performerName = name?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return FavoritePerformer(
            id = performerId,
            name = performerName,
            image = image?.trim()?.takeIf(String::isNotEmpty),
            description = description.orEmpty(),
            date = birthDate ?: creationDate,
        )
    }
}

class RemoteFavoriteArtistRepository(private val adapter: FavoriteArtistServiceAdapter) : FavoriteArtistRepository {
    override suspend fun getFavorites(collectorId: Int) = onIo { adapter.getFavorites(collectorId) }
    override suspend fun addFavorite(collectorId: Int, artistId: Int, type: ArtistType) =
        onIo { adapter.addFavorite(collectorId, artistId, type) }
}
