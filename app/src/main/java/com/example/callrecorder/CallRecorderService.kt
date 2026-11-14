package com.example.callrecorder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistent foreground service that owns the MediaRecorder and listens for
 * phone call state itself.
 *
 * Why it is persistent: on Android 12+ (API 31+) a background BroadcastReceiver
 * is NOT allowed to start a foreground service, so the old approach (a
 * PHONE_STATE receiver calling startForegroundService on OFFHOOK) throws
 * ForegroundServiceStartNotAllowedException and no recording happens. Instead
 * this service is started once from the foreground (MainActivity) or on boot,
 * stays alive, and registers its own telephony listener. When a call goes
 * OFFHOOK it starts recording; on IDLE it stops — all from inside the already
 * running foreground service, which is allowed.
 *
 * WhatsApp/IMO recording is driven separately by the accessibility service via
 * ACTION_START / ACTION_STOP against this same running service.
 */
class CallRecorderService : Service() {

    companion object {
        const val ACTION_START_LISTENING = "com.example.callrecorder.action.START_LISTENING"
        const val ACTION_STOP_LISTENING = "com.example.callrecorder.action.STOP_LISTENING"
        const val ACTION_START = "com.example.callrecorder.action.START"
        const val ACTION_STOP = "com.example.callrecorder.action.STOP"
        const val EXTRA_SOURCE = "source" // "Call" or app name like "WhatsApp"
        private const val CHANNEL_ID = "call_recorder_channel"
        private const val NOTIF_ID = 42
        private const val SOURCE_CALL = "Call"
    }

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var isRecording = false
    private var recordingSource: String? = null

    private var isListening = false
    private var telephonyCallback: Any? = null
    private var phoneStateListener: PhoneStateListener? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A null intent means the system restarted us (START_STICKY) -> resume
        // listening.
        when (intent?.action ?: ACTION_START_LISTENING) {
            ACTION_START_LISTENING -> {
                ensureForeground()
                startListening()
            }
            ACTION_STOP_LISTENING -> {
                stopRecording()
                stopListening()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_START -> {
                val source = intent?.getStringExtra(EXTRA_SOURCE) ?: SOURCE_CALL
                ensureForeground()
                startRecording(source)
            }
            ACTION_STOP -> {
                stopRecording()
                // Keep the service alive and listening; just drop back to the
                // idle notification.
                updateNotification(recording = false)
            }
        }
        return START_STICKY
    }

    // ---- Telephony listening ----------------------------------------------

    private fun startListening() {
        if (isListening) return
        val tm = getSystemService(TELEPHONY_SERVICE) as? TelephonyManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val cb = CallStateCallback()
                tm.registerTelephonyCallback(mainExecutor, cb)
                telephonyCallback = cb
            } else {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        handleCallState(state)
                    }
                }
                @Suppress("DEPRECATION")
                tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
                phoneStateListener = listener
            }
            isListening = true
        } catch (_: Exception) {
            isListening = false
        }
    }

    private fun stopListening() {
        val tm = getSystemService(TELEPHONY_SERVICE) as? TelephonyManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (telephonyCallback as? TelephonyCallback)?.let { tm?.unregisterTelephonyCallback(it) }
            } else {
                @Suppress("DEPRECATION")
                phoneStateListener?.let { tm?.listen(it, PhoneStateListener.LISTEN_NONE) }
            }
        } catch (_: Exception) { }
        telephonyCallback = null
        phoneStateListener = null
        isListening = false
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private inner class CallStateCallback : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) = handleCallState(state)
    }

    private fun handleCallState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> startRecording(SOURCE_CALL)
            TelephonyManager.CALL_STATE_IDLE -> {
                // Only stop if THIS recording belongs to a cellular call, so a
                // WhatsApp/IMO recording is not cut off by an unrelated idle.
                if (recordingSource == SOURCE_CALL) {
                    stopRecording()
                    updateNotification(recording = false)
                }
            }
            // CALL_STATE_RINGING: wait for OFFHOOK before recording.
        }
    }

    // ---- Notification ------------------------------------------------------

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            mgr.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(recording: Boolean): android.app.Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = if (recording)
            getString(R.string.notification_recording_title)
        else
            getString(R.string.notification_listening_title)
        val text = if (recording)
            getString(R.string.notification_recording_text)
        else
            getString(R.string.notification_listening_text)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .build()
    }

    private fun ensureForeground() {
        createChannel()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(isRecording), type)
        } catch (_: Exception) {
            // If the microphone-typed start is rejected, fall back to a plain
            // foreground start so the service still runs.
            try {
                ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(isRecording), 0)
            } catch (_: Exception) { }
        }
    }

    private fun updateNotification(recording: Boolean) {
        try {
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.notify(NOTIF_ID, buildNotification(recording))
        } catch (_: Exception) { }
    }

    // ---- Recording ---------------------------------------------------------

    private fun startRecording(source: String) {
        if (isRecording) return
        val dir = File(getExternalFilesDir(null), "Recordings")
        if (!dir.exists()) dir.mkdirs()

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        outputFile = File(dir, "${source}_$stamp.m4a")

        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)
            setOutputFile(outputFile!!.absolutePath)
            try {
                prepare()
                start()
                isRecording = true
                recordingSource = source
            } catch (e: Exception) {
                // VOICE_COMMUNICATION source can fail on some OEM ROMs; fall
                // back to plain MIC source (speakerphone must be on for the
                // other party's voice to be captured in that case).
                try {
                    reset()
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setOutputFile(outputFile!!.absolutePath)
                    prepare()
                    start()
                    isRecording = true
                    recordingSource = source
                } catch (e2: Exception) {
                    isRecording = false
                    recordingSource = null
                }
            }
        }
        if (isRecording) updateNotification(recording = true)
    }

    private fun stopRecording() {
        if (!isRecording) return
        try {
            recorder?.stop()
        } catch (_: Exception) {
            // stop() can throw if recording was too short; the file is
            // usually still discarded safely in that case.
        }
        recorder?.release()
        recorder = null
        isRecording = false
        recordingSource = null
    }

    override fun onDestroy() {
        stopRecording()
        stopListening()
        super.onDestroy()
    }
}
