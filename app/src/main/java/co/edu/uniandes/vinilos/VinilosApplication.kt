package co.edu.uniandes.vinilos

import android.app.Application
import co.edu.uniandes.vinilos.data.*
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class VinilosApplication : Application() {
    // Punto de composición: los tests UI pueden sustituir la dependencia antes del launch.
    var albumRepository: AlbumRepository = RemoteAlbumRepository(AlbumServiceAdapter(
        Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL)
            .client(OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build())
            .addConverterFactory(GsonConverterFactory.create()).build()
            .create(AlbumService::class.java)
    ))
}
