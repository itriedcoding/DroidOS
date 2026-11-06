package dev.droidos.shell.apps.weather

import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** WMO code wording, URL building and unit formatting for the Open-Meteo client. */
object WeatherEngine {

    const val GEOCODE_ENDPOINT = "https://geocoding-api.open-meteo.com/v1/search"
    const val FORECAST_ENDPOINT = "https://api.open-meteo.com/v1/forecast"

    private val DAILY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)

    fun describe(code: Int): String = when (code) {
        0 -> "Clear sky"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61, 63, 65 -> "Rain"
        66, 67 -> "Freezing rain"
        71, 73, 75 -> "Snow"
        77 -> "Snow grains"
        80, 81, 82 -> "Rain showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm with hail"
        else -> "Unknown conditions"
    }

    fun isWet(code: Int): Boolean = code in setOf(
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99
    )

    fun isSnow(code: Int): Boolean = code in setOf(71, 73, 75, 77, 85, 86)

    fun geocodeUrl(query: String, count: Int = 6): String =
        "$GEOCODE_ENDPOINT?name=${encode(query.trim())}&count=${count.coerceIn(1, 20)}" +
            "&language=en&format=json"

    fun forecastUrl(latitude: Double, longitude: Double, days: Int = 7): String = buildString {
        append(FORECAST_ENDPOINT)
        append("?latitude=").append(String.format(Locale.US, "%.4f", latitude))
        append("&longitude=").append(String.format(Locale.US, "%.4f", longitude))
        append(
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day," +
                "precipitation,weather_code,wind_speed_10m,wind_direction_10m"
        )
        append("&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max")
        append("&timezone=auto")
        append("&forecast_days=").append(days.coerceIn(1, 16))
    }

    fun compassPoint(degrees: Double): String {
        val points = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val normalized = ((degrees % 360.0) + 360.0) % 360.0
        return points[(normalized / 45.0).roundToInt() % 8]
    }

    fun temperature(value: Double?): String =
        value?.let { String.format(Locale.US, "%.1f°", it) } ?: "--"

    fun wind(speedKmh: Double?, degrees: Double?): String {
        if (speedKmh == null) return "--"
        val direction = degrees?.let { " " + compassPoint(it) } ?: ""
        return String.format(Locale.US, "%.0f km/h", speedKmh) + direction
    }

    fun percent(value: Int?): String = value?.let { "$it%" } ?: "--"

    fun placeLabel(name: String, admin1: String?, country: String?): String {
        val parts = listOfNotNull(
            name.trim().takeIf { it.isNotEmpty() },
            admin1?.trim()?.takeIf { it.isNotEmpty() && it != name },
            country?.trim()?.takeIf { it.isNotEmpty() }
        )
        return parts.joinToString(", ").ifEmpty { "Unknown place" }
    }

    fun dayName(isoDate: String): String = runCatching {
        LocalDate.parse(isoDate).format(DAILY)
    }.getOrDefault(isoDate)

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
