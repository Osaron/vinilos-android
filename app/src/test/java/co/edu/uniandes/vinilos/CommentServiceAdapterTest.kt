package co.edu.uniandes.vinilos

import co.edu.uniandes.vinilos.data.CommentService
import co.edu.uniandes.vinilos.data.CommentServiceAdapter
import co.edu.uniandes.vinilos.data.RemoteCommentRepository
import co.edu.uniandes.vinilos.model.AlbumComment
import co.edu.uniandes.vinilos.model.NewComment
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Solicitud, respuesta y errores de los comentarios de un álbum (HU09, #81). */
class CommentServiceAdapterTest {
    private lateinit var server: MockWebServer
    private val comment = NewComment(description = "Un clásico de la salsa", rating = 5, collectorId = 100)

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    private fun repository() = RemoteCommentRepository(CommentServiceAdapter(
        Retrofit.Builder().baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().callTimeout(2, TimeUnit.SECONDS).retryOnConnectionFailure(false).build())
            .addConverterFactory(GsonConverterFactory.create()).build()
            .create(CommentService::class.java),
    ))

    @Test fun `envia POST al album correcto con texto calificacion y coleccionista`() = runTest {
        server.enqueue(MockResponse().setBody(savedBody()))

        repository().addComment(albumId = 7, comment = comment)

        val request = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals("POST", request.method)
        assertEquals("/albums/7/comments", request.path)
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals(setOf("description", "rating", "collector"), body.keySet())
        assertEquals("Un clásico de la salsa", body["description"].asString)
        assertEquals(5, body["rating"].asInt)
        assertEquals(100, body["collector"].asJsonObject["id"].asInt)
    }

    @Test fun `transforma la respuesta 200 en el comentario guardado`() = runTest {
        server.enqueue(MockResponse().setBody(savedBody()))

        val saved = repository().addComment(7, comment)

        assertEquals(AlbumComment(id = 55, description = "Un clásico de la salsa", rating = 5, collectorId = 100), saved)
    }

    @Test fun `propaga el 404 de album o coleccionista inexistente sin reintentar`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404)
            .setBody("""{"statusCode":404,"message":"The album with the given id was not found"}"""))

        try { repository().addComment(999, comment); fail("Debe propagar HTTP 404") }
        catch (error: HttpException) { assertEquals(404, error.code()) }
        assertEquals("una sola solicitud: no se crean duplicados", 1, server.requestCount)
    }

    @Test fun `propaga el 400 de datos invalidos`() = runTest {
        server.enqueue(MockResponse().setResponseCode(400))

        try { repository().addComment(7, comment.copy(rating = 9)); fail("Debe propagar HTTP 400") }
        catch (error: HttpException) { assertEquals(400, error.code()) }
    }

    @Test fun `la falta de red llega como IOException para permitir reintentar`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        try { repository().addComment(7, comment); fail("Debe propagar IOException") }
        catch (_: IOException) { }
        assertEquals(1, server.requestCount)
    }

    @Test fun `lista los comentarios del album y descarta los incompletos`() = runTest {
        server.enqueue(MockResponse().setBody("""[
            {"id":1,"description":"Excelente","rating":5},
            {"id":2,"description":"  ","rating":3},
            {"id":3,"description":"Sin calificación"},
            {"id":4,"description":"Regular","rating":2,"collector":{"id":101,"name":"Jaime Monsalve"}}
        ]"""))

        val comments = repository().getComments(7)

        assertEquals("/albums/7/comments", requireNotNull(server.takeRequest(2, TimeUnit.SECONDS)).path)
        assertEquals(listOf(1, 4), comments.map { it.id })
        assertEquals(101, comments.last().collectorId)
        assertTrue(comments.all { it.rating in 0..5 })
    }

    private fun savedBody() = """{"id":55,"description":"Un clásico de la salsa","rating":5,"collector":{"id":100,"name":"Manolo Bellon"}}"""
}
