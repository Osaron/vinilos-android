package co.edu.uniandes.vinilos

import android.app.Activity
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import co.edu.uniandes.vinilos.data.ActiveCollector
import co.edu.uniandes.vinilos.data.AlbumRepository
import co.edu.uniandes.vinilos.data.ArtistDetailRepository
import co.edu.uniandes.vinilos.data.ArtistRepository
import co.edu.uniandes.vinilos.data.CommentRepository
import co.edu.uniandes.vinilos.data.FavoriteArtistRepository
import co.edu.uniandes.vinilos.data.InMemoryActiveCollectorStore
import co.edu.uniandes.vinilos.model.Album
import co.edu.uniandes.vinilos.model.AlbumComment
import co.edu.uniandes.vinilos.model.AlbumDetail
import co.edu.uniandes.vinilos.model.Artist
import co.edu.uniandes.vinilos.model.ArtistDetail
import co.edu.uniandes.vinilos.model.ArtistType
import co.edu.uniandes.vinilos.model.FavoritePerformer
import co.edu.uniandes.vinilos.model.NewComment
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Perfilamiento de HU09 y HU10, con el mismo método que HU04 (ArtistDetailPerformanceTest):
 * recorridos repetidos con datos en memoria, mediana del tiempo hasta ver el resultado y memoria
 * (PSS y heap Java) antes y después. Es una medición diagnóstica repetible; no fija umbrales que
 * dependan del emulador o del dispositivo. Los resultados quedan en logcat con la etiqueta [TAG].
 */
@RunWith(AndroidJUnit4::class)
class CommentsFavoritesPerformanceTest {
    private val app get() = ApplicationProvider.getApplicationContext<VinilosApplication>()
    private val album = Album(100, "Buscando América", "", listOf("Rubén Blades"), "Salsa")
    private val musician = Artist(50, ArtistType.MUSICIAN, "Rubén Blades", "", "Salsa")

    /** Comentarios en memoria por álbum. */
    private class Comments : CommentRepository {
        val list = mutableListOf(AlbumComment(1, "Clásico de la salsa", 5, 7))
        private var nextId = 10
        override suspend fun getComments(albumId: Int): List<AlbumComment> = synchronized(this) { list.toList() }
        override suspend fun addComment(albumId: Int, comment: NewComment): AlbumComment = synchronized(this) {
            AlbumComment(nextId++, comment.description, comment.rating, comment.collectorId).also { list += it }
        }
    }

