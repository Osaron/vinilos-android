package co.edu.uniandes.vinilos

import co.edu.uniandes.vinilos.network.LegacyImageCalls
import co.edu.uniandes.vinilos.network.wikimediaClient
import okhttp3.Call
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class LegacyImageCallsTest {
    @Test fun soloWikimediaHttpsAntiguoUsaClienteAdicional() {
        val platformClient = OkHttpClient()
        var platformCalls = 0
        var legacyCalls = 0
        val platform = Call.Factory { request -> platformCalls++; platformClient.newCall(request) }
        val legacy = { Call.Factory { request -> legacyCalls++; platformClient.newCall(request) } }
        val old = LegacyImageCalls(platform, 21, legacy)
        listOf("https://upload.wikimedia.org/a.png", "https://example.com/a.png",
            "https://upload.wikimedia.org.example.com/a.png", "http://upload.wikimedia.org/a.png").forEach {
            old.newCall(Request.Builder().url(it).build())
        }
        LegacyImageCalls(platform, 25, legacy).newCall(Request.Builder().url("https://upload.wikimedia.org/a.png").build())
        assertEquals(1, legacyCalls)
        assertEquals(4, platformCalls)
    }

    @Test fun certificadoConRaizAñadidaEsAceptado() = withServer("upload.wikimedia.org") { base, root, url ->
        wikimediaClient(base, listOf(root.certificate)).newCall(Request.Builder().url(url).build()).execute().use {
            assertEquals(200, it.code)
            assertEquals("portada", it.body!!.string())
        }
    }

    @Test fun certificadoDesconocidoEsRechazado() = withServer("upload.wikimedia.org") { base, _, url ->
        val unrelated = HeldCertificate.Builder().certificateAuthority(1).commonName("Otra CA").build()
        expectTlsFailure(wikimediaClient(base, listOf(unrelated.certificate)), url)
    }

    @Test fun nombreDeHostIncorrectoEsRechazado() = withServer("otro.example") { base, root, url ->
        expectTlsFailure(wikimediaClient(base, listOf(root.certificate)), url)
    }

    @Test fun clienteAdicionalNoSigueRedirecciones() {
        val root = HeldCertificate.Builder().certificateAuthority(1).commonName("CA").build()
        val client = wikimediaClient(OkHttpClient(), listOf(root.certificate))
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
    }

    private fun expectTlsFailure(client: OkHttpClient, url: String) {
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { fail("Debe rechazar el certificado") }
        } catch (expected: IOException) {
            assertTrue(expected is javax.net.ssl.SSLException)
        }
    }

    private fun withServer(host: String, action: (OkHttpClient, HeldCertificate, String) -> Unit) {
        val root = HeldCertificate.Builder().certificateAuthority(1).commonName("CA de prueba").build()
        val leaf = HeldCertificate.Builder().commonName(host).addSubjectAlternativeName(host).signedBy(root).build()
        val serverCerts = HandshakeCertificates.Builder().heldCertificate(leaf, root.certificate).build()
        MockWebServer().use { server ->
            server.useHttps(serverCerts.sslSocketFactory(), false)
            server.enqueue(MockResponse().setBody("portada"))
            server.start()
            val base = OkHttpClient.Builder()
                .dns(Dns { listOf(InetAddress.getByName("127.0.0.1")) })
                .callTimeout(5, TimeUnit.SECONDS).build()
            val url = server.url("/cover.png").newBuilder().host("upload.wikimedia.org").build().toString()
            action(base, root, url)
        }
    }
}
