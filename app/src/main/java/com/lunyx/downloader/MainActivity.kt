package com.lunyx.downloader

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.lunyx.downloader.fragments.DownloadFragment
import com.lunyx.downloader.fragments.HistoryFragment
import com.lunyx.downloader.service.DownloadService
import com.lunyx.downloader.utils.Prefs
import com.lunyx.downloader.utils.PythonDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val tabs = listOf(
        getString_safe(R.string.tab_download),
        getString_safe(R.string.tab_history)
    )

    override fun attachBaseContext(newBase: Context) {
        // Застосовуємо мову
        val lang = newBase.getSharedPreferences("dl_prefs", Context.MODE_PRIVATE)
            .getString("lang", "uk") ?: "uk"
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = newBase.resources.configuration
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    private fun getString_safe(resId: Int): String = try { getString(resId) } catch (e: Exception) { "" }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Застосовуємо тему
        applyTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        val tvStatus  = findViewById<TextView>(R.id.tvPythonStatus)
        val btnSettings = findViewById<TextView>(R.id.btnSettings)

        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 2
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> DownloadFragment()
                1 -> HistoryFragment()
                else -> DownloadFragment()
            }
        }
        viewPager.offscreenPageLimit = 1

        TabLayoutMediator(tabLayout, viewPager) { tab, pos ->
            tab.text = listOf(getString(R.string.tab_download), getString(R.string.tab_history))[pos]
        }.attach()

        // Python init
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { PythonDownloader.init(applicationContext) }
            tvStatus.text = "✅"
        }

        btnSettings.setOnClickListener { showSettings() }

        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val url = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            getSharedPreferences("pending", MODE_PRIVATE).edit().putString("url", url).apply()
            findViewById<ViewPager2>(R.id.viewPager).currentItem = 0
        }
    }

    private fun applyTheme() {
        when (Prefs.getTheme(this)) {
            "light" -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            "dark"  -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else    -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    private fun showSettings() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_settings, null)
        dialog.setContentView(view)

        // Theme
        val rgTheme = view.findViewById<RadioGroup>(R.id.rgTheme)
        when (Prefs.getTheme(this)) {
            "light"  -> view.findViewById<RadioButton>(R.id.rbThemeLight).isChecked = true
            "dark"   -> view.findViewById<RadioButton>(R.id.rbThemeDark).isChecked = true
            else     -> view.findViewById<RadioButton>(R.id.rbThemeSystem).isChecked = true
        }
        rgTheme.setOnCheckedChangeListener { _, id ->
            val theme = when (id) {
                R.id.rbThemeLight  -> "light"
                R.id.rbThemeDark   -> "dark"
                else               -> "system"
            }
            Prefs.setTheme(this, theme)
            applyTheme()
        }

        // Language
        val rgLang = view.findViewById<RadioGroup>(R.id.rgLanguage)
        if (Prefs.getLang(this) == "en") view.findViewById<RadioButton>(R.id.rbLangEn).isChecked = true
        rgLang.setOnCheckedChangeListener { _, id ->
            val lang = if (id == R.id.rbLangEn) "en" else "uk"
            Prefs.setLang(this, lang)
            // Перезапускаємо Activity для застосування мови
            dialog.dismiss()
            recreate()
        }

        // Background
        val swBg = view.findViewById<Switch>(R.id.switchBackground)
        swBg.isChecked = Prefs.getBackground(this)
        swBg.setOnCheckedChangeListener { _, checked ->
            Prefs.setBackground(this, checked)
        }

        dialog.show()
    }
}
