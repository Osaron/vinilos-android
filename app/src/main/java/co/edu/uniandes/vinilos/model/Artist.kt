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

/** Datos del detalle; los campos específicos dependen del tipo de artista. */
data class ArtistDetail(
    val id: Int,
    val type: ArtistType,
    val name: String,
    val image: String?,
    val description: String,
    val birthDate: String? = null,
    val creationDate: String? = null,
    val albums: List<ArtistAlbum> = emptyList(),
)

data class ArtistAlbum(
    val id: Int,
    val name: String,
    val cover: String?,
    val releaseDate: String?,
    val recordLabel: String?,
    val genre: String?,
)
