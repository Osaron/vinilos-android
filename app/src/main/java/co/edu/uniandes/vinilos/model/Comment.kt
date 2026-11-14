package co.edu.uniandes.vinilos.model

/** Comentario que el coleccionista activo escribe sobre un álbum (HU09). */
data class NewComment(
    val description: String,
    /** BackVynils acepta valores de 0 a 5. */
    val rating: Int,
    /** Coleccionista activo (#80). Nunca un ID fijo en el código. */
    val collectorId: Int,
)

/** Comentario guardado en un álbum. `collectorId` es null cuando el backend no lo incluye. */
data class AlbumComment(
    val id: Int,
    val description: String,
    val rating: Int,
    val collectorId: Int?,
)
