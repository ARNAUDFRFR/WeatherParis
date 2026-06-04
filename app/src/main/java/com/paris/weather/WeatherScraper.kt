package com.paris.weather

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

    fun scrape(): WeatherData? {
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

        // 2. Scrape Previsions Page for 6 days
        val forecasts = ArrayList<ForecastData>()
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

                // Parse uncertainty indicator
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
                        iconMorning = morningIcon,
                        iconAfternoon = afternoonIcon,
                        tempMin = tempMin,
                        tempMax = tempMax,
                        comment = comment.replace("\"", ""),
                        uncertain = uncertain
                    )
                )

                if (forecasts.size >= 6) {
                    break
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error scraping previsions page", e)
        }

        // Pad with fallbacks if needed
        while (forecasts.size < 6) {
            val idx = forecasts.size + 1
            forecasts.add(
                ForecastData(
                    dayName = "Jour $idx",
                    iconMorning = "picto_44",
                    iconAfternoon = "picto_44",
                    tempMin = "--",
                    tempMax = "--",
                    comment = "Données indisponibles",
                    uncertain = 0
                )
            )
        }

        return WeatherData(
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
            lastUpdate = lastUpdate,
            forecasts = forecasts
        )
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
