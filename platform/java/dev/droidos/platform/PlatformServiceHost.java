package dev.droidos.platform;

import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/**
 * Bound-service entry point for {@link PlatformService} in a device image.
 *
 * <p>The manifest in {@code platform/android/AndroidManifest.xml} protects this service with a
 * signature-level permission, so the platform itself refuses callers that are not signed with the
 * image key; there is no runtime check to get wrong here. The shell binds with an explicit
 * component name and treats a refused or absent bind as "no platform service", which is what
 * happens on a retail phone.
 */
public final class PlatformServiceHost extends Service {

    private PlatformService service;

    @Override
    public void onCreate() {
        super.onCreate();
        service = new PlatformService(Build.FINGERPRINT == null ? "" : Build.FINGERPRINT);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // A null binder is a valid answer: the manifest permission is what decides access.
        return service;
    }

    @Override
    public void onDestroy() {
        service = null;
        super.onDestroy();
    }
}
