package co.edu.uniandes.vinilos

import co.edu.uniandes.vinilos.data.ArtistPrizeService
import co.edu.uniandes.vinilos.data.ArtistPrizeServiceAdapter
import co.edu.uniandes.vinilos.data.RemoteArtistPrizeRepository
import co.edu.uniandes.vinilos.model.ArtistType
import co.edu.uniandes.vinilos.model.Prize
import co.edu.uniandes.vinilos.model.PrizeDate
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/** HU14: contrato del Service Adapter de premios con las respuestas reales de BackVynils. */
class ArtistPrizeServiceAdapterTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: RemoteArtistPrizeRepository

    private val prizesJson = """[
        {"id":100,"organization":"National Academy of Recording Arts &amp; Sciences","name":"Grammy Award","description":"x",
         "performerPrizes":[{"id":100,"premiationDate":"1978-12-10T00:00:00.000Z"},{"id":7,"premiationDate":"2020-05-01T00:00:00.000Z"}]},
        {"id":101,"organization":" Univisión ","name":" Premios lo Nuestro ","description":"x",
         "performerPrizes":[{"id":101,"premiationDate":"1980-12-10T00:00:00.000Z"}]},
        {"id":0,"name":"Sin identificador","performerPrizes":[{"id":100,"premiationDate":"2000-01-01T00:00:00.000Z"}]},
        {"id":102,"name":"  ","organization":"Sin nombre"},
        {"id":103,"name":"Billboard","performerPrizes":[{"id":8,"premiationDate":"no es fecha"}]}
    ]"""

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val service = Retrofit.Builder().baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().callTimeout(2, TimeUnit.SECONDS).retryOnConnectionFailure(false).build())
            .addConverterFactory(GsonConverterFactory.create()).build()
            .create(ArtistPrizeService::class.java)
        repository = RemoteArtistPrizeRepository(ArtistPrizeServiceAdapter(service))
    }

    @After fun tearDown() = server.shutdown()

    private fun json(body: String, code: Int = 200) = MockResponse().setResponseCode(code).setBody(body)

    @Test fun listaLosPremiosValidosOrdenadosPorNombre() = runTest {
        server.enqueue(json(prizesJson))
        val prizes = repository.getPrizes()
        assertEquals("/prizes", server.takeRequest().path)
        assertEquals(
            listOf(
                Prize(103, "Billboard", ""),
                Prize(100, "Grammy Award", "National Academy of Recording Arts &amp; Sciences", "x"),
                Prize(101, "Premios lo Nuestro", "Univisión", "x"),
            ),
            prizes,
        )
    }

    @Test fun cruzaLosPremiosDelMusicoConElCatalogoPorSuRuta() = runTest {
        server.enqueue(json("""{"id":100,"name":"Rubén Blades","performerPrizes":[{"id":100,"premiationDate":"1978-12-10T00:00:00.000Z"},{"id":7,"premiationDate":"2020-05-01T00:00:00.000Z"},{"id":8,"premiationDate":"x"}]}"""))
        server.enqueue(json(prizesJson))
        val prizes = repository.getArtistPrizes(ArtistType.MUSICIAN, 100)
        assertEquals("/musicians/100", server.takeRequest().path)
        assertEquals("/prizes", server.takeRequest().path)
        // Más reciente primero; se descarta la entrega cuya fecha no se puede leer.
        assertEquals(listOf(7 to "2020-05-01", 100 to "1978-12-10"), prizes.map { it.id to it.date })
        assertTrue(prizes.all { it.prizeId == 100 && it.name == "Grammy Award" })
    }

    @Test fun leeLosPremiosDeUnaBandaPorLaRutaDeBandas() = runTest {
        server.enqueue(json("""{"id":101,"name":"Queen","performerPrizes":[{"id":101,"premiationDate":"1980-12-10T00:00:00.000Z"}]}"""))
        server.enqueue(json(prizesJson))
        val prizes = repository.getArtistPrizes(ArtistType.BAND, 101)
        assertEquals("/bands/101", server.takeRequest().path)
        assertEquals(listOf(101), prizes.map { it.prizeId })
        assertEquals("Premios lo Nuestro", prizes.single().name)
    }

    @Test fun sinPremiosNoConsultaElCatalogo() = runTest {
        server.enqueue(json("""{"id":100,"name":"Rubén Blades","performerPrizes":[]}"""))
        assertTrue(repository.getArtistPrizes(ArtistType.MUSICIAN, 100).isEmpty())
        assertEquals(1, server.requestCount)
    }

    @Test fun rechazaUnDetalleDeOtroArtista() = runTest {
        server.enqueue(json("""{"id":999,"performerPrizes":[]}"""))
        try {
            repository.getArtistPrizes(ArtistType.MUSICIAN, 100)
            fail("Debe rechazar la respuesta")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test fun asignaAUnMusicoConLaFechaEnElCuerpo() = runTest {
        server.enqueue(json("""{"premiationDate":"2020-05-01","prize":{"id":100,"name":"Grammy Award"},"performer":{"id":100,"name":"Rubén Blades"},"id":7}"""))
        repository.assignPrize(ArtistType.MUSICIAN, 100, 100, "2020-05-01")
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/prizes/100/musicians/100", request.path)
        assertEquals("""{"premiationDate":"2020-05-01"}""", request.body.readUtf8())
    }

    @Test fun asignaAUnaBandaPorLaRutaDeBandas() = runTest {
        server.enqueue(json("""{"premiationDate":"2021-11-23","prize":{"id":101},"performer":{"id":55},"id":9}"""))
        repository.assignPrize(ArtistType.BAND, 55, 101, "2021-11-23")
        assertEquals("/prizes/101/bands/55", server.takeRequest().path)
    }

    @Test fun noEnviaIdentificadoresNiFechasInvalidas() = runTest {
        val invalid = listOf(
            Triple(0, 100, "2020-05-01"),
            Triple(100, 0, "2020-05-01"),
            Triple(100, 100, "01/05/2020"),
            Triple(100, 100, "2020-02-30"),
            Triple(100, 100, ""),
        )
        invalid.forEach { (artistId, prizeId, date) ->
            try {
                repository.assignPrize(ArtistType.MUSICIAN, artistId, prizeId, date)
                fail("Debe rechazar $artistId, $prizeId, $date")
            } catch (_: IllegalArgumentException) {
            }
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun rechazaUnaConfirmacionDeOtroPremioOArtista() = runTest {
        server.enqueue(json("""{"premiationDate":"2020-05-01","prize":{"id":101},"performer":{"id":100},"id":7}"""))
        server.enqueue(json("""{"premiationDate":"2020-05-01","prize":{"id":100},"performer":{"id":999},"id":7}"""))
        repeat(2) {
            try {
                repository.assignPrize(ArtistType.MUSICIAN, 100, 100, "2020-05-01")
                fail("Debe rechazar la confirmación")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    @Test fun rechazaUnaConfirmacionConFechaDistintaInvalidaOAusente() = runTest {
        val dates = listOf("\"2020-01-05T00:00:00.000Z\"", "\"no-fecha\"", "null")
        dates.forEach { date ->
            server.enqueue(json("""{"id":7,"prize":{"id":100},"performer":{"id":100},"premiationDate":$date}"""))
            try {
                repository.assignPrize(ArtistType.MUSICIAN, 100, 100, "2020-05-01")
                fail("Debe rechazar la confirmación con fecha $date")
            } catch (error: IllegalArgumentException) {
                assertEquals("La confirmación no corresponde a la fecha solicitada", error.message)
            }
        }
        assertEquals("Un POST por intento explícito, sin reenvíos automáticos", 3, server.requestCount)
    }

    @Test fun aceptaUnaConfirmacionConLaMismaFechaEnFormatoCompletoDelBackend() = runTest {
        server.enqueue(json("""{"id":7,"prize":{"id":100},"performer":{"id":100},"premiationDate":"2020-05-01T00:00:00.000Z"}"""))
        repository.assignPrize(ArtistType.MUSICIAN, 100, 100, "2020-05-01")
        assertEquals(1, server.requestCount)
    }

    @Test fun propagaElNoEncontradoDelBackend() = runTest {
        server.enqueue(json("""{"statusCode":404,"message":"The prize with the given id was not found"}""", 404))
        try {
            repository.assignPrize(ArtistType.BAND, 101, 999, "2020-05-01")
            fail("Debe propagar el 404")
        } catch (error: HttpException) {
            assertEquals(404, error.code())
        }
    }

    @Test fun unaConexionCortadaNoSeReintentaSola() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        try {
            repository.assignPrize(ArtistType.MUSICIAN, 100, 100, "2020-05-01")
            fail("Debe propagar el error de red")
        } catch (_: IOException) {
        }
        assertEquals(1, server.requestCount)
    }

    @Test fun laFechaSeValidaYSeMuestraEnFormatoLocal() {
        assertEquals("2020-05-01", PrizeDate.iso(2020, 5, 1))
        assertTrue(PrizeDate.isIso("2024-02-29"))
        assertFalse(PrizeDate.isIso("2023-02-29"))
        assertFalse(PrizeDate.isIso("2020-5-1"))
        assertEquals("1978-12-10", PrizeDate.fromBackend("1978-12-10T00:00:00.000Z"))
        assertNull(PrizeDate.fromBackend("10/12/1978"))
        assertNull(PrizeDate.fromBackend(null))
        assertEquals("01/05/2020", PrizeDate.display("2020-05-01"))
        assertEquals(Triple(2020, 5, 1), PrizeDate.parts("2020-05-01"))
        assertTrue(PrizeDate.isIso(PrizeDate.today()))
    }
}
