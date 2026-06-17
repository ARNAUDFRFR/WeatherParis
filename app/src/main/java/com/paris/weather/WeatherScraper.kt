package com.paris.weather

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import org.jsoup.Jsoup
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

object WeatherScraper {
    private const val TAG = "WeatherScraper"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private fun downloadImage(urlString: String, destFile: java.io.File) {
        try {
            val url = java.net.URL(urlString)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.inputStream.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Downloaded image successfully: $urlString to ${destFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading image: $urlString", e)
        }
    }

    private fun rot13(text: String): String {
        val sb = java.lang.StringBuilder()
        for (c in text) {
            when (c) {
                in 'a'..'z' -> sb.append((97 + (c.code - 97 + 13) % 26).toChar())
                in 'A'..'Z' -> sb.append((65 + (c.code - 65 + 13) % 26).toChar())
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    private fun fetchMeteoFranceNowcast(zone: String): Triple<List<Int>, String, String> {
        val defaultBlocks = List(9) { 1 }
        val defaultStart = "--:--"
        val defaultEnd = "--:--"
        
        try {
            Log.d(TAG, "Fetching Meteo France page to get mfsession cookie for zone $zone...")
            val pageUrl = if (zone == "neuilly") {
                "https://meteofrance.com/previsions-meteo-france/neuilly-sur-seine/92200"
            } else {
                "https://meteofrance.com/previsions-meteo-france/paris/75000"
            }
            val response = Jsoup.connect(pageUrl)
                .userAgent(USER_AGENT)
                .header("Accept-Language", "fr-FR,fr;q=0.9")
                .timeout(10000)
                .execute()
                
            val mfsession = response.cookie("mfsession")
            if (mfsession.isNullOrEmpty()) {
                Log.e(TAG, "mfsession cookie not found!")
                return Triple(defaultBlocks, defaultStart, defaultEnd)
            }
            
            Log.d(TAG, "mfsession cookie found! Decoding token...")
            val decodedToken = rot13(java.net.URLDecoder.decode(mfsession, "UTF-8"))
            
            val url = if (zone == "neuilly") {
                "https://rwg.meteofrance.com/internet2018client/2.0/nowcast/rain?lat=48.887173&lon=2.267001"
            } else {
                "https://rwg.meteofrance.com/internet2018client/2.0/nowcast/rain?lat=48.859333&lon=2.340591"
            }
            Log.d(TAG, "Fetching nowcast API: $url")
            val jsonStr = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .header("Authorization", "Bearer $decodedToken")
                .ignoreContentType(true)
                .timeout(10000)
                .execute()
                .body()
                
            val obj = org.json.JSONObject(jsonStr)
            val forecast = obj.optJSONObject("properties")?.optJSONArray("forecast")
            
            val blocks = ArrayList<Int>()
            var start = defaultStart
            var end = defaultEnd
            
            if (forecast != null && forecast.length() > 0) {
                val formatIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.FRANCE).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                val formatLocal = SimpleDateFormat("HH:mm", Locale.FRANCE)
                
                // Start time: 5 minutes before first block
                val firstObj = forecast.optJSONObject(0)
                val firstTimeStr = firstObj?.optString("time")
                if (!firstTimeStr.isNullOrEmpty()) {
                    try {
                        val dt = formatIso.parse(firstTimeStr)
                        if (dt != null) {
                            val cal = Calendar.getInstance()
                            cal.time = dt
                            cal.add(Calendar.MINUTE, -5)
                            start = formatLocal.format(cal.time)
                        }
                    } catch (e: Exception) {
                        try {
                            start = firstTimeStr.substringAfter("T").substring(0, 5)
                        } catch (ex: Exception) {
                            start = defaultStart
                        }
                    }
                }
                
                // End time: last block time
                val lastObj = forecast.optJSONObject(forecast.length() - 1)
                val lastTimeStr = lastObj?.optString("time")
                if (!lastTimeStr.isNullOrEmpty()) {
                    try {
                        val dt = formatIso.parse(lastTimeStr)
                        if (dt != null) {
                            end = formatLocal.format(dt)
                        }
                    } catch (e: Exception) {
                        try {
                            end = lastTimeStr.substringAfter("T").substring(0, 5)
                        } catch (ex: Exception) {
                            end = defaultEnd
                        }
                    }
                }
                
                // Parse intensities
                for (i in 0 until Math.min(forecast.length(), 9)) {
                    val item = forecast.optJSONObject(i)
                    val intensity = item?.optInt("rain_intensity", 1) ?: 1
                    blocks.add(intensity)
                }
            }
            
            while (blocks.size < 9) {
                blocks.add(1)
            }
            
            return Triple(blocks, start, end)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching nowcast rain data in Kotlin", e)
            return Triple(defaultBlocks, defaultStart, defaultEnd)
        }
    }


    fun scrape(context: Context? = null): WeatherData? {
        var dayText = "Aujourd'hui"
        var weatherIconName = "picto_44"
        var conditionPhrase = "Ensoleillé"
        var summary = "Scraping en cours..."
        var matinTemp = "--"
        var matinPicto = "picto_44"
        var midiTemp = "--"
        var midiPicto = "picto_44"
        var soirTemp = "--"
        var soirPicto = "picto_44"
        var nuitTemp = "--"
        var nuitPicto = "picto_44"
        var currentTempDec = "--.-"
        var windText = "-- km/h"
        var ecartText = "--"
        var precipProba = "0"
        var precipVolume = "0 mm"
        val lastUpdate = SimpleDateFormat("H:mm", Locale.FRANCE).format(Date())

        // 1. Scrape Homepage
        try {
            val doc = Jsoup.connect("https://www.meteo-paris.com")
                .userAgent(USER_AGENT)
                .header("Accept-Language", "fr-FR,fr;q=0.9")
                .timeout(8000)
                .get()

            val htmlClean = doc.html()

            // Today's summary from meta description
            val descMeta = doc.select("meta[name=description]").first()?.attr("content")
            if (descMeta != null) {
                summary = descMeta

                // Extract current day name
                val dayMatcher = Pattern.compile("^(Ce\\s+[a-z\\u00C0-\\u00FF]+)", Pattern.CASE_INSENSITIVE).matcher(summary)
                if (dayMatcher.find()) {
                    dayText = dayMatcher.group(1) ?: "Aujourd'hui"
                }

                // Extract temps
                val matinM = Pattern.compile("Matin\\s+(\\d+)", Pattern.CASE_INSENSITIVE).matcher(summary)
                val midiM = Pattern.compile("(Après-midi|Midi)\\s+(\\d+)", Pattern.CASE_INSENSITIVE).matcher(summary)
                val soirM = Pattern.compile("Soir\\s+(\\d+)", Pattern.CASE_INSENSITIVE).matcher(summary)
                val nuitM = Pattern.compile("Nuit\\s+(\\d+)", Pattern.CASE_INSENSITIVE).matcher(summary)

                if (matinM.find()) matinTemp = matinM.group(1) ?: "--"
                if (midiM.find()) midiTemp = midiM.group(2) ?: "--"
                if (soirM.find()) soirTemp = soirM.group(1) ?: "--"
                if (nuitM.find()) nuitTemp = nuitM.group(1) ?: "--"

                // Clean summary
                summary = summary.replace(Regex("\\s*\\([^)]*\\)\\s*\\.?$"), "")

                // Condition phrase
                val summaryLower = summary.lowercase(Locale.FRANCE)
                val normalizedSummary = removeAccents(summaryLower)
                conditionPhrase = when {
                    normalizedSummary.contains("orage") || normalizedSummary.contains("foudre") || normalizedSummary.contains("grel") || normalizedSummary.contains("orageux") -> "Orageux"
                    normalizedSummary.contains("pluie") || normalizedSummary.contains("averse") || normalizedSummary.contains("bruine") || normalizedSummary.contains("pleuv") || normalizedSummary.contains("ondee") -> "Pluie"
                    normalizedSummary.contains("neige") || normalizedSummary.contains("flocons") -> "Neige"
                    normalizedSummary.contains("brouillard") || normalizedSummary.contains("brume") -> "Brume"
                    normalizedSummary.contains("eclairc") || normalizedSummary.contains("partiellement") || normalizedSummary.contains("changeant") || normalizedSummary.contains("partage") -> "Éclaircie"
                    normalizedSummary.contains("couvert") || normalizedSummary.contains("nuageux") || normalizedSummary.contains("nuages") || normalizedSummary.contains("gris") -> "Nuageux"
                    normalizedSummary.contains("soleil") || normalizedSummary.contains("ensoleill") || normalizedSummary.contains("degage") || normalizedSummary.contains("clair") || normalizedSummary.contains("estival") || normalizedSummary.contains("beau") -> "Ensoleillé"
                    normalizedSummary.contains("vent") || normalizedSummary.contains("rafales") -> "Venteux"
                    else -> "Variable"
                }
            }

            // Current Weather Icon from svg hash
            val pictoMatcher = Pattern.compile("picto\\.svg#(picto_\\d+)").matcher(htmlClean)
            if (pictoMatcher.find()) {
                weatherIconName = pictoMatcher.group(1) ?: "picto_44"
            }

            // Period pictos (Matin, Après-midi, Soir, Nuit)
            val periods = listOf(
                Pair("Matin", { v: String -> matinPicto = v }),
                Pair("Après-midi", { v: String -> midiPicto = v }),
                Pair("Soir", { v: String -> soirPicto = v }),
                Pair("Nuit", { v: String -> nuitPicto = v })
            )

            for ((period, setter) in periods) {
                val altPeriod = period.replace("è", ".")
                val pPattern = Pattern.compile("<span class=\"whitespace-nowrap\">" + altPeriod + "</span>", Pattern.CASE_INSENSITIVE)
                val m = pPattern.matcher(htmlClean)
                if (m.find()) {
                    val pos = m.end()
                    val subHtml = htmlClean.substring(pos, Math.min(pos + 1000, htmlClean.length))
                    val pictoM = Pattern.compile("picto\\.svg#(picto_\\d+)").matcher(subHtml)
                    if (pictoM.find()) {
                        setter(pictoM.group(1) ?: "picto_44")
                    }
                }
            }

            // Real-time station data (JSON)
            val rtMatcher = Pattern.compile("realtimeReport\\\\*\"\\s*:\\s*\\\\*\\{([^}]+)\\}").matcher(htmlClean)
            if (rtMatcher.find()) {
                val rtContent = rtMatcher.group(1) ?: ""
                val tempM = Pattern.compile("temperature\\\\*\"\\s*:\\s*(\\d+[.,]\\d+|\\d+)").matcher(rtContent)
                val windM = Pattern.compile("wind_speed\\\\*\"\\s*:\\s*(null|\\d+[.,]\\d+|\\d+)").matcher(rtContent)
                val windDirM = Pattern.compile("wind_direction\\\\*\"\\s*:\\s*\\\\*\"([^\"\\\\]+)\\\\*\"").matcher(rtContent)

                if (tempM.find()) {
                    val tVal = tempM.group(1)?.replace(',', '.')?.toDoubleOrNull()
                    if (tVal != null) {
                        currentTempDec = String.format(Locale.FRANCE, "%.1f", tVal)
                    }
                }

                var windSpeed: Double? = null
                if (windM.find() && windM.group(1) != "null") {
                    windSpeed = windM.group(1)?.replace(',', '.')?.toDoubleOrNull()
                }

                val windDir = if (windDirM.find()) windDirM.group(1) ?: "" else ""

                if (windSpeed != null) {
                    windText = String.format(Locale.FRANCE, "%.0f km/h %s", windSpeed, windDir).trim()
                } else if (windDir.isNotEmpty()) {
                    windText = "-- km/h $windDir".trim()
                }
            }

            // Seasonal gap
            val ecartMatcher = Pattern.compile("cart<!--\\s*-->\\s*<span[^>]*>saison</span></span><span[^>]*>([+-]?\\d+)", Pattern.CASE_INSENSITIVE).matcher(htmlClean)
            if (ecartMatcher.find()) {
                val valE = ecartMatcher.group(1)?.toIntOrNull()
                if (valE != null) {
                    ecartText = if (valE >= 0) "+$valE" else "$valE"
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error scraping home page", e)
        }

        // 2. Scrape Previsions Page for up to 8 days + tendance
        val forecasts = ArrayList<ForecastData>()
        var tendancePicto1 = "picto_44"
        var tendancePicto2 = "picto_44"
        var tendancePeriod = ""
        var tendanceComment = ""
        try {
            val docPrev = Jsoup.connect("https://www.meteo-paris.com/ile-de-france/previsions")
                .userAgent(USER_AGENT)
                .header("Accept-Language", "fr-FR,fr;q=0.9")
                .timeout(8000)
                .get()

            val htmlPrev = docPrev.html()

            // Find all pictos in previsions
            val allPictos = ArrayList<String>()
            val pictoMatcher = Pattern.compile("picto\\.svg#(picto_\\d+)").matcher(htmlPrev)
            while (pictoMatcher.find()) {
                val picto = pictoMatcher.group(1)
                if (picto != null) {
                    allPictos.add(picto)
                }
            }

            // Skip first picto if it is header/logo fallback (picto_35 in python)
            var startIdx = 0
            if (allPictos.isNotEmpty() && allPictos[0] == "picto_35") {
                startIdx = 1
            }

            val dateMatches = ArrayList<Pair<Int, String>>()
            val dateMatcher = Pattern.compile("\"headline\"\\s*:\\s*\"Paris\\s+[^\"\\d]*(\\d{4}-\\d{2}-\\d{2})\"").matcher(htmlPrev)
            while (dateMatcher.find()) {
                val dStr = dateMatcher.group(1)
                if (dStr != null) {
                    dateMatches.add(Pair(dateMatcher.start(), dStr))
                }
            }

            // Today's precipitation (first date match on the previsions page)
            if (dateMatches.isNotEmpty()) {
                val firstMatch = dateMatches[0]
                val posT = firstMatch.first
                val windowT = htmlPrev.substring(posT, Math.min(posT + 2000, htmlPrev.length))
                
                val probaM = Pattern.compile("Pluie\\s*\\(proba\\)[^}]+\"value\"\\s*:\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(windowT)
                val volM = Pattern.compile("Pluie\\s*\\(24h\\)[^}]+\"value\"\\s*:\\s*(\\d+(?:[.,]\\d+)?)", Pattern.CASE_INSENSITIVE).matcher(windowT)
                
                if (probaM.find()) {
                    precipProba = probaM.group(1) ?: "0"
                }
                if (volM.find()) {
                    precipVolume = "${volM.group(1) ?: "0"} mm"
                }
            }

            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date())
            val seenDates = HashSet<String>()

            for (dm in dateMatches) {
                val dateStr = dm.second
                if (seenDates.contains(dateStr) || dateStr == todayStr) {
                    continue
                }
                seenDates.add(dateStr)

                val dayIdx = forecasts.size

                // 2 pictos per day: Morning and Afternoon
                val mIdx = startIdx + 2 + 2 * dayIdx
                val aIdx = startIdx + 3 + 2 * dayIdx

                val morningIcon = if (mIdx < allPictos.size) allPictos[mIdx] else "picto_44"
                val afternoonIcon = if (aIdx < allPictos.size) allPictos[aIdx] else "picto_44"

                // Parse day name in French
                val dayName = getDayNameFr(dateStr)

                // Parse day-of-month number and uncertainty indicator
                val dtObj = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).parse(dateStr)
                val cal = Calendar.getInstance()
                if (dtObj != null) cal.time = dtObj
                val fDaysCap = listOf("Lun.", "Mar.", "Mer.", "Jeu.", "Ven.", "Sam.", "Dim.")
                val dayNameCap = fDaysCap[if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) 6 else cal.get(Calendar.DAY_OF_WEEK) - 2]
                val dayNum = cal.get(Calendar.DAY_OF_MONTH).toString()

                val headerPattern = Pattern.compile(Pattern.quote(dayNameCap) + "</span>\\s*<span[^>]*>\\s*" + Pattern.quote(dayNum) + "\\s*</span>")
                val headerM = headerPattern.matcher(htmlPrev)

                var uncertain = 0
                if (headerM.find()) {
                    val posH = headerM.end()
                    val subH = htmlPrev.substring(posH, Math.min(posH + 500, htmlPrev.length))
                    if (subH.contains("bg-[#da0f0f]") || subH.lowercase().contains("très incertain") || subH.lowercase().contains("tres incertain")) {
                        uncertain = 2
                    } else if (subH.contains("bg-[#da830f]") || subH.lowercase().contains("à confirmer") || subH.lowercase().contains("a confirmer")) {
                        uncertain = 1
                    }
                }

                // Window search for temperatures & comments
                val pos = dm.first
                val window = htmlPrev.substring(pos, Math.min(pos + 3000, htmlPrev.length))

                val descM = Pattern.compile("\"description\"\\s*:\\s*\"([^\"]+)\"").matcher(window)
                val comment = if (descM.find()) descM.group(1) ?: "" else ""

                val tminM = Pattern.compile("\"Temp[^\"]+\\s+min\"[^}]+\"value\"\\s*:\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(window)
                val tmaxM = Pattern.compile("\"Temp[^\"]+\\s+max\"[^}]+\"value\"\\s*:\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(window)

                val tempMin = if (tminM.find()) tminM.group(1) ?: "0" else "0"
                val tempMax = if (tmaxM.find()) tmaxM.group(1) ?: "0" else "0"

                forecasts.add(
                    ForecastData(
                        dayName = dayName,
                        dayNum = dayNum,
                        iconMorning = morningIcon,
                        iconAfternoon = afternoonIcon,
                        tempMin = tempMin,
                        tempMax = tempMax,
                        comment = comment.replace("\"", ""),
                        uncertain = uncertain
                    )
                )

                if (forecasts.size >= 8) {
                    break
                }
            }

            // Extract TENDANCE block (after the 8 regular days)
            try {
                val tendanceM = Pattern.compile(
                    "TENDANCE[^<]*:[^<]*<",
                    Pattern.CASE_INSENSITIVE
                ).matcher(htmlPrev)
                if (tendanceM.find()) {
                    // Get a window around TENDANCE
                    val tPos = tendanceM.start()
                    val tWindow = htmlPrev.substring(tPos, Math.min(tPos + 3000, htmlPrev.length))

                    // Extract period: text before ":"
                    val periodM = Pattern.compile("(TENDANCE[^:]+):").matcher(tWindow)
                    if (periodM.find()) {
                        tendancePeriod = periodM.group(1)?.trim()?.replace(Regex("<[^>]+>"), "") ?: ""
                    }

                    // Extract comment: description field
                    val tDescM = Pattern.compile("\"description\"\\s*:\\s*\"([^\"]+)\"").matcher(tWindow)
                    if (tDescM.find()) {
                        tendanceComment = tDescM.group(1)?.replace("\"", "") ?: ""
                    }

                    // Extract tendance pictos (2 pictos after the TENDANCE block)
                    val tPictos = ArrayList<String>()
                    val tPictoM = Pattern.compile("picto\\.svg#(picto_\\d+)").matcher(tWindow)
                    while (tPictoM.find() && tPictos.size < 2) {
                        tPictos.add(tPictoM.group(1) ?: "picto_44")
                    }
                    if (tPictos.size >= 1) tendancePicto1 = tPictos[0]
                    if (tPictos.size >= 2) tendancePicto2 = tPictos[1]
                }
            } catch (e2: Exception) {
                Log.e(TAG, "Error extracting tendance block", e2)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error scraping previsions page", e)
        }

        // Pad with fallbacks if needed
        while (forecasts.size < 8) {
            val idx = forecasts.size + 1
            forecasts.add(
                ForecastData(
                    dayName = "Jour $idx",
                    dayNum = "",
                    iconMorning = "picto_44",
                    iconAfternoon = "picto_44",
                    tempMin = "--",
                    tempMax = "--",
                    comment = "Données indisponibles",
                    uncertain = 0
                )
            )
        }

        val zone = if (context != null) {
            val prefs = context.getSharedPreferences("com.paris.weather.WIDGET_PREFS", Context.MODE_PRIVATE)
            prefs.getString("widget_zone", "paris") ?: "paris"
        } else {
            "paris"
        }
        val (nowcastBlocks, nowcastStart, nowcastEnd) = fetchMeteoFranceNowcast(zone)

        val dataObj = WeatherData(
            dayText = dayText,
            weatherIconName = weatherIconName,
            conditionPhrase = conditionPhrase,
            summary = summary,
            matinTemp = matinTemp,
            matinPicto = matinPicto,
            midiTemp = midiTemp,
            midiPicto = midiPicto,
            soirTemp = soirTemp,
            soirPicto = soirPicto,
            nuitTemp = nuitTemp,
            nuitPicto = nuitPicto,
            currentTempDec = currentTempDec,
            windText = windText,
            ecartText = ecartText,
            precipProba = precipProba,
            precipVolume = precipVolume,
            lastUpdate = lastUpdate,
            rainHourBlocks = nowcastBlocks,
            rainHourStart = nowcastStart,
            rainHourEnd = nowcastEnd,
            forecasts = forecasts,
            tendancePicto1 = tendancePicto1,
            tendancePicto2 = tendancePicto2,
            tendancePeriod = tendancePeriod,
            tendanceComment = tendanceComment
        )

        if (context != null) {
            val cacheDir = context.cacheDir
            val precipFile = java.io.File(cacheDir, "precipitation.png")
            val tempFile = java.io.File(cacheDir, "temperatures.png")
            
            val prefs = context.getSharedPreferences("com.paris.weather.WIDGET_PREFS", Context.MODE_PRIVATE)
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, WeatherWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            var anyRainOpen = false
            var anyTempOpen = false
            for (id in appWidgetIds) {
                if (prefs.getBoolean("rain_popup_$id", false)) anyRainOpen = true
                if (prefs.getBoolean("temp_popup_$id", false)) anyTempOpen = true
            }
            
            if (anyRainOpen) {
                var radarUrl = "https://www.infoclimat.fr/api/VTYELlRuCj8AKAQxV2ZSMFMhBWdSMgYxAn4MYAQxByhUNgIwBTcEMQM3X2gFNAdlBWhRO1EzVmlUMw9h/radar/nord_idf?dc0b4fd56dfa02bfeb6024ba1903c10e" // fallback
                try {
                    val docSuivi = Jsoup.connect("https://www.meteo-paris.com/ile-de-france/suivi-des-pluies")
                        .userAgent(USER_AGENT)
                        .header("Accept-Language", "fr-FR,fr;q=0.9")
                        .timeout(8000)
                        .get()
                    val img = docSuivi.select("img[src*=infoclimat.fr/api/]").first()
                    if (img != null) {
                        val srcUrl = img.attr("src")
                        if (srcUrl.isNotEmpty()) {
                            radarUrl = srcUrl
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error scraping suivi des pluies radar image", e)
                }
                downloadImage(radarUrl, precipFile)
            }
            
            if (anyTempOpen) {
                downloadImage("https://s.meteo-villes.com/graphs/station-paris/temperatures.png", tempFile)
            }
        }

        return dataObj
    }

    private fun getDayNameFr(dateStr: String): String {
        return try {
            val dt = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).parse(dateStr)
            val cal = Calendar.getInstance()
            if (dt != null) cal.time = dt
            val frenchDays = listOf("lun.", "mar.", "mer.", "jeu.", "ven.", "sam.", "dim.")
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            // Calendar.SUNDAY = 1, MONDAY = 2...
            val idx = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - 2
            frenchDays[idx]
        } catch (e: Exception) {
            "jour"
        }
    }

    private fun removeAccents(text: String): String {
        var temp = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        temp = temp.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return temp
    }
}
