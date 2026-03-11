package com.lunyx.downloader.model

import android.graphics.Color

data class DownloadItem(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val platform: String,
    val filePath: String,
    val fileSize: Long,
    val format: String,
    val quality: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun fileSizeFormatted() = when {
        fileSize <= 0 -> ""
        fileSize < 1024 * 1024 -> "${fileSize / 1024} KB"
        else -> "%.1f MB".format(fileSize / 1024.0 / 1024.0)
    }

    fun timestampFormatted(): String {
        val diff = System.currentTimeMillis() - timestamp
        return when {
            diff < 60_000 -> "щойно"
            diff < 3_600_000 -> "${diff / 60_000} хв тому"
            diff < 86_400_000 -> "${diff / 3_600_000} год тому"
            else -> java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(timestamp))
        }
    }

    fun platformShort() = when (platform.lowercase()) {
        "youtube" -> "YT"
        "tiktok" -> "TT"
        "instagram" -> "IG"
        "twitter" -> "X"
        "twitch" -> "TV"
        "soundcloud" -> "SC"
        "vimeo" -> "VM"
        "facebook" -> "FB"
        else -> platform.take(2).uppercase()
    }

    fun platformColor() = when (platform.lowercase()) {
        "youtube" -> Color.parseColor("#FF4444")
        "tiktok" -> Color.parseColor("#69C9D0")
        "instagram" -> Color.parseColor("#E1306C")
        "twitter" -> Color.parseColor("#1DA1F2")
        "twitch" -> Color.parseColor("#9146FF")
        "soundcloud" -> Color.parseColor("#FF5500")
        "vimeo" -> Color.parseColor("#1AB7EA")
        "facebook" -> Color.parseColor("#1877F2")
        else -> Color.parseColor("#6C63FF")
    }
}
