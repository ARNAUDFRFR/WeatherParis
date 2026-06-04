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
        
        // Simple programmatic layout since we are a widget helper app
        val rootView = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(android.graphics.Color.parseColor("#121824"))
        }

        val titleView = TextView(this).apply {
            text = "Météo Paris Widget"
            textSize = 24f
            setTextColor(android.graphics.Color.WHITE)
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
            setTextColor(android.graphics.Color.parseColor("#94A3B8"))
            setPadding(0, 0, 0, 48)
        }
        rootView.addView(textStatus)

        btnRefresh = Button(this).apply {
            text = "Actualiser la Météo"
            setBackgroundColor(android.graphics.Color.parseColor("#4285F4"))
            setTextColor(android.graphics.Color.WHITE)
        }
        btnRefresh.setOnClickListener {
            refreshWeather()
        }
        rootView.addView(btnRefresh)

        textGuide = TextView(this).apply {
            text = "\n\nComment ajouter le widget :\n1. Retournez sur votre écran d'accueil.\n2. Restez appuyé sur une zone vide.\n3. Choisissez 'Widgets'.\n4. Cherchez 'Météo Paris' et glissez le widget sur votre écran.\n\nNote : Cliquez sur un jour de prévisions dans le widget pour lire son commentaire en bas !"
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#64748B"))
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
                val data = WeatherScraper.scrape()
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
