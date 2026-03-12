package com.lunyx.downloader.utils

import android.content.Context
import android.os.Environment
import com.chaquo.python.PyException
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.lunyx.downloader.model.DownloadItem

object PythonDownloader {

    data class DownloadResult(
        val success: Boolean,
        val item: DownloadItem? = null,
        val error: String = "",
        val cancelled: Boolean = false
    )

    fun init(ctx: Context) {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(ctx))
        }
    }

    fun isReady() = Python.isStarted()

    fun cancel() {
        try {
            val py = Python.getInstance()
            py.getModule("downloader").callAttr("cancel")
        } catch (e: Exception) { /* ignore */ }
    }

    fun download(
        url: String,
        format: String,
        quality: String,
        onStatus: (String) -> Unit
    ): DownloadResult {
        return try {
            onStatus("Запускаю Python…")
            val py = Python.getInstance()
            val module = py.getModule("downloader")

            val downloadsDir = Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .absolutePath

            onStatus("Завантажую…")
            val resultJson = module.callAttr(
                "download", url, format, quality, downloadsDir
            ).toString()

            val result = Gson().fromJson(resultJson, JsonObject::class.java)
            val success = result.get("success")?.asBoolean ?: false
            val error = result.get("error")?.asString ?: ""

            if (error == "Скасовано") {
                return DownloadResult(success = false, cancelled = true, error = "Скасовано")
            }

            if (success) {
                val item = DownloadItem(
                    title    = result.get("title")?.asString ?: "Без назви",
                    platform = result.get("platform")?.asString ?: "unknown",
                    filePath = result.get("file_path")?.asString ?: "",
                    fileSize = result.get("file_size")?.asLong ?: 0L,
                    format   = result.get("format")?.asString ?: format,
                    quality  = result.get("quality")?.asString ?: quality,
                )
                DownloadResult(success = true, item = item)
            } else {
                DownloadResult(success = false, error = error)
            }
        } catch (e: PyException) {
            DownloadResult(success = false, error = "Python помилка: ${e.message?.take(200)}")
        } catch (e: Exception) {
            DownloadResult(success = false, error = "Помилка: ${e.message?.take(200)}")
        }
    }
}
