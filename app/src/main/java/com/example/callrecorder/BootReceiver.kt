package com.example.callrecorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * CallReceiver is a manifest-registered receiver so it already restarts
 * automatically after boot. This receiver is kept as a hook in case you
 * later want to re-enable the accessibility service or show a "ready"
 * notification after the phone restarts.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // No-op for now; CallReceiver is already manifest-registered.
        }
    }
}
