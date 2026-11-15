package co.edu.uniandes.vinilos.model

/**
 * Canción lista para asociarse a un álbum con POST /albums/{albumId}/tracks (HU08, #33).
 *
 * Se construye a partir del formulario validado de #32; la duración llega como texto
 * (por ejemplo `3:45`) porque BackVynils la guarda como cadena.
 */
data class NewTrack(
    val name: String,
    val duration: String,
)
