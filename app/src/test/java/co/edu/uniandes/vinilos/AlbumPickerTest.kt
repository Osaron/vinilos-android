package co.edu.uniandes.vinilos

import co.edu.uniandes.vinilos.model.Album
import co.edu.uniandes.vinilos.ui.albums.picker.AlbumPicker
import org.junit.Assert.assertEquals
import org.junit.Test

/** Selector de álbumes compartido (HU15, HU11). */
class AlbumPickerTest {
    private val catalog = listOf(
        Album(100, "Buscando América", "", listOf("Rubén Blades"), "Salsa"),
        Album(101, "Poeta del pueblo", "", listOf("Rubén Blades"), "Salsa"),
        Album(102, "A Night at the Opera", "", listOf("Queen"), "Rock"),
        Album(102, "Duplicado", "", emptyList(), "Rock"),
        Album(0, "Sin ID", "", emptyList(), "Rock"),
    )

    @Test fun excluyeAsociadosIdsInvalidosYDuplicados() {
        assertEquals(listOf(101, 102), AlbumPicker.available(catalog, setOf(100)).map { it.id })
    }

    @Test fun filtraPorNombreOInterpreteSinTildesNiMayusculas() {
        val available = AlbumPicker.available(catalog, emptySet())
        assertEquals(listOf(100), AlbumPicker.filter(available, "  AMERICA ").map { it.id })
        assertEquals(listOf(100, 101), AlbumPicker.filter(available, "ruben").map { it.id })
        assertEquals(listOf(102), AlbumPicker.filter(available, "queen").map { it.id })
        assertEquals(available, AlbumPicker.filter(available, "   "))
        assertEquals(emptyList<Album>(), AlbumPicker.filter(available, "zzz"))
    }

    @Test fun laEtiquetaIncluyeLosInterpretes() {
        assertEquals("Buscando América · Rubén Blades", AlbumPicker.label(catalog[0]))
        assertEquals("Duplicado", AlbumPicker.label(catalog[3]))
    }
}
