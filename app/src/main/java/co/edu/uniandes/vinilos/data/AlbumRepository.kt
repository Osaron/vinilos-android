package co.edu.uniandes.vinilos.data

import co.edu.uniandes.vinilos.model.Album

fun interface AlbumRepository {
    suspend fun getAlbums(): List<Album>
}

/** Service Adapter: evita que DTOs de transporte lleguen a la UI. */
class AlbumServiceAdapter(private val service: AlbumService) {
    suspend fun getAlbums(): List<Album> = service.getAlbums().map { dto ->
        val id = requireNotNull(dto.id) { "Álbum sin identificador" }
        val name = requireNotNull(dto.name).trim()
        require(id > 0 && name.isNotEmpty()) { "Álbum inválido" }
        Album(id, name, dto.cover.orEmpty(),
            dto.performers.orEmpty().mapNotNull { it.name?.trim()?.takeIf(String::isNotEmpty) },
            dto.genre.orEmpty())
    }
}

class RemoteAlbumRepository(private val adapter: AlbumServiceAdapter) : AlbumRepository {
    override suspend fun getAlbums(): List<Album> = adapter.getAlbums()
}
