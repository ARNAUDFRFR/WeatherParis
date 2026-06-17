package com.paris.weather

data class WeatherData(
    val dayText: String,
    val weatherIconName: String,
    val conditionPhrase: String,
    val summary: String,
    val matinTemp: String,
    val matinPicto: String,
    val midiTemp: String,
    val midiPicto: String,
    val soirTemp: String,
    val soirPicto: String,
    val nuitTemp: String,
    val nuitPicto: String,
    val currentTempDec: String,
    val windText: String,
    val ecartText: String,
    val precipProba: String,
    val precipVolume: String,
    val lastUpdate: String,
    val rainHourBlocks: List<Int>,
    val rainHourStart: String,
    val rainHourEnd: String,
    val forecasts: List<ForecastData>,
    // Page 2: days 7 & 8 + tendance
    val tendancePicto1: String = "picto_44",
    val tendancePicto2: String = "picto_44",
    val tendancePeriod: String = "",
    val tendanceComment: String = ""
)

data class ForecastData(
    val dayName: String,
    val dayNum: String = "",      // Day of month, e.g. "22"
    val iconMorning: String,
    val iconAfternoon: String,
    val tempMin: String,
    val tempMax: String,
    val comment: String,
    val uncertain: Int // 0=Reliable, 1=To confirm (orange), 2=Very uncertain (red)
)
