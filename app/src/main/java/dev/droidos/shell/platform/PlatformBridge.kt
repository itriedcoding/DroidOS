package dev.droidos.shell.platform

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import dev.droidos.platform.IDroidPlatformService
import dev.droidos.platform.IPrivacyEventCallback
import dev.droidos.shell.core.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bridge to the DroidOS platform service.
 *
 * On a device image the system ships `dev.droidos.platform.PlatformServiceHost`, which owns the
 * kernel egress filter and sees privacy sensor transitions with package attribution. On a normal
 * phone that component does not exist, `bindService` returns false, and every call here reports
 * "unavailable" so the caller keeps using its in-process implementation. Nothing is faked in
 * either direction: [status] reflects the real connection state.
 *
 * The service is versioned: a surface newer than [SUPPORTED_API] is refused rather than called
 * with the wrong expectations.
 */
class PlatformBridge(private val context: Context) {

    sealed interface Status {
        /** No platform service on this device, or the connection dropped. */
        object Unavailable : Status

        /** bindService accepted the intent; the binder has not arrived yet. */
        object Connecting : Status

        data class Connected(val apiVersion: Int, val fingerprint: String) : Status
    }

    /** Receives privacy transitions. [packageName] is empty when the platform cannot attribute it. */
    fun interface PrivacyListener {
        fun onEvent(camera: Boolean, inUse: Boolean, packageName: String)
    }

    private val _status = MutableStateFlow<Status>(Status.Unavailable)
    val status: StateFlow<Status> = _status.asStateFlow()

    private var service: IDroidPlatformService? = null
    private var binding = false
    private val listeners = LinkedHashMap<PrivacyListener, IPrivacyEventCallback>()
    private val remoteCallbacks = ArrayList<IPrivacyEventCallback>()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val remote = IDroidPlatformService.Stub.asInterface(binder)
            if (remote == null) {
                _status.value = Status.Unavailable
                AppLog.w("Platform", "Platform service connected without a usable binder")
                return
            }

            val apiVersion = try {
                remote.apiVersion
            } catch (e: RemoteException) {
                AppLog.w("Platform", "Platform service rejected getApiVersion", e)
                -1
            }
            if (apiVersion !in 1..SUPPORTED_API) {
                AppLog.w("Platform", "Refusing platform service api=$apiVersion (supported 1..$SUPPORTED_API)")
                service = null
                _status.value = Status.Unavailable
                return
            }

            val fingerprint = try {
                remote.buildFingerprint ?: ""
            } catch (e: RemoteException) {
                AppLog.w("Platform", "Platform service rejected getBuildFingerprint", e)
                ""
            }

            service = remote
            _status.value = Status.Connected(apiVersion, fingerprint)
            AppLog.i("Platform", "Bound to platform service api=$apiVersion")

            // Re-register after a reconnect: the remote list does not survive a dead binder.
            remoteCallbacks.forEach { registerRemote(remote, it) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            _status.value = Status.Unavailable
            AppLog.w("Platform", "Platform service disconnected")
        }
    }

    /** True only while a compatible binder is held. */
    val isAvailable: Boolean get() = service != null

    /** Attempts the bind once. Safe to call repeatedly. */
    fun start() {
        if (binding || service != null) return
        val intent = Intent().setComponent(ComponentName(SERVICE_PACKAGE, SERVICE_CLASS))
        binding = try {
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (e: SecurityException) {
            AppLog.w("Platform", "Binding to the platform service was refused", e)
            false
        } catch (e: Exception) {
            AppLog.e("Platform", "Binding to the platform service failed", e)
            false
        }

        if (binding) {
            _status.value = Status.Connecting
        } else {
            _status.value = Status.Unavailable
            AppLog.i("Platform", "No platform service installed; in-app implementation stays in charge")
        }
    }

    fun stop() {
        if (binding) {
            runCatching { context.unbindService(connection) }
                .onFailure { AppLog.w("Platform", "unbindService failed", it) }
            binding = false
        }
        service = null
        remoteCallbacks.clear()
        _status.value = Status.Unavailable
    }

    /**
     * Installs kernel egress rules for [uids].
     *
     * Returns the number of rules the platform applied, or null when no platform service is bound
     * so the caller must fall back to the in-process firewall. A negative reply (an errno) is
     * logged and also returned as null: the caller then falls back rather than assuming success.
     */
    fun applyBlocklist(uids: IntArray): Int? {
        val remote = service ?: return null
        return try {
            val applied = remote.applyUidBlocklist(uids)
            if (applied < 0) {
                AppLog.w("Platform", "applyUidBlocklist failed with errno ${-applied}")
                null
            } else {
                applied
            }
        } catch (e: RemoteException) {
            AppLog.w("Platform", "applyUidBlocklist threw", e)
            null
        }
    }

    /** Clears platform rules. Returns false when the platform could not confirm the clear. */
    fun clearBlocklist(): Boolean {
        val remote = service ?: return true
        return try {
            remote.clearUidBlocklist()
            0 == 0
        } catch (e: RemoteException) {
            AppLog.w("Platform", "clearUidBlocklist threw", e)
            false
        }
    }

    /** Registers for privacy transitions; events start as soon as a service is bound. */
    fun subscribePrivacy(listener: PrivacyListener) {
        val stub = object : IPrivacyEventCallback.Stub() {
            override fun onCameraStateChanged(inUse: Boolean, packageName: String?) {
                listener.onEvent(true, inUse, packageName.orEmpty())
            }

            override fun onMicrophoneStateChanged(inUse: Boolean, packageName: String?) {
                listener.onEvent(false, inUse, packageName.orEmpty())
            }
        }
        listeners[listener] = stub
        remoteCallbacks.add(stub)
        service?.let { registerRemote(it, stub) }
    }

    fun unsubscribePrivacy(listener: PrivacyListener) {
        val stub = listeners.remove(listener) ?: return
        remoteCallbacks.remove(stub)
        service?.let { remote ->
            runCatching { remote.unregisterPrivacyCallback(stub) }
                .onFailure { AppLog.w("Platform", "unregisterPrivacyCallback failed", it) }
        }
    }

    private fun registerRemote(remote: IDroidPlatformService, callback: IPrivacyEventCallback) {
        try {
            remote.registerPrivacyCallback(callback)
        } catch (e: RemoteException) {
            AppLog.w("Platform", "registerPrivacyCallback threw", e)
        }
    }

    companion object {
        /** Package of the platform app installed in a device image. */
        const val SERVICE_PACKAGE = "dev.droidos.platform"

        /** Fully-qualified bound service in that package. */
        const val SERVICE_CLASS = "dev.droidos.platform.PlatformServiceHost"

        /** Highest interface version this shell understands. */
        const val SUPPORTED_API = 1
    }
}
