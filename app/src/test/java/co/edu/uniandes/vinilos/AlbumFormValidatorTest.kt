package co.edu.uniandes.vinilos

import co.edu.uniandes.vinilos.model.NewAlbum
import co.edu.uniandes.vinilos.ui.albums.AlbumField
import co.edu.uniandes.vinilos.ui.albums.AlbumFieldError
import co.edu.uniandes.vinilos.ui.albums.AlbumForm
import co.edu.uniandes.vinilos.ui.albums.AlbumFormValidator
import co.edu.uniandes.vinilos.ui.albums.AlbumValidation
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/** Validaciones del formulario de HU07 (#18). */
class AlbumFormValidatorTest {
    private val today = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 4) }
    private val valid = AlbumForm(
        name = "  Re  ",
        coverUrl = " https://i.pinimg.com/564x/aa/5f/ed/portada.jpg ",
        releaseDate = "22/08/1994",
        description = " Álbum de estudio ",
        genre = "Rock",
        recordLabel = "EMI",
    )

    private fun errorsOf(form: AlbumForm) =
        (AlbumFormValidator.validate(form, today) as AlbumValidation.Invalid).errors

    @Test fun `formulario valido produce el album con fecha ISO y textos sin espacios`() {
        val result = AlbumFormValidator.validate(valid, today)
        assertEquals(
            AlbumValidation.Valid(
                NewAlbum(
                    name = "Re",
                    cover = "https://i.pinimg.com/564x/aa/5f/ed/portada.jpg",
                    releaseDate = "1994-08-22",
                    description = "Álbum de estudio",
                    genre = "Rock",
                    recordLabel = "EMI",
                ),
            ),
            result,
        )
    }

    @Test fun `todos los campos son obligatorios`() {
        val errors = errorsOf(AlbumForm(name = " ", description = "   "))
        assertEquals(AlbumField.values().associateWith { AlbumFieldError.REQUIRED }, errors)
    }

    @Test fun `la portada debe ser una URL http o https`() {
        listOf("portada.jpg", "ftp://servidor/portada.jpg", "https://", "http//sin-dos-puntos.com").forEach {
            assertEquals(it, mapOf(AlbumField.COVER to AlbumFieldError.INVALID_URL), errorsOf(valid.copy(coverUrl = it)))
        }
    }

    @Test fun `la fecha debe tener formato dd-mm-aaaa y existir`() {
        listOf("1994-08-22", "22/8/1994", "31/02/2020", "32/01/2020", "22/08/94", "fecha").forEach {
            assertEquals(it, mapOf(AlbumField.RELEASE_DATE to AlbumFieldError.INVALID_DATE), errorsOf(valid.copy(releaseDate = it)))
        }
    }

    @Test fun `la fecha no puede ser posterior a hoy pero hoy es valido`() {
        assertEquals(
            mapOf(AlbumField.RELEASE_DATE to AlbumFieldError.FUTURE_DATE),
            errorsOf(valid.copy(releaseDate = "05/11/2026")),
        )
        assertEquals(true, AlbumFormValidator.validate(valid.copy(releaseDate = "04/11/2026"), today) is AlbumValidation.Valid)
    }

    @Test fun `genero y sello solo aceptan los valores del backend`() {
        assertEquals(
            mapOf(
                AlbumField.GENRE to AlbumFieldError.NOT_ALLOWED,
                AlbumField.RECORD_LABEL to AlbumFieldError.NOT_ALLOWED,
            ),
            errorsOf(valid.copy(genre = "Rock alternativo", recordLabel = "Warner Music")),
        )
    }
}
