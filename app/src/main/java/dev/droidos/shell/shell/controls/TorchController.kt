package dev.droidos.shell.shell.controls

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import dev.droidos.shell.core.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Real torch control through CameraManager, with live state from the system callback. */
class TorchController(private val context: Context) {

    private val cameraManager: CameraManager? = context.getSystemService(CameraManager::class.java)
    private val _isOn = MutableStateFlow(false)
    val isOn: StateFlow<Boolean> = _isOn.asStateFlow()

    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> = _available.asStateFlow()

    private var flashCameraId: String? = null
    private var observing = false

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == flashCameraId) _isOn.value = enabled
        }

        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId == flashCameraId) _isOn.value = false
        }
    }

    fun startObserving() {
        if (observing) return
        val manager = cameraManager ?: return
        try {
            flashCameraId = manager.cameraIdList.firstOrNull { id ->
                val characteristics = manager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            _available.value = flashCameraId != null
            manager.registerTorchCallback(context.mainExecutor, callback)
            observing = true
        } catch (e: Exception) {
            AppLog.e("Torch", "Could not register torch callback", e)
            _available.value = false
        }
    }

    fun stopObserving() {
        if (!observing) return
        runCatching { cameraManager?.unregisterTorchCallback(callback) }
        observing = false
    }

    fun setTorch(enabled: Boolean): ControlResult {
        val manager = cameraManager ?: return ControlResult(false, "Camera service unavailable")
        val cameraId = flashCameraId ?: return ControlResult(false, "No camera flash on this device")
        return try {
            manager.setTorchMode(cameraId, enabled)
            ControlResult(true)
        } catch (e: Exception) {
            AppLog.e("Torch", "Could not set torch mode", e)
            ControlResult(false, e.message ?: "Flashlight is busy")
        }
    }

    fun toggle(): ControlResult = setTorch(!_isOn.value)
}
