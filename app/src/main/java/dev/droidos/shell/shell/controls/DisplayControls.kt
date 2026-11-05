package dev.droidos.shell.shell.controls

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import dev.droidos.shell.core.AppLog

/**
 * Rotation and brightness live in Settings.System, which requires the WRITE_SETTINGS
 * special access. The screen checks [canWrite] and offers the official grant flow instead
 * of silently failing.
 */
class DisplayControls(private val context: Context) {

    fun canWrite(): Boolean = Settings.System.canWrite(context)

    fun writeAccessIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_WRITE_SETTINGS,
        Uri.parse("package:${context.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    // ---------------------------------------------------------------- rotation
    fun isAutoRotateEnabled(): Boolean = try {
        Settings.System.getInt(
            context.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            1
        ) == 1
    } catch (e: Exception) {
        AppLog.w("DisplayControls", "Could not read rotation setting", e)
        true
    }

    fun setRotationLocked(locked: Boolean): ControlResult {
        if (!canWrite()) {
            return ControlResult(false, "Grant modify-system-settings access to lock rotation", writeAccessIntent())
        }
        return try {
            val applied = Settings.System.putInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                if (locked) 0 else 1
            )
            if (applied) ControlResult(true)
            else ControlResult(false, "The system rejected the rotation change")
        } catch (e: Exception) {
            AppLog.e("DisplayControls", "Could not change rotation", e)
            ControlResult(false, e.message ?: "Rotation could not be changed")
        }
    }

    // -------------------------------------------------------------- brightness
    fun brightness(): Int = try {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
    } catch (e: Exception) {
        128
    }

    fun setBrightness(value: Int): ControlResult {
        if (!canWrite()) {
            return ControlResult(false, "Grant modify-system-settings access to change brightness", writeAccessIntent())
        }
        val clamped = value.coerceIn(1, 255)
        return try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            val applied = Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                clamped
            )
            if (applied) ControlResult(true) else ControlResult(false, "The system rejected the brightness change")
        } catch (e: Exception) {
            AppLog.e("DisplayControls", "Could not change brightness", e)
            ControlResult(false, e.message ?: "Brightness could not be changed")
        }
    }
}
