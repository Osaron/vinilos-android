package co.edu.uniandes.vinilos.model

/**
 * Álbum listo para enviarse a POST /albums de BackVynils (HU07).
 *
 * Solo se construye a partir de un formulario válido (ver AlbumFormValidator), así que sus
 * valores ya cumplen las restricciones del backend.
 *
 * @property releaseDate fecha en formato ISO `yyyy-MM-dd`.
 */
data class NewAlbum(
    val name: String,
    val cover: String,
    val releaseDate: String,
    val description: String,
    val genre: String,
    val recordLabel: String,
)

/** Valores que acepta BackVynils para los campos con lista cerrada (validación Joi del backend). */
object AlbumAllowedValues {
    val GENRES = listOf("Classical", "Salsa", "Rock", "Folk")
    val RECORD_LABELS = listOf("Sony Music", "EMI", "Discos Fuentes", "Elektra", "Fania Records")
}
