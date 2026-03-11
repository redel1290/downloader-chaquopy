package com.lunyx.downloader.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lunyx.downloader.model.DownloadItem

object Prefs {
    private const val NAME = "dl_prefs"
    private const val KEY_HISTORY = "history"

    fun getHistory(ctx: Context): MutableList<DownloadItem> {
        val json = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_HISTORY, "[]") ?: "[]"
        return try {
            val type = object : TypeToken<MutableList<DownloadItem>>() {}.type
            Gson().fromJson(json, type) ?: mutableListOf()
        } catch (e: Exception) { mutableListOf() }
    }

    fun addHistory(ctx: Context, item: DownloadItem) {
        val list = getHistory(ctx)
        list.add(0, item)
        if (list.size > 200) list.removeAt(list.size - 1)
        save(ctx, list)
    }

    fun removeItem(ctx: Context, id: Long) {
        save(ctx, getHistory(ctx).filter { it.id != id }.toMutableList())
    }

    fun clearHistory(ctx: Context) =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().remove(KEY_HISTORY).apply()

    private fun save(ctx: Context, list: List<DownloadItem>) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_HISTORY, Gson().toJson(list)).apply()
    }
}
