package co.edu.uniandes.vinilos.data

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** #80: coleccionista activo de HU09 y HU10. */
class ActiveCollectorStoreTest {
    private val ana = ActiveCollector(8, "Ana María")
    private val jose = ActiveCollector(7, "José Pérez")

    @Test fun `un coleccionista activo necesita id y nombre reales`() {
        assertThrows(IllegalArgumentException::class.java) { ActiveCollector(0, "Ana") }
        assertThrows(IllegalArgumentException::class.java) { ActiveCollector(-3, "Ana") }
        assertThrows(IllegalArgumentException::class.java) { ActiveCollector(8, "  ") }
    }

    @Test fun `en memoria empieza sin coleccionista y permite elegir, cambiar y olvidar`() {
        val store = InMemoryActiveCollectorStore()
        assertNull(store.active.value)
        store.choose(ana)
        assertEquals(ana, store.active.value)
        store.choose(jose)
        assertEquals(jose, store.active.value)
        store.clear()
        assertNull(store.active.value)
    }

    @Test fun `la eleccion persiste para una nueva instancia de la app`() {
        val preferences = FakePreferences()
        PreferencesActiveCollectorStore(preferences).choose(ana)

        val reopened = PreferencesActiveCollectorStore(preferences)

        assertEquals(ana, reopened.active.value)
    }

    @Test fun `cambiar de coleccionista reemplaza al anterior`() {
        val preferences = FakePreferences()
        val store = PreferencesActiveCollectorStore(preferences)
        store.choose(ana)
        store.choose(jose)

        assertEquals(jose, store.active.value)
        assertEquals(jose, PreferencesActiveCollectorStore(preferences).active.value)
    }

    @Test fun `olvidar el coleccionista lo borra tambien de lo guardado`() {
        val preferences = FakePreferences()
        val store = PreferencesActiveCollectorStore(preferences)
        store.choose(ana)
        store.clear()

        assertNull(store.active.value)
        assertNull(PreferencesActiveCollectorStore(preferences).active.value)
    }

    @Test fun `datos guardados incompletos o invalidos no cuentan como coleccionista`() {
        assertNull(PreferencesActiveCollectorStore(FakePreferences(mutableMapOf("collector_id" to 8))).active.value)
        assertNull(PreferencesActiveCollectorStore(FakePreferences(mutableMapOf("collector_name" to "Ana"))).active.value)
        assertNull(PreferencesActiveCollectorStore(FakePreferences(mutableMapOf("collector_id" to 0, "collector_name" to "Ana"))).active.value)
        assertNull(PreferencesActiveCollectorStore(FakePreferences(mutableMapOf("collector_id" to 8, "collector_name" to " "))).active.value)
    }

    /** SharedPreferences en memoria: solo lo que usa el almacén. */
    private class FakePreferences(val values: MutableMap<String, Any?> = mutableMapOf()) : SharedPreferences {
        override fun getAll(): MutableMap<String, *> = values
        override fun getString(key: String?, defValue: String?) = values[key] as String? ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?) = defValues
        override fun getInt(key: String?, defValue: Int) = values[key] as Int? ?: defValue
        override fun getLong(key: String?, defValue: Long) = defValue
        override fun getFloat(key: String?, defValue: Float) = defValue
        override fun getBoolean(key: String?, defValue: Boolean) = defValue
        override fun contains(key: String?) = values.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val changes = mutableMapOf<String, Any?>()
            private val removals = mutableSetOf<String>()
            override fun putString(key: String, value: String?) = apply { changes[key] = value }
            override fun putStringSet(key: String, values: MutableSet<String>?) = this
            override fun putInt(key: String, value: Int) = apply { changes[key] = value }
            override fun putLong(key: String, value: Long) = this
            override fun putFloat(key: String, value: Float) = this
            override fun putBoolean(key: String, value: Boolean) = this
            override fun remove(key: String) = apply { removals += key }
            override fun clear() = apply { removals += values.keys }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                removals.forEach { values.remove(it) }
                values.putAll(changes)
            }
        }
    }
}
