package com.selfagency.app.blocking

/** Immutable snapshot of everything the user has chosen to block. */
data class Rules(
    val enabled: Boolean = true,
    val blockedApps: Set<String> = emptySet(),
    val blockedSites: Set<String> = emptySet(),
    val blockYoutubeShortsApp: Boolean = true,
    val blockYoutubeShortsWeb: Boolean = true,
    val blockInstagramReelsApp: Boolean = true,
    val blockInstagramReelsWeb: Boolean = true,
)

object KnownApps {
    const val YOUTUBE = "com.google.android.youtube"
    const val INSTAGRAM = "com.instagram.android"
}
