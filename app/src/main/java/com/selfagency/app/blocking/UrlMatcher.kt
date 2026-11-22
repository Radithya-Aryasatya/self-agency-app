package com.selfagency.app.blocking

/**
 * Pure-Kotlin URL logic (no Android dependencies) so it can be unit tested on the JVM.
 *
 * Browsers show URLs in many shapes: "https://m.youtube.com/shorts/abc", "youtube.com/shorts/abc",
 * "www.instagram.com", etc. Everything is normalised to a host + path before matching.
 */
object UrlMatcher {

    data class ParsedUrl(val host: String, val path: String)

    sealed interface Verdict {
        data object Allowed : Verdict
        data class BlockedSite(val domain: String) : Verdict
        data object YoutubeShorts : Verdict
        data object InstagramReels : Verdict
    }

    /** Returns null when [raw] doesn't look like a URL (e.g. a search query being typed). */
    fun parse(raw: String?): ParsedUrl? {
        var s = raw?.trim()?.lowercase() ?: return null
        if (s.isEmpty() || s.any { it.isWhitespace() }) return null
        s = s.substringAfter("://", s)

        val hostEnd = s.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it == -1) s.length else it }
        val host = s.substring(0, hostEnd)
            .substringAfterLast('@')
            .substringBefore(':')
            .trimEnd('.')
        if (!host.contains('.') || host.startsWith('.')) return null
        if (!host.all { it.isLetterOrDigit() || it == '.' || it == '-' }) return null

        val rest = s.substring(hostEnd)
        val path = when {
            rest.startsWith("/") -> rest.substringBefore('?').substringBefore('#')
            else -> "/"
        }
        return ParsedUrl(host, path)
    }

    /**
     * Turns user input like "https://www.YouTube.com/feed" into "youtube.com".
     * Returns null if it isn't a usable domain.
     */
    fun normalizeDomain(input: String): String? {
        val parsed = parse(input) ?: return null
        return parsed.host.removePrefix("www.").takeIf { it.contains('.') }
    }

    /** True for the domain itself and any subdomain (m.youtube.com matches youtube.com). */
    fun hostMatches(host: String, domain: String): Boolean =
        host == domain || host.endsWith(".$domain")

    fun evaluate(rawUrl: String?, rules: Rules): Verdict {
        val url = parse(rawUrl) ?: return Verdict.Allowed

        rules.blockedSites.firstOrNull { hostMatches(url.host, it) }?.let {
            return Verdict.BlockedSite(it)
        }
        if (rules.blockYoutubeShortsWeb && isYoutubeShorts(url)) return Verdict.YoutubeShorts
        if (rules.blockInstagramReelsWeb && isInstagramReels(url)) return Verdict.InstagramReels
        return Verdict.Allowed
    }

    fun isYoutubeShorts(url: ParsedUrl): Boolean =
        hostMatches(url.host, "youtube.com") &&
            (url.path == "/shorts" || url.path.startsWith("/shorts/"))

    fun isInstagramReels(url: ParsedUrl): Boolean =
        hostMatches(url.host, "instagram.com") &&
            (url.path == "/reels" || url.path.startsWith("/reels/") || url.path.startsWith("/reel/"))
}
