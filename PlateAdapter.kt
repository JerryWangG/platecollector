package com.example.platecollector


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.platecollector.data.PlateRecord

class PlateAdapter(
    private val onItemClick: (PlateRecord) -> Unit,
    private val onDeleteClick: (PlateRecord) -> Unit
) : RecyclerView.Adapter<PlateAdapter.VH>() {

    private val items = mutableListOf<PlateRecord>()

    fun submit(list: List<PlateRecord>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_plate, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val record = items[position]
        holder.bind(record)
        holder.itemView.setOnClickListener { onItemClick(record) }
        holder.btnDelete.setOnClickListener { onDeleteClick(record) }
    }

    override fun getItemCount(): Int = items.size

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvPlate: TextView = itemView.findViewById(R.id.tvPlate)
        private val tvInfo: TextView = itemView.findViewById(R.id.tvInfo)
        val btnDelete: View = itemView.findViewById(R.id.btnDelete)

        fun bind(record: PlateRecord) {
            tvPlate.text = record.plate
            tvInfo.text = "识别 ${record.count} 次 · 首次 ${formatTime(record.firstSeen)}"
        }

        private fun formatTime(ts: Long): String {
            val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
            return sdf.format(java.util.Date(ts))
        }
    }
}