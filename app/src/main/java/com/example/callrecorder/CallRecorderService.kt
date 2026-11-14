package com.example.callrecorder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Foreground service that owns the MediaRecorder instance.
 */
class CallRecorderService : Service() {

    companion object {
        const val ACTION_START = "com.example.callrecorder.action.START"
        const val ACTION_STOP = "com.example.callrecorder.action.STOP"
        const val EXTRA_SOURCE = "source" // "call" or app name like "WhatsApp"
        private const val CHANNEL_ID = "call_recorder_channel"
        private const val NOTIF_ID = 42
    }

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var isRecording = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val source = intent.getStringExtra(EXTRA_SOURCE) ?: "call"
                startForegroundNotification()
                startRecording(source)
            }
            ACTION_STOP -> {
                stopRecording()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

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

    private fun startForegroundNotification() {
        createChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_recording_title))
            .setContentText(getString(R.string.notification_recording_text))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID, notification)
    }

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
                } catch (e2: Exception) {
                    isRecording = false
                }
            }
        }
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
    }

    override fun onDestroy() {
        stopRecording()
        super.onDestroy()
    }
}
