package co.edu.uniandes.vinilos.data

import co.edu.uniandes.vinilos.model.Collector
import co.edu.uniandes.vinilos.model.CollectorComment
import co.edu.uniandes.vinilos.model.CollectorDetail
import co.edu.uniandes.vinilos.model.FavoritePerformer

fun interface CollectorRepository {
    suspend fun getCollectors(): List<Collector>
}

fun interface CollectorDetailRepository {
    suspend fun getCollectorDetail(id: Int): CollectorDetail
}

class CollectorServiceAdapter(private val service: CollectorService) {
    suspend fun getCollectors(): List<Collector> = service.getCollectors().map { dto ->
        val id = requireNotNull(dto.id) { "Coleccionista sin identificador" }
        val name = dto.name?.trim().orEmpty()
        require(id > 0 && name.isNotEmpty()) { "Coleccionista inválido" }
        Collector(id, name, dto.email.orEmpty(), dto.telephone.orEmpty())
    }
}

class RemoteCollectorRepository(private val adapter: CollectorServiceAdapter) : CollectorRepository {
    override suspend fun getCollectors(): List<Collector> = adapter.getCollectors()
}

class CollectorDetailServiceAdapter(private val service: CollectorService) {
    suspend fun getCollectorDetail(id: Int): CollectorDetail {
        require(id > 0) { "Identificador de coleccionista inválido" }
        val dto = service.getCollector(id)
        val responseId = requireNotNull(dto.id) { "Detalle de coleccionista sin identificador" }
        val name = dto.name?.trim().orEmpty()
        require(responseId == id && name.isNotEmpty()) { "Detalle de coleccionista inválido" }
        return CollectorDetail(
            id = responseId,
            name = name,
            email = dto.email.orEmpty(),
            telephone = dto.telephone.orEmpty(),
            comments = dto.comments.orEmpty().mapNotNull { comment ->
                val commentId = comment.id ?: return@mapNotNull null
                val description = comment.description?.trim().orEmpty()
                if (commentId <= 0 || description.isEmpty()) return@mapNotNull null
                CollectorComment(commentId, description, comment.rating?.takeIf { it in 1..5 })
            },
            favoritePerformers = dto.favoritePerformers.orEmpty().mapNotNull { performer ->
                val performerId = performer.id ?: return@mapNotNull null
                val performerName = performer.name?.trim().orEmpty()
                if (performerId <= 0 || performerName.isEmpty()) return@mapNotNull null
                FavoritePerformer(
                    id = performerId,
                    name = performerName,
                    image = performer.image?.trim()?.takeIf(String::isNotEmpty),
                    description = performer.description.orEmpty(),
                    date = performer.birthDate ?: performer.creationDate,
                )
            },
        )
    }
}

class RemoteCollectorDetailRepository(private val adapter: CollectorDetailServiceAdapter) : CollectorDetailRepository {
    override suspend fun getCollectorDetail(id: Int): CollectorDetail = adapter.getCollectorDetail(id)
}
