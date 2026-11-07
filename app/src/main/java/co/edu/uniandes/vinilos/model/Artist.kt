package co.edu.uniandes.vinilos.model

enum class ArtistType { MUSICIAN, BAND }

/** La navegación debe conservar id y type: cada tipo usa una ruta distinta. */
data class Artist(
    val id: Int,
    val type: ArtistType,
    val name: String,
    val image: String,
    val description: String,
)
