package co.edu.uniandes.vinilos

import co.edu.uniandes.vinilos.data.FavoriteArtistService
import co.edu.uniandes.vinilos.data.FavoriteArtistServiceAdapter
import co.edu.uniandes.vinilos.data.RemoteFavoriteArtistRepository
import co.edu.uniandes.vinilos.model.ArtistType
import co.edu.uniandes.vinilos.model.FavoritePerformer
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Solicitud, respuesta y errores de los artistas favoritos del coleccionista (HU10, #86). */
class FavoriteArtistServiceAdapterTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    private fun repository() = RemoteFavoriteArtistRepository(FavoriteArtistServiceAdapter(
        Retrofit.Builder().baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().callTimeout(2, TimeUnit.SECONDS).retryOnConnectionFailure(false).build())
            .addConverterFactory(GsonConverterFactory.create()).build()
            .create(FavoriteArtistService::class.java),
    ))

    @Test fun `un musico se agrega por la ruta de musicos del coleccionista`() = runTest {
        server.enqueue(MockResponse().setBody(rubenBlades()))

        val favorite = repository().addFavorite(collectorId = 100, artistId = 100, type = ArtistType.MUSICIAN)

        val request = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals("POST", request.method)
        assertEquals("/collectors/100/musicians/100", request.path)
        assertEquals(FavoritePerformer(100, "Rubén Blades Bellido de Luna", "https://img/ruben.jpg",
            "Cantante panameño", "1948-07-16T00:00:00.000Z"), favorite)
    }

    @Test fun `una banda se agrega por la ruta de bandas del coleccionista`() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"id":101,"name":"Queen","image":"https://img/queen.jpg","description":"Banda británica","creationDate":"1970-01-01T00:00:00.000Z"}"""))

        val favorite = repository().addFavorite(collectorId = 100, artistId = 101, type = ArtistType.BAND)

        assertEquals("/collectors/100/bands/101", requireNotNull(server.takeRequest(2, TimeUnit.SECONDS)).path)
        assertEquals("1970-01-01T00:00:00.000Z", favorite.date)
    }

    @Test fun `propaga el 404 de coleccionista o artista inexistente sin reintentar`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404)
            .setBody("""{"statusCode":404,"message":"The performer with the given id was not found"}"""))

        try { repository().addFavorite(100, 999, ArtistType.MUSICIAN); fail("Debe propagar HTTP 404") }
        catch (error: HttpException) { assertEquals(404, error.code()) }
        assertEquals("una sola solicitud: no se crean duplicados", 1, server.requestCount)
    }

    @Test fun `la falta de red llega como IOException para permitir reintentar`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        try { repository().addFavorite(100, 100, ArtistType.MUSICIAN); fail("Debe propagar IOException") }
        catch (_: IOException) { }
        assertEquals(1, server.requestCount)
    }

    @Test fun `rechaza una respuesta con un artista distinto al solicitado`() = runTest {
        server.enqueue(MockResponse().setBody(rubenBlades()))

        try { repository().addFavorite(100, 7, ArtistType.MUSICIAN); fail("Debe rechazar la respuesta") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun `lista los favoritos del coleccionista y descarta los incompletos`() = runTest {
        server.enqueue(MockResponse().setBody("[${rubenBlades()},{\"id\":0,\"name\":\"Sin id\"},{\"id\":5,\"name\":\"  \"}]"))

        val favorites = repository().getFavorites(100)

        assertEquals("/collectors/100/performers", requireNotNull(server.takeRequest(2, TimeUnit.SECONDS)).path)
        assertEquals(listOf(100), favorites.map { it.id })
    }

    private fun rubenBlades() =
        """{"id":100,"name":"Rubén Blades Bellido de Luna","image":"https://img/ruben.jpg","description":"Cantante panameño","birthDate":"1948-07-16T00:00:00.000Z","collectors":[{"id":100}]}"""
}
