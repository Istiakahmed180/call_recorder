package com.example.callrecorder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Locale

class RecordingsAdapter(
    private val items: MutableList<Recording>,
    private val onPlay: (Recording) -> Unit,
    private val onShare: (Recording) -> Unit,
    private val onDelete: (Recording) -> Unit
) : RecyclerView.Adapter<RecordingsAdapter.VH>() {

    private val dateFmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US)

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.textTitle)
        val subtitle: TextView = view.findViewById(R.id.textSubtitle)
        val btnPlay: View = view.findViewById(R.id.btnPlay)
        val btnShare: View = view.findViewById(R.id.btnShare)
        val btnDelete: View = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_recording, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val rec = items[position]
        holder.title.text = rec.displayName
        val sizeMb = rec.sizeBytes / (1024.0 * 1024.0)
        holder.subtitle.text = "${dateFmt.format(rec.dateMillis)} • %.1f MB".format(sizeMb)
        holder.btnPlay.setOnClickListener { onPlay(rec) }
        holder.btnShare.setOnClickListener { onShare(rec) }
        holder.btnDelete.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                // Confirmation + actual deletion are handled by the host; the
                // list is refreshed only after the user confirms.
                onDelete(items[pos])
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<Recording>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
