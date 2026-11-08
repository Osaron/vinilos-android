package co.edu.uniandes.vinilos

import android.os.SystemClock
import android.app.Activity
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.core.os.bundleOf
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.recyclerview.widget.RecyclerView
import co.edu.uniandes.vinilos.data.AlbumRepository
import co.edu.uniandes.vinilos.data.ArtistDetailRepository
import co.edu.uniandes.vinilos.data.ArtistRepository
import co.edu.uniandes.vinilos.model.Album
import co.edu.uniandes.vinilos.model.Artist
import co.edu.uniandes.vinilos.model.ArtistAlbum
import co.edu.uniandes.vinilos.model.ArtistDetail
import co.edu.uniandes.vinilos.model.ArtistType
import co.edu.uniandes.vinilos.ui.artists.ArtistCatalogFragment
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class ArtistDetailNavigationTest {
    private val app get() = ApplicationProvider.getApplicationContext<VinilosApplication>()
    private val artists = listOf(
        Artist(100, ArtistType.MUSICIAN, "Rubén Blades", "", "Cantautor"),
        Artist(101, ArtistType.BAND, "Queen", "", "Banda de rock"),
    )

    @Test fun abre_detalle_de_musico_y_vuelve_conservando_la_busqueda() {
        val calls = mutableListOf<Pair<ArtistType, Int>>()
        withRepositories(detail = { type, id ->
            calls += type to id
            ArtistDetail(id, type, "Rubén Blades", null, "Cantautor", albums = listOf(
                ArtistAlbum(1, "Buscando América", null, "1984-08-01", "Elektra", "Salsa"),
            ))
        }) {
            tocar(R.id.open_artists)
            esperarVisible(R.id.artist_list)
            onView(withId(R.id.search_artists)).perform(replaceText("ruben"), closeSoftKeyboard())
            tocarArtista(0)
            esperarTexto(R.id.artist_detail_title, "Rubén Blades")
            onView(withText("Buscando América")).check(matches(isDisplayed()))
            pressBack()
            onView(withId(R.id.search_artists)).check(matches(withText("ruben")))
            onView(withText("Rubén Blades")).check(matches(isDisplayed()))
        }
        assertEquals(listOf(ArtistType.MUSICIAN to 100), calls)
    }

    @Test fun abre_detalle_de_banda_usando_tipo_e_id_de_la_fila() {
        val calls = mutableListOf<Pair<ArtistType, Int>>()
        withRepositories(detail = { type, id ->
            calls += type to id
            ArtistDetail(id, type, "Queen", null, "Banda de rock", creationDate = "1970-01-01")
        }) {
            tocar(R.id.open_artists)
            esperarVisible(R.id.artist_list)
            tocarArtista(1)
            esperarTexto(R.id.artist_detail_title, "Queen")
            onView(withText("1970-01-01")).check(matches(isDisplayed()))
        }
        assertEquals(listOf(ArtistType.BAND to 101), calls)
    }

    @Test fun doble_seleccion_rapida_abre_un_solo_detalle_y_consulta_una_vez() {
        val calls = mutableListOf<Pair<ArtistType, Int>>()
        withRepositories(detail = { type, id ->
            calls += type to id
            ArtistDetail(id, type, "Queen", null, "Rock")
        }) {
            tocar(R.id.open_artists)
            esperarVisible(R.id.artist_list)
            onActivity { activity ->
                val result = bundleOf("artistId" to 101, "artistType" to ArtistType.BAND.name)
                activity.supportFragmentManager.setFragmentResult(ArtistCatalogFragment.ARTIST_SELECTED, result)
                activity.supportFragmentManager.setFragmentResult(ArtistCatalogFragment.ARTIST_SELECTED, result)
            }
            esperarTexto(R.id.artist_detail_title, "Queen")
            onActivity { activity -> assertEquals(2, activity.supportFragmentManager.backStackEntryCount) }
        }
        assertEquals(listOf(ArtistType.BAND to 101), calls)
    }

    @Test fun muestra_carga_y_luego_el_detalle_de_la_respuesta() {
        val pending = CompletableDeferred<ArtistDetail>()
        withRepositories(detail = { type, id -> pending.await() }) {
            tocar(R.id.open_artists)
            esperarVisible(R.id.artist_list)
            tocarArtista(1)
            esperarVisible(R.id.artist_detail_loading)
            SystemClock.sleep(6_000)
            pending.complete(ArtistDetail(101, ArtistType.BAND, "Queen", null, "Rock"))
            esperarTexto(R.id.artist_detail_title, "Queen")
        }
    }

    @Test fun salir_del_detalle_durante_la_carga_cancela_la_consulta() {
        val pending = CompletableDeferred<ArtistDetail>()
        var cancelled = false
        withRepositories(detail = { _, _ ->
            try {
                pending.await()
            } finally {
                cancelled = true
            }
        }) {
            tocar(R.id.open_artists)
            esperarVisible(R.id.artist_list)
            tocarArtista(1)
            esperarVisible(R.id.artist_detail_loading)
            enHiloPrincipal { (it as MainActivity).onBackPressedDispatcher.onBackPressed() }
            esperarVisible(R.id.search_artists)
            esperar { assertEquals(true, cancelled) }
            pending.complete(ArtistDetail(101, ArtistType.BAND, "Queen", null, "Rock"))
            onView(withId(R.id.search_artists)).check(matches(isDisplayed()))
        }
    }

    @Test fun error_de_detalle_permite_reintentar_y_recuperar() {
        var calls = 0
        withRepositories(detail = { type, id ->
            if (++calls == 1) throw IOException("sin red")
            ArtistDetail(101, ArtistType.BAND, "Queen", null, "Rock")
        }) {
            tocar(R.id.open_artists)
            esperarVisible(R.id.artist_list)
            tocarArtista(1)
            esperar { onView(withText(R.string.artist_detail_error)).check(matches(isDisplayed())) }
            tocar(R.id.retry_artist_detail)
            esperarTexto(R.id.artist_detail_title, "Queen")
        }
        assertEquals(2, calls)
    }

    @Test fun rotacion_conserva_el_detalle_sin_repetir_la_consulta() {
        val calls = mutableListOf<Pair<ArtistType, Int>>()
        withRepositories(detail = { type, id ->
            calls += type to id
            ArtistDetail(id, type, "Queen", null, "Rock")
        }) {
            tocar(R.id.open_artists)
            esperarVisible(R.id.artist_list)
            tocarArtista(1)
            esperarTexto(R.id.artist_detail_title, "Queen")
            recreate()
            esperarTexto(R.id.artist_detail_title, "Queen")
        }
        assertEquals(listOf(ArtistType.BAND to 101), calls)
    }

    private fun withRepositories(
        detail: suspend (ArtistType, Int) -> ArtistDetail,
        block: ActivityScenario<MainActivity>.() -> Unit,
    ) {
        val oldAlbums = app.albumRepository
        val oldArtists = app.artistRepository
        val oldDetails = app.artistDetailRepository
        app.albumRepository = object : AlbumRepository { override suspend fun getAlbums(): List<Album> = emptyList() }
        app.artistRepository = ArtistRepository { artists }
        app.artistDetailRepository = ArtistDetailRepository(detail)
        try { ActivityScenario.launch(MainActivity::class.java).use { it.block() } }
        finally {
            app.albumRepository = oldAlbums
            app.artistRepository = oldArtists
            app.artistDetailRepository = oldDetails
        }
    }

    private fun esperar(check: () -> Unit) {
        val end = SystemClock.elapsedRealtime() + 5_000
        while (true) {
            try { check(); return }
            catch (error: AssertionError) { if (SystemClock.elapsedRealtime() > end) throw error }
            catch (error: androidx.test.espresso.NoMatchingViewException) {
                if (SystemClock.elapsedRealtime() > end) throw error
            }
            SystemClock.sleep(100)
        }
    }

    /** Lee el árbol de vistas sin la sincronización de Espresso, que puede bloquearse con el spinner en API 21. */
    private fun esperarVisible(id: Int) = esperarHasta { estaVisible(id) }

    private fun esperarTexto(id: Int, expected: String) = esperar {
        var actual: CharSequence? = null
        enHiloPrincipal { actual = it.findViewById<android.widget.TextView>(id)?.text }
        actual == expected
    }

    private fun esperarHasta(check: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 5_000
        while (!check()) {
            require(SystemClock.elapsedRealtime() <= end) { "La vista esperada no apareció antes del timeout" }
            SystemClock.sleep(100)
        }
    }

    private fun estaVisible(id: Int): Boolean {
        var visible = false
        enHiloPrincipal { visible = it.findViewById<View>(id)?.isShown == true }
        return visible
    }

    private fun tocar(id: Int) = enHiloPrincipal { activity ->
        checkNotNull(activity.findViewById<View>(id)) { "No existe la vista $id" }.performClick()
    }

    private fun tocarArtista(position: Int) = enHiloPrincipal { activity ->
        val list = checkNotNull(activity.findViewById<RecyclerView>(R.id.artist_list))
        checkNotNull(list.findViewHolderForAdapterPosition(position)) { "No está visible el artista $position" }
            .itemView.performClick()
    }

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
}
