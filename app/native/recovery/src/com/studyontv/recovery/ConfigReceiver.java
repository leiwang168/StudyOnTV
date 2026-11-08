package com.studyontv.recovery;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import com.studyontv.recovery.RecoveryListener;
import com.studyontv.recovery.RecoverySettings;

public final class ConfigReceiver
extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !RecoverySettings.unlocked((Context)context)) {
            return;
        }
        SharedPreferences sharedPreferences = RecoverySettings.prefs((Context)context);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        if (intent.hasExtra("enabled")) {
            editor.putBoolean("enabled", intent.getBooleanExtra("enabled", false));
        }
        if (intent.hasExtra("keep_adb")) {
            editor.putBoolean("keep_adb", intent.getBooleanExtra("keep_adb", false));
        }
        if (!editor.commit()) {
            this.setResultCode(1);
            this.setResultData("Could not persist recovery configuration");
            return;
        }
        RecoveryListener.configurationChanged((Context)context);
        this.setResultCode(0);
        this.setResultData("enabled=" + sharedPreferences.getBoolean("enabled", false) + " keep_adb=" + sharedPreferences.getBoolean("keep_adb", false));
    }
}
