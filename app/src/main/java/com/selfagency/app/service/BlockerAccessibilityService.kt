package com.selfagency.app.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.selfagency.app.R
import com.selfagency.app.blocking.BrowserUrlReader
import com.selfagency.app.blocking.KnownApps
import com.selfagency.app.blocking.Rules
import com.selfagency.app.blocking.ShortFormDetector
import com.selfagency.app.blocking.UrlMatcher
import com.selfagency.app.data.BlockSettings
import com.selfagency.app.ui.BlockedActivity

/**
 * Watches which app / screen / website is in the foreground and steps in when it's blocked.
 *
 * - Blocked app            -> send the user Home and show the block screen.
 * - Blocked website        -> press Back in the browser, send Home and show the block screen.
 * - Shorts / Reels         -> press Back (leaves the player, keeps the rest of the app usable).
 */
class BlockerAccessibilityService : AccessibilityService() {

    private lateinit var settings: BlockSettings
    @Volatile private var rules = Rules()

    private val handler = Handler(Looper.getMainLooper())
    private val browserPackages = mutableSetOf<String>()

    private var lastActionAt = 0L
    private var lastActionKey: String? = null

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        rules = settings.snapshot()
    }

    /** Content-change events fire constantly, so screen inspection is debounced. */
    private val inspectScreen = Runnable { inspectActiveWindow() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        settings = BlockSettings(this)
        rules = settings.snapshot()
        settings.registerListener(prefsListener)
        loadBrowserPackages()
    }

    override fun onDestroy() {
        handler.removeCallbacks(inspectScreen)
        if (::settings.isInitialized) settings.unregisterListener(prefsListener)
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val current = rules
        if (!current.enabled) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        // Whole-app blocks are handled immediately, without waiting for the debounce.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg in current.blockedApps) {
            blockApp(pkg)
            return
        }

        if (needsScreenInspection(pkg, current)) {
            handler.removeCallbacks(inspectScreen)
            handler.postDelayed(inspectScreen, INSPECT_DEBOUNCE_MS)
        }
    }

    private fun needsScreenInspection(pkg: String, r: Rules): Boolean = when {
        pkg in r.blockedApps -> true
        pkg == KnownApps.YOUTUBE -> r.blockYoutubeShortsApp
        pkg == KnownApps.INSTAGRAM -> r.blockInstagramReelsApp
        isBrowser(pkg) -> r.blockedSites.isNotEmpty() || r.blockYoutubeShortsWeb || r.blockInstagramReelsWeb
        else -> false
    }

    private fun inspectActiveWindow() {
        val r = rules
        if (!r.enabled) return
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: return

        when {
            pkg in r.blockedApps -> blockApp(pkg)

            pkg == KnownApps.YOUTUBE && r.blockYoutubeShortsApp && ShortFormDetector.isYoutubeShorts(root) ->
                leaveShortForm("yt-app", getString(R.string.toast_shorts_blocked))

            pkg == KnownApps.INSTAGRAM && r.blockInstagramReelsApp && ShortFormDetector.isInstagramReels(root) ->
                leaveShortForm("ig-app", getString(R.string.toast_reels_blocked))

            isBrowser(pkg) -> {
                val url = BrowserUrlReader.readUrl(root, pkg) ?: return
                when (val verdict = UrlMatcher.evaluate(url, r)) {
                    is UrlMatcher.Verdict.BlockedSite -> blockSite(verdict.domain)
                    UrlMatcher.Verdict.YoutubeShorts ->
                        leaveShortForm("yt-web", getString(R.string.toast_shorts_blocked))
                    UrlMatcher.Verdict.InstagramReels ->
                        leaveShortForm("ig-web", getString(R.string.toast_reels_blocked))
                    UrlMatcher.Verdict.Allowed -> Unit
                }
            }
        }
    }

    private fun blockApp(pkg: String) {
        if (!throttle("app:$pkg")) return
        performGlobalAction(GLOBAL_ACTION_HOME)
        showBlockScreen(appLabel(pkg))
    }

    private fun blockSite(domain: String) {
        if (!throttle("site:$domain")) return
        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 150)
        showBlockScreen(domain)
    }

    private fun leaveShortForm(key: String, message: String) {
        if (!throttle(key)) return
        performGlobalAction(GLOBAL_ACTION_BACK)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showBlockScreen(what: String) {
        val intent = Intent(this, BlockedActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(BlockedActivity.EXTRA_BLOCKED_NAME, what)
        handler.postDelayed({ startActivity(intent) }, 200)
    }

    /** Avoids firing the same action repeatedly while the screen is still transitioning. */
    private fun throttle(key: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (key == lastActionKey && now - lastActionAt < THROTTLE_MS) return false
        lastActionKey = key
        lastActionAt = now
        return true
    }

    private fun isBrowser(pkg: String) = BrowserUrlReader.isKnownBrowser(pkg) || pkg in browserPackages

    /** Any installed app that can open http links counts as a browser. */
    private fun loadBrowserPackages() {
        val intent = Intent(Intent.ACTION_VIEW, "https://example.com".toUri())
        @Suppress("DEPRECATION")
        val handlers = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        browserPackages.clear()
        browserPackages += handlers.map { it.activityInfo.packageName }
            .filter { it != KnownApps.YOUTUBE && it != KnownApps.INSTAGRAM && it != packageName }
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg
    }

    companion object {
        private const val INSPECT_DEBOUNCE_MS = 250L
        private const val THROTTLE_MS = 1500L

        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, BlockerAccessibilityService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
        }
    }
}
