package co.edu.uniandes.vinilos.data

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Coleccionista que usa la app (#80). HU09 y HU10 escriben siempre a su nombre. */
data class ActiveCollector(val id: Int, val name: String) {
    init {
        require(id > 0) { "Coleccionista sin identificador" }
        require(name.isNotBlank()) { "Coleccionista sin nombre" }
    }
}

/**
 * Guarda qué coleccionista eligió el usuario. Nunca hay un ID fijo en el código: si [active] es
 * `null`, la app pide elegir uno antes de comentar (HU09) o marcar un favorito (HU10).
 */
interface ActiveCollectorStore {
    val active: StateFlow<ActiveCollector?>
    fun choose(collector: ActiveCollector)
    fun clear()
}

/** Solo en memoria: para pruebas y como respaldo. */
class InMemoryActiveCollectorStore(initial: ActiveCollector? = null) : ActiveCollectorStore {
    private val state = MutableStateFlow(initial)
    override val active: StateFlow<ActiveCollector?> = state.asStateFlow()
    override fun choose(collector: ActiveCollector) { state.value = collector }
    override fun clear() { state.value = null }
}

/**
 * Persiste la elección en [SharedPreferences] para que sobreviva a la rotación, a la muerte del
 * proceso y a un nuevo inicio de la app. Se escribe con `apply()`, que guarda en segundo plano y
 * no bloquea el hilo principal. Si lo guardado está incompleto o es inválido, se ignora.
 */
class PreferencesActiveCollectorStore(private val preferences: SharedPreferences) : ActiveCollectorStore {
    private val state = MutableStateFlow(read())
    override val active: StateFlow<ActiveCollector?> = state.asStateFlow()

    override fun choose(collector: ActiveCollector) {
        preferences.edit().putInt(KEY_ID, collector.id).putString(KEY_NAME, collector.name).apply()
        state.value = collector
    }

    override fun clear() {
        preferences.edit().remove(KEY_ID).remove(KEY_NAME).apply()
        state.value = null
    }

    private fun read(): ActiveCollector? {
        val id = preferences.getInt(KEY_ID, 0)
        val name = preferences.getString(KEY_NAME, null)
        return if (id > 0 && !name.isNullOrBlank()) ActiveCollector(id, name) else null
    }

    companion object {
        const val FILE = "active_collector"
        private const val KEY_ID = "collector_id"
        private const val KEY_NAME = "collector_name"
    }
}
