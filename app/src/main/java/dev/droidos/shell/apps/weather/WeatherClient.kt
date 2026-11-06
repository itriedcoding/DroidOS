package dev.droidos.shell.apps.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Open-Meteo needs no API key, so nothing here depends on a secret the user must obtain.
 * Both endpoints return JSON; every failure is surfaced instead of being papered over.
 */
class WeatherClient {

    data class Place(
        val name: String,
        val admin1: String?,
        val country: String?,
        val latitude: Double,
        val longitude: Double
    )

    data class Current(
        val temperature: Double?,
        val feelsLike: Double?,
        val humidity: Int?,
        val precipitation: Double?,
        val code: Int,
        val windSpeed: Double?,
        val windDirection: Double?,
        val isDay: Boolean
    )

    data class Day(
        val date: String,
        val code: Int,
        val max: Double?,
        val min: Double?,
        val precipitationChance: Int?
    )

    data class Forecast(val current: Current, val days: List<Day>, val timezone: String?)

    suspend fun geocode(query: String): Result<List<Place>> {
        if (query.isBlank()) return Result.success(emptyList())
        return get(WeatherEngine.geocodeUrl(query)).mapCatching { body ->
            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: return@mapCatching emptyList()
            buildList {
                for (index in 0 until results.length()) {
                    val place = results.optJSONObject(index) ?: continue
                    add(
                        Place(
                            name = place.optString("name", "Unknown"),
                            admin1 = place.optString("admin1").takeIf { it.isNotBlank() },
                            country = place.optString("country").takeIf { it.isNotBlank() },
                            latitude = place.optDouble("latitude", Double.NaN),
                            longitude = place.optDouble("longitude", Double.NaN)
                        )
                    )
                }
            }.filter { !it.latitude.isNaN() && !it.longitude.isNaN() }
        }
    }

    suspend fun forecast(latitude: Double, longitude: Double): Result<Forecast> =
        get(WeatherEngine.forecastUrl(latitude, longitude)).mapCatching { body ->
            val json = JSONObject(body)
            val current = json.optJSONObject("current")
                ?: throw IllegalStateException("Open-Meteo returned no current conditions")
            val daily = json.optJSONObject("daily")
            val times = daily?.optJSONArray("time")
            val codes = daily?.optJSONArray("weather_code")
            val maxima = daily?.optJSONArray("temperature_2m_max")
            val minima = daily?.optJSONArray("temperature_2m_min")
            val chances = daily?.optJSONArray("precipitation_probability_max")
            val days = buildList {
                if (times != null && codes != null) {
                    for (index in 0 until times.length()) {
                        add(
                            Day(
                                date = times.optString(index),
                                code = codes.optInt(index, -1),
                                max = maxima?.optDouble(index)?.takeUnless { it.isNaN() },
                                min = minima?.optDouble(index)?.takeUnless { it.isNaN() },
                                precipitationChance = chances?.optInt(index, -1)
                                    ?.takeIf { it >= 0 }
                            )
                        )
                    }
                }
            }
            Forecast(
                current = Current(
                    temperature = current.optDouble("temperature_2m").takeUnless { it.isNaN() },
                    feelsLike = current.optDouble("apparent_temperature").takeUnless { it.isNaN() },
                    humidity = current.optInt("relative_humidity_2m", -1).takeIf { it >= 0 },
                    precipitation = current.optDouble("precipitation").takeUnless { it.isNaN() },
                    code = current.optInt("weather_code", -1),
                    windSpeed = current.optDouble("wind_speed_10m").takeUnless { it.isNaN() },
                    windDirection = current.optDouble("wind_direction_10m").takeUnless { it.isNaN() },
                    isDay = current.optInt("is_day", 1) == 1
                ),
                days = days,
                timezone = json.optString("timezone").takeIf { it.isNotBlank() }
            )
        }

    private suspend fun get(url: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "DroidOS/1.9.0")
            }
            try {
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status !in 200..299) {
                    throw IllegalStateException(
                        when (status) {
                            400 -> "Open-Meteo rejected the request (400)"
                            429 -> "Open-Meteo is rate limiting this device. Try again shortly."
                            in 500..599 -> "Open-Meteo had a server error ($status)."
                            else -> "Open-Meteo returned HTTP $status"
                        }
                    )
                }
                text
            } finally {
                connection.disconnect()
            }
        }
    }
}
