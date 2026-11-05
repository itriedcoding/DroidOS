package dev.droidos.shell.shell

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.droidos.shell.core.AppLog
import dev.droidos.shell.core.ServiceLocator
import dev.droidos.shell.data.model.AppSettings
import dev.droidos.shell.security.PinManager
import dev.droidos.shell.security.PinValidation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShellViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = ServiceLocator.settings

    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppSettings()
    )

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private val _unlockError = MutableStateFlow<String?>(null)
    val unlockError: StateFlow<String?> = _unlockError.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** False until persisted preferences have actually been read, so the shell never
     *  flashes the home screen before a configured lock is applied. */
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        viewModelScope.launch {
            settings.first()
            _ready.value = true
            ensureLockState()
        }
    }

    /** Called on first composition so an unlocked session survives recomposition only. */
    fun ensureLockState() {
        val current = settings.value
        if (!current.lockEnabled) _unlocked.value = true
    }

    fun submitPin(pin: String) {
        viewModelScope.launch {
            val current = settingsRepository.current()
            val saltHex = current.pinSalt
            val hashHex = current.pinHash
            if (saltHex == null || hashHex == null) {
                _unlockError.value = "No PIN configured"
                return@launch
            }

            val now = System.currentTimeMillis()
            if (current.lockoutUntil > now) {
                val seconds = (current.lockoutUntil - now) / 1000 + 1
                _unlockError.value = "Too many attempts. Try again in ${seconds}s"
                return@launch
            }

            val salt = PinManager.decode(saltHex)
            val expected = PinManager.decode(hashHex)
            if (salt == null || expected == null) {
                _unlockError.value = "Stored PIN is corrupt. Reset it from Settings."
                AppLog.e("ShellViewModel", "PIN material is not decodable")
                return@launch
            }

            val ok = try {
                PinManager.verify(pin.toCharArray(), salt, expected)
            } catch (e: Exception) {
                AppLog.e("ShellViewModel", "PIN verification failed", e)
                false
            }

            if (ok) {
                _unlockError.value = null
                _unlocked.value = true
                settingsRepository.setLockout(0, 0L)
                AppLog.i("ShellViewModel", "Unlocked with PIN")
            } else {
                val failed = current.failedUnlocks + 1
                val lockout = PinManager.lockoutDurationMillis(failed)
                settingsRepository.setLockout(failed, if (lockout > 0) now + lockout else 0L)
                _unlockError.value = when {
                    lockout > 0 -> "Locked for ${lockout / 1000}s after $failed failed attempts"
                    else -> "Incorrect PIN"
                }
                AppLog.w("ShellViewModel", "Failed unlock attempt $failed")
            }
        }
    }

    fun onBiometricSuccess() {
        _unlocked.value = true
        _unlockError.value = null
        AppLog.i("ShellViewModel", "Unlocked with biometrics")
    }

    fun lockNow() {
        if (settings.value.lockEnabled) {
            _unlocked.value = false
            _unlockError.value = null
        }
    }

    /** Returns the validation failure, or null when the PIN was stored. */
    fun configureLock(pin: String, enableBiometric: Boolean, onDone: (PinValidation?) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            val validation = PinManager.validate(pin)
            if (validation != PinValidation.Valid) {
                _busy.value = false
                onDone(validation)
                return@launch
            }
            try {
                val salt = PinManager.newSalt()
                val hash = PinManager.hash(pin.toCharArray(), salt)
                settingsRepository.setLock(PinManager.encode(salt), PinManager.encode(hash))
                settingsRepository.setBiometric(enableBiometric)
                _unlocked.value = true
                AppLog.i("ShellViewModel", "Lock configured (biometric=$enableBiometric)")
                onDone(null)
            } catch (e: Exception) {
                AppLog.e("ShellViewModel", "Could not store PIN", e)
                onDone(PinValidation.NotNumeric)
            } finally {
                _busy.value = false
            }
        }
    }

    fun disableLock() {
        viewModelScope.launch {
            settingsRepository.setLock(null, null)
            settingsRepository.setBiometric(false)
            _unlocked.value = true
            AppLog.i("ShellViewModel", "Lock disabled")
        }
    }

    fun clearUnlockError() {
        _unlockError.value = null
    }
}
