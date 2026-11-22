package com.selfagency.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.selfagency.app.blocking.KnownApps
import com.selfagency.app.blocking.Rules

/**
 * Stores the user's block list. Shared by the settings screen and the accessibility service,
 * which listens for changes so edits take effect immediately.
 */
class BlockSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        if (!prefs.getBoolean(KEY_SEEDED, false)) {
            prefs.edit {
                putBoolean(KEY_SEEDED, true)
                putStringSet(KEY_APPS, setOf(KnownApps.INSTAGRAM, KnownApps.YOUTUBE))
                putStringSet(KEY_SITES, setOf("instagram.com", "youtube.com"))
            }
        }
    }

    fun snapshot(): Rules = Rules(
        enabled = prefs.getBoolean(KEY_ENABLED, true),
        blockedApps = prefs.getStringSet(KEY_APPS, emptySet())!!.toSet(),
        blockedSites = prefs.getStringSet(KEY_SITES, emptySet())!!.toSet(),
        blockYoutubeShortsApp = prefs.getBoolean(KEY_YT_SHORTS_APP, true),
        blockYoutubeShortsWeb = prefs.getBoolean(KEY_YT_SHORTS_WEB, true),
        blockInstagramReelsApp = prefs.getBoolean(KEY_IG_REELS_APP, true),
        blockInstagramReelsWeb = prefs.getBoolean(KEY_IG_REELS_WEB, true),
    )

    fun setEnabled(value: Boolean) = prefs.edit { putBoolean(KEY_ENABLED, value) }

    fun addApp(pkg: String) = updateSet(KEY_APPS) { it + pkg }
    fun removeApp(pkg: String) = updateSet(KEY_APPS) { it - pkg }

    fun addSite(domain: String) = updateSet(KEY_SITES) { it + domain }
    fun removeSite(domain: String) = updateSet(KEY_SITES) { it - domain }

    fun setYoutubeShortsApp(value: Boolean) = prefs.edit { putBoolean(KEY_YT_SHORTS_APP, value) }
    fun setYoutubeShortsWeb(value: Boolean) = prefs.edit { putBoolean(KEY_YT_SHORTS_WEB, value) }
    fun setInstagramReelsApp(value: Boolean) = prefs.edit { putBoolean(KEY_IG_REELS_APP, value) }
    fun setInstagramReelsWeb(value: Boolean) = prefs.edit { putBoolean(KEY_IG_REELS_WEB, value) }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    private fun updateSet(key: String, transform: (Set<String>) -> Set<String>) {
        val current = prefs.getStringSet(key, emptySet())!!.toSet()
        // Always write a new Set instance; SharedPreferences ignores in-place mutations.
        prefs.edit { putStringSet(key, HashSet(transform(current))) }
    }

    private companion object {
        const val PREFS_NAME = "block_settings"
        const val KEY_SEEDED = "seeded"
        const val KEY_ENABLED = "enabled"
        const val KEY_APPS = "blocked_apps"
        const val KEY_SITES = "blocked_sites"
        const val KEY_YT_SHORTS_APP = "yt_shorts_app"
        const val KEY_YT_SHORTS_WEB = "yt_shorts_web"
        const val KEY_IG_REELS_APP = "ig_reels_app"
        const val KEY_IG_REELS_WEB = "ig_reels_web"
    }
}
