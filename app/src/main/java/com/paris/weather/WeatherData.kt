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
    val lastUpdate: String,
    val forecasts: List<ForecastData>
)

data class ForecastData(
    val dayName: String,
    val iconMorning: String,
    val iconAfternoon: String,
    val tempMin: String,
    val tempMax: String,
    val comment: String,
    val uncertain: Int // 0=Reliable, 1=To confirm (orange), 2=Very uncertain (red)
)
