package co.edu.uniandes.vinilos.data

import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import java.io.InputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit

/** Añade una CA pública a las del sistema, sin omitir cadena, caducidad ni hostname. */
internal object LegacyImageTls {
    fun client(additionalRoot: InputStream): OkHttpClient {
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(additionalRoot) as X509Certificate
        val certificates = HandshakeCertificates.Builder()
            .addPlatformTrustedCertificates()
            .addTrustedCertificate(certificate)
            .build()
        return OkHttpClient.Builder()
            .sslSocketFactory(certificates.sslSocketFactory(), certificates.trustManager)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
