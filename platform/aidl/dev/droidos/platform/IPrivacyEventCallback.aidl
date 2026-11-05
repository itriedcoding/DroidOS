// Callback the platform service uses to report privacy sensor state to the shell.
// Compiled with the Android SDK aidl tool; see scripts/build-platform.py.

package dev.droidos.platform;

interface IPrivacyEventCallback {
    /**
     * Camera usage changed. packageName is the package the kernel attributes the capture to,
     * or an empty string when the platform cannot attribute it.
     */
    void onCameraStateChanged(boolean inUse, String packageName);

    /**
     * Microphone usage changed, with the same attribution contract as the camera event.
     */
    void onMicrophoneStateChanged(boolean inUse, String packageName);
}
