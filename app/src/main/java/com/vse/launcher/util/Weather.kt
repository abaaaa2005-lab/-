package com.vse.launcher.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class DayForecast(val date: String, val min: Double, val max: Double, val code: Int)

data class WeatherData(
    val temp: Double,
    val feelsLike: Double,
    val wind: Double,
    val humidity: Int,
    val code: Int,
    val days: List<DayForecast>,
    val fetchedAt: Long,
)

data class City(val name: String, val lat: Double, val lon: Double, val country: String)

/** Погода через бесплатный Open-Meteo (без ключей и регистрации). */
object Weather {
    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        return try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    suspend fun fetch(lat: Double, lon: Double): WeatherData? = withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject(
                get(
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                        "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m" +
                        "&daily=temperature_2m_max,temperature_2m_min,weather_code&timezone=auto&forecast_days=5&wind_speed_unit=ms"
                )
            )
            val cur = json.getJSONObject("current")
            val daily = json.getJSONObject("daily")
            val times = daily.getJSONArray("time")
            val days = List(times.length()) {
                DayForecast(
                    times.getString(it),
                    daily.getJSONArray("temperature_2m_min").getDouble(it),
                    daily.getJSONArray("temperature_2m_max").getDouble(it),
                    daily.getJSONArray("weather_code").getInt(it),
                )
            }
            WeatherData(
                temp = cur.getDouble("temperature_2m"),
                feelsLike = cur.optDouble("apparent_temperature", cur.getDouble("temperature_2m")),
                wind = cur.optDouble("wind_speed_10m", 0.0),
                humidity = cur.optInt("relative_humidity_2m", 0),
                code = cur.getInt("weather_code"),
                days = days,
                fetchedAt = System.currentTimeMillis(),
            )
        }.getOrNull()
    }

    suspend fun findCity(name: String): List<City> = withContext(Dispatchers.IO) {
        runCatching {
            val q = URLEncoder.encode(name, "UTF-8")
            val json = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=5&language=ru"))
            val arr = json.optJSONArray("results") ?: return@runCatching emptyList()
            List(arr.length()) {
                val o = arr.getJSONObject(it)
                City(o.getString("name"), o.getDouble("latitude"), o.getDouble("longitude"), o.optString("country"))
            }
        }.getOrDefault(emptyList())
    }

    fun emoji(code: Int): String = when (code) {
        0 -> "☀️"
        1, 2 -> "🌤️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..57 -> "🌦️"
        in 61..67, in 80..82 -> "🌧️"
        in 71..77, 85, 86 -> "❄️"
        in 95..99 -> "⛈️"
        else -> "🌡️"
    }

    fun describe(code: Int): String = when (code) {
        0 -> "Ясно"
        1 -> "Преимущественно ясно"
        2 -> "Переменная облачность"
        3 -> "Пасмурно"
        45, 48 -> "Туман"
        in 51..57 -> "Морось"
        in 61..67 -> "Дождь"
        in 71..77 -> "Снег"
        in 80..82 -> "Ливень"
        85, 86 -> "Снегопад"
        in 95..99 -> "Гроза"
        else -> "—"
    }
}
