package com.studyontv.recovery;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;
import com.studyontv.recovery.RecoverySettings;
import com.studyontv.recovery.ServiceEntries;
import java.io.FileDescriptor;
import java.io.PrintWriter;

public final class RecoveryListener
extends NotificationListenerService {
    private static final String TAG = "StudyOnTVRecovery";
    private static final long[] DELAYS = new long[]{0L, 5000L, 10000L, 15000L, 30000L, 60000L, 5000L};
    private static RecoveryListener instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean recovering;
    private boolean destroyed;
    private boolean registered;
    private int attempt;
    private String outcome = "not started";
    private final Runnable check = () -> this.checkTarget();
    private final Runnable settingRecovery = () -> {
        if (!this.recovering) {
            this.restartChecks("StudyOnTV accessibility removed");
        }
    };
    private final Runnable reinsert = () -> {
        try {
            RecoveryListener.restorePendingRebind((Context)this);
        }
        catch (RuntimeException runtimeException) {
            this.fail("restore after rebind", (Exception)runtimeException);
        }
    };
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { restartChecks(intent.getAction()); }
    };
    private final ContentObserver adbObserver = new ContentObserver(handler) {
        @Override public void onChange(boolean selfChange) {
            try { ensureAdb(); } catch (RuntimeException e) { fail("keep ADB", e); }
        }
    };
    private final ContentObserver accessibilityObserver = new ContentObserver(handler) {
        @Override public void onChange(boolean selfChange) {
            if (destroyed || !RecoverySettings.enabled(RecoveryListener.this) || !RecoverySettings.canWrite(RecoveryListener.this) || RecoverySettings.prefs(RecoveryListener.this).getBoolean("pending_rebind", false)) return;
            if (!ServiceEntries.contains(readServices(), RecoverySettings.TARGET_COMPONENT)) {
                handler.removeCallbacks(settingRecovery);
                handler.postDelayed(settingRecovery, 2000L);
            }
        }
    };

    public void onCreate() {
        super.onCreate();
        instance = this;
        IntentFilter intentFilter = new IntentFilter("android.intent.action.SCREEN_ON");
        intentFilter.addAction("android.intent.action.USER_UNLOCKED");
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                this.registerReceiver(this.screen, intentFilter, 4);
            } else {
                this.registerReceiver(this.screen, intentFilter);
            }
            this.registered = true;
            this.getContentResolver().registerContentObserver(Settings.Global.getUriFor((String)"adb_enabled"), false, this.adbObserver);
            this.getContentResolver().registerContentObserver(Settings.Secure.getUriFor((String)"enabled_accessibility_services"), false, this.accessibilityObserver);
        }
        catch (RuntimeException runtimeException) {
            this.fail("register system observers", (Exception)runtimeException);
        }
        this.restartChecks("service created");
    }

    public void onListenerConnected() {
        super.onListenerConnected();
        Log.i((String)"StudyOnTVRecovery", (String)"notification listener connected");
        this.restartChecks("listener connected");
    }

    static void configurationChanged(Context context) {
        if (instance != null) {
            instance.restartChecks("configuration changed");
        } else if (RecoverySettings.enabled((Context)context) && Build.VERSION.SDK_INT >= 24) {
            try {
                RecoveryListener.requestRebind((ComponentName)new ComponentName(context, RecoveryListener.class));
            }
            catch (RuntimeException runtimeException) {
                Log.w((String)"StudyOnTVRecovery", (String)"Listener rebind request failed", (Throwable)runtimeException);
            }
        }
    }

    private void restartChecks(String string) {
        if (this.destroyed) {
            return;
        }
        this.handler.removeCallbacks(this.check);
        this.attempt = 0;
        this.recovering = true;
        this.outcome = string;
        this.handler.post(this.check);
    }

    private void checkTarget() {
        if (this.destroyed || !this.recovering) {
            return;
        }
        try {
            if (!RecoverySettings.unlocked((Context)this)) {
                this.outcome = "waiting for user unlock";
            } else {
                if (!RecoverySettings.enabled((Context)this)) {
                    RecoveryListener.restorePendingRebind((Context)this);
                    this.finish("disabled");
                    return;
                }
                if (!RecoverySettings.canWrite((Context)this)) {
                    this.finish("WRITE_SECURE_SETTINGS not granted");
                    return;
                }
                this.ensureAdb();
                if (!((PowerManager)this.getSystemService("power")).isInteractive()) {
                    this.outcome = "waiting for screen on";
                } else {
                    this.getPackageManager().getServiceInfo(new ComponentName("com.lion.studyontv", "com.studyontv.tv.WatchAccessibilityService"), 0);
                    RecoveryListener.restorePendingRebind((Context)this);
                    if (this.targetBound()) {
                        this.finish("StudyOnTV accessibility bound");
                        return;
                    }
                    String string = this.readServices();
                    if (!ServiceEntries.contains((String)string, (String)"com.lion.studyontv/com.studyontv.tv.WatchAccessibilityService")) {
                        RecoveryListener.enableTarget((Context)this);
                        this.outcome = "restored StudyOnTV accessibility entry";
                    } else if (this.attempt >= 2 && this.attempt + 1 < DELAYS.length && !RecoverySettings.prefs((Context)this).getBoolean("pending_rebind", false)) {
                        RecoverySettings.prefs((Context)this).edit().putBoolean("pending_rebind", true).commit();
                        if (!Settings.Secure.putString((ContentResolver)this.getContentResolver(), (String)"enabled_accessibility_services", (String)ServiceEntries.remove((String)string, (String)"com.lion.studyontv/com.studyontv.tv.WatchAccessibilityService"))) {
                            throw new IllegalStateException("Could not remove stalled StudyOnTV entry");
                        }
                        this.handler.postDelayed(this.reinsert, 1500L);
                        this.outcome = "requested StudyOnTV accessibility rebind";
                    } else {
                        this.outcome = "waiting for StudyOnTV accessibility binding";
                    }
                }
            }
        }
        catch (Exception exception) {
            this.fail("recovery attempt", exception);
        }
        Log.i((String)"StudyOnTVRecovery", (String)("attempt=" + this.attempt + " " + this.outcome));
        ++this.attempt;
        if (this.attempt < DELAYS.length) {
            this.handler.postDelayed(this.check, DELAYS[this.attempt]);
        } else {
            this.finish("recovery window exhausted: " + this.outcome);
        }
    }

    private String readServices() {
        return Settings.Secure.getString((ContentResolver)this.getContentResolver(), (String)"enabled_accessibility_services");
    }

    private static void enableTarget(Context context) {
        String string = Settings.Secure.getString((ContentResolver)context.getContentResolver(), (String)"enabled_accessibility_services");
        if (!ServiceEntries.contains((String)string, (String)"com.lion.studyontv/com.studyontv.tv.WatchAccessibilityService") && !Settings.Secure.putString((ContentResolver)context.getContentResolver(), (String)"enabled_accessibility_services", (String)ServiceEntries.append((String)string, (String)"com.lion.studyontv/com.studyontv.tv.WatchAccessibilityService"))) {
            throw new IllegalStateException("Could not enable StudyOnTV accessibility");
        }
        if (Settings.Secure.getInt((ContentResolver)context.getContentResolver(), (String)"accessibility_enabled", (int)0) != 1 && !Settings.Secure.putInt((ContentResolver)context.getContentResolver(), (String)"accessibility_enabled", (int)1)) {
            throw new IllegalStateException("Could not enable accessibility");
        }
    }

    private static void restorePendingRebind(Context context) {
        if (!RecoverySettings.unlocked((Context)context) || !RecoverySettings.canWrite((Context)context)) {
            return;
        }
        if (!RecoverySettings.prefs((Context)context).getBoolean("pending_rebind", false)) {
            return;
        }
        RecoveryListener.enableTarget((Context)context);
        RecoverySettings.prefs((Context)context).edit().remove("pending_rebind").commit();
    }

    private boolean targetBound() {
        AccessibilityManager accessibilityManager = (AccessibilityManager)this.getSystemService("accessibility");
        for (AccessibilityServiceInfo accessibilityServiceInfo : accessibilityManager.getEnabledAccessibilityServiceList(-1)) {
            if (accessibilityServiceInfo.getResolveInfo() == null || accessibilityServiceInfo.getResolveInfo().serviceInfo == null || !"com.lion.studyontv".equals(accessibilityServiceInfo.getResolveInfo().serviceInfo.packageName) || !"com.studyontv.tv.WatchAccessibilityService".equals(accessibilityServiceInfo.getResolveInfo().serviceInfo.name)) continue;
            return true;
        }
        return false;
    }

    private void ensureAdb() {
        if (!(RecoverySettings.enabled((Context)this) && RecoverySettings.canWrite((Context)this) && RecoverySettings.prefs((Context)this).getBoolean("keep_adb", false))) {
            return;
        }
        if (Settings.Global.getInt((ContentResolver)this.getContentResolver(), (String)"adb_enabled", (int)0) == 0) {
            if (!Settings.Global.putInt((ContentResolver)this.getContentResolver(), (String)"adb_enabled", (int)1)) {
                throw new IllegalStateException("Could not restore ADB debugging");
            }
            Log.i((String)"StudyOnTVRecovery", (String)"restored ADB debugging (explicit keep_adb option)");
        }
    }

    private void finish(String string) {
        this.recovering = false;
        this.outcome = string;
        this.handler.removeCallbacks(this.check);
        Log.i((String)"StudyOnTVRecovery", (String)string);
    }

    private void fail(String string, Exception exception) {
        this.outcome = string + ": " + exception.getClass().getSimpleName() + ": " + exception.getMessage();
        Log.w((String)"StudyOnTVRecovery", (String)this.outcome, (Throwable)exception);
    }

    protected void dump(FileDescriptor fileDescriptor, PrintWriter printWriter, String[] stringArray) {
        printWriter.println("StudyOnTV recovery: active=" + this.recovering + " attempts=" + this.attempt + " outcome=" + this.outcome);
        printWriter.println("unlocked=" + RecoverySettings.unlocked((Context)this) + " enabled=" + RecoverySettings.enabled((Context)this) + " secureSettings=" + RecoverySettings.canWrite((Context)this));
        if (RecoverySettings.unlocked((Context)this)) {
            printWriter.println("targetBound=" + this.targetBound() + " keepAdb=" + RecoverySettings.prefs((Context)this).getBoolean("keep_adb", false));
        }
    }

    public void onDestroy() {
        this.destroyed = true;
        this.recovering = false;
        try {
            RecoveryListener.restorePendingRebind((Context)this);
        }
        catch (RuntimeException runtimeException) {
            this.fail("restore on destroy", (Exception)runtimeException);
        }
        this.handler.removeCallbacksAndMessages(null);
        this.getContentResolver().unregisterContentObserver(this.adbObserver);
        this.getContentResolver().unregisterContentObserver(this.accessibilityObserver);
        if (this.registered) {
            this.unregisterReceiver(this.screen);
        }
        if (instance == this) {
            instance = null;
        }
        super.onDestroy();
    }

}
