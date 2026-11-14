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

        val svc = Intent(context, CallRecorderService::class.java)
        when (state) {
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                svc.action = CallRecorderService.ACTION_START
                svc.putExtra(CallRecorderService.EXTRA_SOURCE, "Call")
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                svc.action = CallRecorderService.ACTION_STOP
            }
            else -> return // RINGING: wait for OFFHOOK
        }

        // Only reaches the service if it is already running; never starts a new
        // foreground service from the background.
        try {
            context.startService(svc)
        } catch (_: Exception) { }
    }
}
