package co.edu.uniandes.vinilos.ui.artists

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.databinding.ItemArtistBinding
import co.edu.uniandes.vinilos.model.Artist
import co.edu.uniandes.vinilos.model.ArtistType
import coil.load

class ArtistAdapter(private val selected: (Artist) -> Unit) : ListAdapter<Artist, ArtistAdapter.Holder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemArtistBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))
    inner class Holder(private val binding: ItemArtistBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(artist: Artist) = with(binding) {
            artistName.text = artist.name
            artistType.setText(if (artist.type == ArtistType.BAND) R.string.artist_band else R.string.artist_musician)
            artistImage.load(artist.image) {
                addHeader("User-Agent", "VinilosAndroid/1.0 (https://github.com/Osaron/vinilos-android)")
                placeholder(R.drawable.ic_artist)
                error(R.drawable.ic_artist)
            }
            root.contentDescription = root.context.getString(R.string.select_artist, artist.name, artistType.text)
            root.setOnClickListener { selected(artist) }
        }
    }
    private object Diff : DiffUtil.ItemCallback<Artist>() {
        override fun areItemsTheSame(old: Artist, new: Artist) = old.id == new.id && old.type == new.type
        override fun areContentsTheSame(old: Artist, new: Artist) = old == new
    }
}
