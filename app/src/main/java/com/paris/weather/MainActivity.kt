package com.paris.weather

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var textStatus: TextView
    private lateinit var textGuide: TextView
    private lateinit var btnRefresh: Button
    private lateinit var progressLoading: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val isDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val bgColor = if (isDarkMode) "#121824" else "#F0F2F5"
        val titleColor = if (isDarkMode) "#FFFFFF" else "#141823"
        val statusColor = if (isDarkMode) "#94A3B8" else "#464E5A"
        val guideColor = if (isDarkMode) "#64748B" else "#828A96"
        val btnColor = if (isDarkMode) "#4285F4" else "#0277BD"

        // Simple programmatic layout since we are a widget helper app
        val rootView = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(android.graphics.Color.parseColor(bgColor))
        }

        val titleView = TextView(this).apply {
            text = "Météo Paris Widget"
            textSize = 24f
            setTextColor(android.graphics.Color.parseColor(titleColor))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 48)
        }
        rootView.addView(titleView)

        progressLoading = ProgressBar(this).apply {
            visibility = View.GONE
        }
        rootView.addView(progressLoading)

        textStatus = TextView(this).apply {
            text = "Prêt"
            textSize = 16f
            setTextColor(android.graphics.Color.parseColor(statusColor))
            setPadding(0, 0, 0, 48)
        }
        rootView.addView(textStatus)

        btnRefresh = Button(this).apply {
            text = "Actualiser la Météo"
            setBackgroundColor(android.graphics.Color.parseColor(btnColor))
            setTextColor(android.graphics.Color.WHITE)
        }
        btnRefresh.setOnClickListener {
            refreshWeather()
        }
        rootView.addView(btnRefresh)

        val textTransparency = TextView(this).apply {
            text = "Transparence du widget : 85%"
            textSize = 16f
            setTextColor(android.graphics.Color.parseColor(titleColor))
            setPadding(0, 24, 0, 8)
        }
        rootView.addView(textTransparency)

        val prefs = getSharedPreferences("com.paris.weather.WIDGET_PREFS", android.content.Context.MODE_PRIVATE)
        val initialTransparency = prefs.getInt("widget_transparency", 85)
        textTransparency.text = "Transparence du widget : $initialTransparency%"

        val seekBar = android.widget.SeekBar(this).apply {
            max = 100
            progress = initialTransparency
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 24)
            }
        }
        seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                textTransparency.text = "Transparence du widget : $progress%"
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                val progressValue = seekBar?.progress ?: 85
                prefs.edit().putInt("widget_transparency", progressValue).apply()
                
                // Notify widgets to redraw with new transparency
                val widgetIntent = Intent(this@MainActivity, WeatherWidgetProvider::class.java).apply {
                    action = WeatherWidgetProvider.ACTION_REFRESH
                }
                sendBroadcast(widgetIntent)
            }
        })
        rootView.addView(seekBar)

        textGuide = TextView(this).apply {
            text = "\n\nComment ajouter le widget :\n1. Retournez sur votre écran d'accueil.\n2. Restez appuyé sur une zone vide.\n3. Choisissez 'Widgets'.\n4. Cherchez 'Météo Paris' et glissez le widget sur votre écran.\n\nNote : Cliquez sur un jour de prévisions dans le widget pour lire son commentaire en bas !"
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor(guideColor))
            gravity = android.view.Gravity.CENTER
        }
        rootView.addView(textGuide)

        setContentView(rootView)
    }

    private fun refreshWeather() {
        progressLoading.visibility = View.VISIBLE
        btnRefresh.isEnabled = false
        textStatus.text = "Chargement des données de meteo-paris.com..."

        Thread {
            try {
                val data = WeatherScraper.scrape(this)
                runOnUiThread {
                    progressLoading.visibility = View.GONE
                    btnRefresh.isEnabled = true
                    if (data != null) {
                        textStatus.text = "Actualisé avec succès !\nTempérature : ${data.currentTempDec}°C\nCondition : ${data.conditionPhrase}"
                        Toast.makeText(this, "Météo mise à jour !", Toast.LENGTH_SHORT).show()
                        
                        // Notify widgets
                        val widgetIntent = Intent(this, WeatherWidgetProvider::class.java).apply {
                            action = WeatherWidgetProvider.ACTION_REFRESH
                        }
                        sendBroadcast(widgetIntent)
                    } else {
                        textStatus.text = "Erreur de chargement. Vérifiez votre connexion internet."
                        Toast.makeText(this, "Impossible de charger les données", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressLoading.visibility = View.GONE
                    btnRefresh.isEnabled = true
                    textStatus.text = "Erreur : ${e.localizedMessage}"
                }
            }
        }.start()
    }
}
