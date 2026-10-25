package co.edu.uniandes.vinilos

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import co.edu.uniandes.vinilos.ui.albums.AlbumCatalogFragment

class MainActivity : AppCompatActivity(R.layout.activity_main) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager.setFragmentResultListener(AlbumCatalogFragment.ALBUM_SELECTED, this) { _, data ->
            // HU02 reemplaza este aviso por la navegación al detalle usando albumId.
            Toast.makeText(this, getString(R.string.detail_pending, data.getString("albumName")), Toast.LENGTH_LONG).show()
        }
        if (savedInstanceState == null) supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, AlbumCatalogFragment()).commit()
    }
}
