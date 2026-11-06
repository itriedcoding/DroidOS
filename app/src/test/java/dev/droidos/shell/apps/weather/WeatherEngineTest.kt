package dev.droidos.shell.apps.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherEngineTest {

    @Test
    fun wmoCodesBecomeSentences() {
        assertEquals("Clear sky", WeatherEngine.describe(0))
        assertEquals("Partly cloudy", WeatherEngine.describe(2))
        assertEquals("Rain", WeatherEngine.describe(63))
        assertEquals("Thunderstorm with hail", WeatherEngine.describe(99))
        assertEquals("Unknown conditions", WeatherEngine.describe(1234))
    }

    @Test
    fun precipitationCodesAreFlagged() {
        assertTrue(WeatherEngine.isWet(61))
        assertTrue(WeatherEngine.isWet(95))
        assertFalse(WeatherEngine.isWet(0))
        assertTrue(WeatherEngine.isSnow(75))
        assertFalse(WeatherEngine.isSnow(61))
    }

    @Test
    fun urlsCarryTheQueryAndCoordinates() {
        val geocode = WeatherEngine.geocodeUrl("New York")
        assertTrue(geocode.startsWith(WeatherEngine.GEOCODE_ENDPOINT))
        assertTrue(geocode.contains("name=New+York"))
        assertTrue(geocode.contains("format=json"))

        val forecast = WeatherEngine.forecastUrl(51.50721, -0.1275)
        assertTrue(forecast.startsWith(WeatherEngine.FORECAST_ENDPOINT))
        assertTrue(forecast.contains("latitude=51.5072"))
        assertTrue(forecast.contains("longitude=-0.1275"))
        assertTrue(forecast.contains("forecast_days=7"))
        assertTrue(forecast.contains("timezone=auto"))
    }

    @Test
    fun compassPointsWrapAround() {
        assertEquals("N", WeatherEngine.compassPoint(0.0))
        assertEquals("NE", WeatherEngine.compassPoint(45.0))
        assertEquals("E", WeatherEngine.compassPoint(90.0))
        assertEquals("S", WeatherEngine.compassPoint(180.0))
        assertEquals("W", WeatherEngine.compassPoint(-90.0))
        assertEquals("N", WeatherEngine.compassPoint(359.0))
        assertEquals("N", WeatherEngine.compassPoint(720.0))
    }

    @Test
    fun numbersAreFormattedOrReplaced() {
        assertEquals("18.5°", WeatherEngine.temperature(18.5))
        assertEquals("--", WeatherEngine.temperature(null))
        assertEquals("12 km/h NW", WeatherEngine.wind(12.0, 315.0))
        assertEquals("12 km/h", WeatherEngine.wind(12.0, null))
        assertEquals("--", WeatherEngine.wind(null, null))
        assertEquals("40%", WeatherEngine.percent(40))
        assertEquals("--", WeatherEngine.percent(null))
    }

    @Test
    fun placeLabelsSkipDuplicates() {
        assertEquals("Paris, Ile-de-France, France", WeatherEngine.placeLabel("Paris", "Ile-de-France", "France"))
        assertEquals("Paris, France", WeatherEngine.placeLabel("Paris", "Paris", "France"))
        assertEquals("Paris", WeatherEngine.placeLabel("Paris", null, null))
        assertEquals("Unknown place", WeatherEngine.placeLabel(" ", null, null))
    }

    @Test
    fun datesUseShortDayNames() {
        assertEquals("Fri 6 Nov", WeatherEngine.dayName("2026-11-06"))
        assertEquals("nonsense", WeatherEngine.dayName("nonsense"))
    }
}
