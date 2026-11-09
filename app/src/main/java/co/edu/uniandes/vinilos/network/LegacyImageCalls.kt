package co.edu.uniandes.vinilos.network

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.tls.HandshakeCertificates
import java.security.cert.X509Certificate

/** Raíces adicionales solo para HTTPS de Wikimedia en Android anterior a 7.1. */
class LegacyImageCalls(
    private val platform: Call.Factory,
    private val sdkInt: Int,
    private val createLegacy: () -> Call.Factory,
) : Call.Factory {
    private val legacy by lazy(createLegacy)

    override fun newCall(request: Request): Call =
        if (sdkInt < 25 && request.url.isHttps && request.url.host == "upload.wikimedia.org") {
            legacy.newCall(request)
        } else {
            platform.newCall(request)
        }
}

internal fun wikimediaClient(base: OkHttpClient, roots: List<X509Certificate>): OkHttpClient {
    val certificates = HandshakeCertificates.Builder().addPlatformTrustedCertificates().apply {
        roots.forEach { addTrustedCertificate(it) }
    }.build()
    return base.newBuilder()
        .sslSocketFactory(certificates.sslSocketFactory(), certificates.trustManager)
        // No transportar las raíces adicionales a otro host mediante redirecciones.
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
}
