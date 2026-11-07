package co.edu.uniandes.vinilos.data

import co.edu.uniandes.vinilos.model.Collector

fun interface CollectorRepository {
    suspend fun getCollectors(): List<Collector>
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
