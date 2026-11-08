package co.edu.uniandes.vinilos

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import co.edu.uniandes.vinilos.data.LegacyImageTls
import co.edu.uniandes.vinilos.data.*
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class VinilosApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this).apply {
        // Android 5/6 no incluyen ISRG Root X1. Se conservan las raíces del sistema.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            okHttpClient {
                resources.openRawResource(R.raw.isrg_root_x1).use { LegacyImageTls.client(it) }
            }
        }
    }.build()

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
