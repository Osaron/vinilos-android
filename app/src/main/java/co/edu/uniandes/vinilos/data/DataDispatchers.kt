package co.edu.uniandes.vinilos.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dispatcher de la capa de datos. Toda llamada de red de los repositorios remotos se ejecuta en
 * [Dispatchers.IO], aunque el ViewModel la lance desde `viewModelScope` (hilo principal). Así la
 * serialización JSON, el mapeo de DTOs y las validaciones nunca ocupan el hilo principal (ANR).
 * Las pruebas pueden sustituirlo por un dispatcher de prueba.
 */
object DataDispatchers {
    @Volatile
    var io: CoroutineDispatcher = Dispatchers.IO
}

/** Ejecuta [block] en el dispatcher de E/S de la capa de datos. */
suspend fun <T> onIo(block: suspend () -> T): T = withContext(DataDispatchers.io) { block() }
