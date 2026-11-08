package com.studyontv.recovery;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.UserManager;

final class RecoverySettings {
    static final String TARGET_PACKAGE = "com.lion.studyontv";
    static final String TARGET_CLASS = "com.studyontv.tv.WatchAccessibilityService";
    static final String TARGET_COMPONENT = "com.lion.studyontv/com.studyontv.tv.WatchAccessibilityService";

    private RecoverySettings() {
    }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("recovery", 0);
    }

    static boolean unlocked(Context context) {
        return Build.VERSION.SDK_INT < 24 || ((UserManager)context.getSystemService("user")).isUserUnlocked();
    }

    static boolean canWrite(Context context) {
        return context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == 0;
    }

    static boolean enabled(Context context) {
        return RecoverySettings.unlocked((Context)context) && RecoverySettings.prefs((Context)context).getBoolean("enabled", false);
    }
}
