package co.edu.uniandes.vinilos

import co.edu.uniandes.vinilos.data.ArtistPrizeService
import co.edu.uniandes.vinilos.data.ArtistPrizeServiceAdapter
import co.edu.uniandes.vinilos.data.ArtistService
import co.edu.uniandes.vinilos.data.RemoteArtistPrizeRepository
import co.edu.uniandes.vinilos.model.ArtistPrize
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
 * HU14 contra un BackVynils real y desechable. Se omite salvo que exista `BACKVYNILS_URL`.
 * Escribe entregas de premios, así que nunca debe apuntar a una instancia compartida.
 */
class ArtistPrizeBackendRealTest {
    private val baseUrl = System.getenv("BACKVYNILS_URL").orEmpty()
    private lateinit var retrofit: Retrofit

    @Before fun setUp() {
        assumeTrue("Define BACKVYNILS_URL para validar contra BackVynils", baseUrl.isNotBlank())
        retrofit = Retrofit.Builder().baseUrl(baseUrl)
            .client(OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).retryOnConnectionFailure(false).build())
            .addConverterFactory(GsonConverterFactory.create()).build()
    }

    private fun repository() = RemoteArtistPrizeRepository(ArtistPrizeServiceAdapter(retrofit.create(ArtistPrizeService::class.java)))

    private suspend fun artists(type: ArtistType) = retrofit.create(ArtistService::class.java).let {
        if (type == ArtistType.BAND) it.getBands() else it.getMusicians()
    }.mapNotNull { it.id }

    /** Premio y fecha de cada entrega, por artista. Sin el ID de la entrega, que el backend genera. */
    private suspend fun everyAward(): Map<Pair<ArtistType, Int>, List<Pair<Int, String>>> =
        ArtistType.values().flatMap { type -> artists(type).map { type to it } }.associateWith { (type, id) ->
            repository().getArtistPrizes(type, id).map(::key).sorted()
        }

    private fun key(prize: ArtistPrize) = prize.prizeId to prize.date

    private fun List<Pair<Int, String>>.sorted() = sortedWith(compareBy({ it.first }, { it.second }))

    private suspend fun assignAndCheck(type: ArtistType, date: String) {
        val artist = artists(type).first()
        val prize = repository().getPrizes().first()
        val before = everyAward()
        assertTrue("La fecha de prueba no debe existir", (prize.id to date) !in before.getValue(type to artist))

        repository().assignPrize(type, artist, prize.id, date)

        val after = everyAward()
        before.forEach { (target, awards) ->
            val expected = if (target == type to artist) (awards + (prize.id to date)).sorted() else awards
            assertEquals("Premios de $target", expected, after.getValue(target))
        }
        // Lo que ve la app tras recargar: nombre del premio y la misma fecha que se envió.
        val shown = repository().getArtistPrizes(type, artist).single { key(it) == prize.id to date }
        assertEquals(prize.name, shown.name)
    }

    @Test fun asignaUnPremioAUnMusicoSinTocarAOtrosArtistas() = runBlocking {
        assignAndCheck(ArtistType.MUSICIAN, "2001-02-03")
    }

    @Test fun asignaUnPremioAUnaBandaSinTocarAOtrosArtistas() = runBlocking {
        assignAndCheck(ArtistType.BAND, "2002-03-04")
    }

    @Test fun elCatalogoTraeLosPremiosDeLosDatosDePrueba() = runBlocking {
        val prizes = repository().getPrizes()
        assertTrue(prizes.isNotEmpty())
        assertTrue(prizes.all { it.id > 0 && it.name.isNotBlank() })
    }

    @Test fun informaPremioInexistente() = runBlocking {
        val musician = artists(ArtistType.MUSICIAN).first()
        val before = everyAward()
        try {
            repository().assignPrize(ArtistType.MUSICIAN, musician, 999_999, "2003-04-05")
            fail("Debe responder 404")
        } catch (error: HttpException) {
            assertEquals(404, error.code())
        }
        assertEquals(before, everyAward())
    }

    @Test fun laLecturaPorTipoDetectaUnIdQueNoEsDeEseTipo() = runBlocking {
        // El POST del backend no comprueba el tipo; la lectura previa de la app sí lo hace.
        val band = artists(ArtistType.BAND).first { it !in artists(ArtistType.MUSICIAN) }
        try {
            repository().getArtistPrizes(ArtistType.MUSICIAN, band)
            fail("Debe responder 404")
        } catch (error: HttpException) {
            assertEquals(404, error.code())
        }
    }
}
