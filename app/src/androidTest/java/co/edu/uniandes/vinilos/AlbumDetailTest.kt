package co.edu.uniandes.vinilos

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.edu.uniandes.vinilos.data.AlbumRepository
import co.edu.uniandes.vinilos.model.Album
import co.edu.uniandes.vinilos.model.AlbumDetail
import co.edu.uniandes.vinilos.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.hamcrest.Matchers.containsString

@RunWith(AndroidJUnit4::class)
class AlbumDetailTest {
    private val app get() = ApplicationProvider.getApplicationContext<VinilosApplication>()
    private val album = Album(7, "Re", "", listOf("Café Tacvba"), "Rock")
    private val detail = AlbumDetail(
        7, "Re", "", "1994-01-01", "Álbum de estudio", "Rock", "Warner Music Mexico",
        listOf("Café Tacvba"), listOf(Track(1, "El aparato", "4:20")),
    )

    @Test fun navegaAlDetalleCorrectoYRegresaAlCatalogo() {
        val original = app.albumRepository
        var requestedId = -1
        app.albumRepository = object : AlbumRepository {
            override suspend fun getAlbums(): List<Album> = listOf(album)
            override suspend fun getAlbum(albumId: Int): AlbumDetail {
                requestedId = albumId
                return detail
            }
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                onView(withId(R.id.album_list)).perform(
                    RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(0, click()),
                )
                onView(withId(R.id.detail_name)).check(matches(withText("Re")))
                // En pantallas pequeñas (p. ej. API 21) los tracks quedan bajo la portada:
                // se desplaza el ScrollView antes de verificar que son visibles.
                onView(withId(R.id.detail_tracks))
                    .perform(scrollTo())
                    .check(matches(withText(containsString("El aparato (4:20)"))))
                    .check(matches(isDisplayed()))
                assertEquals(7, requestedId)
                pressBack()
                onView(withId(R.id.album_list)).check(matches(isDisplayed()))
            }
        } finally { app.albumRepository = original }
    }
}
