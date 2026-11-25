package co.edu.uniandes.vinilos.data

import co.edu.uniandes.vinilos.model.ArtistPrize
import co.edu.uniandes.vinilos.model.ArtistType
import co.edu.uniandes.vinilos.model.Prize
import co.edu.uniandes.vinilos.model.PrizeDate
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * HU14: premios de un músico o una banda.
 *
 * Contrato comprobado con BackVynils aislado (commit 7178f83):
 * - `POST /prizes/{prizeId}/musicians|bands/{id}` con `{"premiationDate":"yyyy-MM-dd"}` responde 200
 *   con el premio y el artista. Sin fecha responde 500 y con un texto que no es fecha, 400.
 * - El backend no evita repetidos: dos envíos iguales crean dos entregas. Tampoco comprueba que el
 *   ID de la ruta `musicians` sea de un músico. Por eso la app lee primero al artista por la ruta de
 *   su tipo (que sí responde 404 si el tipo no coincide) y no reenvía una entrega que ya existe.
 * - El detalle del artista solo trae `performerPrizes` con `id` y `premiationDate`; el nombre del
 *   premio se obtiene cruzando esos IDs con `GET /prizes`.
 */
interface ArtistPrizeService {
    @GET("prizes")
    suspend fun getPrizes(): List<PrizeDto>

    @GET("musicians/{musicianId}")
    suspend fun getMusician(@Path("musicianId") musicianId: Int): PerformerPrizesDto

    @GET("bands/{bandId}")
    suspend fun getBand(@Path("bandId") bandId: Int): PerformerPrizesDto

    @POST("prizes/{prizeId}/musicians/{musicianId}")
    suspend fun awardMusician(
        @Path("prizeId") prizeId: Int,
        @Path("musicianId") musicianId: Int,
        @Body body: PremiationDto,
    ): PerformerPrizeDto

    @POST("prizes/{prizeId}/bands/{bandId}")
    suspend fun awardBand(
        @Path("prizeId") prizeId: Int,
        @Path("bandId") bandId: Int,
        @Body body: PremiationDto,
    ): PerformerPrizeDto
}

data class PrizeDto(
    val id: Int? = null,
    val name: String? = null,
    val organization: String? = null,
    val performerPrizes: List<PerformerPrizeRefDto>? = null,
)

data class PerformerPrizeRefDto(val id: Int? = null, val premiationDate: String? = null)

data class PerformerPrizesDto(val id: Int? = null, val performerPrizes: List<PerformerPrizeRefDto>? = null)

data class PremiationDto(val premiationDate: String)

data class PerformerPrizeDto(
    val id: Int? = null,
    val premiationDate: String? = null,
    val prize: EntityRefDto? = null,
    val performer: EntityRefDto? = null,
)

data class EntityRefDto(val id: Int? = null)

interface ArtistPrizeRepository {
    /** Catálogo de premios que se pueden asignar. */
    suspend fun getPrizes(): List<Prize>

    /** Premios que ya recibió el artista, del más reciente al más antiguo. */
    suspend fun getArtistPrizes(type: ArtistType, artistId: Int): List<ArtistPrize>

    /** Registra la entrega. [date] es `yyyy-MM-dd`. Propaga HttpException e IOException. */
    suspend fun assignPrize(type: ArtistType, artistId: Int, prizeId: Int, date: String)
}

/** Service Adapter: valida identificadores, fecha y confirmación; no expone DTOs a la UI. */
class ArtistPrizeServiceAdapter(private val service: ArtistPrizeService) {
    suspend fun getPrizes(): List<Prize> =
        service.getPrizes().mapNotNull(::toPrize).distinctBy { it.id }.sortedBy { it.name.lowercase() }

    suspend fun getArtistPrizes(type: ArtistType, artistId: Int): List<ArtistPrize> {
        require(artistId > 0) { "Identificador de artista inválido" }
        val performer = when (type) {
            ArtistType.MUSICIAN -> service.getMusician(artistId)
            ArtistType.BAND -> service.getBand(artistId)
        }
        require(performer.id == artistId) { "La respuesta no corresponde al artista solicitado" }
        val own = performer.performerPrizes.orEmpty().mapNotNull { it.id }.toSet()
        if (own.isEmpty()) return emptyList()
        return service.getPrizes().flatMap { dto ->
            val prize = toPrize(dto) ?: return@flatMap emptyList()
            dto.performerPrizes.orEmpty().mapNotNull { award ->
                val awardId = award.id?.takeIf { it in own } ?: return@mapNotNull null
                val date = PrizeDate.fromBackend(award.premiationDate) ?: return@mapNotNull null
                ArtistPrize(awardId, prize.id, prize.name, prize.organization, date)
            }
        }.distinctBy { it.id }.sortedWith(compareByDescending<ArtistPrize> { it.date }.thenBy { it.name.lowercase() })
    }

    suspend fun assignPrize(type: ArtistType, artistId: Int, prizeId: Int, date: String) {
        require(artistId > 0 && prizeId > 0) { "Identificadores inválidos" }
        require(PrizeDate.isIso(date)) { "La fecha debe ser yyyy-MM-dd" }
        val response = when (type) {
            ArtistType.MUSICIAN -> service.awardMusician(prizeId, artistId, PremiationDto(date))
            ArtistType.BAND -> service.awardBand(prizeId, artistId, PremiationDto(date))
        }
        require(response.prize?.id == prizeId) { "La confirmación no corresponde al premio solicitado" }
        require(response.performer?.id == artistId) { "La confirmación no corresponde al artista solicitado" }
    }

    private fun toPrize(dto: PrizeDto): Prize? {
        val id = dto.id?.takeIf { it > 0 } ?: return null
        val name = dto.name?.trim().orEmpty()
        if (name.isEmpty()) return null
        return Prize(id, name, dto.organization?.trim().orEmpty())
    }
}

class RemoteArtistPrizeRepository(private val adapter: ArtistPrizeServiceAdapter) : ArtistPrizeRepository {
    override suspend fun getPrizes() = adapter.getPrizes()
    override suspend fun getArtistPrizes(type: ArtistType, artistId: Int) = adapter.getArtistPrizes(type, artistId)
    override suspend fun assignPrize(type: ArtistType, artistId: Int, prizeId: Int, date: String) =
        adapter.assignPrize(type, artistId, prizeId, date)
}
