package com.example.callrecorder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class RecordingsAdapter(
    private val items: MutableList<Recording>,
    private val onPlayToggle: (Recording) -> Unit,
    private val onShare: (Recording) -> Unit,
    private val onDelete: (Recording) -> Unit
) : RecyclerView.Adapter<RecordingsAdapter.VH>() {

    private val dateFmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US)
    private var playingFile: File? = null

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.textTitle)
        val subtitle: TextView = view.findViewById(R.id.textSubtitle)
        val btnPlay: ImageButton = view.findViewById(R.id.btnPlay)
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
        val meta = buildString {
            append(dateFmt.format(rec.dateMillis))
            if (rec.durationMs > 0) append(" • ").append(formatDuration(rec.durationMs))
            append(" • ").append("%.1f MB".format(sizeMb))
        }
        holder.subtitle.text = meta

        val isPlaying = rec.file == playingFile
        holder.btnPlay.setImageResource(if (isPlaying) R.drawable.ic_stop else R.drawable.ic_play)
        holder.btnPlay.contentDescription =
            holder.itemView.context.getString(if (isPlaying) R.string.stop else R.string.play)

        holder.btnPlay.setOnClickListener { onPlayToggle(rec) }
        holder.btnShare.setOnClickListener { onShare(rec) }
        holder.btnDelete.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) onDelete(items[pos])
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<Recording>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    /** Marks which file is currently playing so its button shows a stop icon. */
    fun setPlaying(file: File?) {
        if (playingFile == file) return
        playingFile = file
        notifyDataSetChanged()
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val m = TimeUnit.SECONDS.toMinutes(totalSec)
        val s = totalSec - TimeUnit.MINUTES.toSeconds(m)
        return "%d:%02d".format(m, s)
    }
}
