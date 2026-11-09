package co.edu.uniandes.vinilos

import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import co.edu.uniandes.vinilos.ui.albums.CreateAlbumFragment
import co.edu.uniandes.vinilos.ui.albums.AlbumCatalogFragment
import co.edu.uniandes.vinilos.ui.albums.AlbumDetailFragment

import co.edu.uniandes.vinilos.ui.artists.ArtistCatalogFragment
import co.edu.uniandes.vinilos.ui.artists.ArtistDetailFragment
import co.edu.uniandes.vinilos.ui.collectors.CollectorCatalogFragment
import co.edu.uniandes.vinilos.ui.collectors.CollectorDetailFragment

class MainActivity : AppCompatActivity(R.layout.activity_main) {
    private var openingArtistDetail = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager.setFragmentResultListener(CreateAlbumFragment.OPEN_CREATE_ALBUM, this) { _, _ ->
            if (supportFragmentManager.findFragmentById(R.id.fragment_container) is CreateAlbumFragment) {
                return@setFragmentResultListener
            }
            hideKeyboard()
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, CreateAlbumFragment())
                .addToBackStack("create_album")
                .commit()
        }
        supportFragmentManager.setFragmentResultListener(AlbumCatalogFragment.ALBUM_SELECTED, this) { _, data ->
            val albumId = data.getInt("albumId", -1)
            if (albumId <= 0 || supportFragmentManager.findFragmentById(R.id.fragment_container) is AlbumDetailFragment) {
                return@setFragmentResultListener
            }
            hideKeyboard()
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, AlbumDetailFragment.newInstance(albumId))
                .addToBackStack("album_detail")
                .commit()
        }
        supportFragmentManager.setFragmentResultListener(AlbumCatalogFragment.OPEN_ARTISTS, this) { _, _ ->
            if (supportFragmentManager.findFragmentById(R.id.fragment_container) !is ArtistCatalogFragment) {
                hideKeyboard()
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, ArtistCatalogFragment())
                    .addToBackStack("artist_catalog").commit()
            }
        }
        supportFragmentManager.setFragmentResultListener(ArtistCatalogFragment.ARTIST_SELECTED, this) { _, data ->
            val artistId = data.getInt("artistId", -1)
            val artistType = data.getString("artistType").orEmpty()
            if (artistId <= 0 || openingArtistDetail || supportFragmentManager.findFragmentById(R.id.fragment_container) is ArtistDetailFragment) {
                return@setFragmentResultListener
            }
            openingArtistDetail = true
            hideKeyboard()
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ArtistDetailFragment.newInstance(artistId, artistType))
                .addToBackStack("artist_detail")
                .commit()
            supportFragmentManager.executePendingTransactions()
            openingArtistDetail = false
        }
        supportFragmentManager.setFragmentResultListener(AlbumCatalogFragment.OPEN_COLLECTORS, this) { _, _ ->
            if (supportFragmentManager.findFragmentById(R.id.fragment_container) !is CollectorCatalogFragment) {
                hideKeyboard()
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, CollectorCatalogFragment())
                    .addToBackStack("collector_catalog").commit()
            }
        }
        supportFragmentManager.setFragmentResultListener(CollectorCatalogFragment.OPEN_DETAIL, this) { _, data ->
            val collectorId = data.getInt("collectorId", -1)
            if (collectorId <= 0 || supportFragmentManager.findFragmentById(R.id.fragment_container) is CollectorDetailFragment) {
                return@setFragmentResultListener
            }
            hideKeyboard()
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, CollectorDetailFragment.newInstance(collectorId))
                .addToBackStack("collector_detail")
                .commit()
        }
        if (savedInstanceState == null) supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, AlbumCatalogFragment()).commit()
    }

    /** Evita que el teclado del buscador siga abierto en el detalle y consuma el botón Atrás. */
    private fun hideKeyboard() {
        val focused = currentFocus ?: return
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(focused.windowToken, 0)
        focused.clearFocus()
    }
}
