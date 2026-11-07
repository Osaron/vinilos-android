package co.edu.uniandes.vinilos.data

import co.edu.uniandes.vinilos.model.Album
import co.edu.uniandes.vinilos.model.NewAlbum
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Contrato POST /albums de BackVynils (HU07, #19).
 *
 * El backend valida con Joi los seis campos: todos obligatorios, `cover` es una URL de texto
 * (no hay subida de archivos), `releaseDate` llega como `yyyy-MM-dd`, y `genre` y
 * `recordLabel` pertenecen a listas cerradas. Responde 200 con el álbum creado o 400 si algo
 * no es válido.
 */
interface AlbumCreationService {
    @POST("albums") suspend fun createAlbum(@Body album: CreateAlbumRequestDto): AlbumDetailDto
}

/** Cuerpo exacto que espera POST /albums. */
data class CreateAlbumRequestDto(
    val name: String,
    val cover: String,
    val releaseDate: String,
    val description: String,
    val genre: String,
    val recordLabel: String,
)

/**
 * Service Adapter de creación: traduce el modelo del dominio al DTO de transporte y la
 * respuesta del backend a [Album], sin que los DTO lleguen a la UI.
 *
 * Los errores no se ocultan: un 400/5xx se propaga como `HttpException`, la falta de red como
 * `IOException` y una respuesta sin identificador como `IllegalArgumentException`, para que el
 * ViewModel conserve los datos del formulario y permita reintentar.
 */
class AlbumCreationServiceAdapter(private val service: AlbumCreationService) {
    suspend fun createAlbum(album: NewAlbum): Album {
        val dto = service.createAlbum(album.toRequest())
        val id = requireNotNull(dto.id) { "El backend no devolvió el identificador del álbum creado" }
        val name = dto.name?.trim().orEmpty()
        require(id > 0 && name.isNotEmpty()) { "Respuesta de creación de álbum inválida" }
        return Album(
            id = id,
            name = name,
            cover = dto.cover.orEmpty(),
            performers = dto.performers.orEmpty().mapNotNull { it.name?.trim()?.takeIf(String::isNotEmpty) },
            genre = dto.genre.orEmpty(),
        )
    }

    private fun NewAlbum.toRequest() = CreateAlbumRequestDto(
        name = name,
        cover = cover,
        releaseDate = releaseDate,
        description = description,
        genre = genre,
        recordLabel = recordLabel,
    )
}

/** Implementación real del contrato acordado con el ViewModel de creación. */
class RemoteAlbumCreationRepository(
    private val adapter: AlbumCreationServiceAdapter,
) : AlbumCreationRepository {
    override suspend fun createAlbum(album: NewAlbum): Album = adapter.createAlbum(album)
}
