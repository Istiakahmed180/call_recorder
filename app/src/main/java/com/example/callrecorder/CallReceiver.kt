package com.example.callrecorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

class CallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.intent.action.PHONE_STATE") return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        when (state) {
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // Call connected (either answered incoming, or outgoing call
                // that is now active) -> start recording.
                val svc = Intent(context, CallRecorderService::class.java).apply {
                    action = CallRecorderService.ACTION_START
                    putExtra(CallRecorderService.EXTRA_SOURCE, "Call")
                }
                startServiceCompat(context, svc)
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                // Call ended -> stop recording.
                val svc = Intent(context, CallRecorderService::class.java).apply {
                    action = CallRecorderService.ACTION_STOP
                }
                startServiceCompat(context, svc)
            }
            TelephonyManager.EXTRA_STATE_RINGING -> {
                // Incoming call ringing - do nothing yet, wait for OFFHOOK
                // (i.e. the call actually being answered) before recording.
            }
        }
    }

    private fun startServiceCompat(context: Context, intent: Intent) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
