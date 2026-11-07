package co.edu.uniandes.vinilos.ui.albums

import co.edu.uniandes.vinilos.model.AlbumAllowedValues
import co.edu.uniandes.vinilos.model.NewAlbum
import java.net.URI
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Datos tal como los escribe el coleccionista en el formulario de HU07. */
data class AlbumForm(
    val name: String = "",
    val coverUrl: String = "",
    /** Fecha como se muestra en el formulario: `dd/MM/yyyy`. */
    val releaseDate: String = "",
    val description: String = "",
    val genre: String = "",
    val recordLabel: String = "",
)

enum class AlbumField { NAME, COVER, RELEASE_DATE, DESCRIPTION, GENRE, RECORD_LABEL }

/**
 * Motivo por el que un campo no es válido. La vista lo traduce a un mensaje, por ejemplo:
 * REQUIRED → «Este campo es obligatorio», INVALID_URL → «Ingresa una URL que empiece por http:// o https://»,
 * INVALID_DATE → «Usa el formato dd/mm/aaaa», FUTURE_DATE → «La fecha no puede ser posterior a hoy»,
 * NOT_ALLOWED → «Selecciona una opción de la lista».
 */
enum class AlbumFieldError { REQUIRED, INVALID_URL, INVALID_DATE, FUTURE_DATE, NOT_ALLOWED }

sealed interface AlbumValidation {
    data class Valid(val album: NewAlbum) : AlbumValidation
    data class Invalid(val errors: Map<AlbumField, AlbumFieldError>) : AlbumValidation
}

/**
 * Validaciones de HU07 (#18). Replican las restricciones de POST /albums para no enviar
 * solicitudes que el backend rechazaría: todos los campos son obligatorios, la portada es una
 * URL http(s), la fecha es real y no futura, y género y sello pertenecen a la lista del backend.
 */
object AlbumFormValidator {
    private val DATE_PATTERN = Regex("""\d{2}/\d{2}/\d{4}""")

    fun validate(form: AlbumForm, today: Calendar = Calendar.getInstance()): AlbumValidation {
        val errors = linkedMapOf<AlbumField, AlbumFieldError>()

        val name = form.name.trim()
        if (name.isEmpty()) errors[AlbumField.NAME] = AlbumFieldError.REQUIRED

        val cover = form.coverUrl.trim()
        when {
            cover.isEmpty() -> errors[AlbumField.COVER] = AlbumFieldError.REQUIRED
            !isHttpUrl(cover) -> errors[AlbumField.COVER] = AlbumFieldError.INVALID_URL
        }

        val dateText = form.releaseDate.trim()
        val releaseDate = parseDate(dateText)
        when {
            dateText.isEmpty() -> errors[AlbumField.RELEASE_DATE] = AlbumFieldError.REQUIRED
            releaseDate == null -> errors[AlbumField.RELEASE_DATE] = AlbumFieldError.INVALID_DATE
            releaseDate.isAfter(today) -> errors[AlbumField.RELEASE_DATE] = AlbumFieldError.FUTURE_DATE
        }

        val description = form.description.trim()
        if (description.isEmpty()) errors[AlbumField.DESCRIPTION] = AlbumFieldError.REQUIRED

        checkAllowed(form.genre.trim(), AlbumAllowedValues.GENRES)
            ?.let { errors[AlbumField.GENRE] = it }
        checkAllowed(form.recordLabel.trim(), AlbumAllowedValues.RECORD_LABELS)
            ?.let { errors[AlbumField.RECORD_LABEL] = it }

        if (errors.isNotEmpty() || releaseDate == null) return AlbumValidation.Invalid(errors)
        return AlbumValidation.Valid(
            NewAlbum(
                name = name,
                cover = cover,
                releaseDate = releaseDate.toIso(),
                description = description,
                genre = form.genre.trim(),
                recordLabel = form.recordLabel.trim(),
            ),
        )
    }

    private fun checkAllowed(value: String, allowed: List<String>): AlbumFieldError? = when {
        value.isEmpty() -> AlbumFieldError.REQUIRED
        value !in allowed -> AlbumFieldError.NOT_ALLOWED
        else -> null
    }

    private fun isHttpUrl(value: String): Boolean = try {
        val uri = URI(value)
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
    } catch (_: Exception) {
        false
    }

    /** Interpreta `dd/MM/yyyy` de forma estricta: rechaza fechas como 31/02/2020. */
    private fun parseDate(value: String): Calendar? {
        if (!DATE_PATTERN.matches(value)) return null
        val format = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { isLenient = false }
        val position = ParsePosition(0)
        val date = format.parse(value, position) ?: return null
        if (position.index != value.length) return null
        return Calendar.getInstance().apply { time = date }
    }

    private fun Calendar.dayKey() =
        get(Calendar.YEAR) * 10_000 + (get(Calendar.MONTH) + 1) * 100 + get(Calendar.DAY_OF_MONTH)

    private fun Calendar.isAfter(other: Calendar) = dayKey() > other.dayKey()

    private fun Calendar.toIso() = String.format(
        Locale.US, "%04d-%02d-%02d",
        get(Calendar.YEAR), get(Calendar.MONTH) + 1, get(Calendar.DAY_OF_MONTH),
    )
}
