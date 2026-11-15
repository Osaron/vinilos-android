package co.edu.uniandes.vinilos

import co.edu.uniandes.vinilos.data.AddTrackRequestDto
import co.edu.uniandes.vinilos.data.AlbumService
import co.edu.uniandes.vinilos.data.RemoteTrackAssociationRepository
import co.edu.uniandes.vinilos.data.TrackAssociationException
import co.edu.uniandes.vinilos.data.TrackAssociationException.Reason
import co.edu.uniandes.vinilos.data.TrackService
import co.edu.uniandes.vinilos.data.TrackServiceAdapter
import co.edu.uniandes.vinilos.model.NewTrack
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Verifica el contrato de HU08 contra un BackVynils real y desechable (#33).
 *
 * Se omite salvo que exista `BACKVYNILS_URL` (por ejemplo `http://localhost:3000/`). Escribe
 * canciones, así que nunca debe apuntar a una instancia compartida. Ver `scripts/test-tracks-real.sh`.
 */
class TrackBackendRealTest {
    private val baseUrl = System.getenv("BACKVYNILS_URL").orEmpty()
    private lateinit var retrofit: Retrofit

    @Before fun setUp() {
        assumeTrue("Define BACKVYNILS_URL para validar contra BackVynils", baseUrl.isNotBlank())
        retrofit = Retrofit.Builder().baseUrl(baseUrl)
            .client(OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).retryOnConnectionFailure(false).build())
            .addConverterFactory(GsonConverterFactory.create()).build()
    }

    private fun repository() = RemoteTrackAssociationRepository(TrackServiceAdapter(retrofit.create(TrackService::class.java)))

    @Test fun asociaLaCancionAlAlbumElegidoYApareceEnSusCanciones() = runBlocking {
        val albums = retrofit.create(AlbumService::class.java).getAlbums()
        assertTrue("BackVynils necesita al menos dos álbumes", albums.size >= 2)
        val target = requireNotNull(albums[1].id)
        val other = requireNotNull(albums[0].id)
        val name = "HU08 real ${System.currentTimeMillis()}"

        val saved = repository().addTrack(target, NewTrack(name, "3:45"))

        assertTrue(requireNotNull(saved.id) > 0)
        assertEquals(name, saved.name)
        assertEquals("3:45", saved.duration)
        assertTrue("queda en el álbum elegido", repository().getTracks(target).any { it.id == saved.id })
        assertTrue("no queda en otro álbum", repository().getTracks(other).none { it.id == saved.id })
        val detail = retrofit.create(AlbumService::class.java).getAlbum(target)
        assertTrue("el detalle del álbum la incluye", detail.tracks.orEmpty().any { it.id == saved.id })
    }

    @Test fun informaAlbumInexistente() = runBlocking {
        try { repository().addTrack(999_999, NewTrack("Sin álbum", "1:00")); fail("Debe responder 404") }
        catch (error: TrackAssociationException) { assertEquals(Reason.ALBUM_NOT_FOUND, error.reason) }
    }

    @Test fun elBackendRechazaUnaCancionSinDuracion() = runBlocking {
        val albumId = requireNotNull(retrofit.create(AlbumService::class.java).getAlbums().first().id)
        try {
            retrofit.create(TrackService::class.java).addTrack(albumId, AddTrackRequestDto("Sin duración", ""))
            fail("Joi exige duration")
        } catch (error: HttpException) { assertEquals(400, error.code()) }
    }
}
