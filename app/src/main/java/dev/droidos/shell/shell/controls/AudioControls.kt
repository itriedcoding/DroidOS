package dev.droidos.shell.shell.controls

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import dev.droidos.shell.core.AppLog
import kotlin.math.roundToInt

/**
 * Conversions between a stream's step index and a 0..1 fraction. Deliberately free of
 * Android types so the arithmetic can be unit-tested on the JVM.
 */
object VolumeScale {

    fun percent(index: Int, max: Int): Float {
        if (max <= 0) return 0f
        return (index.toFloat() / max.toFloat()).coerceIn(0f, 1f)
    }

    fun index(percent: Float, max: Int): Int {
        if (max <= 0) return 0
        return (percent.coerceIn(0f, 1f) * max).roundToInt().coerceIn(0, max)
    }

    fun label(index: Int, max: Int): String = "${(percent(index, max) * 100).roundToInt()}%"
}

/**
 * Real per-stream volume control. Changes go through [AudioManager.setStreamVolume], so the
 * hardware mixer really moves; when the platform refuses (for example ring volume under Do
 * Not Disturb) the caller is told why and handed the system sound panel.
 */
class AudioControls(private val context: Context) {

    private val audio: AudioManager? = context.getSystemService(AudioManager::class.java)

    fun isAvailable(): Boolean = audio != null

    fun maxVolume(stream: Int): Int = try {
        audio?.getStreamMaxVolume(stream) ?: 0
    } catch (e: Exception) {
        AppLog.w("AudioControls", "Could not read max volume for stream $stream", e)
        0
    }

    fun currentVolume(stream: Int): Int = try {
        audio?.getStreamVolume(stream) ?: 0
    } catch (e: Exception) {
        AppLog.w("AudioControls", "Could not read volume for stream $stream", e)
        0
    }

    fun isMuted(stream: Int): Boolean = try {
        audio?.isStreamMute(stream) ?: false
    } catch (e: Exception) {
        false
    }

    fun setVolume(stream: Int, fraction: Float): ControlResult {
        val manager = audio ?: return ControlResult(false, "This device exposes no audio service")
        val max = maxVolume(stream)
        if (max <= 0) {
            return ControlResult(false, "That audio stream has no adjustable volume on this device")
        }
        val index = VolumeScale.index(fraction, max)
        return try {
            manager.setStreamVolume(stream, index, 0)
            ControlResult(true)
        } catch (e: SecurityException) {
            AppLog.w("AudioControls", "Volume change refused for stream $stream", e)
            ControlResult(
                applied = false,
                message = "Android refused the volume change - Do Not Disturb or an audio policy is blocking it",
                needsUserAction = soundSettingsIntent()
            )
        } catch (e: Exception) {
            AppLog.e("AudioControls", "Could not set volume for stream $stream", e)
            ControlResult(false, e.message ?: "Volume could not be changed")
        }
    }

    fun setMuted(stream: Int, muted: Boolean): ControlResult {
        val manager = audio ?: return ControlResult(false, "This device exposes no audio service")
        return try {
            manager.adjustStreamVolume(
                stream,
                if (muted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
                0
            )
            ControlResult(true)
        } catch (e: SecurityException) {
            AppLog.w("AudioControls", "Mute change refused for stream $stream", e)
            ControlResult(false, "Android refused the mute change", soundSettingsIntent())
        } catch (e: Exception) {
            AppLog.e("AudioControls", "Could not change mute for stream $stream", e)
            ControlResult(false, e.message ?: "Mute could not be changed")
        }
    }

    fun soundSettingsIntent(): Intent =
        Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
