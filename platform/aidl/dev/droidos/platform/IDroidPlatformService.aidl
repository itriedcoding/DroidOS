// System service surface for DroidOS. The shell binds to this interface; a real device
// implementation lives in the AOSP system server and registers itself under the name returned
// by getServiceName().
//
// Compiled with the Android SDK aidl tool; see scripts/build-platform.py.

package dev.droidos.platform;

import dev.droidos.platform.IPrivacyEventCallback;

interface IDroidPlatformService {
    /** Version of this interface, so the shell can refuse an incompatible service. */
    int getApiVersion();

    /** Service name to register with ServiceManager. */
    String getServiceName();

    /** Human readable build fingerprint of the platform image. */
    String getBuildFingerprint();

    /** True while the kernel-level egress filter is installed. */
    boolean isFirewallActive();

    /**
     * Installs an egress rule dropping traffic for the given application UIDs.
     * Returns the number of rules applied, or a negative errno on failure.
     */
    int applyUidBlocklist(in int[] uids);

    /** Removes every rule installed by applyUidBlocklist. Returns 0 or a negative errno. */
    int clearUidBlocklist();

    /** Registers for camera and microphone state changes. */
    void registerPrivacyCallback(IPrivacyEventCallback callback);

    /** Unregisters a previously registered callback. */
    void unregisterPrivacyCallback(IPrivacyEventCallback callback);
}
