package dev.droidos.shell.core

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DroidOsApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)

        // Tamper detection runs once per launch; a changed signing identity is logged
        // loudly and surfaced on the Settings > Security screen.
        scope.launch {
            when (val result = ServiceLocator.integrity.verify()) {
                is dev.droidos.shell.security.IntegrityVerifier.Result.Trusted ->
                    AppLog.i("DroidOsApp", "Integrity trusted (first pin: ${result.firstPin})")
                is dev.droidos.shell.security.IntegrityVerifier.Result.Changed ->
                    AppLog.e("DroidOsApp", "Signing identity mismatch: ${result.expected} != ${result.actual}")
                is dev.droidos.shell.security.IntegrityVerifier.Result.Unavailable ->
                    AppLog.w("DroidOsApp", "Integrity unavailable: ${result.reason}")
            }
        }
    }
}
