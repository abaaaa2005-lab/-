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
        conn.connectTimeout = 6_000
        conn.readTimeout = 8_000
        return try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** Сначала Open-Meteo, если не ответил — запасной источник wttr.in. */
    suspend fun fetch(lat: Double, lon: Double): WeatherData? =
        fetchOpenMeteo(lat, lon) ?: fetchWttr(lat, lon)

    private suspend fun fetchOpenMeteo(lat: Double, lon: Double): WeatherData? = withContext(Dispatchers.IO) {
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

    private suspend fun fetchWttr(lat: Double, lon: Double): WeatherData? = withContext(Dispatchers.IO) {
        runCatching {
            val json = JSONObject(get("https://wttr.in/${"%.4f".format(java.util.Locale.US, lat)},${"%.4f".format(java.util.Locale.US, lon)}?format=j1"))
            val cur = json.getJSONArray("current_condition").getJSONObject(0)
            val days = json.getJSONArray("weather")
            WeatherData(
                temp = cur.getString("temp_C").toDouble(),
                feelsLike = cur.optString("FeelsLikeC", cur.getString("temp_C")).toDouble(),
                wind = cur.optString("windspeedKmph", "0").toDouble() / 3.6,
                humidity = cur.optString("humidity", "0").toInt(),
                code = wwoToWmo(cur.optString("weatherCode", "113").toInt()),
                days = List(days.length()) {
                    val d = days.getJSONObject(it)
                    val hourly = d.optJSONArray("hourly")
                    val code = hourly?.optJSONObject((hourly.length() / 2).coerceAtLeast(0))?.optString("weatherCode", "113")?.toInt() ?: 113
                    DayForecast(d.getString("date"), d.getString("mintempC").toDouble(), d.getString("maxtempC").toDouble(), wwoToWmo(code))
                },
                fetchedAt = System.currentTimeMillis(),
            )
        }.getOrNull()
    }

    /** Коды погоды wttr.in (WWO) → коды WMO, которые понимает лаунчер. */
    private fun wwoToWmo(c: Int): Int = when (c) {
        113 -> 0
        116 -> 2
        119, 122 -> 3
        143, 248, 260 -> 45
        176, 263, 266, 293, 296 -> 51
        179, 227, 230, 323, 326, 329, 332, 335, 338, 368, 371 -> 71
        200, 386, 389, 392, 395 -> 95
        else -> 61
    }

    suspend fun findCity(name: String): List<City> = withContext(Dispatchers.IO) {
        val q = name.trim().lowercase()
        val local = KZ_CITIES.filter { it.name.lowercase().startsWith(q) }
        val remote = runCatching {
            val enc = URLEncoder.encode(name.trim(), "UTF-8")
            val json = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=$enc&count=8&language=ru"))
            val arr = json.optJSONArray("results") ?: return@runCatching emptyList()
            List(arr.length()) {
                val o = arr.getJSONObject(it)
                City(o.getString("name"), o.getDouble("latitude"), o.getDouble("longitude"), o.optString("country"))
            }
        }.getOrDefault(emptyList())
        // Казахстан — первым
        (local + remote.sortedByDescending { it.country == "Казахстан" })
            .distinctBy { it.name + it.country }
    }

    /** Города Казахстана — работают даже без поиска в интернете. */
    val KZ_CITIES = listOf(
        City("Алматы", 43.2389, 76.8897, "Казахстан"),
        City("Астана", 51.1694, 71.4491, "Казахстан"),
        City("Шымкент", 42.3417, 69.5901, "Казахстан"),
        City("Караганда", 49.8047, 73.1094, "Казахстан"),
        City("Актобе", 50.2839, 57.1670, "Казахстан"),
        City("Тараз", 42.9000, 71.3667, "Казахстан"),
        City("Павлодар", 52.2873, 76.9674, "Казахстан"),
        City("Усть-Каменогорск", 49.9483, 82.6279, "Казахстан"),
        City("Семей", 50.4111, 80.2275, "Казахстан"),
        City("Атырау", 47.1167, 51.8833, "Казахстан"),
        City("Костанай", 53.2144, 63.6246, "Казахстан"),
        City("Кызылорда", 44.8528, 65.5092, "Казахстан"),
        City("Уральск", 51.2333, 51.3667, "Казахстан"),
        City("Петропавловск", 54.8667, 69.1500, "Казахстан"),
        City("Актау", 43.6500, 51.1667, "Казахстан"),
        City("Туркестан", 43.2973, 68.2518, "Казахстан"),
        City("Талдыкорган", 45.0156, 78.3739, "Казахстан"),
        City("Кокшетау", 53.2833, 69.4000, "Казахстан"),
        City("Экибастуз", 51.7298, 75.3266, "Казахстан"),
        City("Жезказган", 47.7833, 67.7667, "Казахстан"),
        City("Конаев", 43.8667, 77.0667, "Казахстан"),
    )

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
