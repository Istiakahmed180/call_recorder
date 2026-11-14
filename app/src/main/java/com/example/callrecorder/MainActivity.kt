package com.example.callrecorder

import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.callrecorder.databinding.ActivityMainBinding
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: RecordingsAdapter
    private var player: MediaPlayer? = null
    private var playingFile: File? = null
    private var allRecordings: List<Recording> = emptyList()
    private var searchQuery: String = ""

    private val requiredPermissions = buildList {
        add(android.Manifest.permission.RECORD_AUDIO)
        add(android.Manifest.permission.READ_PHONE_STATE)
        add(android.Manifest.permission.READ_CALL_LOG)
        add(android.Manifest.permission.READ_CONTACTS)
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

        // Edge-to-edge (targetSdk 35): pad the header below the status bar and
        // the list above the navigation bar.
        val headerTop = binding.headerBar.paddingTop
        val listBottom = binding.recyclerView.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.headerBar.updatePadding(top = headerTop + bars.top)
            binding.recyclerView.updatePadding(bottom = listBottom + bars.bottom)
            insets
        }

        adapter = RecordingsAdapter(
            mutableListOf(),
            onPlayToggle = { togglePlay(it) },
            onShare = { shareRecording(it) },
            onDelete = { confirmDelete(it) },
            onRename = { showRenameDialog(it) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.searchInput.doAfterTextChanged {
            searchQuery = it?.toString()?.trim().orEmpty()
            applyFilter()
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

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
        cleanupOldRecordings()
        refreshList()
        updateSetupStatus()
        maybeStartListening()
    }

    /** Deletes recordings older than the user's auto-delete setting (if set). */
    private fun cleanupOldRecordings() {
        val days = Prefs.autoDeleteDays(this)
        if (days <= 0) return
        val cutoff = System.currentTimeMillis() - days * 24L * 60 * 60 * 1000
        val dir = File(getExternalFilesDir(null), "Recordings")
        dir.listFiles()?.forEach { if (it.isFile && it.lastModified() < cutoff) it.delete() }
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
                displayName = prettyName(it.name),
                dateMillis = it.lastModified(),
                sizeBytes = it.length(),
                durationMs = readDuration(it),
                kind = kindOf(it.name)
            )
        }
        allRecordings = recordings
        applyFilter()
    }

    private fun applyFilter() {
        val noneAtAll = allRecordings.isEmpty()
        // Search box only makes sense once there are recordings.
        binding.searchInput.visibility = if (noneAtAll) View.GONE else View.VISIBLE

        val filtered = if (searchQuery.isBlank()) {
            allRecordings
        } else {
            allRecordings.filter { it.displayName.contains(searchQuery, ignoreCase = true) }
        }
        adapter.updateData(filtered)

        binding.textEmpty.visibility = if (noneAtAll) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
        binding.textRecordCount.text = if (noneAtAll) "" else filtered.size.toString()
    }

    private fun kindOf(fileName: String): String {
        return when (fileName.substringBefore('_')) {
            "Incoming" -> Recording.KIND_INCOMING
            "Outgoing" -> Recording.KIND_OUTGOING
            "Missed" -> Recording.KIND_MISSED
            "WhatsApp" -> Recording.KIND_WHATSAPP
            "IMO" -> Recording.KIND_IMO
            "Call" -> Recording.KIND_CALL
            else -> Recording.KIND_OTHER
        }
    }

    /** Turns a recording file name into a friendly list title. */
    private fun prettyName(fileName: String): String {
        val base = fileName.substringBeforeLast('.')
        val parts = base.split('_')
        return when (parts.firstOrNull()) {
            "Incoming", "Outgoing", "Missed" -> {
                val num = parts.getOrNull(1)?.takeIf { it.isNotBlank() && it != "Unknown" }
                if (num != null) "${parts[0]} · $num" else "${parts[0]} call"
            }
            "WhatsApp" -> "WhatsApp call"
            "IMO" -> "IMO call"
            "Call" -> "Phone call"
            else -> base
        }
    }

    private fun readDuration(f: File): Long {
        return try {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(f.absolutePath)
            val d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            mmr.release()
            d
        } catch (_: Exception) {
            0L
        }
    }

    private fun togglePlay(rec: Recording) {
        if (playingFile == rec.file) {
            stopPlayback()
            return
        }
        stopPlayback()
        try {
            player = MediaPlayer().apply {
                setDataSource(rec.file.absolutePath)
                setOnCompletionListener { stopPlayback() }
                prepare()
                start()
            }
            playingFile = rec.file
            adapter.setPlaying(rec.file)
        } catch (_: Exception) {
            stopPlayback()
        }
    }

    private fun stopPlayback() {
        try {
            player?.stop()
        } catch (_: Exception) { }
        player?.release()
        player = null
        playingFile = null
        adapter.setPlaying(null)
    }

    private fun showRenameDialog(rec: Recording) {
        val input = EditText(this).apply {
            setText(rec.file.nameWithoutExtension)
            setSelection(text.length)
            setSingleLine()
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val container = FrameLayout(this).apply {
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.rename_title)
            .setView(container)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.rename) { _, _ ->
                val raw = input.text.toString().trim()
                if (raw.isEmpty()) return@setPositiveButton
                val safe = raw.replace(Regex("[/\\\\:*?\"<>|]"), "_")
                val ext = rec.file.extension.ifEmpty { "m4a" }
                val newFile = File(rec.file.parentFile, "$safe.$ext")
                if (newFile.name != rec.file.name && !newFile.exists()) {
                    rec.file.renameTo(newFile)
                    refreshList()
                }
            }
            .show()
    }

    private fun confirmDelete(rec: Recording) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_title)
            .setMessage(getString(R.string.delete_message, rec.displayName))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                rec.file.delete()
                refreshList()
            }
            .show()
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

    override fun onPause() {
        super.onPause()
        stopPlayback()
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }
}
