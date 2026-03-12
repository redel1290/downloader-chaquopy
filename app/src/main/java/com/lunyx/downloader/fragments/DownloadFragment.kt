package com.lunyx.downloader.fragments

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.lunyx.downloader.R
import com.lunyx.downloader.service.DownloadService
import com.lunyx.downloader.utils.Prefs
import com.lunyx.downloader.utils.PythonDownloader
import kotlinx.coroutines.*
import java.io.File

class DownloadFragment : Fragment() {

    private lateinit var etUrl: EditText
    private lateinit var btnPaste: TextView
    private lateinit var btnDownload: TextView
    private lateinit var btnStop: TextView
    private lateinit var rgFormat: RadioGroup
    private lateinit var rgQuality: RadioGroup
    private lateinit var cardProgress: View
    private lateinit var cardResult: View
    private lateinit var tvStatus: TextView
    private lateinit var tvPercent: TextView
    private lateinit var tvProgressDetail: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvResultIcon: TextView
    private lateinit var tvResultTitle: TextView
    private lateinit var tvResultMeta: TextView
    private lateinit var btnOpen: TextView
    private lateinit var btnShare: TextView

    private var lastFilePath: String? = null
    private var lastFormat: String = "mp4"
    private var downloadJob: Job? = null

    // Service binding
    private var downloadService: DownloadService? = null
    private var serviceBound = false
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val b = binder as? DownloadService.LocalBinder ?: return
            downloadService = b.getService()
            serviceBound = true
            // Підключаємо callbacks якщо сервіс вже завантажує
            if (downloadService?.isDownloading == true) {
                setDownloading(true)
                cardProgress.visibility = View.VISIBLE
                attachServiceCallbacks()
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            downloadService = null
            serviceBound = false
        }
    }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.all { it }) startDownload()
        else Toast.makeText(requireContext(), "Потрібен дозвіл для збереження", Toast.LENGTH_LONG).show()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_download, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etUrl            = view.findViewById(R.id.etUrl)
        btnPaste         = view.findViewById(R.id.btnPaste)
        btnDownload      = view.findViewById(R.id.btnDownload)
        btnStop          = view.findViewById(R.id.btnStop)
        rgFormat         = view.findViewById(R.id.rgFormat)
        rgQuality        = view.findViewById(R.id.rgQuality)
        cardProgress     = view.findViewById(R.id.cardProgress)
        cardResult       = view.findViewById(R.id.cardResult)
        tvStatus         = view.findViewById(R.id.tvStatus)
        tvPercent        = view.findViewById(R.id.tvPercent)
        tvProgressDetail = view.findViewById(R.id.tvProgressDetail)
        progressBar      = view.findViewById(R.id.progressBar)
        tvResultIcon     = view.findViewById(R.id.tvResultIcon)
        tvResultTitle    = view.findViewById(R.id.tvResultTitle)
        tvResultMeta     = view.findViewById(R.id.tvResultMeta)
        btnOpen          = view.findViewById(R.id.btnOpen)
        btnShare         = view.findViewById(R.id.btnShare)

        btnPaste.setOnClickListener { smartPaste() }
        btnDownload.setOnClickListener { checkPermAndDownload() }
        btnStop.setOnClickListener { stopDownload() }
        btnOpen.setOnClickListener { openFile() }
        btnShare.setOnClickListener { shareFile() }

        rgFormat.setOnCheckedChangeListener { _, id ->
            rgQuality.visibility = if (id == R.id.rbAudio) View.INVISIBLE else View.VISIBLE
        }

        setDownloading(false)

        // Підключаємось до сервісу якщо він вже запущений
        val intent = Intent(requireContext(), DownloadService::class.java)
        requireContext().bindService(intent, serviceConnection, 0)
    }

    override fun onDestroyView() {
        if (serviceBound) {
            requireContext().unbindService(serviceConnection)
            serviceBound = false
        }
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        checkPendingUrl()
    }

    private fun checkPendingUrl() {
        val prefs = requireContext().getSharedPreferences("pending", Context.MODE_PRIVATE)
        val url = prefs.getString("url", null)
        if (!url.isNullOrBlank()) {
            etUrl.setText(url)
            prefs.edit().remove("url").apply()
        }
    }

    /** Шукає перше посилання в буфері обміну */
    private fun smartPaste() {
        val cb = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cb.primaryClip
        if (clip == null || clip.itemCount == 0) {
            Toast.makeText(requireContext(), getString(R.string.clipboard_empty), Toast.LENGTH_SHORT).show()
            return
        }
        // Збираємо весь текст з усіх елементів буфера в один рядок
        val allText = buildString {
            for (i in 0 until clip.itemCount) {
                val part = clip.getItemAt(i)?.coerceToText(requireContext())?.toString()
                if (!part.isNullOrBlank()) append(part).append(" ")
            }
        }
        // Шукаємо ВСІ http/https посилання в тексті
        val urlRegex = Regex("https?://[^\\s<>"']+")
        val allUrls = urlRegex.findAll(allText).map { it.value }.toList()

        if (allUrls.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.clipboard_empty), Toast.LENGTH_SHORT).show()
            return
        }

        // Якщо одне — вставляємо одразу
        // Якщо декілька — вибираємо перше (найімовірніше саме те що треба)
        val url = allUrls.first()
        etUrl.setText(url)
        etUrl.setSelection(url.length)
    }

    private fun setDownloading(active: Boolean) {
        btnStop.isEnabled = active
        btnDownload.isEnabled = !active
        etUrl.isEnabled = !active
        rgFormat.isEnabled = !active
        rgQuality.isEnabled = !active
        for (i in 0 until rgFormat.childCount) rgFormat.getChildAt(i).isEnabled = !active
        for (i in 0 until rgQuality.childCount) rgQuality.getChildAt(i).isEnabled = !active
        btnPaste.isEnabled = !active
    }

    private fun stopDownload() {
        if (Prefs.getBackground(requireContext())) {
            DownloadService.cancel(requireContext())
        } else {
            PythonDownloader.cancel()
        }
        tvStatus.text = "⏹ Зупиняю…"
    }

    private fun checkPermAndDownload() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val perm = Manifest.permission.WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(requireContext(), perm) != PackageManager.PERMISSION_GRANTED) {
                permLauncher.launch(arrayOf(perm))
                return
            }
        }
        startDownload()
    }

    private fun startDownload() {
        val url = etUrl.text.toString().trim()
        if (url.isBlank() || !url.startsWith("http")) {
            Toast.makeText(requireContext(), "Вставте коректне посилання", Toast.LENGTH_SHORT).show()
            return
        }
        if (!PythonDownloader.isReady()) {
            Toast.makeText(requireContext(), "Python ще завантажується…", Toast.LENGTH_SHORT).show()
            return
        }

        val format = if (rgFormat.checkedRadioButtonId == R.id.rbAudio) "audio" else "video"
        val quality = when (rgQuality.checkedRadioButtonId) {
            R.id.rb1080 -> "1080"
            R.id.rb720  -> "720"
            else        -> "best"
        }

        setDownloading(true)
        cardProgress.visibility = View.VISIBLE
        cardResult.visibility = View.GONE
        progressBar.isIndeterminate = true
        tvPercent.text = ""
        tvProgressDetail.text = ""
        tvStatus.text = "Починаю…"

        val useBackground = Prefs.getBackground(requireContext())

        if (useBackground) {
            // Запускаємо ForegroundService — працює навіть у фоні
            DownloadService.start(requireContext(), url, format, quality)
            // Підключаємось до сервісу
            val intent = Intent(requireContext(), DownloadService::class.java)
            requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            attachServiceCallbacks()
        } else {
            // Звичайний coroutine — зупиниться при виході
            downloadJob = lifecycleScope.launch {
                val result = withContext(Dispatchers.IO) {
                    PythonDownloader.download(
                        url = url, format = format, quality = quality,
                        onStatus = { msg -> lifecycleScope.launch { tvStatus.text = msg } }
                    )
                }
                handleResult(result, format, quality)
            }
        }
    }

    private fun attachServiceCallbacks() {
        downloadService?.onStatus = { msg ->
            activity?.runOnUiThread { tvStatus.text = msg }
        }
        downloadService?.onResult = { result ->
            activity?.runOnUiThread {
                val format = if (rgFormat.checkedRadioButtonId == R.id.rbAudio) "audio" else "video"
                val quality = when (rgQuality.checkedRadioButtonId) {
                    R.id.rb1080 -> "1080"; R.id.rb720 -> "720"; else -> "best"
                }
                handleResult(result, format, quality)
            }
        }
    }

    private fun handleResult(result: PythonDownloader.DownloadResult, format: String, quality: String) {
        setDownloading(false)
        progressBar.isIndeterminate = false

        when {
            result.cancelled -> {
                cardProgress.visibility = View.GONE
                Toast.makeText(requireContext(), getString(R.string.download_cancelled), Toast.LENGTH_SHORT).show()
            }
            result.success && result.item != null -> {
                val item = result.item
                lastFilePath = item.filePath
                lastFormat = item.format

                cardProgress.visibility = View.GONE
                cardResult.visibility = View.VISIBLE
                tvResultIcon.text = "✅"
                tvResultTitle.text = item.title
                tvResultMeta.text = buildString {
                    if (item.fileSizeFormatted().isNotBlank()) append("${item.fileSizeFormatted()} • ")
                    append(item.format.uppercase())
                    if (format == "video") append(" • $quality")
                }
            }
            else -> {
                progressBar.progress = 0
                tvStatus.text = "❌ ${result.error}"
                tvPercent.text = ""
            }
        }
    }

    private fun openFile() {
        val path = lastFilePath ?: return
        val file = File(path)
        if (!file.exists()) { Toast.makeText(requireContext(), "Файл не знайдено", Toast.LENGTH_SHORT).show(); return }
        try {
            val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
            val mime = if (lastFormat == "mp3") "audio/*" else "video/*"
            startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Відкрити у…"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Немає додатку для відкриття", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareFile() {
        val path = lastFilePath ?: return
        val file = File(path)
        if (!file.exists()) return
        try {
            val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
            val mime = if (lastFormat == "mp3") "audio/*" else "video/*"
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = mime; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Поділитись…"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Помилка", Toast.LENGTH_SHORT).show()
        }
    }
}
