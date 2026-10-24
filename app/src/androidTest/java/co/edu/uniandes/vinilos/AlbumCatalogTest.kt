package co.edu.uniandes.vinilos

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import co.edu.uniandes.vinilos.data.AlbumRepository
import co.edu.uniandes.vinilos.model.Album
import co.edu.uniandes.vinilos.ui.albums.AlbumCatalogFragment
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/** UI con repositorio controlado. No reemplaza las pruebas contra el backend real. */
@RunWith(AndroidJUnit4::class)
class AlbumCatalogTest {
    private val app get() = ApplicationProvider.getApplicationContext<VinilosApplication>()
    private val album = Album(7, "Re", "", listOf("Café Tacvba"), "Rock")

    @Test fun muestraBuscaYEntregaElIdSeleccionado() {
        val original = app.albumRepository
        app.albumRepository = AlbumRepository { listOf(album) }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                onView(withText("Re")).check(matches(isDisplayed()))
                onView(withId(R.id.search_albums)).perform(typeText("zzz"), closeSoftKeyboard())
                onView(withText(R.string.no_search_results)).check(matches(isDisplayed()))
                onView(withId(R.id.search_albums)).perform(clearText(), closeSoftKeyboard())
                var selectedId = -1
                scenario.onActivity { activity ->
                    activity.supportFragmentManager.setFragmentResultListener(AlbumCatalogFragment.ALBUM_SELECTED, activity) { _, data ->
                        selectedId = data.getInt("albumId")
                    }
                }
                onView(withId(R.id.album_list)).perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(0, click()))
                scenario.onActivity { assertEquals(7, selectedId) }
                scenario.recreate()
                onView(withText("Re")).check(matches(isDisplayed()))
            }
        } finally { app.albumRepository = original }
    }

    @Test fun muestraCatalogoVacio() {
        val original = app.albumRepository
        app.albumRepository = AlbumRepository { emptyList() }
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                onView(withText(R.string.catalog_empty)).check(matches(isDisplayed()))
            }
        } finally { app.albumRepository = original }
    }

    @Test fun permiteReintentarDespuesDeError() {
        val original = app.albumRepository
        var attempts = 0
        app.albumRepository = AlbumRepository { if (++attempts == 1) throw IOException() else listOf(album) }
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                onView(withText(R.string.catalog_error)).check(matches(isDisplayed()))
                onView(withId(R.id.retry_button)).perform(click())
                onView(withText("Re")).check(matches(isDisplayed()))
            }
        } finally { app.albumRepository = original }
    }
}
