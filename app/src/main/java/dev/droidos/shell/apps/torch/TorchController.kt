package dev.droidos.shell.apps.torch

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

/**
 * Drives the actual camera flash unit through CameraManager. A device without a flash, or with
 * a camera already in use by another app, is reported as such - the UI never pretends the light
 * is on when the platform refused the request.
 */
class TorchController(private val context: Context) {

    private val manager: CameraManager? =
        runCatching { context.getSystemService(CameraManager::class.java) }.getOrNull()

    data class Camera(val id: String, val hasFlash: Boolean)

    fun cameras(): List<Camera> {
        val service = manager ?: return emptyList()
        return runCatching {
            service.cameraIdList.map { id ->
                val characteristics = service.getCameraCharacteristics(id)
                val flash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                Camera(id, flash)
            }
        }.getOrDefault(emptyList())
    }

    fun flashCamera(): Camera? = cameras().firstOrNull { it.hasFlash }

    fun setTorch(cameraId: String, enabled: Boolean): Result<Unit> {
        val service = manager ?: return Result.failure(IllegalStateException("No camera service on this device"))
        return runCatching { service.setTorchMode(cameraId, enabled) }
            .recoverCatching { error ->
                // A camera the platform cannot reach is the common real-world failure here.
                throw IllegalStateException(describe(error))
            }
    }

    /** Live state changes, including ones made by other apps or the system. */
    fun observe(onChange: (cameraId: String, enabled: Boolean) -> Unit): CameraManager.TorchCallback? {
        val service = manager ?: return null
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                onChange(cameraId, enabled)
            }

            override fun onTorchModeUnavailable(cameraId: String) {
                onChange(cameraId, false)
            }
        }
        runCatching { service.registerTorchCallback(callback, null) }
            .onFailure { return null }
        return callback
    }

    fun stopObserving(callback: CameraManager.TorchCallback?) {
        val service = manager ?: return
        if (callback == null) return
        runCatching { service.unregisterTorchCallback(callback) }
    }

    private fun describe(error: Throwable): String = when (error) {
        is CameraAccessException -> "The camera service refused access (${error.reason})"
        is SecurityException -> "Camera permission denied by the platform"
        else -> error.message ?: "The flash could not be switched"
    }
}
