package dev.droidos.shell.apps.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Reads whatever sensors this device actually reports. Nothing is invented: if a sensor is absent
 * it is not listed, and if the system refuses a listener the flow fails with that reason.
 */
class SensorRepository(context: Context) {

    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    fun available(): List<Sensor> {
        val list = manager?.getSensorList(Sensor.TYPE_ALL).orEmpty()
        return list.sortedWith(compareBy({ SensorFormat.label(it.type) }, { it.name }))
    }

    fun stream(sensor: Sensor): Flow<FloatArray> = callbackFlow {
        val currentManager = manager
        if (currentManager == null) {
            close(IllegalStateException("This device has no SensorManager"))
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == sensor.type) {
                    trySend(event.values.copyOf())
                }
            }

            override fun onAccuracyChanged(changed: Sensor?, accuracy: Int) = Unit
        }

        val registered = try {
            currentManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        } catch (e: SecurityException) {
            false
        }

        if (!registered) {
            close(
                IllegalStateException(
                    "The system refused a listener for ${sensor.name}. Some sensors need a permission " +
                        "this app does not hold."
                )
            )
        }

        awaitClose { runCatching { currentManager.unregisterListener(listener) } }
    }
}
