package co.edu.uniandes.vinilos

import co.edu.uniandes.vinilos.data.AlbumCreationRepository
import co.edu.uniandes.vinilos.model.Album
import co.edu.uniandes.vinilos.model.NewAlbum
import co.edu.uniandes.vinilos.ui.albums.AlbumField
import co.edu.uniandes.vinilos.ui.albums.AlbumFieldError
import co.edu.uniandes.vinilos.ui.albums.AlbumForm
import co.edu.uniandes.vinilos.ui.albums.CreateAlbumViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.Calendar

/** Estados, envío y prevención de duplicados del formulario de HU07 (#18 y #20). */
@OptIn(ExperimentalCoroutinesApi::class)
class CreateAlbumViewModelTest {
    private val today = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 4) }
    private val validForm = AlbumForm(
        name = "Re",
        coverUrl = "https://i.pinimg.com/564x/aa/5f/ed/portada.jpg",
        releaseDate = "22/08/1994",
        description = "Álbum de estudio",
        genre = "Rock",
        recordLabel = "EMI",
    )
    private val created = Album(101, "Re", "https://i.pinimg.com/564x/aa/5f/ed/portada.jpg", emptyList(), "Rock")

    private fun withMain(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try { block() } finally { Dispatchers.resetMain() }
    }

    @Test fun `con errores no envia la solicitud y muestra los errores`() = withMain {
        val requests = mutableListOf<NewAlbum>()
        val viewModel = CreateAlbumViewModel(AlbumCreationRepository { requests += it; created }) { today }

        viewModel.updateForm(validForm.copy(name = "", genre = "Rock alternativo"))
        assertTrue("antes de guardar no se muestran errores", viewModel.state.value.errors.isEmpty())

        viewModel.submit()
        advanceUntilIdle()

        assertTrue(requests.isEmpty())
        assertEquals(
            mapOf(AlbumField.NAME to AlbumFieldError.REQUIRED, AlbumField.GENRE to AlbumFieldError.NOT_ALLOWED),
            viewModel.state.value.errors,
        )
    }

    @Test fun `despues del primer intento los errores se actualizan al corregir`() = withMain {
        val viewModel = CreateAlbumViewModel(AlbumCreationRepository { created }) { today }
        viewModel.updateForm(validForm.copy(name = ""))
        viewModel.submit()

        viewModel.updateForm(validForm)

        assertTrue(viewModel.state.value.errors.isEmpty())
    }

    @Test fun `envia el album valido y entrega el album creado`() = withMain {
        val response = CompletableDeferred<Album>()
        val requests = mutableListOf<NewAlbum>()
        val viewModel = CreateAlbumViewModel(AlbumCreationRepository { requests += it; response.await() }) { today }
        viewModel.updateForm(validForm)

        viewModel.submit()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.submitting)

        response.complete(created)
        advanceUntilIdle()

        assertEquals("1994-08-22", requests.single().releaseDate)
        assertFalse(viewModel.state.value.submitting)
        assertEquals(created, viewModel.state.value.createdAlbum)
    }

    @Test fun `pulsaciones repetidas durante el envio crean un solo album`() = withMain {
        val response = CompletableDeferred<Album>()
        var calls = 0
        val viewModel = CreateAlbumViewModel(AlbumCreationRepository { calls++; response.await() }) { today }
        viewModel.updateForm(validForm)

        repeat(3) { viewModel.submit() }
        advanceUntilIdle()
        response.complete(created)
        advanceUntilIdle()
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(1, calls)
    }

    @Test fun `si falla conserva los datos y permite reintentar`() = withMain {
        var calls = 0
        val viewModel = CreateAlbumViewModel(
            AlbumCreationRepository { if (++calls == 1) throw IOException("sin red") else created },
        ) { today }
        viewModel.updateForm(validForm)

        viewModel.submit()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.failed)
        assertFalse(viewModel.state.value.submitting)
        assertEquals(validForm, viewModel.state.value.form)
        assertNull(viewModel.state.value.createdAlbum)

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(2, calls)
        assertFalse(viewModel.state.value.failed)
        assertEquals(created, viewModel.state.value.createdAlbum)
    }

    @Test fun `mientras envia no se puede editar el formulario`() = withMain {
        val response = CompletableDeferred<Album>()
        val viewModel = CreateAlbumViewModel(AlbumCreationRepository { response.await() }) { today }
        viewModel.updateForm(validForm)
        viewModel.submit()
        advanceUntilIdle()

        viewModel.updateForm(validForm.copy(name = "Otro"))

        assertEquals("Re", viewModel.state.value.form.name)
        response.complete(created)
    }
}
