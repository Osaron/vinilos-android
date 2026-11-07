package co.edu.uniandes.vinilos.ui.collectors

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import co.edu.uniandes.vinilos.R
import co.edu.uniandes.vinilos.databinding.ItemCollectorBinding
import co.edu.uniandes.vinilos.model.Collector

class CollectorAdapter(private val selected: (Collector) -> Unit) : ListAdapter<Collector, CollectorAdapter.Holder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
        ItemCollectorBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))
    inner class Holder(private val binding: ItemCollectorBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(collector: Collector) = with(binding) {
            collectorName.text = collector.name
            collectorSubtitle.text = collector.email.ifBlank { root.context.getString(R.string.collector_member) }
            root.contentDescription = root.context.getString(R.string.select_collector, collector.name)
            root.setOnClickListener { selected(collector) }
        }
    }
    private object Diff : DiffUtil.ItemCallback<Collector>() {
        override fun areItemsTheSame(old: Collector, new: Collector) = old.id == new.id
        override fun areContentsTheSame(old: Collector, new: Collector) = old == new
    }
}
