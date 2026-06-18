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
            ACTION_SHOW_RAIN_POPUP -> {
                val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("rain_popup_$widgetId", true).apply()
                    prefs.edit().putBoolean("temp_popup_$widgetId", false).apply()
                    
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val cachedData = getCachedWeatherData(context)
                    updateWidget(context, appWidgetManager, widgetId, cachedData)
                    
                    // Trigger immediate background update to fetch the latest radar image
                    triggerScrapeAndUpdate(context, intArrayOf(widgetId))
                }
            }
            ACTION_HIDE_RAIN_POPUP -> {
                val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("rain_popup_$widgetId", false).apply()
                    
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val cachedData = getCachedWeatherData(context)
                    updateWidget(context, appWidgetManager, widgetId, cachedData)
                }
            }
            ACTION_SHOW_TEMP_POPUP -> {
                val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("temp_popup_$widgetId", true).apply()
                    prefs.edit().putBoolean("rain_popup_$widgetId", false).apply()
                    
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val cachedData = getCachedWeatherData(context)
                    updateWidget(context, appWidgetManager, widgetId, cachedData)
                    
                    // Trigger immediate background update to fetch the latest temperature graph
                    triggerScrapeAndUpdate(context, intArrayOf(widgetId))
                }
            }
            ACTION_HIDE_TEMP_POPUP -> {
                val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("temp_popup_$widgetId", false).apply()
                    
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val cachedData = getCachedWeatherData(context)
                    updateWidget(context, appWidgetManager, widgetId, cachedData)
                }
            }
            ACTION_SELECT_ZONE_PARIS -> {
                val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().putString("widget_zone", "paris").apply()
                    val appWidgetIds = intArrayOf(widgetId)
                    triggerScrapeAndUpdate(context, appWidgetIds)
                }
            }
            ACTION_SELECT_ZONE_NEUILLY -> {
                val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().putString("widget_zone", "neuilly").apply()
                    val appWidgetIds = intArrayOf(widgetId)
                    triggerScrapeAndUpdate(context, appWidgetIds)
                }
            }
            ACTION_TOGGLE_FORECAST_PAGE -> {
                val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val currentPage = prefs.getInt("forecast_page_$widgetId", 1)
                    val newPage = if (currentPage == 1) 2 else 1
                    prefs.edit().putInt("forecast_page_$widgetId", newPage).apply()
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
                val weatherData = WeatherScraper.scrape(context)
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
        
        const val ACTION_SHOW_RAIN_POPUP = "com.paris.weather.ACTION_SHOW_RAIN_POPUP"
        const val ACTION_HIDE_RAIN_POPUP = "com.paris.weather.ACTION_HIDE_RAIN_POPUP"
        const val ACTION_SHOW_TEMP_POPUP = "com.paris.weather.ACTION_SHOW_TEMP_POPUP"
        const val ACTION_HIDE_TEMP_POPUP = "com.paris.weather.ACTION_HIDE_TEMP_POPUP"
        
        const val ACTION_SELECT_ZONE_PARIS = "com.paris.weather.ACTION_SELECT_ZONE_PARIS"
        const val ACTION_SELECT_ZONE_NEUILLY = "com.paris.weather.ACTION_SELECT_ZONE_NEUILLY"
        const val ACTION_TOGGLE_FORECAST_PAGE = "com.paris.weather.ACTION_TOGGLE_FORECAST_PAGE"
        
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
                AlarmManager.ELAPSED_REALTIME,
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
                    put("rainHourStart", data.rainHourStart)
                    put("rainHourEnd", data.rainHourEnd)
                    
                    val rBlocksArray = JSONArray()
                    for (b in data.rainHourBlocks) {
                        rBlocksArray.put(b)
                    }
                    put("rainHourBlocks", rBlocksArray)
                    
                    val fArray = JSONArray()
                    for (f in data.forecasts) {
                        val fObj = JSONObject().apply {
                            put("dayName", f.dayName)
                            put("dayNum", f.dayNum)
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
                    put("tendancePicto1", data.tendancePicto1)
                    put("tendancePicto2", data.tendancePicto2)
                    put("tendancePeriod", data.tendancePeriod)
                    put("tendanceComment", data.tendanceComment)
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
                            dayNum = fObj.optString("dayNum", ""),
                            iconMorning = fObj.getString("iconMorning"),
                            iconAfternoon = fObj.getString("iconAfternoon"),
                            tempMin = fObj.getString("tempMin"),
                            tempMax = fObj.getString("tempMax"),
                            comment = fObj.getString("comment"),
                            uncertain = fObj.getInt("uncertain")
                        )
                    )
                }
                
                val rainHourBlocks = ArrayList<Int>()
                val rBlocksArray = obj.optJSONArray("rainHourBlocks")
                if (rBlocksArray != null) {
                    for (i in 0 until rBlocksArray.length()) {
                        rainHourBlocks.add(rBlocksArray.getInt(i))
                    }
                } else {
                    repeat(9) { rainHourBlocks.add(1) }
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
                    rainHourBlocks = rainHourBlocks,
                    rainHourStart = obj.optString("rainHourStart", "--:--"),
                    rainHourEnd = obj.optString("rainHourEnd", "--:--"),
                    forecasts = forecasts,
                    tendancePicto1 = obj.optString("tendancePicto1", "picto_44"),
                    tendancePicto2 = obj.optString("tendancePicto2", "picto_44"),
                    tendancePeriod = obj.optString("tendancePeriod", ""),
                    tendanceComment = obj.optString("tendanceComment", "")
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

                // Set popups click intents
                setPopupIntent(context, views, widgetId, R.id.layout_precip, ACTION_SHOW_RAIN_POPUP)
                setPopupIntent(context, views, widgetId, R.id.btn_close_rain_popup, ACTION_HIDE_RAIN_POPUP)
                setPopupIntent(context, views, widgetId, R.id.text_current_temp, ACTION_SHOW_TEMP_POPUP)
                setPopupIntent(context, views, widgetId, R.id.btn_close_temp_popup, ACTION_HIDE_TEMP_POPUP)
                setPopupIntent(context, views, widgetId, R.id.btn_tab_paris, ACTION_SELECT_ZONE_PARIS)
                setPopupIntent(context, views, widgetId, R.id.btn_tab_neuilly, ACTION_SELECT_ZONE_NEUILLY)

                // Highlight selected zone tab
                val currentZone = prefs.getString("widget_zone", "paris") ?: "paris"
                if (currentZone == "neuilly") {
                    views.setTextColor(R.id.btn_tab_paris, Color.parseColor("#828A96"))
                    views.setTextColor(R.id.btn_tab_neuilly, Color.WHITE)
                } else {
                    views.setTextColor(R.id.btn_tab_paris, Color.WHITE)
                    views.setTextColor(R.id.btn_tab_neuilly, Color.parseColor("#828A96"))
                }

                // Dynamic popup visibility & image loading
                val showRain = prefs.getBoolean("rain_popup_$widgetId", false)
                val showTemp = prefs.getBoolean("temp_popup_$widgetId", false)

                if (showRain) {
                    views.setViewVisibility(R.id.layout_rain_popup, android.view.View.VISIBLE)
                    val precipFile = java.io.File(context.cacheDir, "precipitation.png")
                    if (precipFile.exists()) {
                        val bmp = decodeScaledBitmap(precipFile.absolutePath, 500)
                        if (bmp != null) views.setImageViewBitmap(R.id.image_rain_graph, bmp)
                    }
                    
                    // Bind nowcast times
                    views.setTextViewText(R.id.text_rain_hour_start, weatherData.rainHourStart)
                    views.setTextViewText(R.id.text_rain_hour_end, weatherData.rainHourEnd)
                    
                    // Bind nowcast blocks color
                    val blockViewIds = listOf(
                        R.id.rain_block_1, R.id.rain_block_2, R.id.rain_block_3,
                        R.id.rain_block_4, R.id.rain_block_5, R.id.rain_block_6,
                        R.id.rain_block_7, R.id.rain_block_8, R.id.rain_block_9
                    )
                    for (i in 0 until 9) {
                        val intensity = if (i < weatherData.rainHourBlocks.size) weatherData.rainHourBlocks[i] else 1
                        val colorRes = when (intensity) {
                            2 -> R.color.rain_light
                            3 -> R.color.rain_moderate
                            4 -> R.color.rain_heavy
                            else -> R.color.rain_none
                        }
                        views.setInt(blockViewIds[i], "setBackgroundResource", colorRes)
                    }
                } else {
                    views.setViewVisibility(R.id.layout_rain_popup, android.view.View.GONE)
                }

                if (showTemp) {
                    views.setViewVisibility(R.id.layout_temp_popup, android.view.View.VISIBLE)
                    val tempFile = java.io.File(context.cacheDir, "temperatures.png")
                    if (tempFile.exists()) {
                        val bmp = decodeScaledBitmap(tempFile.absolutePath, 500)
                        if (bmp != null) views.setImageViewBitmap(R.id.image_temp_graph, bmp)
                    }
                } else {
                    views.setViewVisibility(R.id.layout_temp_popup, android.view.View.GONE)
                }

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

                // 6 Forecasts Page 1 (days 1-6) + Page 2 (days 7-8 + tendance)
                val forecastPage = prefs.getInt("forecast_page_$widgetId", 1)

                // Toggle arrow click intent (between rows)
                val toggleIntent = Intent(context, WeatherWidgetProvider::class.java).apply {
                    action = ACTION_TOGGLE_FORECAST_PAGE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                }
                val togglePending = PendingIntent.getBroadcast(
                    context,
                    900000 + widgetId,
                    toggleIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.btn_page_toggle, togglePending)

                if (forecastPage == 1) {
                    views.setViewVisibility(R.id.layout_forecast_page1, View.VISIBLE)
                    views.setViewVisibility(R.id.layout_forecast_page2, View.GONE)
                    views.setTextViewText(R.id.btn_page_toggle, "›")
                } else {
                    views.setViewVisibility(R.id.layout_forecast_page1, View.GONE)
                    views.setViewVisibility(R.id.layout_forecast_page2, View.VISIBLE)
                    views.setTextViewText(R.id.btn_page_toggle, "‹")
                }

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
                        val nameLabel = if (f.dayNum.isNotEmpty()) "${f.dayName} ${f.dayNum}" else f.dayName
                        views.setTextViewText(fNames[i], nameLabel)
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

                // Page 2: Day 7
                if (weatherData.forecasts.size > 6) {
                    val f7 = weatherData.forecasts[6]
                    val nameLabel7 = if (f7.dayNum.isNotEmpty()) "${f7.dayName} ${f7.dayNum}" else f7.dayName
                    views.setTextViewText(R.id.text_day7_name, nameLabel7)
                    views.setTextViewText(R.id.text_day7_temp, "${f7.tempMax}° / ${f7.tempMin}°")
                    val resM7 = context.resources.getIdentifier(f7.iconMorning, "drawable", context.packageName)
                    if (resM7 != 0) views.setImageViewResource(R.id.icon_day7_morning, resM7)
                    val resA7 = context.resources.getIdentifier(f7.iconAfternoon, "drawable", context.packageName)
                    if (resA7 != 0) views.setImageViewResource(R.id.icon_day7_afternoon, resA7)
                    setDayClickIntent(context, views, widgetId, R.id.layout_day7, f7.comment)
                }

                // Page 2: Day 8
                if (weatherData.forecasts.size > 7) {
                    val f8 = weatherData.forecasts[7]
                    val nameLabel8 = if (f8.dayNum.isNotEmpty()) "${f8.dayName} ${f8.dayNum}" else f8.dayName
                    views.setTextViewText(R.id.text_day8_name, nameLabel8)
                    views.setTextViewText(R.id.text_day8_temp, "${f8.tempMax}° / ${f8.tempMin}°")
                    val resM8 = context.resources.getIdentifier(f8.iconMorning, "drawable", context.packageName)
                    if (resM8 != 0) views.setImageViewResource(R.id.icon_day8_morning, resM8)
                    val resA8 = context.resources.getIdentifier(f8.iconAfternoon, "drawable", context.packageName)
                    if (resA8 != 0) views.setImageViewResource(R.id.icon_day8_afternoon, resA8)
                    setDayClickIntent(context, views, widgetId, R.id.layout_day8, f8.comment)
                }

                // Page 2: Tendance section
                val tp1Res = context.resources.getIdentifier(weatherData.tendancePicto1, "drawable", context.packageName)
                if (tp1Res != 0) views.setImageViewResource(R.id.icon_tendance1, tp1Res)
                val tp2Res = context.resources.getIdentifier(weatherData.tendancePicto2, "drawable", context.packageName)
                if (tp2Res != 0) views.setImageViewResource(R.id.icon_tendance2, tp2Res)
                views.setTextViewText(R.id.text_tendance_period, weatherData.tendancePeriod)
                views.setTextViewText(R.id.text_tendance_comment, weatherData.tendanceComment)

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

        private fun setPopupIntent(context: Context, views: RemoteViews, widgetId: Int, viewId: Int, actionStr: String) {
            val intent = Intent(context, WeatherWidgetProvider::class.java).apply {
                action = actionStr
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                viewId + widgetId * 1000,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(viewId, pendingIntent)
        }

        private fun decodeScaledBitmap(filePath: String, maxDim: Int): android.graphics.Bitmap? {
            try {
                val options = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                android.graphics.BitmapFactory.decodeFile(filePath, options)
                
                var scale = 1
                while ((options.outWidth / scale / 2) >= maxDim && (options.outHeight / scale / 2) >= maxDim) {
                    scale *= 2
                }
                
                val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                    inSampleSize = scale
                    inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                }
                return android.graphics.BitmapFactory.decodeFile(filePath, decodeOptions)
            } catch (e: Exception) {
                Log.e(TAG, "Error decoding scaled bitmap: $filePath", e)
                return null
            }
        }
    }
}
