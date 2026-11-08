package com.studyontv.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
import java.io.FileDescriptor
import java.io.PrintWriter

/**
 * Inspired by Aerial's RevivalListener/GuardSetup (MIT; see assets/notices/aerial-tv.txt).
 * The firmware can rebind a notification listener after killing the app. Restore our own
 * accessibility service from that callback, then let it start the existing management service.
 * No notification callbacks are implemented. Recovery is bounded, not a permanent polling loop.
 */
class RevivalListener : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())
    private var active = false
    private var destroyed = false
    private var registered = false
    private var attempt = 0
    private val check = Runnable { checkTarget() }
    private val changed = Runnable { startRecovery("accessibility entry removed") }
    private val reinsert = Runnable {
        runCatching { BackgroundRecovery.restorePending(this) }
            .onFailure { failure("restore delayed rebind", it) }
    }
    private val wake = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { startRecovery(intent.action.orEmpty()) }
    }
    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            if (destroyed || !BackgroundRecovery.enabled(this@RevivalListener) || BackgroundRecovery.pending(this@RevivalListener)) return
            if (!AccessibilityEntries.contains(BackgroundRecovery.services(this@RevivalListener), BackgroundRecovery.target(this@RevivalListener))) {
                handler.removeCallbacks(changed)
                handler.postDelayed(changed, 2_000)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON).apply {
            if (Build.VERSION.SDK_INT >= 24) addAction(Intent.ACTION_USER_UNLOCKED)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(wake, filter, Context.RECEIVER_NOT_EXPORTED)
            else registerReceiver(wake, filter)
            registered = true
            contentResolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, observer)
        }.onFailure { failure("register recovery observers", it) }
        startRecovery("listener created")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = true
        startRecovery("notification listener bound")
    }

    override fun onListenerDisconnected() {
        connected = false
        if (!destroyed) BackgroundRecovery.requestBinding(this)
        super.onListenerDisconnected()
    }

    private fun startRecovery(reason: String) {
        if (destroyed || active) return
        active = true
        attempt = 0
        outcome = reason
        Log.i(TAG, reason)
        handler.post(check)
    }

    private fun checkTarget() {
        if (destroyed || !active) return
        try {
            if (!BackgroundRecovery.unlocked(this)) {
                outcome = "waiting for user unlock"
            } else {
                BackgroundRecovery.restorePending(this)
                if (!BackgroundRecovery.enabled(this)) { finish("recovery not enabled"); return }
                if (!BackgroundRecovery.canWrite(this)) { finish("WRITE_SECURE_SETTINGS not granted"); return }
                if (WatchAccessibilityService.connected) {
                    GateService.ensureRunning(this, "notification-revival")
                    if (GateService.running) { finish("accessibility and GateService running"); return }
                    outcome = "accessibility bound; waiting for GateService"
                } else if (!AccessibilityEntries.contains(BackgroundRecovery.services(this), BackgroundRecovery.target(this))) {
                    BackgroundRecovery.ensureEnabled(this)
                    outcome = "restored accessibility entry"
                } else if (attempt >= 2 && attempt + 1 < DELAYS.size && !BackgroundRecovery.pending(this)) {
                    BackgroundRecovery.beginRebind(this)
                    handler.postDelayed(reinsert, 1_500)
                    outcome = "requested accessibility rebind"
                } else outcome = "waiting for accessibility binding"
            }
        } catch (e: Exception) { failure("recovery attempt", e) }
        Log.i(TAG, "attempt=$attempt $outcome")
        attempt++
        if (attempt < DELAYS.size) handler.postDelayed(check, DELAYS[attempt])
        else finish("recovery window exhausted: $outcome")
    }

    private fun finish(result: String) {
        active = false
        outcome = result
        handler.removeCallbacks(check)
        Log.i(TAG, result)
    }

    private fun failure(action: String, error: Throwable) {
        outcome = "$action: ${error.javaClass.simpleName}: ${error.message}"
        Log.w(TAG, outcome, error)
    }

    override fun dump(fd: FileDescriptor?, writer: PrintWriter, args: Array<out String>?) {
        writer.println("StudyOnTV revival: active=$active attempts=$attempt outcome=$outcome")
        writer.println("unlocked=${BackgroundRecovery.unlocked(this)} connected=$connected")
        if (BackgroundRecovery.unlocked(this)) {
            writer.println("enabled=${BackgroundRecovery.enabled(this)} secureSettings=${BackgroundRecovery.canWrite(this)}")
            writer.println("accessibility=${WatchAccessibilityService.connected} gateService=${GateService.running} pending=${BackgroundRecovery.pending(this)}")
        }
    }

    override fun onDestroy() {
        destroyed = true
        active = false
        connected = false
        runCatching { BackgroundRecovery.restorePending(this) }.onFailure { failure("restore on destroy", it) }
        handler.removeCallbacksAndMessages(null)
        contentResolver.unregisterContentObserver(observer)
        if (registered) unregisterReceiver(wake)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "StudyOnTVRevival"
        private val DELAYS = longArrayOf(0, 5_000, 10_000, 15_000, 30_000, 60_000, 5_000)
        @Volatile var connected = false
            private set
        @Volatile var outcome = "not started"
            private set
    }
}
