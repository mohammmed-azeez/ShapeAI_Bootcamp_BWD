package com.azeez.reelblock;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

/** Device admin: Android will not uninstall ReelBlock until this is turned off. */
public class AdminReceiver extends DeviceAdminReceiver {
    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        return "Turning this off lets ReelBlock be uninstalled. Are you sure?";
    }
}
