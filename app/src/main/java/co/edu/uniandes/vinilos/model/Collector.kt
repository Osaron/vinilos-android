package co.edu.uniandes.vinilos.model

/** Campos comunes del listado; el detalle HU06 se consulta por id. */
data class Collector(val id: Int, val name: String, val email: String, val telephone: String)

data class CollectorDetail(
    val id: Int,
    val name: String,
    val email: String,
    val telephone: String,
    val comments: List<CollectorComment>,
    val favoritePerformers: List<FavoritePerformer>,
)

data class CollectorComment(val id: Int, val description: String, val rating: Int?)

data class FavoritePerformer(
    val id: Int,
    val name: String,
    val image: String?,
    val description: String,
    val date: String?,
)
