package co.edu.uniandes.vinilos

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import co.edu.uniandes.vinilos.ui.albums.AlbumCatalogFragment
import co.edu.uniandes.vinilos.ui.albums.AlbumDetailFragment

class MainActivity : AppCompatActivity(R.layout.activity_main) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager.setFragmentResultListener(AlbumCatalogFragment.ALBUM_SELECTED, this) { _, data ->
            val albumId = data.getInt("albumId", -1)
            if (albumId <= 0 || supportFragmentManager.findFragmentById(R.id.fragment_container) is AlbumDetailFragment) {
                return@setFragmentResultListener
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, AlbumDetailFragment.newInstance(albumId))
                .addToBackStack("album_detail")
                .commit()
        }
        if (savedInstanceState == null) supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, AlbumCatalogFragment()).commit()
    }
}
