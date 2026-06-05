package com.paris.weather

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import org.json.JSONArray
import org.json.JSONObject

class WeatherWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Log.d(TAG, "onUpdate called for ${appWidgetIds.size} widgets")
        
        // Setup Alarm for periodic updates (every 3 hours)
        scheduleNextUpdate(context)

        // Perform scrape in background
        triggerScrapeAndUpdate(context, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d(TAG, "onReceive action: ${intent.action}")

        when (intent.action) {
            ACTION_REFRESH -> {
                val appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                    ?: getWidgetIds(context)
                triggerScrapeAndUpdate(context, appWidgetIds)
            }
            ACTION_SHOW_COMMENT -> {
                val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                val comment = intent.getStringExtra(EXTRA_COMMENT)
                
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID && comment != null) {
                    saveCurrentComment(context, widgetId, comment)
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val cachedData = getCachedWeatherData(context)
                    updateWidget(context, appWidgetManager, widgetId, cachedData)
                }
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                scheduleNextUpdate(context)
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        for (id in appWidgetIds) {
            removeSavedComment(context, id)
        }
    }

    private fun triggerScrapeAndUpdate(context: Context, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        // Run scraper in a plain background thread
        Thread {
            try {
                Log.d(TAG, "Scraping weather data in background thread...")
                val weatherData = WeatherScraper.scrape()
                if (weatherData != null) {
                    Log.d(TAG, "Scrape successful! Saving to cache...")
                    saveCachedWeatherData(context, weatherData)
                    
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    for (widgetId in appWidgetIds) {
                        // When refreshing data, clear the custom selected day comment so it shows the summary again
                        removeSavedComment(context, widgetId)
                        updateWidget(context, appWidgetManager, widgetId, weatherData)
                    }
                } else {
                    Log.e(TAG, "Scrape failed: weatherData is null")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in scraping thread", e)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    companion object {
        private const val TAG = "WeatherWidgetProvider"
        const val ACTION_REFRESH = "com.paris.weather.ACTION_REFRESH"
        const val ACTION_SHOW_COMMENT = "com.paris.weather.ACTION_SHOW_COMMENT"
        const val EXTRA_COMMENT = "com.paris.weather.EXTRA_COMMENT"
        
        private const val PREFS_NAME = "com.paris.weather.WIDGET_PREFS"
        private const val KEY_COMMENT_PREFIX = "comment_"
        private const val KEY_WEATHER_CACHE = "weather_cache"

        fun scheduleNextUpdate(context: Context) {
            val intent = Intent(context, WeatherWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 
                0, 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            
            // Alarm fires in 3 hours (10800000 ms)
            val interval = 3 * 60 * 60 * 1000L
            val triggerTime = SystemClock.elapsedRealtime() + interval
            
            alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerTime,
                interval,
                pendingIntent
            )
            Log.d(TAG, "Scheduled next update in 3 hours")
        }

        private fun getWidgetIds(context: Context): IntArray {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, WeatherWidgetProvider::class.java)
            return appWidgetManager.getAppWidgetIds(componentName)
        }

        private fun saveCurrentComment(context: Context, widgetId: Int, comment: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_COMMENT_PREFIX + widgetId, comment).apply()
        }

        private fun getSavedComment(context: Context, widgetId: Int): String? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_COMMENT_PREFIX + widgetId, null)
        }

        private fun removeSavedComment(context: Context, widgetId: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(KEY_COMMENT_PREFIX + widgetId).apply()
        }

        // Cache WeatherData as JSON in SharedPreferences
        private fun saveCachedWeatherData(context: Context, data: WeatherData) {
            try {
                val obj = JSONObject().apply {
                    put("dayText", data.dayText)
                    put("weatherIconName", data.weatherIconName)
                    put("conditionPhrase", data.conditionPhrase)
                    put("summary", data.summary)
                    put("matinTemp", data.matinTemp)
                    put("matinPicto", data.matinPicto)
                    put("midiTemp", data.midiTemp)
                    put("midiPicto", data.midiPicto)
                    put("soirTemp", data.soirTemp)
                    put("soirPicto", data.soirPicto)
                    put("nuitTemp", data.nuitTemp)
                    put("nuitPicto", data.nuitPicto)
                    put("currentTempDec", data.currentTempDec)
                    put("windText", data.windText)
                    put("ecartText", data.ecartText)
                    put("precipProba", data.precipProba)
                    put("precipVolume", data.precipVolume)
                    put("lastUpdate", data.lastUpdate)
                    
                    val fArray = JSONArray()
                    for (f in data.forecasts) {
                        val fObj = JSONObject().apply {
                            put("dayName", f.dayName)
                            put("iconMorning", f.iconMorning)
                            put("iconAfternoon", f.iconAfternoon)
                            put("tempMin", f.tempMin)
                            put("tempMax", f.tempMax)
                            put("comment", f.comment)
                            put("uncertain", f.uncertain)
                        }
                        fArray.put(fObj)
                    }
                    put("forecasts", fArray)
                }
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_WEATHER_CACHE, obj.toString())
                    .apply()
            } catch (e: Exception) {
                Log.e(TAG, "Error caching weather data JSON", e)
            }
        }

        private fun getCachedWeatherData(context: Context): WeatherData? {
            val jsonStr = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_WEATHER_CACHE, null) ?: return null
            return try {
                val obj = JSONObject(jsonStr)
                val fArray = obj.getJSONArray("forecasts")
                val forecasts = ArrayList<ForecastData>()
                for (i in 0 until fArray.length()) {
                    val fObj = fArray.getJSONObject(i)
                    forecasts.add(
                        ForecastData(
                            dayName = fObj.getString("dayName"),
                            iconMorning = fObj.getString("iconMorning"),
                            iconAfternoon = fObj.getString("iconAfternoon"),
                            tempMin = fObj.getString("tempMin"),
                            tempMax = fObj.getString("tempMax"),
                            comment = fObj.getString("comment"),
                            uncertain = fObj.getInt("uncertain")
                        )
                    )
                }
                WeatherData(
                    dayText = obj.getString("dayText"),
                    weatherIconName = obj.getString("weatherIconName"),
                    conditionPhrase = obj.getString("conditionPhrase"),
                    summary = obj.getString("summary"),
                    matinTemp = obj.getString("matinTemp"),
                    matinPicto = obj.getString("matinPicto"),
                    midiTemp = obj.getString("midiTemp"),
                    midiPicto = obj.getString("midiPicto"),
                    soirTemp = obj.getString("soirTemp"),
                    soirPicto = obj.getString("soirPicto"),
                    nuitTemp = obj.getString("nuitTemp"),
                    nuitPicto = obj.getString("nuitPicto"),
                    currentTempDec = obj.getString("currentTempDec"),
                    windText = obj.getString("windText"),
                    ecartText = obj.getString("ecartText"),
                    precipProba = obj.getString("precipProba"),
                    precipVolume = obj.getString("precipVolume"),
                    lastUpdate = obj.getString("lastUpdate"),
                    forecasts = forecasts
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing cached weather data JSON", e)
                null
            }
        }

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int, weatherData: WeatherData?) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            // Apply widget transparency from preferences
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val transparencyPercent = prefs.getInt("widget_transparency", 85)
            val alpha = (transparencyPercent * 255) / 100
            views.setInt(R.id.widget_background_image, "setImageAlpha", alpha)

            if (weatherData != null) {
                // Last update
                views.setTextViewText(R.id.text_last_update, "Actualisé : ${weatherData.lastUpdate}")

                // Current block
                views.setTextViewText(R.id.text_current_temp, "${weatherData.currentTempDec}°")
                views.setTextViewText(R.id.text_precip_proba, "Pluie : ${weatherData.precipProba}%")
                views.setTextViewText(R.id.text_precip_volume, "Volume : ${weatherData.precipVolume}")
                views.setTextViewText(R.id.text_wind, weatherData.windText)
                views.setTextViewText(R.id.text_ecart, "Écart saison : ${weatherData.ecartText}")

                val currentIconRes = context.resources.getIdentifier(weatherData.weatherIconName, "drawable", context.packageName)
                if (currentIconRes != 0) {
                    views.setImageViewResource(R.id.icon_weather, currentIconRes)
                }

                // Matin | Après-midi | Soir | Nuit
                views.setTextViewText(R.id.text_matin_temp, "${weatherData.matinTemp}°")
                val matinRes = context.resources.getIdentifier(weatherData.matinPicto, "drawable", context.packageName)
                if (matinRes != 0) views.setImageViewResource(R.id.icon_matin, matinRes)

                views.setTextViewText(R.id.text_midi_temp, "${weatherData.midiTemp}°")
                val midiRes = context.resources.getIdentifier(weatherData.midiPicto, "drawable", context.packageName)
                if (midiRes != 0) views.setImageViewResource(R.id.icon_midi, midiRes)

                views.setTextViewText(R.id.text_soir_temp, "${weatherData.soirTemp}°")
                val soirRes = context.resources.getIdentifier(weatherData.soirPicto, "drawable", context.packageName)
                if (soirRes != 0) views.setImageViewResource(R.id.icon_soir, soirRes)

                views.setTextViewText(R.id.text_nuit_temp, "${weatherData.nuitTemp}°")
                val nuitRes = context.resources.getIdentifier(weatherData.nuitPicto, "drawable", context.packageName)
                if (nuitRes != 0) views.setImageViewResource(R.id.icon_nuit, nuitRes)

                // 6 Forecasts
                val fLayouts = listOf(
                    R.id.layout_day1, R.id.layout_day2, R.id.layout_day3,
                    R.id.layout_day4, R.id.layout_day5, R.id.layout_day6
                )
                val fNames = listOf(
                    R.id.text_day1_name, R.id.text_day2_name, R.id.text_day3_name,
                    R.id.text_day4_name, R.id.text_day5_name, R.id.text_day6_name
                )
                val fTemps = listOf(
                    R.id.text_day1_temp, R.id.text_day2_temp, R.id.text_day3_temp,
                    R.id.text_day4_temp, R.id.text_day5_temp, R.id.text_day6_temp
                )
                val fIconsM = listOf(
                    R.id.icon_day1_morning, R.id.icon_day2_morning, R.id.icon_day3_morning,
                    R.id.icon_day4_morning, R.id.icon_day5_morning, R.id.icon_day6_morning
                )
                val fIconsA = listOf(
                    R.id.icon_day1_afternoon, R.id.icon_day2_afternoon, R.id.icon_day3_afternoon,
                    R.id.icon_day4_afternoon, R.id.icon_day5_afternoon, R.id.icon_day6_afternoon
                )
                val fDots = listOf(
                    R.id.dot_uncertain_day1, R.id.dot_uncertain_day2, R.id.dot_uncertain_day3,
                    R.id.dot_uncertain_day4, R.id.dot_uncertain_day5, R.id.dot_uncertain_day6
                )

                for (i in 0 until 6) {
                    if (i < weatherData.forecasts.size) {
                        val f = weatherData.forecasts[i]
                        views.setTextViewText(fNames[i], f.dayName)
                        views.setTextViewText(fTemps[i], "${f.tempMax}° / ${f.tempMin}°")

                        val resM = context.resources.getIdentifier(f.iconMorning, "drawable", context.packageName)
                        if (resM != 0) views.setImageViewResource(fIconsM[i], resM)

                        val resA = context.resources.getIdentifier(f.iconAfternoon, "drawable", context.packageName)
                        if (resA != 0) views.setImageViewResource(fIconsA[i], resA)

                        if (f.uncertain > 0) {
                            views.setViewVisibility(fDots[i], View.VISIBLE)
                            val dotColor = if (f.uncertain == 2) Color.RED else Color.rgb(218, 131, 15)
                            views.setInt(fDots[i], "setColorFilter", dotColor)
                        } else {
                            views.setViewVisibility(fDots[i], View.GONE)
                        }

                        // Day click triggers ACTION_SHOW_COMMENT
                        setDayClickIntent(context, views, widgetId, fLayouts[i], f.comment)
                    }
                }

                // Dynamic Comment
                val currentComment = getSavedComment(context, widgetId) ?: weatherData.summary
                views.setTextViewText(R.id.text_comment, currentComment)

                // Click on upper parts resets comment to today's summary
                setDayClickIntent(context, views, widgetId, R.id.layout_current_weather, weatherData.summary)
                setDayClickIntent(context, views, widgetId, R.id.layout_temp_row, weatherData.summary)

                // Set refresh click intent on btn_refresh
                val refreshIntent = Intent(context, WeatherWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
                }
                val refreshPending = PendingIntent.getBroadcast(
                    context, 
                    widgetId, 
                    refreshIntent, 
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.btn_refresh, refreshPending)

            } else {
                // Initial/error state
                views.setTextViewText(R.id.text_comment, "Chargement des prévisions...")
                val refreshIntent = Intent(context, WeatherWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
                }
                val refreshPending = PendingIntent.getBroadcast(
                    context, 
                    widgetId, 
                    refreshIntent, 
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.btn_refresh, refreshPending)
                views.setOnClickPendingIntent(R.id.widget_root, refreshPending)
            }

            appWidgetManager.updateAppWidget(widgetId, views)
        }

        private fun setDayClickIntent(context: Context, views: RemoteViews, widgetId: Int, viewId: Int, comment: String) {
            val intent = Intent(context, WeatherWidgetProvider::class.java).apply {
                action = ACTION_SHOW_COMMENT
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                putExtra(EXTRA_COMMENT, comment)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                viewId + widgetId * 1000, // Unique request code per layout and widget
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(viewId, pendingIntent)
        }
    }
}
