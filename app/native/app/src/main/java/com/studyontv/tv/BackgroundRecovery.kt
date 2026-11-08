package com.studyontv.tv

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import org.json.JSONObject

/** Local recovery only: no notification contents, account or network service is used. */
object BackgroundRecovery {
    private fun prefs(context: Context) = context.getSharedPreferences("sotv_recovery", Context.MODE_PRIVATE)
    fun unlocked(context: Context): Boolean = Build.VERSION.SDK_INT < 24 ||
        (context.getSystemService(Context.USER_SERVICE) as UserManager).isUserUnlocked
    fun canWrite(context: Context): Boolean = Build.VERSION.SDK_INT >= 23 &&
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    fun target(context: Context): String = ComponentName(context, WatchAccessibilityService::class.java).flattenToString()
    fun services(context: Context): String? = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    fun listenerAllowed(context: Context): Boolean = AccessibilityEntries.contains(
        Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners"),
        ComponentName(context, RevivalListener::class.java).flattenToString())

    fun noteAccessibilityConnected(context: Context) {
        if (unlocked(context)) prefs(context).edit().putBoolean("accessibility_authorized", true).apply()
    }

    fun enabled(context: Context): Boolean = unlocked(context) && Prefs.onboarded(context) &&
        prefs(context).getBoolean("accessibility_authorized", false) && listenerAllowed(context)

    fun pending(context: Context): Boolean = prefs(context).getBoolean("pending_rebind", false)

    fun ensureEnabled(context: Context) {
        val current = services(context)
        if (!AccessibilityEntries.contains(current, target(context))) {
            check(Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                AccessibilityEntries.append(current, target(context)))) { "Cannot restore accessibility entry" }
        }
        check(Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)) {
            "Cannot enable accessibility"
        }
    }

    // Persist before removing our entry: a process kill must not leave it removed permanently.
    fun beginRebind(context: Context) {
        check(prefs(context).edit().putBoolean("pending_rebind", true).commit()) { "Cannot persist pending rebind" }
        check(Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            AccessibilityEntries.remove(services(context), target(context)))) { "Cannot remove stalled accessibility entry" }
    }

    fun restorePending(context: Context) {
        if (!unlocked(context) || !canWrite(context) || !pending(context)) return
        // Read the current list again, including services enabled during our rebind delay.
        ensureEnabled(context)
        check(prefs(context).edit().remove("pending_rebind").commit()) { "Cannot clear pending rebind" }
    }

    fun requestBinding(context: Context) {
        if (Build.VERSION.SDK_INT >= 24 && unlocked(context) && listenerAllowed(context)) {
            runCatching { NotificationListenerService.requestRebind(ComponentName(context, RevivalListener::class.java)) }
                .onFailure { android.util.Log.w("StudyOnTVRevival", "Cannot request listener binding", it) }
        }
    }

    fun report(context: Context): JSONObject = JSONObject()
        .put("notificationAccess", listenerAllowed(context))
        .put("secureSettings", canWrite(context))
        .put("enabled", enabled(context))
        .put("listenerConnected", RevivalListener.connected)
        .put("outcome", RevivalListener.outcome)
}
