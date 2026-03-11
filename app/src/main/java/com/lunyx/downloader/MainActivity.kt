package com.lunyx.downloader

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.lunyx.downloader.fragments.DownloadFragment
import com.lunyx.downloader.fragments.HistoryFragment
import com.lunyx.downloader.utils.PythonDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val tabs = listOf("⬇ Завантажити", "📋 Історія")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        val tvStatus = findViewById<TextView>(R.id.tvPythonStatus)

        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = tabs.size
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> DownloadFragment()
                1 -> HistoryFragment()
                else -> DownloadFragment()
            }
        }

        viewPager.offscreenPageLimit = 1

        TabLayoutMediator(tabLayout, viewPager) { tab, pos ->
            tab.text = tabs[pos]
        }.attach()

        // Ініціалізуємо Python у фоні
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                PythonDownloader.init(applicationContext)
            }
            tvStatus.text = "✅ Ready"
        }

        // Share intent
        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val url = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            getSharedPreferences("pending", MODE_PRIVATE).edit()
                .putString("url", url).apply()
            findViewById<ViewPager2>(R.id.viewPager).currentItem = 0
        }
    }
}
