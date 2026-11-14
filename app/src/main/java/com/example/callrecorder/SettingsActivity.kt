package com.example.callrecorder

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.example.callrecorder.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val autoDeleteDays = intArrayOf(0, 7, 30, 90)
    private val releasesUrl = "https://github.com/Istiakahmed180/call_recorder/releases/latest"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val headerTop = binding.headerBar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.headerBar.updatePadding(top = headerTop + bars.top)
            v.updatePadding(bottom = bars.bottom)
            insets
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.switchAudioSource.isChecked = Prefs.useCallAudioSource(this)
        binding.switchAudioSource.setOnCheckedChangeListener { _, checked ->
            Prefs.setUseCallAudioSource(this, checked)
        }

        binding.switchDiscardShort.isChecked = Prefs.discardShort(this)
        binding.switchDiscardShort.setOnCheckedChangeListener { _, checked ->
            Prefs.setDiscardShort(this, checked)
        }

        binding.rowAutoDelete.setOnClickListener { showAutoDeleteDialog() }
        updateAutoDeleteLabel()

        binding.textVersion.text = appVersion()
        binding.rowLatest.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(releasesUrl)))
            } catch (_: Exception) { }
        }
    }

    private fun appVersion(): String {
        return try {
            val p = packageManager.getPackageInfo(packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                p.longVersionCode else @Suppress("DEPRECATION") p.versionCode.toLong()
            "${p.versionName} ($code)"
        } catch (_: Exception) {
            ""
        }
    }

    private fun showAutoDeleteDialog() {
        val labels = autoDeleteDays.map { days ->
            if (days == 0) getString(R.string.auto_delete_never)
            else getString(R.string.auto_delete_days, days)
        }.toTypedArray()
        val current = autoDeleteDays.indexOf(Prefs.autoDeleteDays(this)).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.pref_auto_delete_title)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                Prefs.setAutoDeleteDays(this, autoDeleteDays[which])
                updateAutoDeleteLabel()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateAutoDeleteLabel() {
        val days = Prefs.autoDeleteDays(this)
        binding.textAutoDeleteValue.text =
            if (days == 0) getString(R.string.auto_delete_never)
            else getString(R.string.auto_delete_days, days)
    }
}
