package co.edu.uniandes.vinilos

import co.edu.uniandes.vinilos.data.LegacyImageTls
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class LegacyImageTlsTest {
    private val root = HeldCertificate.Builder().certificateAuthority(1).commonName("Test CA").build()

    @Test fun acceptsCertificateSignedByAdditionalRoot() {
        val leaf = HeldCertificate.Builder().commonName("localhost")
            .addSubjectAlternativeName("localhost").signedBy(root).build()
        request(leaf, true)
    }

    @Test fun rejectsCertificateSignedByUnknownRoot() {
        val leaf = HeldCertificate.Builder().commonName("localhost")
            .addSubjectAlternativeName("localhost").build()
        request(leaf, false)
    }

    @Test fun rejectsWrongHostnameEvenWithTrustedRoot() {
        val leaf = HeldCertificate.Builder().commonName("other.example")
            .addSubjectAlternativeName("other.example").signedBy(root).build()
        request(leaf, false)
    }

    private fun request(leaf: HeldCertificate, succeeds: Boolean) {
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(leaf).build()
        MockWebServer().use { server ->
            server.useHttps(serverCertificates.sslSocketFactory(), false)
            server.enqueue(MockResponse().setBody("image"))
            server.start()
            val client = root.certificatePem().byteInputStream().use { LegacyImageTls.client(it) }
            try {
                client.newCall(Request.Builder().url(server.url("/cover.png")).build()).execute().use {
                    if (!succeeds) fail("Debe rechazar el certificado inválido")
                    assertEquals(200, it.code)
                    assertEquals("image", it.body!!.string())
                }
            } catch (error: IOException) {
                if (succeeds) throw error
                assertTrue(error is javax.net.ssl.SSLHandshakeException ||
                    error is javax.net.ssl.SSLPeerUnverifiedException)
            } finally {
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdown()
            }
        }
    }
}
