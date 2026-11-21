package co.edu.uniandes.vinilos

import co.edu.uniandes.vinilos.data.*
import co.edu.uniandes.vinilos.model.ArtistType
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Crea solo datos HU12 identificables en el backend de pruebas indicado por HU12_REAL_URL. */
class BandMusicianBackendRealTest {
    private lateinit var retrofit: Retrofit
    private val run = UUID.randomUUID().toString().take(8)

    private interface SeedService {
        @POST("bands") suspend fun createBand(@Body data: Map<String, String>): ArtistDto
        @POST("musicians") suspend fun createMusician(@Body data: Map<String, String>): ArtistDto
    }

    @Before fun setup() {
        val base = System.getenv("HU12_REAL_URL").orEmpty()
        assumeTrue("Define HU12_REAL_URL para ejecutar", base.isNotBlank())
        retrofit = Retrofit.Builder().baseUrl(base)
            .client(OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).retryOnConnectionFailure(false).build())
            .addConverterFactory(GsonConverterFactory.create()).build()
    }

    private fun repository() = RemoteBandMusicianRepository(
        BandMusicianServiceAdapter(retrofit.create(BandMusicianService::class.java)),
    )

    private suspend fun band(label: String): Int = requireNotNull(
        retrofit.create(SeedService::class.java).createBand(
            mapOf("name" to "HU12 $run $label", "image" to "https://example.com/hu12.png",
                "description" to "Datos de prueba HU12", "creationDate" to "2000-01-01"),
        ).id,
    )

    private suspend fun musician(): Int = requireNotNull(
        retrofit.create(SeedService::class.java).createMusician(
            mapOf("name" to "HU12 $run músico", "image" to "https://example.com/hu12.png",
                "description" to "Datos de prueba HU12", "birthDate" to "1980-01-01"),
        ).id,
    )

    @Test fun asociaElMusicoSoloALaBandaElegida() = runBlocking {
        val target = band("destino")
        val other = band("otra")
        val musicianId = musician()
        val repo = repository()
        val saved = repo.addMusician(target, musicianId)
        assertEquals(musicianId, saved.id)
        assertEquals(ArtistType.MUSICIAN, saved.type)
        assertTrue(repo.getMembers(target).any { it.id == musicianId })
        assertTrue(repo.getMembers(other).none { it.id == musicianId })
        println("HU12 corrida=$run banda=$target otra=$other músico=$musicianId asociación=200 consulta=200")
    }

    @Test fun confirmaElCambioDeBandaAnterior() = runBlocking {
        val first = band("anterior")
        val next = band("nueva")
        val musicianId = musician()
        val repo = repository()
        repo.addMusician(first, musicianId)
        repo.addMusician(next, musicianId)
        assertTrue(repo.getMembers(first).none { it.id == musicianId })
        assertTrue(repo.getMembers(next).any { it.id == musicianId })
        println("HU12 corrida=$run anterior=$first nueva=$next músico=$musicianId cambio=200")
    }

    @Test fun devuelve404ParaUnaBandaInexistente() = runBlocking {
        val musicianId = musician()
        try {
            repository().addMusician(Int.MAX_VALUE, musicianId)
            fail("Debía responder 404")
        } catch (error: HttpException) {
            assertEquals(404, error.code())
        }
    }

    @Test fun devuelve404ParaUnMusicoInexistente() = runBlocking {
        val target = band("sin músico")
        try {
            repository().addMusician(target, Int.MAX_VALUE)
            fail("Debía responder 404")
        } catch (error: HttpException) {
            assertEquals(404, error.code())
        }
    }
}
