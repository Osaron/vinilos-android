package co.edu.uniandes.vinilos.ui.albums

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.databinding.ItemAlbumBinding
import co.edu.uniandes.vinilos.model.Album
import coil.load

class AlbumAdapter(private val selected: (Album) -> Unit) : ListAdapter<Album, AlbumAdapter.Holder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemAlbumBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemAlbumBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(album: Album) = with(binding) {
            albumName.text = album.name
            albumArtist.text = album.performers.joinToString(", ").ifBlank {
                album.genre.ifBlank { root.context.getString(R.string.unknown_artist) }
            }
            albumCover.contentDescription = root.context.getString(R.string.album_cover, album.name)
            albumCover.load(album.cover) { placeholder(R.drawable.ic_album); error(R.drawable.ic_album) }
            root.contentDescription = root.context.getString(R.string.select_album, album.name, albumArtist.text)
            root.setOnClickListener { selected(album) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<Album>() {
        override fun areItemsTheSame(old: Album, new: Album) = old.id == new.id
        override fun areContentsTheSame(old: Album, new: Album) = old == new
    }
}
