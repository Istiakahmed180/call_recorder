package com.example.callrecorder

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.callrecorder.databinding.ActivityMainBinding
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: RecordingsAdapter
    private var player: MediaPlayer? = null

    private val requiredPermissions = buildList {
        add(android.Manifest.permission.RECORD_AUDIO)
        add(android.Manifest.permission.READ_PHONE_STATE)
        add(android.Manifest.permission.READ_CALL_LOG)
        add(android.Manifest.permission.MODIFY_AUDIO_SETTINGS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add(android.Manifest.permission.PROCESS_OUTGOING_CALLS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshList()
        maybeStartListening()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = RecordingsAdapter(
            mutableListOf(),
            onPlay = { playRecording(it) },
            onShare = { shareRecording(it) },
            onDelete = { it.file.delete() }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.btnGrantPermissions.setOnClickListener {
            permissionLauncher.launch(requiredPermissions.toTypedArray())
        }

        binding.btnEnableAccessibility.setOnClickListener {
            startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.btnIgnoreBattery.setOnClickListener {
            try {
                val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
            } catch (_: Exception) { }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshList()
        maybeStartListening()
    }

    /**
     * Start (or ensure running) the persistent recording service so it can
     * listen for calls itself. Starting from the foreground Activity is allowed
     * on Android 12+, unlike starting from a background broadcast receiver.
     */
    private fun maybeStartListening() {
        val micOk = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val phoneOk = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.READ_PHONE_STATE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!micOk || !phoneOk) return
        val intent = Intent(this, CallRecorderService::class.java).apply {
            action = CallRecorderService.ACTION_START_LISTENING
        }
        try {
            ContextCompat.startForegroundService(this, intent)
        } catch (_: Exception) { }
    }

    private fun refreshList() {
        val dir = File(getExternalFilesDir(null), "Recordings")
        val files = dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()
        val recordings = files.map {
            Recording(
                file = it,
                displayName = it.name,
                dateMillis = it.lastModified(),
                sizeBytes = it.length()
            )
        }
        adapter.updateData(recordings)
        binding.textEmpty.visibility = if (recordings.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun playRecording(rec: Recording) {
        player?.release()
        player = MediaPlayer().apply {
            setDataSource(rec.file.absolutePath)
            prepare()
            start()
        }
    }

    private fun shareRecording(rec: Recording) {
        val uri: Uri = FileProvider.getUriForFile(
            this, "$packageName.fileprovider", rec.file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_recording)))
    }

    override fun onDestroy() {
        player?.release()
        super.onDestroy()
    }
}
