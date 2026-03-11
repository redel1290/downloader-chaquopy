package com.lunyx.downloader.fragments

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lunyx.downloader.R
import com.lunyx.downloader.model.DownloadItem
import com.lunyx.downloader.utils.Prefs
import java.io.File

class HistoryFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_history, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        load(view)
        view.findViewById<TextView>(R.id.btnClearHistory).setOnClickListener {
            Prefs.clearHistory(requireContext())
            load(view)
        }
    }

    override fun onResume() {
        super.onResume()
        view?.let { load(it) }
    }

    private fun load(view: View) {
        val items = Prefs.getHistory(requireContext())
        val rv = view.findViewById<RecyclerView>(R.id.rvHistory)
        val empty = view.findViewById<LinearLayout>(R.id.emptyState)

        if (items.isEmpty()) {
            rv.visibility = View.GONE
            empty.visibility = View.VISIBLE
        } else {
            rv.visibility = View.VISIBLE
            empty.visibility = View.GONE
            rv.layoutManager = LinearLayoutManager(requireContext())
            rv.adapter = HistoryAdapter(items,
                onOpen = { openFile(it) },
                onDelete = { Prefs.removeItem(requireContext(), it.id); load(view) }
            )
        }
    }

    private fun openFile(item: DownloadItem) {
        val file = File(item.filePath)
        if (!file.exists()) {
            Toast.makeText(requireContext(), "Файл не знайдено", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(
                requireContext(), "${requireContext().packageName}.fileprovider", file
            )
            val mime = if (item.format == "mp3") "audio/*" else "video/*"
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Відкрити у…"
            ))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Немає додатку для відкриття", Toast.LENGTH_SHORT).show()
        }
    }
}

class HistoryAdapter(
    private val items: List<DownloadItem>,
    private val onOpen: (DownloadItem) -> Unit,
    private val onDelete: (DownloadItem) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvPlatform: TextView = v.findViewById(R.id.tvPlatform)
        val tvTitle: TextView = v.findViewById(R.id.tvTitle)
        val tvMeta: TextView = v.findViewById(R.id.tvMeta)
        val tvDate: TextView = v.findViewById(R.id.tvDate)
        val btnOpen: TextView = v.findViewById(R.id.btnItemOpen)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val item = items[pos]
        holder.tvPlatform.text = item.platformShort()
        holder.tvPlatform.setTextColor(item.platformColor())
        holder.tvTitle.text = item.title
        holder.tvMeta.text = buildString {
            if (item.fileSizeFormatted().isNotBlank()) append("${item.fileSizeFormatted()} • ")
            append(item.format.uppercase())
        }
        holder.tvDate.text = item.timestampFormatted()
        holder.btnOpen.setOnClickListener { onOpen(item) }
        holder.itemView.setOnLongClickListener { onDelete(item); true }
    }
}
