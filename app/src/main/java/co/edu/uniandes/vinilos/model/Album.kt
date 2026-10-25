package co.edu.uniandes.vinilos.model

data class Album(
    val id: Int,
    val name: String,
    val cover: String,
    val performers: List<String>,
    val genre: String,
)
