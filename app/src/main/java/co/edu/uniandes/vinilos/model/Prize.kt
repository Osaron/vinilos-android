package co.edu.uniandes.vinilos.model

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Premio del catálogo de BackVynils (HU13 los crea, HU14 los asigna). */
data class Prize(val id: Int, val name: String, val organization: String)

/** Premio ya entregado a un artista. [date] siempre es `yyyy-MM-dd`. */
data class ArtistPrize(
    val id: Int,
    val prizeId: Int,
    val name: String,
    val organization: String,
    val date: String,
)

/**
 * Fecha de entrega de un premio (HU14).
 *
 * Viaja como `yyyy-MM-dd`: BackVynils la guarda a medianoche UTC y la devuelve como
 * `yyyy-MM-ddT00:00:00.000Z`. Un texto `dd/MM/yyyy` no sirve para el envío, porque el backend lo
 * interpreta como mes/día y guarda otra fecha sin avisar.
 */
object PrizeDate {
    private val ISO = Regex("""\d{4}-\d{2}-\d{2}""")

    /** [month] va de 1 a 12. */
    fun iso(year: Int, month: Int, day: Int): String =
        String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day)

    fun isIso(text: String): Boolean {
        if (!ISO.matches(text)) return false
        return try {
            SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }.parse(text) != null
        } catch (_: ParseException) {
            false
        }
    }

    /** Toma la fecha de una respuesta del backend (`1978-12-10T00:00:00.000Z`) o `null` si no es válida. */
    fun fromBackend(text: String?): String? = text?.trim()?.take(10)?.takeIf(::isIso)

    /** `2020-05-01` se muestra como `01/05/2020`. */
    fun display(iso: String): String =
        if (isIso(iso)) "${iso.substring(8, 10)}/${iso.substring(5, 7)}/${iso.substring(0, 4)}" else iso

    /** Año, mes (1 a 12) y día de una fecha válida. */
    fun parts(iso: String): Triple<Int, Int, Int> {
        require(isIso(iso)) { "Fecha inválida" }
        return Triple(iso.substring(0, 4).toInt(), iso.substring(5, 7).toInt(), iso.substring(8, 10).toInt())
    }

    fun today(): String = Calendar.getInstance().let {
        iso(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH))
    }
}
