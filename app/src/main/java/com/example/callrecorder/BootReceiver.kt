package com.example.callrecorder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Restarts the persistent recording/listening service after the phone boots.
 *
 * Starting a foreground service from BOOT_COMPLETED is one of the explicit
 * exemptions to the Android 12+ background-start restriction, so this is
 * allowed (unlike starting one from a PHONE_STATE broadcast).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val micOk = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val phoneOk = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        if (!micOk || !phoneOk) return

        val svc = Intent(context, CallRecorderService::class.java).apply {
            action = CallRecorderService.ACTION_START_LISTENING
        }
        try {
            ContextCompat.startForegroundService(context, svc)
        } catch (_: Exception) { }
    }
}
