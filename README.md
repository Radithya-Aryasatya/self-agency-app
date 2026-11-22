# Self Agency

An Android app that blocks the apps and websites you choose, with extra protection against
**YouTube Shorts** and **Instagram Reels**, in the apps and in the browser. Its one job is to help you take your time back.

## Open it in Android Studio

1. **File → Open…** and pick this folder (the one that contains `settings.gradle.kts`).
2. Let Gradle sync. Android Studio will download the SDK pieces it needs (Android 15 / API 35).
3. Plug in your phone (USB debugging on), choose the **app** run configuration and press **Run ▶**.

It's a standard Gradle + Kotlin + Jetpack Compose project, so everything (code, resources and
tests) shows up in the Project view.

## First launch on the phone

1. Open **Self Agency** and tap **Open Accessibility settings**.
2. Find **Self Agency blocker** (often under *Downloaded apps* / *Installed apps*) and turn it on.
3. Return to the app. The card at the top should say **Blocking is on**.

> If Android greys out the switch with *"Restricted setting"*, go to
> **Settings → Apps → Self Agency → ⋮ (top right) → Allow restricted settings**, then try again.
> This only happens for apps that didn't come from the Play Store.

## What it blocks (defaults)

| Rule | Default | What happens |
|---|---|---|
| Instagram app | blocked | Sent to the home screen and shown a "Not now" screen |
| YouTube app | blocked | Same as above |
| instagram.com, youtube.com | blocked | Browser goes back, then home and the "Not now" screen |
| YouTube Shorts (app and web) | on | Pressing Back leaves Shorts, and the rest of YouTube still works |
| Instagram Reels (app and web) | on | Same as above for Reels |

The Shorts/Reels rules only apply when the whole app or site isn't blocked. For example, if you want
YouTube for normal videos but not Shorts, unblock the YouTube app (and/or youtube.com) and leave the
Shorts switches on.

Everything is editable in the app. You can add any installed app, or any website (subdomains are
included, so `youtube.com` also covers `m.youtube.com`).

**Speed bump:** loosening a rule (unblocking, switching off Shorts blocking, or pausing everything)
needs a 15-second wait before you can confirm. Cravings fade quickly, and the wait gives you time to change your mind.
To change it, edit `UNBLOCK_DELAY_SECONDS` in `ui/Components.kt`.

## How it works

Android has no "block this app" API for regular apps, so Self Agency uses an
**Accessibility Service** (`service/BlockerAccessibilityService.kt`) to see which app is in front:

- **Apps**: when a blocked app's window appears, it presses Home.
- **Websites**: it reads the browser's address bar (`blocking/BrowserUrlReader.kt`) and matches it
  against your list (`blocking/UrlMatcher.kt`). It supports Chrome, Samsung Internet, Firefox, Brave,
  Edge, Opera, DuckDuckGo, Vivaldi and others, plus a generic fallback for any other browser.
- **Shorts / Reels in the app**: it looks for the views that make up the Shorts/Reels player
  (`blocking/ShortFormDetector.kt`) and presses Back.

Nothing is stored or sent anywhere. The app has no internet permission at all.

## Project layout

```
app/src/main/java/com/selfagency/app/
├── blocking/   Rules, URL matching, address-bar reading, Shorts/Reels detection
├── data/       BlockSettings: the saved block list (SharedPreferences)
├── service/    BlockerAccessibilityService: the part that actually blocks
└── ui/         MainActivity (settings), BlockedActivity ("Not now" screen), dialogs
app/src/test/   Unit tests for URL matching (run with ./gradlew test)
```

## Known limits / maintenance

- **YouTube/Instagram updates** can rename the views used to detect Shorts/Reels. If Shorts stops
  getting blocked, open Shorts with the phone connected, use Android Studio's **Layout Inspector**
  (or `adb shell uiautomator dump`) to find the new view id, and add it to the lists in
  `ShortFormDetector.kt`.
- **Browser address bars**: if a browser isn't detected, add its package name and address-bar
  view id to `BrowserUrlReader.kt`.
- Some phones (Xiaomi, Huawei, Oppo, etc.) kill background services aggressively. If blocking stops after a while,
  set Self Agency's battery usage to **Unrestricted** in the app's system settings.
- A determined you can always turn the accessibility service off in Settings. This app adds
  friction, it doesn't lock you out.
