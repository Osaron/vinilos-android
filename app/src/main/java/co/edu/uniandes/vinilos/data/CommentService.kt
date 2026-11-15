package co.edu.uniandes.vinilos.data

import co.edu.uniandes.vinilos.model.AlbumComment
import co.edu.uniandes.vinilos.model.NewComment
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Contrato de comentarios de BackVynils (HU09, #81).
 *
 * `POST /albums/{albumId}/comments` exige `description` y un `rating` numérico entre 0 y 5, y
 * asocia el comentario al coleccionista de `collector.id`. Responde 200 con el comentario guardado,
 * 400 si los datos no son válidos y 404 si el álbum o el coleccionista no existen.
 */
interface CommentService {
    @GET("albums/{albumId}/comments")
    suspend fun getComments(@Path("albumId") albumId: Int): List<CommentDto>

    @POST("albums/{albumId}/comments")
    suspend fun addComment(@Path("albumId") albumId: Int, @Body comment: CommentRequestDto): CommentDto
}

/** Cuerpo exacto que espera POST /albums/{albumId}/comments. */
data class CommentRequestDto(
    val description: String,
    val rating: Int,
    val collector: CollectorRefDto,
)

data class CollectorRefDto(val id: Int)

data class CommentDto(
    val id: Int? = null,
    val description: String? = null,
    val rating: Int? = null,
    val collector: CollectorDto? = null,
)

/** Lectura y escritura de comentarios de un álbum, sin exponer Retrofit ni DTO a la UI. */
interface CommentRepository {
    suspend fun getComments(albumId: Int): List<AlbumComment>
    suspend fun addComment(albumId: Int, comment: NewComment): AlbumComment
}

/**
 * Service Adapter de comentarios. Los errores no se ocultan: un 400/404/5xx se propaga como
 * `HttpException` y la falta de red como `IOException`, para que el ViewModel conserve lo escrito
 * y permita reintentar. Nunca reintenta el POST por su cuenta: una respuesta perdida podría haber
 * guardado el comentario.
 */
class CommentServiceAdapter(private val service: CommentService) {
    suspend fun getComments(albumId: Int): List<AlbumComment> =
        service.getComments(albumId).mapNotNull { it.toModel() }

    suspend fun addComment(albumId: Int, comment: NewComment): AlbumComment {
        val dto = service.addComment(
            albumId,
            CommentRequestDto(comment.description, comment.rating, CollectorRefDto(comment.collectorId)),
        )
        return requireNotNull(dto.toModel(fallbackCollectorId = comment.collectorId)) {
            "Respuesta de creación de comentario inválida"
        }
    }

    private fun CommentDto.toModel(fallbackCollectorId: Int? = null): AlbumComment? {
        val commentId = id?.takeIf { it > 0 } ?: return null
        val text = description?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val score = rating?.takeIf { it in 0..5 } ?: return null
        return AlbumComment(commentId, text, score, collector?.id ?: fallbackCollectorId)
    }
}

class RemoteCommentRepository(private val adapter: CommentServiceAdapter) : CommentRepository {
    override suspend fun getComments(albumId: Int) = adapter.getComments(albumId)
    override suspend fun addComment(albumId: Int, comment: NewComment) = adapter.addComment(albumId, comment)
}
