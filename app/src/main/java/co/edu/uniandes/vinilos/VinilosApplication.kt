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

    /**
     * Un único cliente HTTP para toda la app. Cada OkHttpClient tiene su propio pool de conexiones
     * y su pool de hilos; crear uno por servicio multiplicaba ese costo en memoria. Las variantes se
     * derivan con newBuilder(), que comparte el pool de hilos y el de conexiones del cliente base.
     */
    private val httpClient: OkHttpClient by lazy { OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build() }
    private val gsonConverter: GsonConverterFactory by lazy { GsonConverterFactory.create() }
    private fun api(client: OkHttpClient): Retrofit = Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL)
        .client(client).addConverterFactory(gsonConverter).build()

    /** Lecturas: OkHttp puede reintentar ante un fallo de conexión. */
    private val readApi: Retrofit by lazy { api(httpClient) }

    /** Escrituras: sin reintento automático para no duplicar registros en BackVynils. */
    private val writeApi: Retrofit by lazy { api(httpClient.newBuilder().retryOnConnectionFailure(false).build()) }

    var collectorRepository: CollectorRepository = RemoteCollectorRepository(CollectorServiceAdapter(
        readApi.create(CollectorService::class.java)
    ))
    var collectorDetailRepository: CollectorDetailRepository = RemoteCollectorDetailRepository(CollectorDetailServiceAdapter(
        readApi.create(CollectorService::class.java)
    ))
    var collectorAlbumRepository = CollectorAlbumRepository(
        // BackVynils puede cerrar conexiones inactivas. Abrir una conexión para cada
        // operación evita reutilizarlas sin reenviar automáticamente el POST.
        api(httpClient.newBuilder().connectionPool(okhttp3.ConnectionPool(0, 1, TimeUnit.SECONDS))
            .retryOnConnectionFailure(false).build()).create(CollectorAlbumService::class.java)
    )
    var artistRepository: ArtistRepository = RemoteArtistRepository(ArtistServiceAdapter(
        readApi.create(ArtistService::class.java)
    ))
    var artistDetailRepository: ArtistDetailRepository = RemoteArtistDetailRepository(ArtistDetailServiceAdapter(
        writeApi.create(ArtistDetailService::class.java)
    ))
    // Punto de composición: los tests UI pueden sustituir la dependencia antes del launch.
    var albumRepository: AlbumRepository = RemoteAlbumRepository(AlbumServiceAdapter(
        readApi.create(AlbumService::class.java)
    ))

    // HU07: los tests UI pueden sustituirlo por un doble controlado antes del launch.
    var albumCreationRepository: AlbumCreationRepository = RemoteAlbumCreationRepository(AlbumCreationServiceAdapter(
        readApi.create(AlbumCreationService::class.java)
    ))

    // #80: coleccionista activo de HU09 y HU10. Se crea al primer uso, cuando el contexto ya existe;
    // los tests UI pueden sustituirlo antes del launch.
    private val persistedActiveCollectorStore: ActiveCollectorStore by lazy {
        PreferencesActiveCollectorStore(getSharedPreferences(PreferencesActiveCollectorStore.FILE, MODE_PRIVATE))
    }
    private var activeCollectorStoreOverride: ActiveCollectorStore? = null
    var activeCollectorStore: ActiveCollectorStore
        get() = activeCollectorStoreOverride ?: persistedActiveCollectorStore
        set(value) { activeCollectorStoreOverride = value }

    // HU09: comentarios de un álbum; los tests UI pueden sustituirlo antes del launch.
    var commentRepository: CommentRepository = RemoteCommentRepository(CommentServiceAdapter(
        writeApi.create(CommentService::class.java)
    ))

    // HU10: artistas favoritos del coleccionista; los tests UI pueden sustituirlo antes del launch.
    var favoriteArtistRepository: FavoriteArtistRepository = RemoteFavoriteArtistRepository(FavoriteArtistServiceAdapter(
        writeApi.create(FavoriteArtistService::class.java)
    ))

    // HU12: dependencia sustituible por un doble en las pruebas del flujo.
    var bandMusicianRepository: BandMusicianRepository = RemoteBandMusicianRepository(BandMusicianServiceAdapter(
        writeApi.create(BandMusicianService::class.java)
    ))

    // HU15: discografía de músicos y bandas; sin reintentos automáticos para no duplicar asociaciones.
    var artistAlbumRepository: ArtistAlbumRepository = RemoteArtistAlbumRepository(ArtistAlbumServiceAdapter(
        writeApi.create(ArtistAlbumService::class.java)
    ))

    // HU14: premios de músicos y bandas; sin reintentos automáticos porque el backend no evita repetidos.
    var artistPrizeRepository: ArtistPrizeRepository = RemoteArtistPrizeRepository(ArtistPrizeServiceAdapter(
        writeApi.create(ArtistPrizeService::class.java)
    ))

    // HU08 (#33): sin reintentos automáticos de OkHttp para no duplicar canciones.
    var trackAssociationRepository: TrackAssociationRepository = RemoteTrackAssociationRepository(TrackServiceAdapter(
        writeApi.create(TrackService::class.java)
    ))
    // HU13 comparte Prize/PrizeDto con HU14, con creación sin reintentos de escritura.
    var prizeRepository: PrizeRepository = RemotePrizeRepository(PrizeServiceAdapter(
        writeApi.create(PrizeService::class.java)
    ))

}
