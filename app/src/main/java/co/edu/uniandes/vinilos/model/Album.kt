package co.edu.uniandes.vinilos.model

data class Album(
    val id: Int,
    val name: String,
    val cover: String,
    val performers: List<String>,
    val genre: String,
)

data class AlbumDetail(
    val id: Int,
    val name: String,
    val cover: String,
    val releaseDate: String,
    val description: String,
    val genre: String,
    val recordLabel: String,
    val performers: List<String>,
    val tracks: List<Track>,
)

data class Track(
    val id: Int?,
    val name: String,
    val duration: String,
)
