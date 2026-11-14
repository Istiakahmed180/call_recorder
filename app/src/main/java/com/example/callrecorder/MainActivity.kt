package com.example.callrecorder

import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.TextView
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
        updateSetupStatus()
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
        updateSetupStatus()
        maybeStartListening()
    }

    private fun hasPermission(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun isAccessibilityEnabled(): Boolean {
        val expected = "$packageName/${MessengerAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun isIgnoringBattery(): Boolean {
        val pm = getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun setRowStatus(
        status: TextView, check: ImageView, chevron: ImageView,
        ok: Boolean, okText: String, notText: String
    ) {
        status.text = if (ok) okText else notText
        check.visibility = if (ok) View.VISIBLE else View.GONE
        chevron.visibility = if (ok) View.GONE else View.VISIBLE
    }

    private fun updateSetupStatus() {
        val permOk = hasPermission(android.Manifest.permission.RECORD_AUDIO) &&
            hasPermission(android.Manifest.permission.READ_PHONE_STATE)
        setRowStatus(
            binding.textPermStatus, binding.iconPermCheck, binding.iconPermChevron, permOk,
            getString(R.string.setup_granted), getString(R.string.setup_not_granted)
        )
        setRowStatus(
            binding.textAccStatus, binding.iconAccCheck, binding.iconAccChevron,
            isAccessibilityEnabled(),
            getString(R.string.setup_enabled), getString(R.string.setup_not_enabled)
        )
        setRowStatus(
            binding.textBatStatus, binding.iconBatCheck, binding.iconBatChevron,
            isIgnoringBattery(),
            getString(R.string.setup_disabled_ok), getString(R.string.setup_battery_on)
        )

        val dotColor = if (permOk) R.color.ok_green else R.color.pending_amber
        binding.statusDot.background?.mutate()?.setTint(ContextCompat.getColor(this, dotColor))
        binding.statusText.text = getString(
            if (permOk) R.string.status_active else R.string.status_needs_setup
        )
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
        val empty = recordings.isEmpty()
        binding.textEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (empty) View.GONE else View.VISIBLE
        binding.textRecordCount.text = if (empty) "" else recordings.size.toString()
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
