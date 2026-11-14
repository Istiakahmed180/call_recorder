package com.example.callrecorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

/**
 * Backup phone-state receiver.
 *
 * The real work is done by [CallRecorderService], which listens for call
 * state itself while it runs in the foreground. This receiver only forwards
 * the state to that already-running service — it deliberately does NOT try to
 * start a foreground service from the background, because Android 12+ forbids
 * that (it throws ForegroundServiceStartNotAllowedException and crashes).
 * Everything here is wrapped in try/catch so a failed forward is a no-op.
 */
class CallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.intent.action.PHONE_STATE") return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)

        // Only forward OFFHOOK as a fallback to start recording if the running
        // service's own listener somehow missed it. Stopping (and tagging the
        // file with number/direction) is handled solely by the service's
        // telephony callback on IDLE, so forwarding STOP here is avoided to
        // prevent a race that would skip the call-log lookup.
        if (state != TelephonyManager.EXTRA_STATE_OFFHOOK) return

        val svc = Intent(context, CallRecorderService::class.java).apply {
            action = CallRecorderService.ACTION_START
            putExtra(CallRecorderService.EXTRA_SOURCE, "Call")
        }
        // Only reaches the service if it is already running; never starts a new
        // foreground service from the background.
        try {
            context.startService(svc)
        } catch (_: Exception) { }
    }
}
