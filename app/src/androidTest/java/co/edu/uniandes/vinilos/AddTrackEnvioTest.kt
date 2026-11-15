package co.edu.uniandes.vinilos

import android.app.Activity
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import co.edu.uniandes.vinilos.data.AlbumRepository
import co.edu.uniandes.vinilos.data.TrackAssociationException
import co.edu.uniandes.vinilos.data.TrackAssociationException.Reason
import co.edu.uniandes.vinilos.data.TrackAssociationRepository
import co.edu.uniandes.vinilos.model.Album
import co.edu.uniandes.vinilos.model.AlbumDetail
import co.edu.uniandes.vinilos.model.NewTrack
import co.edu.uniandes.vinilos.model.Track
import kotlinx.coroutines.CompletableDeferred
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Escenarios E2E de HU08 (#35): envío de la canción, estados de éxito y error (#34) y refresco
 * del detalle del álbum (ESC-27 a ESC-31). Usan dobles en memoria para el catálogo y para el
 * contrato de #33, de modo que no dependen de la red.
 */
@RunWith(AndroidJUnit4::class)
class AddTrackEnvioTest {
    private val app get() = ApplicationProvider.getApplicationContext<VinilosApplication>()

    /** Catálogo y canciones en memoria compartidos por ambos dobles. */
    private class Backend {
        val albums = mutableListOf(
            Album(100, "Buscando América", "", listOf("Rubén Blades"), "Salsa"),
            Album(101, "Poeta del pueblo", "", listOf("Rubén Blades"), "Salsa"),
        )
        val tracks = mutableMapOf(100 to mutableListOf(Track(1, "Decisiones", "4:05")), 101 to mutableListOf())
        val requests = mutableListOf<Pair<Int, NewTrack>>()
        var nextFailure: Reason? = null
        var nextGate: CompletableDeferred<Unit>? = null
        var loseNextResponse = false
        private var nextId = 10

        val albumRepository = object : AlbumRepository {
            override suspend fun getAlbums(): List<Album> = synchronized(this@Backend) { albums.toList() }
            override suspend fun getAlbum(albumId: Int): AlbumDetail = synchronized(this@Backend) {
                val album = albums.first { it.id == albumId }
                AlbumDetail(album.id, album.name, "", "1984-08-01", "", album.genre, "Elektra", album.performers,
                    tracks[albumId].orEmpty().toList())
            }
        }

        val trackRepository = object : TrackAssociationRepository {
            override suspend fun addTrack(albumId: Int, track: NewTrack): Track {
                synchronized(this@Backend) { requests += albumId to track }
                nextGate?.also { nextGate = null }?.await()
                synchronized(this@Backend) {
                    nextFailure?.let { nextFailure = null; throw TrackAssociationException(it, "Fallo simulado: $it") }
                    if (albums.none { it.id == albumId }) throw TrackAssociationException(Reason.ALBUM_NOT_FOUND, "No existe")
                    val saved = Track(nextId++, track.name.trim(), track.duration.trim())
                    tracks.getOrPut(albumId) { mutableListOf() } += saved
                    if (loseNextResponse) {
                        loseNextResponse = false
                        throw TrackAssociationException(Reason.NETWORK, "Respuesta perdida")
                    }
                    return saved
                }
            }

            override suspend fun getTracks(albumId: Int): List<Track> = synchronized(this@Backend) {
                tracks[albumId]?.toList() ?: throw TrackAssociationException(Reason.ALBUM_NOT_FOUND, "No existe")
            }
        }
    }

    private fun conBackend(block: (Backend) -> Unit) {
        val backend = Backend()
        val albums = app.albumRepository
        val tracks = app.trackAssociationRepository
        app.albumRepository = backend.albumRepository
        app.trackAssociationRepository = backend.trackRepository
        try { ActivityScenario.launch(MainActivity::class.java).use { block(backend) } }
        finally {
            app.albumRepository = albums
            app.trackAssociationRepository = tracks
        }
    }

    private fun abrirAgregarCancion(posicion: Int = 0) {
        onView(withId(R.id.album_list)).perform(
            RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(posicion, click()),
        )
        onView(withId(R.id.add_track_button)).perform(scrollTo(), click())
    }

    private fun escribirCancion(nombre: String, duracion: String) {
        onView(withId(R.id.track_name_input)).perform(replaceText(nombre), closeSoftKeyboard())
        onView(withId(R.id.track_duration_input)).perform(replaceText(duracion), closeSoftKeyboard())
    }

    private fun guardar() = onView(withId(R.id.track_save_button)).perform(scrollTo(), click())

    private fun texto(id: Int) = app.getString(id)

    /** ESC-27: guardar una canción válida vuelve al detalle y la muestra en la lista. */
    @Test fun esc27_guardaYRefrescaElDetalle() = conBackend { backend ->
        abrirAgregarCancion()
        escribirCancion(" Todos vuelven ", "5:20")
        guardar()

        onView(withId(R.id.detail_name)).check(matches(withText("Buscando América")))
        onView(withId(R.id.detail_tracks)).check(matches(withText(containsString("• Todos vuelven (5:20)"))))
        assertEquals(listOf(100 to NewTrack("Todos vuelven", "5:20")), backend.requests)
    }

    /** ESC-28: un error del servidor conserva el borrador, lo explica y permite reintentar. */
    @Test fun esc28_errorDelServidorPermiteReintentar() = conBackend { backend ->
        backend.nextFailure = Reason.SERVER
        abrirAgregarCancion()
        escribirCancion("Todos vuelven", "5:20")
        guardar()

        onView(withId(R.id.track_status)).check(matches(withText(texto(R.string.add_track_save_error))))
        onView(withId(R.id.track_name_input)).check(matches(withText("Todos vuelven")))
        onView(withId(R.id.track_duration_input)).check(matches(withText("5:20")))

        guardar()
        onView(withId(R.id.detail_tracks)).check(matches(withText(containsString("• Todos vuelven (5:20)"))))
        assertEquals(2, backend.requests.size)
    }

    /**
     * ESC-29: mientras se guarda, el botón queda deshabilitado y varios toques crean una sola
     * canción. Se consulta y toca en el hilo principal sin esperar inactividad, porque el
     * indicador de guardado animado bloquearía a Espresso en Android 5 (ver ESC-19).
     */
    @Test fun esc29_tocarVariasVecesCreaUnaSolaCancion() = conBackend { backend ->
        val gate = CompletableDeferred<Unit>().also { backend.nextGate = it }
        abrirAgregarCancion()
        escribirCancion("Todos vuelven", "5:20")
        onView(withId(R.id.track_save_button)).perform(scrollTo())

        repeat(3) { enHiloPrincipal { it.findViewById<Button>(R.id.track_save_button).performClick() } }
        enHiloPrincipal { actividad ->
            assertTrue(actividad.findViewById<View>(R.id.saving_indicator).isShown)
            assertFalse(actividad.findViewById<Button>(R.id.track_save_button).isEnabled)
            assertFalse(actividad.findViewById<Button>(R.id.change_album_button).isEnabled)
            assertEquals(texto(R.string.add_track_saving), actividad.findViewById<TextView>(R.id.track_status).text.toString())
        }
        assertEquals(1, backend.requests.size)

        gate.complete(Unit)
        esperar {
            enHiloPrincipal { actividad ->
                val canciones = actividad.findViewById<TextView>(R.id.detail_tracks)?.text?.toString().orEmpty()
                assertTrue(canciones, canciones.contains("• Todos vuelven (5:20)"))
            }
        }
        assertEquals(1, backend.requests.size)
        assertEquals(1, backend.tracks.getValue(100).count { it.name == "Todos vuelven" })
    }

    /** ESC-30: si el álbum se eliminó, pide elegir otro y guarda la canción en el nuevo. */
    @Test fun esc30_albumEliminadoPideElegirOtro() = conBackend { backend ->
        abrirAgregarCancion()
        synchronized(backend) { backend.albums.removeAll { it.id == 100 } }
        escribirCancion("Todos vuelven", "5:20")
        guardar()

        onView(withId(R.id.track_status)).check(matches(withText(texto(R.string.add_track_save_album_missing))))
        onView(withId(R.id.album_status)).check(matches(withText(texto(R.string.add_track_album_missing))))

        onView(withId(R.id.change_album_button)).perform(click())
        onView(withText("Poeta del pueblo · Rubén Blades")).inRoot(isDialog()).perform(click())
        onView(withId(R.id.track_status)).check(matches(not(isDisplayed())))
        guardar()

        onView(withId(R.id.detail_name)).check(matches(withText("Poeta del pueblo")))
        onView(withId(R.id.detail_tracks)).check(matches(withText(containsString("• Todos vuelven (5:20)"))))
        assertEquals(listOf(100, 101), backend.requests.map { it.first })
    }

    /**
     * ESC-31: si se pierde la respuesta pero la canción ya está en el álbum, no se reenvía: se
     * avisa, se ofrece «Ver álbum» y el detalle la muestra una sola vez.
     */
    @Test fun esc31_respuestaPerdidaNoDuplicaLaCancion() = conBackend { backend ->
        backend.loseNextResponse = true
        abrirAgregarCancion()
        escribirCancion("Todos vuelven", "5:20")
        guardar()

        onView(withId(R.id.track_status))
            .check(matches(withText(app.getString(R.string.add_track_possibly_saved, "Todos vuelven", "5:20"))))
        onView(withId(R.id.view_album_button)).check(matches(isDisplayed())).perform(click())

        onView(withId(R.id.detail_name)).check(matches(withText("Buscando América")))
        onView(withId(R.id.detail_tracks)).check(matches(withText(containsString("• Todos vuelven (5:20)"))))
        assertEquals(1, backend.requests.size)
        assertEquals(1, backend.tracks.getValue(100).count { it.name == "Todos vuelven" })
    }

    /** Ejecuta [accion] en el hilo principal sobre la actividad reanudada, sin esperar inactividad. */
    private fun enHiloPrincipal(accion: (Activity) -> Unit) {
        var error: Throwable? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                val actividad = checkNotNull(
                    ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull(),
                ) { "No hay una actividad en primer plano" }
                accion(actividad)
            } catch (e: Throwable) {
                error = e
            }
        }
        error?.let { throw it }
    }

    /** Reintenta [verificacion] hasta que pase o se agote el tiempo. */
    private fun esperar(tiempoMaximoMs: Long = 5_000, verificacion: () -> Unit) {
        val limite = System.currentTimeMillis() + tiempoMaximoMs
        while (true) {
            try { verificacion(); return } catch (e: Throwable) {
                if (System.currentTimeMillis() > limite) throw e
                Thread.sleep(100)
            }
        }
    }
}
