package co.edu.uniandes.vinilos

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import co.edu.uniandes.vinilos.network.LegacyImageCalls
import co.edu.uniandes.vinilos.network.wikimediaClient
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import co.edu.uniandes.vinilos.data.*
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class VinilosApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        val platform = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
        val calls = LegacyImageCalls(platform, Build.VERSION.SDK_INT) {
            val roots = listOf(R.raw.isrg_root_x1, R.raw.isrg_root_x2).map { resource ->
                resources.openRawResource(resource).use {
                    CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
                }
            }
            wikimediaClient(platform, roots)
        }
        return ImageLoader.Builder(this).callFactory(calls).build()
    }

    var collectorRepository: CollectorRepository = RemoteCollectorRepository(CollectorServiceAdapter(
        Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL)
            .client(OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build())
            .addConverterFactory(GsonConverterFactory.create()).build()
            .create(CollectorService::class.java)
    ))
    var artistRepository: ArtistRepository = RemoteArtistRepository(ArtistServiceAdapter(
        Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL)
            .client(OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build())
            .addConverterFactory(GsonConverterFactory.create()).build()
            .create(ArtistService::class.java)
    ))
    // Punto de composición: los tests UI pueden sustituir la dependencia antes del launch.
    var albumRepository: AlbumRepository = RemoteAlbumRepository(AlbumServiceAdapter(
        Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL)
            .client(OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build())
            .addConverterFactory(GsonConverterFactory.create()).build()
            .create(AlbumService::class.java)
    ))

    // HU07: los tests UI pueden sustituirlo por un doble controlado antes del launch.
    var albumCreationRepository: AlbumCreationRepository = RemoteAlbumCreationRepository(AlbumCreationServiceAdapter(
        Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL)
            .client(OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build())
            .addConverterFactory(GsonConverterFactory.create()).build()
            .create(AlbumCreationService::class.java)
    ))
}
