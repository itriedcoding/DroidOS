package dev.droidos.shell.apps.sensors

import android.hardware.Sensor
import org.junit.Assert.assertEquals
import org.junit.Test

class SensorFormatTest {

    @Test
    fun labelsCoverKnownTypesAndFallBackToTheNumber() {
        assertEquals("Accelerometer", SensorFormat.label(Sensor.TYPE_ACCELEROMETER))
        assertEquals("Gyroscope", SensorFormat.label(Sensor.TYPE_GYROSCOPE))
        assertEquals("Light", SensorFormat.label(Sensor.TYPE_LIGHT))
        assertEquals("Sensor 4242", SensorFormat.label(4242))
    }

    @Test
    fun unitsMatchTheQuantity() {
        assertEquals("m/s²", SensorFormat.unit(Sensor.TYPE_ACCELEROMETER))
        assertEquals("µT", SensorFormat.unit(Sensor.TYPE_MAGNETIC_FIELD))
        assertEquals("rad/s", SensorFormat.unit(Sensor.TYPE_GYROSCOPE))
        assertEquals("lx", SensorFormat.unit(Sensor.TYPE_LIGHT))
        assertEquals("hPa", SensorFormat.unit(Sensor.TYPE_PRESSURE))
        assertEquals("steps", SensorFormat.unit(Sensor.TYPE_STEP_COUNTER))
        assertEquals("", SensorFormat.unit(4242))
    }

    @Test
    fun valuesKeepThreeDecimalsAndStepsStayIntegers() {
        assertEquals(
            listOf("0.000", "-9.807", "1.250"),
            SensorFormat.format(floatArrayOf(0f, -9.807f, 1.25f), Sensor.TYPE_ACCELEROMETER)
        )
        assertEquals(listOf("1234"), SensorFormat.format(floatArrayOf(1234.4f), Sensor.TYPE_STEP_COUNTER))
    }

    @Test
    fun describeAppendsTheUnitOncePerValue() {
        assertEquals(
            "0.100 m/s²  9.800 m/s²",
            SensorFormat.describe(floatArrayOf(0.1f, 9.8f), Sensor.TYPE_ACCELEROMETER)
        )
        assertEquals("42.500", SensorFormat.describe(floatArrayOf(42.5f), 4242))
    }
}
