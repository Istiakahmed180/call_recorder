package com.example.callrecorder

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.accessibility.AccessibilityEvent

/**
 * Detects when WhatsApp or IMO show an active call screen and starts /
 * stops the recording service accordingly.
 *
 * IMPORTANT LIMITATION: WhatsApp and IMO do not expose an official "call
 * started" API to other apps. This service uses heuristics (window package
 * name + on-screen text such as "Calling…", a running mm:ss timer, or
 * "Connected") to guess when a call is active. Those apps change their UI
 * between versions, so these heuristics WILL occasionally miss a call or
 * fire a beat late — this is a known, inherent limitation of this approach,
 * not a bug you can fully fix. If it stops working after a WhatsApp/IMO
 * update, the keyword lists below are the first place to update.
 *
 * Because neither app's call audio can be tapped directly by a third-party
 * app on modern Android, the microphone + speakerphone route is used, same
 * as for normal calls: the phone's speaker is turned on so the other
 * party's voice reaches the mic too.
 */
class MessengerAccessibilityService : AccessibilityService() {

    private var callActive = false

    private val whatsappPackages = setOf("com.whatsapp", "com.whatsapp.w4b")
    private val imoPackages = setOf("com.imo.android.imous", "com.imo.android.imoim")

    private val callStartKeywords = listOf("calling", "ringing", "incoming call", "connecting")
    private val callConnectedKeywords = listOf("connected", "encrypted")
    private val timerPattern = Regex("""^\d{1,2}:\d{2}$""")

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        val isTargetApp = pkg in whatsappPackages || pkg in imoPackages
        if (!isTargetApp) {
            if (callActive) {
                // User navigated away from the messenger app -> assume call ended.
                stopRecording()
            }
            return
        }

        val root = rootInActiveWindow ?: return
        val screenText = collectText(root).lowercase()

        val looksLikeCallScreen =
            callStartKeywords.any { screenText.contains(it) } ||
            callConnectedKeywords.any { screenText.contains(it) } ||
            screenText.lines().any { timerPattern.matches(it.trim()) }

        if (looksLikeCallScreen && !callActive) {
            val appName = if (pkg in whatsappPackages) "WhatsApp" else "IMO"
            startRecording(appName)
        } else if (!looksLikeCallScreen && callActive) {
            stopRecording()
        }
    }

    private fun collectText(node: android.view.accessibility.AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        fun walk(n: android.view.accessibility.AccessibilityNodeInfo?) {
            if (n == null) return
            n.text?.let { sb.append(it).append('\n') }
            for (i in 0 until n.childCount) {
                walk(n.getChild(i))
            }
        }
        walk(node)
        return sb.toString()
    }

    private fun startRecording(appName: String) {
        callActive = true
        enableSpeakerphone(true)
        val intent = Intent(this, CallRecorderService::class.java).apply {
            action = CallRecorderService.ACTION_START
            putExtra(CallRecorderService.EXTRA_SOURCE, appName)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopRecording() {
        callActive = false
        enableSpeakerphone(false)
        val intent = Intent(this, CallRecorderService::class.java).apply {
            action = CallRecorderService.ACTION_STOP
        }
        startService(intent)
    }

    private fun enableSpeakerphone(on: Boolean) {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            am.isSpeakerphoneOn = on
        } catch (_: Exception) { }
    }

    override fun onInterrupt() {
        if (callActive) stopRecording()
    }
}
