package dev.droidos.platform;

import android.os.RemoteCallbackList;
import android.os.RemoteException;

import java.util.SortedSet;
import java.util.TreeSet;

/**
 * System-side implementation of {@link IDroidPlatformService}.
 *
 * <p>This is the class a device image registers with ServiceManager. It owns the blocklist state
 * and multiplies privacy events to the registered shell callbacks. The shell never receives a
 * client-supplied identity: entries are keyed by UID, and the UID range check matches
 * {@code DROID_UID_MAX} in {@code platform/native/netfilter_rules.h} and the CHECK constraint in
 * {@code platform/sql/schema.sql}.
 *
 * <p>Compiled here against {@code android.jar} together with the generated AIDL stubs; see
 * {@code scripts/build-platform.py}.
 */
public final class PlatformService extends IDroidPlatformService.Stub {

    /** Version of the exposed surface; a shell refuses a service it does not understand. */
    public static final int API_VERSION = 1;

    /** Name the service is registered under with ServiceManager. */
    public static final String SERVICE_NAME = "droidos_platform";

    /** Mirrors netfilter_rules.h: the highest UID the platform will ever block. */
    public static final int MAX_APP_UID = 99999;

    /** Linux errno for an invalid argument, negated as the AIDL contract requires. */
    private static final int EINVAL = 22;

    private final SortedSet<Integer> blockedUids = new TreeSet<>();
    private final RemoteCallbackList<IPrivacyEventCallback> callbacks = new RemoteCallbackList<>();
    private final String buildFingerprint;

    public PlatformService(String buildFingerprint) {
        this.buildFingerprint = buildFingerprint == null ? "" : buildFingerprint;
    }

    @Override
    public int getApiVersion() {
        return API_VERSION;
    }

    @Override
    public String getServiceName() {
        return SERVICE_NAME;
    }

    @Override
    public String getBuildFingerprint() {
        return buildFingerprint;
    }

    @Override
    public boolean isFirewallActive() {
        synchronized (blockedUids) {
            return !blockedUids.isEmpty();
        }
    }

    @Override
    public int applyUidBlocklist(int[] uids) throws RemoteException {
        if (uids == null) {
            return -EINVAL;
        }
        // Copy first: the caller's array is only valid for the duration of the call.
        final int[] requested = uids.clone();
        // Validate the whole request before touching state, so a bad element applies nothing.
        for (int uid : requested) {
            if (uid < 0 || uid > MAX_APP_UID) {
                return -EINVAL;
            }
        }
        int applied = 0;
        synchronized (blockedUids) {
            for (int uid : requested) {
                if (blockedUids.add(uid)) {
                    applied++;
                }
            }
        }
        return applied;
    }

    @Override
    public int clearUidBlocklist() {
        synchronized (blockedUids) {
            blockedUids.clear();
        }
        return 0;
    }

    @Override
    public void registerPrivacyCallback(IPrivacyEventCallback callback) {
        if (callback != null) {
            callbacks.register(callback);
        }
    }

    @Override
    public void unregisterPrivacyCallback(IPrivacyEventCallback callback) {
        if (callback != null) {
            callbacks.unregister(callback);
        }
    }

    /**
     * Called by the platform's camera observer. The shell cannot call this: it is not part of the
     * AIDL surface, so events can only originate inside the platform.
     */
    public void onCameraStateChanged(boolean inUse, String packageName) {
        dispatch(true, inUse, packageName);
    }

    /** Called by the platform's microphone observer. */
    public void onMicrophoneStateChanged(boolean inUse, String packageName) {
        dispatch(false, inUse, packageName);
    }

    private void dispatch(boolean camera, boolean inUse, String packageName) {
        final String attributed = packageName == null ? "" : packageName;
        final int count = callbacks.beginBroadcast();
        try {
            for (int index = 0; index < count; index++) {
                IPrivacyEventCallback callback = callbacks.getBroadcastItem(index);
                try {
                    if (camera) {
                        callback.onCameraStateChanged(inUse, attributed);
                    } else {
                        callback.onMicrophoneStateChanged(inUse, attributed);
                    }
                } catch (RemoteException dead) {
                    // A dead client is dropped by the next broadcast; there is nothing to recover.
                }
            }
        } finally {
            callbacks.finishBroadcast();
        }
    }
}
