package com.lunyx.downloader

import android.content.Context
import android.os.Bundle
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.lunyx.downloader.utils.Prefs
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

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
        super.onCreate(savedInstanceState)
        setContentView(R.layout.bottom_settings)

        // Close button
        findViewById<TextView>(R.id.btnCloseSettings).setOnClickListener { finish() }

        // ── Тема ──
        val rgTheme = findViewById<RadioGroup>(R.id.rgTheme)
        when (Prefs.getTheme(this)) {
            "light" -> findViewById<RadioButton>(R.id.rbThemeLight).isChecked = true
            "dark"  -> findViewById<RadioButton>(R.id.rbThemeDark).isChecked = true
            else    -> findViewById<RadioButton>(R.id.rbThemeSystem).isChecked = true
        }
        rgTheme.setOnCheckedChangeListener { _, id ->
            val theme = when (id) {
                R.id.rbThemeLight -> "light"
                R.id.rbThemeDark  -> "dark"
                else              -> "system"
            }
            Prefs.setTheme(this, theme)
            AppCompatDelegate.setDefaultNightMode(
                when (theme) {
                    "light" -> AppCompatDelegate.MODE_NIGHT_NO
                    "dark"  -> AppCompatDelegate.MODE_NIGHT_YES
                    else    -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
            // Перезапускаємо і MainActivity, і SettingsActivity
            setResult(RESULT_OK)
            finish()
        }

        // ── Мова ──
        val rgLang = findViewById<RadioGroup>(R.id.rgLanguage)
        if (Prefs.getLang(this) == "en") findViewById<RadioButton>(R.id.rbLangEn).isChecked = true
        else findViewById<RadioButton>(R.id.rbLangUk).isChecked = true

        rgLang.setOnCheckedChangeListener { _, id ->
            val lang = if (id == R.id.rbLangEn) "en" else "uk"
            Prefs.setLang(this, lang)
            setResult(RESULT_OK)
            finish()
        }

        // ── Фоновий режим ──
        val swBg = findViewById<Switch>(R.id.switchBackground)
        swBg.isChecked = Prefs.getBackground(this)
        swBg.setOnCheckedChangeListener { _, checked ->
            Prefs.setBackground(this, checked)
        }
    }
}
