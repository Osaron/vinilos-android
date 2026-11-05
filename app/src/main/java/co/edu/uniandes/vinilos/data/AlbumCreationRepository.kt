package co.edu.uniandes.vinilos.data

import co.edu.uniandes.vinilos.model.Album
import co.edu.uniandes.vinilos.model.NewAlbum

/**
 * Contrato de creación de álbumes (HU07) entre el ViewModel y la capa de datos.
 *
 * La implementación real consume POST /albums mediante el Service Adapter; las pruebas usan un
 * doble controlado. Debe lanzar una excepción si el backend rechaza el álbum o no responde.
 */
fun interface AlbumCreationRepository {
    suspend fun createAlbum(album: NewAlbum): Album
}
