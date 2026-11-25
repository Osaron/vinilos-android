package co.edu.uniandes.vinilos.data

import co.edu.uniandes.vinilos.model.Prize
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.io.IOException

/** Los únicos tres campos admitidos al crear. No enviar ID, coleccionista ni asociaciones. */
data class NewPrize(val name: String, val description: String, val organization: String)

interface PrizeService {
    @GET("prizes") suspend fun getPrizes(): List<PrizeDto>
    @POST("prizes") suspend fun createPrize(@Body body: NewPrize): PrizeDto
}

interface PrizeRepository {
    suspend fun getPrizes(): List<Prize>
    suspend fun createPrize(prize: NewPrize): Prize
}

/** Una respuesta 2xx ilegible o inconsistente tampoco permite repetir una escritura a ciegas. */
class PrizeWriteUnconfirmed(cause: Exception) : IOException("No se pudo confirmar la creación", cause)

class PrizeServiceAdapter(private val service: PrizeService) {
    suspend fun getPrizes(): List<Prize> = service.getPrizes().mapNotNull { dto ->
        val id = dto.id?.takeIf { it > 0 } ?: return@mapNotNull null
        val name = dto.name?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        Prize(id, name, dto.organization?.trim().orEmpty(), dto.description.orEmpty())
    }.distinctBy { it.id }.sortedBy { it.name.lowercase(java.util.Locale.ROOT) }

    suspend fun createPrize(prize: NewPrize): Prize {
        val body = prize.copy(name = prize.name.trim(), description = prize.description.trim(), organization = prize.organization.trim())
        require(body.name.isNotEmpty() && body.description.isNotEmpty() && body.organization.isNotEmpty())
        return try {
            val dto = service.createPrize(body)
            require(dto.id != null && dto.id > 0) { "Premio sin identificador" }
            require(dto.name == body.name && dto.description == body.description && dto.organization == body.organization) {
                "Confirmación inconsistente"
            }
            Prize(dto.id, body.name, body.organization, body.description)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (http: HttpException) {
            throw http
        } catch (error: Exception) {
            throw PrizeWriteUnconfirmed(error)
        }
    }
}

class RemotePrizeRepository(private val adapter: PrizeServiceAdapter) : PrizeRepository {
    override suspend fun getPrizes() = adapter.getPrizes()
    override suspend fun createPrize(prize: NewPrize) = adapter.createPrize(prize)
}
