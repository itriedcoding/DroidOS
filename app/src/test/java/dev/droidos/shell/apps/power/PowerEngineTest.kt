package dev.droidos.shell.apps.power

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PowerEngineTest {

    @Test
    fun statusesAndHealthAreNamed() {
        assertEquals("Charging", PowerEngine.statusLabel(PowerEngine.STATUS_CHARGING))
        assertEquals("Discharging", PowerEngine.statusLabel(PowerEngine.STATUS_DISCHARGING))
        assertEquals("Unknown", PowerEngine.statusLabel(99))
        assertEquals("Overheating", PowerEngine.healthLabel(PowerEngine.HEALTH_OVERHEAT))
        assertEquals("Good", PowerEngine.healthLabel(PowerEngine.HEALTH_GOOD))
        assertEquals("Unknown", PowerEngine.healthLabel(0))
    }

    @Test
    fun pluggedSourcesCombine() {
        assertEquals("Unplugged", PowerEngine.plugLabel(0))
        assertEquals("AC", PowerEngine.plugLabel(PowerEngine.PLUGGED_AC))
        assertEquals("USB", PowerEngine.plugLabel(PowerEngine.PLUGGED_USB))
        assertEquals(
            "AC + Wireless",
            PowerEngine.plugLabel(PowerEngine.PLUGGED_AC or PowerEngine.PLUGGED_WIRELESS)
        )
        assertEquals("External", PowerEngine.plugLabel(16))
    }

    @Test
    fun temperatureUsesTenths() {
        assertEquals(31.5, PowerEngine.temperatureCelsius(315), 0.001)
        assertEquals("31.5 °C", PowerEngine.formatTemperature(315))
    }

    @Test
    fun chargeAndCurrentFormatting() {
        assertEquals("4.05 Ah", PowerEngine.formatCharge(4_050_000))
        assertEquals("850 mAh", PowerEngine.formatCharge(850_000))
        assertEquals("unavailable", PowerEngine.formatCharge(0))
        assertEquals("-412 mA", PowerEngine.formatCurrent(-412_000))
        assertEquals("+1500 mA", PowerEngine.formatCurrent(1_500_000))
        assertEquals("4.020 V", PowerEngine.formatVoltage(4_020))
    }

    @Test
    fun remainingTimeUsesTheCurrentMagnitude() {
        // 3.0 Ah at -600 mA is five hours.
        val hours = PowerEngine.hoursRemaining(3_000_000, -600_000)
        assertEquals(5.0, hours!!, 0.001)
        assertEquals("5.0 h", PowerEngine.formatHours(hours))
        assertEquals("30 min", PowerEngine.formatHours(0.5))
        assertEquals("--", PowerEngine.formatHours(null))
        assertNull(PowerEngine.hoursRemaining(0, -500_000))
        assertNull(PowerEngine.hoursRemaining(3_000_000, 0))
    }

    @Test
    fun wattsCombineVoltsAndAmps() {
        assertEquals(1.5, PowerEngine.powerWatts(5_000, 300_000), 0.001)
        assertEquals("1.50 W", PowerEngine.formatWatts(5_000, 300_000))
        assertEquals("--", PowerEngine.formatWatts(0, 300_000))
    }

    @Test
    fun bandsTrackSeverity() {
        assertEquals(PowerEngine.Band.LOW, PowerEngine.levelColorBand(8))
        assertEquals(PowerEngine.Band.MEDIUM, PowerEngine.levelColorBand(30))
        assertEquals(PowerEngine.Band.HEALTHY, PowerEngine.levelColorBand(80))
    }
}
