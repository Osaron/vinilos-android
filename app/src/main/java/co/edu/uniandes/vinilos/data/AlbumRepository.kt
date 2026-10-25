package co.edu.uniandes.vinilos.data

import co.edu.uniandes.vinilos.model.Album
import co.edu.uniandes.vinilos.model.AlbumDetail
import co.edu.uniandes.vinilos.model.Track

fun interface AlbumRepository {
    suspend fun getAlbums(): List<Album>
    suspend fun getAlbum(albumId: Int): AlbumDetail =
        throw UnsupportedOperationException("Detalle de álbum no disponible")
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

    suspend fun getAlbum(albumId: Int): AlbumDetail {
        require(albumId > 0) { "Identificador de álbum inválido" }
        val dto = service.getAlbum(albumId)
        val id = requireNotNull(dto.id) { "Álbum sin identificador" }
        val name = requireNotNull(dto.name).trim()
        require(id == albumId && name.isNotEmpty()) { "Detalle de álbum inválido" }
        return AlbumDetail(
            id = id,
            name = name,
            cover = dto.cover.orEmpty(),
            releaseDate = dto.releaseDate.orEmpty(),
            description = dto.description.orEmpty(),
            genre = dto.genre.orEmpty(),
            recordLabel = dto.recordLabel.orEmpty(),
            performers = dto.performers.orEmpty()
                .mapNotNull { it.name?.trim()?.takeIf(String::isNotEmpty) },
            tracks = dto.tracks.orEmpty().mapNotNull { track ->
                val trackName = track.name?.trim().orEmpty()
                if (trackName.isEmpty()) null else Track(track.id, trackName, track.duration.orEmpty())
            },
        )
    }
}

class RemoteAlbumRepository(private val adapter: AlbumServiceAdapter) : AlbumRepository {
    override suspend fun getAlbums(): List<Album> = adapter.getAlbums()
    override suspend fun getAlbum(albumId: Int): AlbumDetail = adapter.getAlbum(albumId)
}
