package dev.droidos.shell.security

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import dev.droidos.shell.core.AppLog
import dev.droidos.shell.platform.PlatformBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live privacy indicators built on real system signals:
 *  - camera in use  -> CameraManager reports a device camera as unavailable to others
 *  - microphone    -> AudioManager reports an active audio recording configuration
 *  - mic mute      -> AudioManager microphone mute state
 *
 * These signals are process-wide, so the indicator is truthful even when another app is
 * the one recording. Package attribution is deliberately not invented.
 */
class PrivacyMonitor(
    private val context: Context,
    private val platform: PlatformBridge? = null
) {

    data class State(
        val cameraInUse: Boolean = false,
        val microphoneInUse: Boolean = false,
        val microphoneMuted: Boolean = false,
        val activeCameras: Set<String> = emptySet(),
        /** Package the platform service attributed to the last transition, when it could. */
        val attributedPackage: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var cameraManager: CameraManager? = null
    private var audioManager: AudioManager? = null
    private var registered = false
    private val unavailableCameras = mutableSetOf<String>()

    private val cameraCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraAvailable(cameraId: String) {
            synchronized(unavailableCameras) {
                unavailableCameras.remove(cameraId)
                publish()
            }
        }

        override fun onCameraUnavailable(cameraId: String) {
            synchronized(unavailableCameras) {
                unavailableCameras.add(cameraId)
                publish()
            }
        }
    }

    private val recordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
            val active = configs.any { !it.isClientSilenced }
            _state.value = _state.value.copy(
                microphoneInUse = active,
                microphoneMuted = readMicMuted()
            )
        }
    }

    /**
     * Sensor transitions reported by the platform service, which sees the UID behind a capture.
     * This is the only path that can name a package; without the service the field stays null
     * instead of being filled with a guess.
     */
    private val platformListener = PlatformBridge.PrivacyListener { camera, inUse, packageName ->
        val state = _state.value
        val attributed = if (inUse && packageName.isNotEmpty()) packageName else null
        val cameraBusy = synchronized(unavailableCameras) { unavailableCameras.isNotEmpty() }
        _state.value = if (camera) {
            state.copy(cameraInUse = inUse || cameraBusy, attributedPackage = attributed)
        } else {
            state.copy(microphoneInUse = inUse, attributedPackage = attributed)
        }
    }

    fun start() {
        if (registered) return
        try {
            cameraManager = context.getSystemService(CameraManager::class.java)
            audioManager = context.getSystemService(AudioManager::class.java)
            cameraManager?.registerAvailabilityCallback(context.mainExecutor, cameraCallback)
            audioManager?.registerAudioRecordingCallback(
                recordingCallback,
                android.os.Handler(android.os.Looper.getMainLooper())
            )
            registered = true
            _state.value = _state.value.copy(microphoneMuted = readMicMuted())
            platform?.subscribePrivacy(platformListener)
            AppLog.i("Privacy", "Privacy monitors registered")
        } catch (e: Exception) {
            AppLog.e("Privacy", "Could not register privacy monitors", e)
        }
    }

    fun stop() {
        if (!registered) return
        runCatching { cameraManager?.unregisterAvailabilityCallback(cameraCallback) }
        runCatching { audioManager?.unregisterAudioRecordingCallback(recordingCallback) }
        platform?.unsubscribePrivacy(platformListener)
        registered = false
        synchronized(unavailableCameras) {
            unavailableCameras.clear()
            publish()
        }
    }

    /** Real microphone mute toggle; requires MODIFY_AUDIO_SETTINGS. */
    fun setMicrophoneMuted(muted: Boolean): Boolean = try {
        audioManager?.isMicrophoneMute = muted
        val applied = readMicMuted() == muted
        _state.value = _state.value.copy(microphoneMuted = readMicMuted())
        AppLog.i("Privacy", "Microphone mute=$muted applied=$applied")
        applied
    } catch (e: SecurityException) {
        AppLog.w("Privacy", "Microphone mute rejected by platform", e)
        false
    } catch (e: Exception) {
        AppLog.e("Privacy", "Microphone mute failed", e)
        false
    }

    private fun readMicMuted(): Boolean = try {
        audioManager?.isMicrophoneMute ?: false
    } catch (e: Exception) {
        false
    }

    private fun publish() {
        _state.value = _state.value.copy(
            cameraInUse = unavailableCameras.isNotEmpty(),
            activeCameras = unavailableCameras.toSet()
        )
    }
}
