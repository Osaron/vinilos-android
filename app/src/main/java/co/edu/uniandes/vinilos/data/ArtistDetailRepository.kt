package co.edu.uniandes.vinilos.data

import co.edu.uniandes.vinilos.model.ArtistAlbum
import co.edu.uniandes.vinilos.model.ArtistDetail
import co.edu.uniandes.vinilos.model.ArtistType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun interface ArtistDetailRepository {
    suspend fun getArtistDetail(type: ArtistType, artistId: Int): ArtistDetail
}

/** El adaptador valida la identidad de respuesta y evita exponer DTOs a la UI. */
class ArtistDetailServiceAdapter(
    private val service: ArtistDetailService,
    private val mappingDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    suspend fun getArtistDetail(type: ArtistType, artistId: Int): ArtistDetail {
        require(artistId > 0) { "Identificador de artista inválido" }
        val dto = when (type) {
            ArtistType.MUSICIAN -> service.getMusician(artistId)
            ArtistType.BAND -> service.getBand(artistId)
        }
        return withContext(mappingDispatcher) {
            val id = requireNotNull(dto.id) { "Detalle de artista sin identificador" }
            val name = dto.name?.trim().orEmpty()
            require(id == artistId && name.isNotEmpty()) { "Detalle de artista inválido" }

            ArtistDetail(
                id = id,
                type = type,
                name = name,
                image = dto.image?.trim()?.takeIf(String::isNotEmpty),
                description = dto.description.orEmpty(),
                birthDate = dto.birthDate,
                creationDate = dto.creationDate,
                albums = dto.albums.orEmpty().mapNotNull { album ->
                    val albumId = album.id ?: return@mapNotNull null
                    val albumName = album.name?.trim().orEmpty()
                    if (albumId <= 0 || albumName.isEmpty()) return@mapNotNull null
                    ArtistAlbum(
                        id = albumId,
                        name = albumName,
                        cover = album.cover?.trim()?.takeIf(String::isNotEmpty),
                        releaseDate = album.releaseDate,
                        recordLabel = album.recordLabel,
                        genre = album.genre,
                    )
                },
            )
        }
    }
}

class RemoteArtistDetailRepository(
    private val adapter: ArtistDetailServiceAdapter,
) : ArtistDetailRepository {
    override suspend fun getArtistDetail(type: ArtistType, artistId: Int): ArtistDetail =
        onIo { adapter.getArtistDetail(type, artistId) }
}
