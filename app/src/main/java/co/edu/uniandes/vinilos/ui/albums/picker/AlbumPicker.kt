package co.edu.uniandes.vinilos.ui.albums.picker

import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.model.Album
import java.text.Normalizer
import java.util.Locale

/**
 * Selector de álbumes compartido (HU15; pensado para reutilizarse en HU11).
 *
 * Reglas: solo álbumes con ID válido, sin repetir, sin los ya asociados y filtrables por nombre o
 * intérprete sin distinguir mayúsculas ni tildes. La selección se identifica siempre por ID, porque
 * puede haber álbumes con el mismo nombre.
 */
object AlbumPicker {
    fun available(catalog: List<Album>, excluded: Set<Int>): List<Album> =
        catalog.filter { it.id > 0 && it.id !in excluded }.distinctBy { it.id }

    fun filter(albums: List<Album>, query: String): List<Album> {
        val wanted = normalize(query)
        if (wanted.isEmpty()) return albums
        return albums.filter { album ->
            normalize(album.name).contains(wanted) || album.performers.any { normalize(it).contains(wanted) }
        }
    }

    fun normalize(text: String): String =
        Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)

    fun label(album: Album): String =
        if (album.performers.isEmpty()) album.name else "${album.name} · ${album.performers.joinToString(", ")}"
}

/**
 * Pinta las opciones en un [RadioGroup]. Conserva las vistas cuando la lista no cambia para no
 * perder el foco de TalkBack al seleccionar (mismo patrón que HU12). La descripción incluye todo el
 * texto visible de la opción.
 */
class AlbumOptionsRenderer(private val group: RadioGroup, private val onSelect: (Int) -> Unit) {
    fun render(albums: List<Album>, selectedId: Int?, enabled: Boolean) {
        val context = group.context
        val currentIds = (0 until group.childCount).map { group.getChildAt(it).tag }
        if (currentIds != albums.map { it.id }) {
            group.removeAllViews()
            albums.forEach { album ->
                group.addView(RadioButton(context).apply {
                    id = View.generateViewId()
                    tag = album.id
                    minHeight = (48 * resources.displayMetrics.density).toInt()
                    setOnClickListener { onSelect(album.id) }
                })
            }
        }
        albums.forEachIndexed { index, album ->
            (group.getChildAt(index) as RadioButton).apply {
                text = AlbumPicker.label(album)
                contentDescription = if (album.performers.isEmpty()) {
                    context.getString(R.string.album_picker_option, album.name)
                } else {
                    context.getString(R.string.album_picker_option_performers, album.name, album.performers.joinToString(", "))
                }
                isChecked = album.id == selectedId
                isEnabled = enabled
            }
        }
    }
}
