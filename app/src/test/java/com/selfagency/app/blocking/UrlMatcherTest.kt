package com.selfagency.app.blocking

import com.selfagency.app.blocking.UrlMatcher.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlMatcherTest {

    private val defaults = Rules(blockedSites = setOf("instagram.com", "youtube.com"))
    private val shortsOnly = Rules(blockedSites = emptySet())

    @Test fun parsesUrlsWithAndWithoutScheme() {
        assertEquals(UrlMatcher.ParsedUrl("m.youtube.com", "/shorts/abc"), UrlMatcher.parse("https://m.youtube.com/shorts/abc?feature=x"))
        assertEquals(UrlMatcher.ParsedUrl("youtube.com", "/shorts/abc"), UrlMatcher.parse("youtube.com/shorts/abc"))
        assertEquals(UrlMatcher.ParsedUrl("www.instagram.com", "/"), UrlMatcher.parse("WWW.Instagram.com"))
        assertEquals(UrlMatcher.ParsedUrl("example.com", "/"), UrlMatcher.parse("http://user@example.com:8080?q=1"))
    }

    @Test fun ignoresNonUrls() {
        assertNull(UrlMatcher.parse("how to stop scrolling"))
        assertNull(UrlMatcher.parse("youtube"))
        assertNull(UrlMatcher.parse(""))
        assertNull(UrlMatcher.parse(null))
    }

    @Test fun blocksDomainAndSubdomains() {
        assertEquals(Verdict.BlockedSite("youtube.com"), UrlMatcher.evaluate("m.youtube.com/watch?v=1", defaults))
        assertEquals(Verdict.BlockedSite("youtube.com"), UrlMatcher.evaluate("https://www.youtube.com", defaults))
        assertEquals(Verdict.BlockedSite("instagram.com"), UrlMatcher.evaluate("instagram.com/someone", defaults))
    }

    @Test fun doesNotOverMatchLookalikeDomains() {
        assertEquals(Verdict.Allowed, UrlMatcher.evaluate("notyoutube.com", defaults))
        assertEquals(Verdict.Allowed, UrlMatcher.evaluate("youtube.com.evil.net", defaults))
        assertEquals(Verdict.Allowed, UrlMatcher.evaluate("google.com/search?q=youtube.com", defaults))
    }

    @Test fun blocksShortsWhenSiteIsAllowed() {
        assertEquals(Verdict.YoutubeShorts, UrlMatcher.evaluate("m.youtube.com/shorts/xyz", shortsOnly))
        assertEquals(Verdict.YoutubeShorts, UrlMatcher.evaluate("https://www.youtube.com/shorts", shortsOnly))
        assertEquals(Verdict.Allowed, UrlMatcher.evaluate("m.youtube.com/watch?v=abc", shortsOnly))
        assertEquals(Verdict.Allowed, UrlMatcher.evaluate("youtube.com/@channel/shortsfan", shortsOnly))
    }

    @Test fun blocksReelsWhenSiteIsAllowed() {
        assertEquals(Verdict.InstagramReels, UrlMatcher.evaluate("instagram.com/reels/abc", shortsOnly))
        assertEquals(Verdict.InstagramReels, UrlMatcher.evaluate("www.instagram.com/reel/abc/", shortsOnly))
        assertEquals(Verdict.Allowed, UrlMatcher.evaluate("instagram.com/direct/inbox", shortsOnly))
    }

    @Test fun respectsShortFormToggles() {
        val off = shortsOnly.copy(blockYoutubeShortsWeb = false, blockInstagramReelsWeb = false)
        assertEquals(Verdict.Allowed, UrlMatcher.evaluate("m.youtube.com/shorts/xyz", off))
        assertEquals(Verdict.Allowed, UrlMatcher.evaluate("instagram.com/reels", off))
    }

    @Test fun normalizesUserEnteredDomains() {
        assertEquals("tiktok.com", UrlMatcher.normalizeDomain("https://www.TikTok.com/foryou"))
        assertEquals("m.facebook.com", UrlMatcher.normalizeDomain("m.facebook.com"))
        assertNull(UrlMatcher.normalizeDomain("tiktok"))
    }
}
