package co.edu.uniandes.vinilos

import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
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
import co.edu.uniandes.vinilos.data.AlbumRepository
import co.edu.uniandes.vinilos.model.Album
import co.edu.uniandes.vinilos.model.AlbumDetail
import co.edu.uniandes.vinilos.ui.tracks.AddTrackFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** HU08 (#31): el flujo abre desde el detalle, permite cambiar el álbum y conserva su ID. */
@RunWith(AndroidJUnit4::class)
class AddTrackAlbumSelectionTest {
    private val app get() = ApplicationProvider.getApplicationContext<VinilosApplication>()
    private val albums = listOf(
        Album(100, "Buscando América", "", listOf("Rubén Blades"), "Salsa"),
        Album(101, "Poeta del pueblo", "", listOf("Rubén Blades"), "Salsa"),
    )

    private fun withAlbums(block: (ActivityScenario<MainActivity>) -> Unit) {
        val original = app.albumRepository
        app.albumRepository = object : AlbumRepository {
            override suspend fun getAlbums(): List<Album> = albums
            override suspend fun getAlbum(albumId: Int): AlbumDetail {
                val album = albums.first { it.id == albumId }
                return AlbumDetail(album.id, album.name, "", "1984-08-01", "", "Salsa", "Elektra", album.performers, emptyList())
            }
        }
        try { ActivityScenario.launch(MainActivity::class.java).use(block) }
        finally { app.albumRepository = original }
    }

    private fun openAddTrackFromFirstAlbum() {
        onView(withId(R.id.album_list)).perform(
            RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(0, click()),
        )
        onView(withId(R.id.add_track_button)).perform(scrollTo(), click())
    }

    @Test fun abreConElAlbumDelDetalleYPermiteCambiarlo() = withAlbums { scenario ->
        openAddTrackFromFirstAlbum()
        onView(withId(R.id.selected_album)).check(matches(withText("Buscando América · Rubén Blades")))

        onView(withId(R.id.change_album_button)).perform(click())
        onView(withText("Poeta del pueblo · Rubén Blades")).inRoot(isDialog()).perform(click())
        onView(withId(R.id.selected_album)).check(matches(withText("Poeta del pueblo · Rubén Blades")))

        // Cancelar el selector mantiene el álbum anterior.
        onView(withId(R.id.change_album_button)).perform(click())
        onView(withText(android.R.string.cancel)).inRoot(isDialog()).perform(click())
        onView(withId(R.id.selected_album)).check(matches(withText("Poeta del pueblo · Rubén Blades")))

        // Rotación: el ID elegido y el borrador sobreviven a la recreación.
        onView(withId(R.id.track_name_input)).perform(replaceText("Decisiones"), closeSoftKeyboard())
        scenario.recreate()
        onView(withId(R.id.selected_album)).check(matches(withText("Poeta del pueblo · Rubén Blades")))
        onView(withId(R.id.track_name_input)).check(matches(withText("Decisiones")))
    }

    @Test fun elFormularioValidoQuedaAsociadoAlIdElegido() = withAlbums { scenario ->
        var ready: String? = null
        openAddTrackFromFirstAlbum()
        scenario.onActivity { activity ->
            activity.supportFragmentManager.setFragmentResultListener(AddTrackFragment.TRACK_READY, activity) { _, data ->
                ready = "${data.getInt(AddTrackFragment.ALBUM_ID)}/${data.getString(AddTrackFragment.TRACK_NAME)}/${data.getString(AddTrackFragment.TRACK_DURATION)}"
            }
        }
        onView(withId(R.id.change_album_button)).perform(click())
        onView(withText("Poeta del pueblo · Rubén Blades")).inRoot(isDialog()).perform(click())

        onView(withId(R.id.track_name_input)).perform(replaceText(" Decisiones "), closeSoftKeyboard())
        onView(withId(R.id.track_duration_input)).perform(replaceText("5:05"), closeSoftKeyboard())
        onView(withId(R.id.track_save_button)).perform(scrollTo(), click())

        assertEquals("101/Decisiones/5:05", ready)
    }

    @Test fun atrasRegresaAlDetalleSinPublicarNada() = withAlbums { scenario ->
        var ready: String? = null
        openAddTrackFromFirstAlbum()
        scenario.onActivity { activity ->
            activity.supportFragmentManager.setFragmentResultListener(AddTrackFragment.TRACK_READY, activity) { _, _ -> ready = "enviado" }
        }
        onView(withId(R.id.selected_album)).check(matches(isDisplayed()))
        closeSoftKeyboard()
        pressBack()
        onView(withId(R.id.detail_name)).check(matches(withText("Buscando América")))
        assertNull(ready)
    }
}