    /** HU09: 20 aperturas del detalle hasta ver los comentarios y 10 publicaciones completas. */
    @Test fun hu09_ver_comentarios_y_publicar_registran_tiempo_y_memoria() {
        val comments = Comments()
        val albums = app.albumRepository
        val oldComments = app.commentRepository
        val store = app.activeCollectorStore
        app.albumRepository = object : AlbumRepository {
            override suspend fun getAlbums(): List<Album> = listOf(album)
            override suspend fun getAlbum(albumId: Int) = AlbumDetail(album.id, album.name, "", "1984-08-01", "", "Salsa",
                "Elektra", album.performers, emptyList())
        }
        app.commentRepository = comments
        app.activeCollectorStore = InMemoryActiveCollectorStore(ActiveCollector(8, "Ana María"))
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                esperarVisible(R.id.album_list)
                val before = memoriaEstabilizada()
                val openMs = mutableListOf<Long>()
                repeat(20) {
                    val start = SystemClock.elapsedRealtimeNanos()
                    tocarFila(R.id.album_list, 0)
                    esperarTextoQueContenga(R.id.detail_comments, "Clásico de la salsa")
                    openMs += msDesde(start)
                    atras()
                    esperarVisible(R.id.album_list)
                }
                val afterOpen = memoriaEstabilizada()

                tocarFila(R.id.album_list, 0)
                esperarVisible(R.id.add_comment_button)
                val publishMs = mutableListOf<Long>()
                repeat(10) { cycle ->
                    tocar(R.id.add_comment_button)
                    esperarVisible(R.id.comment_input)
                    val text = "Comentario de prueba $cycle"
                    enHiloPrincipal { activity ->
                        activity.findViewById<EditText>(R.id.comment_input).setText(text)
                        activity.findViewById<RadioButton>(R.id.rating_5).isChecked = true
                    }
                    val start = SystemClock.elapsedRealtimeNanos()
                    tocar(R.id.publish_comment_button)
                    esperarTextoQueContenga(R.id.detail_comments, text)
                    publishMs += msDesde(start)
                }
                val after = memoriaEstabilizada()
                assertEquals(11, comments.list.size)
                Log.i(TAG, "hu=HU09; open_cycles=20; detail_comments_visible_ms_p50=${mediana(openMs.take(10))}/${mediana(openMs.takeLast(10))}; " +
                    "publish_cycles=10; publish_to_comment_visible_ms_p50=${mediana(publishMs)}; " +
                    memoria(before, afterOpen, after))
            }
        } finally {
            app.albumRepository = albums
            app.commentRepository = oldComments
            app.activeCollectorStore = store
        }
    }

    /** HU10: 20 aperturas del detalle del artista hasta resolver si ya es favorito, y un agregado. */
    @Test fun hu10_consultar_y_agregar_favorito_registran_tiempo_y_memoria() {
        val favorites = mutableListOf<Int>()
        val albums = app.albumRepository
        val artists = app.artistRepository
        val details = app.artistDetailRepository
        val oldFavorites = app.favoriteArtistRepository
        val store = app.activeCollectorStore
        app.albumRepository = AlbumRepository { emptyList() }
        app.artistRepository = ArtistRepository { listOf(musician) }
        app.artistDetailRepository = ArtistDetailRepository { type, id ->
            ArtistDetail(id, type, musician.name, null, "Cantante panameño", birthDate = "1948-07-16")
        }
        app.favoriteArtistRepository = object : FavoriteArtistRepository {
            override suspend fun getFavorites(collectorId: Int) = synchronized(favorites) {
                favorites.map { FavoritePerformer(it, musician.name, null, "", null) }
            }
            override suspend fun addFavorite(collectorId: Int, artistId: Int, type: ArtistType) = synchronized(favorites) {
                favorites += artistId
                FavoritePerformer(artistId, musician.name, null, "", null)
            }
        }
        app.activeCollectorStore = InMemoryActiveCollectorStore(ActiveCollector(8, "Ana María"))
        val ready = app.getString(R.string.favorite_collector, "Ana María")
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                tocar(R.id.open_artists)
                esperarVisible(R.id.artist_list)
                val before = memoriaEstabilizada()
                val openMs = mutableListOf<Long>()
                repeat(20) {
                    val start = SystemClock.elapsedRealtimeNanos()
                    tocarFila(R.id.artist_list, 0)
                    esperarTexto(R.id.favorite_status, ready)
                    openMs += msDesde(start)
                    tocar(R.id.back_artist_detail)
                    esperarVisible(R.id.search_artists)
                }
                val afterOpen = memoriaEstabilizada()

                tocarFila(R.id.artist_list, 0)
                esperarTexto(R.id.favorite_status, ready)
                val start = SystemClock.elapsedRealtimeNanos()
                tocar(R.id.add_favorite_button)
                esperarTexto(R.id.favorite_status, app.getString(R.string.favorite_added, "Ana María"))
                val addMs = msDesde(start)
                val after = memoriaEstabilizada()
                assertEquals(listOf(musician.id), favorites)
                Log.i(TAG, "hu=HU10; open_cycles=20; favorite_state_ready_ms_p50=${mediana(openMs.take(10))}/${mediana(openMs.takeLast(10))}; " +
                    "add_to_confirmation_ms=$addMs; " + memoria(before, afterOpen, after))
            }
        } finally {
            app.albumRepository = albums
            app.artistRepository = artists
            app.artistDetailRepository = details
            app.favoriteArtistRepository = oldFavorites
            app.activeCollectorStore = store
        }
    }

    private fun msDesde(startNanos: Long) = (SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000

    private fun mediana(values: List<Long>): Long {
        val sorted = values.sorted()
        return if (sorted.size % 2 == 0) (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2 else sorted[sorted.size / 2]
    }

    private fun memoria(before: MemorySnapshot, middle: MemorySnapshot, after: MemorySnapshot) =
        "pss_kb_before=${before.pssKb}; pss_kb_after_open=${middle.pssKb}; pss_kb_after=${after.pssKb}; " +
            "pss_delta_kb=${after.pssKb - before.pssKb}; java_heap_kb_before=${before.javaHeapKb}; " +
            "java_heap_kb_after=${after.javaHeapKb}; java_heap_delta_kb=${after.javaHeapKb - before.javaHeapKb}"

    private fun memoriaEstabilizada(): MemorySnapshot {
        System.gc()
        Runtime.getRuntime().gc()
        SystemClock.sleep(300)
        val runtime = Runtime.getRuntime()
        return MemorySnapshot(Debug.getPss(), (runtime.totalMemory() - runtime.freeMemory()) / 1024)
    }

    // Se consulta el árbol de vistas en el hilo principal sin esperar inactividad: en API 21 un
    // indicador animado puede dejar a Espresso esperando (ver ESC-19).
    private fun esperarVisible(id: Int) = esperar { var shown = false; enHiloPrincipal { shown = it.findViewById<View>(id)?.isShown == true }; shown }

    private fun esperarTexto(id: Int, expected: String) = esperar { texto(id) == expected }

    private fun esperarTextoQueContenga(id: Int, expected: String) = esperar { texto(id)?.contains(expected) == true }

    private fun texto(id: Int): String? {
        var actual: String? = null
        enHiloPrincipal { actual = it.findViewById<TextView>(id)?.takeIf(View::isShown)?.text?.toString() }
        return actual
    }

    private fun esperar(check: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 5_000
        while (true) {
            if (check()) return
            require(SystemClock.elapsedRealtime() <= end) { "La vista esperada no apareció antes del timeout" }
            SystemClock.sleep(50)
        }
    }

    private fun tocar(id: Int) = enHiloPrincipal { activity ->
        checkNotNull(activity.findViewById<View>(id)) { "No existe la vista $id" }.performClick()
    }

    private fun tocarFila(listId: Int, position: Int) = enHiloPrincipal { activity ->
        val list = checkNotNull(activity.findViewById<RecyclerView>(listId))
        checkNotNull(list.findViewHolderForAdapterPosition(position)) { "No está visible la fila $position" }.itemView.performClick()
    }

    private fun atras() = enHiloPrincipal { (it as AppCompatActivity).onBackPressedDispatcher.onBackPressed() }

    private fun enHiloPrincipal(action: (Activity) -> Unit) {
        var error: Throwable? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                val activity = checkNotNull(
                    ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull(),
                ) { "No hay una actividad en primer plano" }
                action(activity)
            } catch (failure: Throwable) { error = failure }
        }
        error?.let { throw it }
    }

    private data class MemorySnapshot(val pssKb: Long, val javaHeapKb: Long)

    private companion object { const val TAG = "HU09_HU10_PERFORMANCE" }
}
