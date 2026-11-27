package co.edu.uniandes.vinilos

import co.edu.uniandes.vinilos.data.AlbumService
import co.edu.uniandes.vinilos.data.ArtistAlbumService
import co.edu.uniandes.vinilos.data.ArtistAlbumServiceAdapter
import co.edu.uniandes.vinilos.data.ArtistService
import co.edu.uniandes.vinilos.data.RemoteArtistAlbumRepository
import co.edu.uniandes.vinilos.model.ArtistType
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
 * HU15 contra un BackVynils real y desechable. Se omite salvo que exista `BACKVYNILS_URL`.
 * Escribe asociaciones, así que nunca debe apuntar a una instancia compartida.
 */
class ArtistAlbumBackendRealTest {
    private val baseUrl = System.getenv("BACKVYNILS_URL").orEmpty()
    private lateinit var retrofit: Retrofit

    @Before fun setUp() {
        assumeTrue("Define BACKVYNILS_URL para validar contra BackVynils", baseUrl.isNotBlank())
        retrofit = Retrofit.Builder().baseUrl(baseUrl)
            .client(OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).retryOnConnectionFailure(false).build())
            .addConverterFactory(GsonConverterFactory.create()).build()
    }

    private fun repository() = RemoteArtistAlbumRepository(ArtistAlbumServiceAdapter(retrofit.create(ArtistAlbumService::class.java)))

    private suspend fun artists(type: String) = retrofit.create(ArtistService::class.java).let {
        if (type == "band") it.getBands() else it.getMusicians()
    }.mapNotNull { it.id }

    private suspend fun allAlbumIds() = retrofit.create(AlbumService::class.java).getAlbums().mapNotNull { it.id }

    private suspend fun discography(type: ArtistType, id: Int) = repository().getAlbumIds(type, id)

    private suspend fun everyDiscography(): Map<Pair<ArtistType, Int>, Set<Int>> =
        artists("musician").associate { (ArtistType.MUSICIAN to it) to discography(ArtistType.MUSICIAN, it) } +
            artists("band").associate { (ArtistType.BAND to it) to discography(ArtistType.BAND, it) }

    private fun assertOnlyTargetChanged(before: Map<Pair<ArtistType, Int>, Set<Int>>, after: Map<Pair<ArtistType, Int>, Set<Int>>, target: Pair<ArtistType, Int>, albumId: Int) {
        before.forEach { (artist, albums) ->
            val expected = if (artist == target) albums + albumId else albums
            assertEquals("Discografía de $artist", expected, after.getValue(artist))
        }
    }

    @Test fun agregaUnAlbumAUnMusicoSinTocarAOtrosArtistas() = runBlocking {
        val musician = artists("musician").first()
        val albumId = allAlbumIds().first { it !in discography(ArtistType.MUSICIAN, musician) }
        val before = everyDiscography()
        repository().addAlbum(ArtistType.MUSICIAN, musician, albumId)
        assertOnlyTargetChanged(before, everyDiscography(), ArtistType.MUSICIAN to musician, albumId)
    }

    @Test fun agregaUnAlbumAUnaBandaConservandoAlInterpreteQueYaLoTenia() = runBlocking {
        val band = artists("band").first()
        val before = everyDiscography()
        // Un álbum que ya tiene otro intérprete: la ruta del álbum debe conservarlo.
        val albumId = before.filterKeys { it != ArtistType.BAND to band }.values.flatten()
            .first { it !in before.getValue(ArtistType.BAND to band) }
        repository().addAlbum(ArtistType.BAND, band, albumId)
        assertOnlyTargetChanged(before, everyDiscography(), ArtistType.BAND to band, albumId)
    }

    @Test fun informaAlbumInexistente() = runBlocking {
        val musician = artists("musician").first()
        try {
            repository().addAlbum(ArtistType.MUSICIAN, musician, 999_999)
            fail("Debe responder 404")
        } catch (error: HttpException) {
            assertEquals(404, error.code())
        }
        assertTrue(999_999 !in discography(ArtistType.MUSICIAN, musician))
    }
}
