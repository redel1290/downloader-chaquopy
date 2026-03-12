package com.lunyx.downloader

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.lunyx.downloader.fragments.DownloadFragment
import com.lunyx.downloader.fragments.HistoryFragment
import com.lunyx.downloader.utils.Prefs
import com.lunyx.downloader.utils.PythonDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Тема або мова змінилась — перезапускаємо Activity
            recreate()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val lang = newBase.getSharedPreferences("dl_prefs", Context.MODE_PRIVATE)
            .getString("lang", "uk") ?: "uk"
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = newBase.resources.configuration
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Застосовуємо збережену тему ДО super.onCreate
        val theme = getSharedPreferences("dl_prefs", Context.MODE_PRIVATE)
            .getString("theme", "system") ?: "system"
        AppCompatDelegate.setDefaultNightMode(
            when (theme) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark"  -> AppCompatDelegate.MODE_NIGHT_YES
                else    -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val viewPager   = findViewById<ViewPager2>(R.id.viewPager)
        val tabLayout   = findViewById<TabLayout>(R.id.tabLayout)
        val tvStatus    = findViewById<TextView>(R.id.tvPythonStatus)
        val btnSettings = findViewById<TextView>(R.id.btnSettings)

        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 2
            override fun createFragment(position: Int): Fragment =
                if (position == 0) DownloadFragment() else HistoryFragment()
        }
        viewPager.offscreenPageLimit = 1

        TabLayoutMediator(tabLayout, viewPager) { tab, pos ->
            tab.text = if (pos == 0) getString(R.string.tab_download) else getString(R.string.tab_history)
        }.attach()

        lifecycleScope.launch {
            withContext(Dispatchers.IO) { PythonDownloader.init(applicationContext) }
            tvStatus.text = "✅"
        }

        btnSettings.setOnClickListener {
            settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
        }

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
}
