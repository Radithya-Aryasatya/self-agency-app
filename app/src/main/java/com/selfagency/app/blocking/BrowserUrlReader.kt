package com.selfagency.app.blocking

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Reads the address bar of a browser through the accessibility node tree.
 * Browsers don't expose the URL any other way, so we look up the address bar by its view id.
 */
object BrowserUrlReader {

    /** Address bar view ids for popular browsers. */
    private val knownUrlBarIds: Map<String, List<String>> = mapOf(
        "com.android.chrome" to listOf("url_bar"),
        "com.chrome.beta" to listOf("url_bar"),
        "com.chrome.dev" to listOf("url_bar"),
        "com.chrome.canary" to listOf("url_bar"),
        "com.google.android.apps.chrome" to listOf("url_bar"),
        "com.brave.browser" to listOf("url_bar"),
        "com.microsoft.emmx" to listOf("url_bar"),
        "com.vivaldi.browser" to listOf("url_bar"),
        "com.kiwibrowser.browser" to listOf("url_bar"),
        "org.chromium.chrome" to listOf("url_bar"),
        "com.sec.android.app.sbrowser" to listOf("location_bar_edit_text"),
        "com.sec.android.app.sbrowser.beta" to listOf("location_bar_edit_text"),
        "org.mozilla.firefox" to listOf("mozac_browser_toolbar_url_view", "url_bar_title"),
        "org.mozilla.firefox_beta" to listOf("mozac_browser_toolbar_url_view"),
        "org.mozilla.fenix" to listOf("mozac_browser_toolbar_url_view"),
        "org.mozilla.focus" to listOf("mozac_browser_toolbar_url_view", "display_url"),
        "com.opera.browser" to listOf("url_field"),
        "com.opera.mini.native" to listOf("url_field"),
        "com.opera.gx" to listOf("addressbarEdit", "url_field"),
        "com.duckduckgo.mobile.android" to listOf("omnibarTextInput"),
        "com.mi.globalbrowser" to listOf("url"),
        "com.UCMobile.intl" to listOf("url_bar"),
        "com.yandex.browser" to listOf("bro_omnibar_address_title_text"),
    )

    /** Tried for any app that handles http links but isn't in the list above. */
    private val genericUrlBarIds = listOf(
        "url_bar", "url_field", "location_bar_edit_text",
        "mozac_browser_toolbar_url_view", "omnibarTextInput", "address_bar", "url",
    )

    fun isKnownBrowser(pkg: String) = pkg in knownUrlBarIds

    /**
     * Returns the URL currently shown, or null if none was found or the user is typing in the bar
     * (we never want to block half-typed text).
     */
    fun readUrl(root: AccessibilityNodeInfo, pkg: String): String? {
        val ids = (knownUrlBarIds[pkg] ?: emptyList()) + genericUrlBarIds
        for (id in ids.distinct()) {
            val nodes = try {
                root.findAccessibilityNodeInfosByViewId("$pkg:id/$id")
            } catch (_: Exception) {
                null
            } ?: continue
            for (node in nodes) {
                if (node.isFocused) return null
                val text = node.text?.toString()?.takeIf { it.isNotBlank() } ?: continue
                return text
            }
        }
        return null
    }
}
