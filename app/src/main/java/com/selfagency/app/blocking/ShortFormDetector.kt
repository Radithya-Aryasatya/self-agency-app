package com.selfagency.app.blocking

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Detects the short-form video players inside the YouTube and Instagram apps.
 *
 * These apps don't offer an official API, so we look for the views that make up the
 * Shorts / Reels screens. If an app update renames these views, add the new ids here
 * (Android Studio's Layout Inspector, or `uiautomator dump`, shows the current ones).
 */
object ShortFormDetector {

    private val youtubeShortsIds = listOf(
        "reel_recycler",
        "reel_player_page_container",
        "reel_watch_player",
        "reel_watch_fragment_root",
        "reel_time_bar",
        "shorts_player_container",
    )

    private val instagramReelsIds = listOf(
        "clips_viewer_view_pager",
        "clips_viewer_container",
        "clips_video_container",
        "clips_tab",
    )

    fun isYoutubeShorts(root: AccessibilityNodeInfo): Boolean =
        anyVisible(root, KnownApps.YOUTUBE, youtubeShortsIds) || selectedTab(root, "Shorts")

    fun isInstagramReels(root: AccessibilityNodeInfo): Boolean =
        anyVisible(root, KnownApps.INSTAGRAM, instagramReelsIds) || selectedTab(root, "Reels")

    private fun anyVisible(root: AccessibilityNodeInfo, pkg: String, ids: List<String>): Boolean =
        ids.any { id ->
            try {
                root.findAccessibilityNodeInfosByViewId("$pkg:id/$id").any { it.isVisibleToUser }
            } catch (_: Exception) {
                false
            }
        }

    /** Fallback: the bottom navigation tab with this label is currently selected. */
    private fun selectedTab(root: AccessibilityNodeInfo, label: String): Boolean = try {
        root.findAccessibilityNodeInfosByText(label).any { node ->
            node.isSelected && node.isVisibleToUser &&
                (node.contentDescription?.toString().equals(label, ignoreCase = true) ||
                    node.text?.toString().equals(label, ignoreCase = true))
        }
    } catch (_: Exception) {
        false
    }
}
