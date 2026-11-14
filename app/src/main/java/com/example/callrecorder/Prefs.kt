package com.example.callrecorder

import android.content.Context

/**
 * Thin wrapper over SharedPreferences for the user-configurable settings.
 */
object Prefs {
    private const val FILE = "settings"
    private const val KEY_CALL_AUDIO_SOURCE = "use_call_audio_source"
    private const val KEY_DISCARD_SHORT = "discard_short"
    private const val KEY_AUTO_DELETE_DAYS = "auto_delete_days"

    /** Minimum kept recording length when "discard short" is on. */
    const val SHORT_RECORDING_MS = 2000L

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** true = VOICE_COMMUNICATION (recommended), false = plain MIC. */
    fun useCallAudioSource(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_CALL_AUDIO_SOURCE, true)

    fun setUseCallAudioSource(ctx: Context, value: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_CALL_AUDIO_SOURCE, value).apply()

    /** true = delete recordings shorter than SHORT_RECORDING_MS. */
    fun discardShort(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_DISCARD_SHORT, true)

    fun setDiscardShort(ctx: Context, value: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_DISCARD_SHORT, value).apply()

    /** Delete recordings older than this many days; 0 = never. */
    fun autoDeleteDays(ctx: Context): Int =
        prefs(ctx).getInt(KEY_AUTO_DELETE_DAYS, 0)

    fun setAutoDeleteDays(ctx: Context, value: Int) =
        prefs(ctx).edit().putInt(KEY_AUTO_DELETE_DAYS, value).apply()
}
