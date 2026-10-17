# Spotifree source review

## Review boundary

This review covers the newly supplied source in `android/`, `desktop/`,
`tests/`, and the requirements in [SPEC-1](SPEC-1-spotifree-v1.md). The
directories `spotifree-android/` and `spotifree-desktop/` are legacy and were
left unchanged. No new APK or desktop executable was present in the source
trees, so this is a source-level reconstruction and review, not a decompilation
of packaged application binaries. The installed Spotify client was not used as
a target.

The REA skill instructions were reviewed, but no REA executable or analysis
service was available in this environment. An attempt to invoke the unverified
`rea-agents` npm package was rejected by automatic review because it would run
third-party package code with elevated permissions. Consequently, there are no
REA-generated evidence IDs or binary-analysis claims in this report. The
project's `scripts/` directory was not run.

## Application architecture

The project is a web-player wrapper. Android embeds `https://open.spotify.com/`
in a native `WebView`; desktop embeds it in Qt WebEngine. Neither app contains
a Spotify backend or a native music decoder. Spotify's web player remains
responsible for authentication, search results, playlists, and streaming.

| Capability | Android source | Desktop source |
| --- | --- | --- |
| Main view | One fullscreen `WebView` in `MainActivity` | One `QWebEngineView` in `AppWindow` |
| Persistent profile | Android WebView's per-app cookie and storage profile | One named `QWebEngineProfile` with disk storage under app data |
| Search | Native dialog builds a Spotify search route; trims input and caps it at 512 characters | Native dialog uses percent-encoded Spotify search URL; trims and caps input at 512 characters |
| Playback controls | Native media session, notification actions, widget actions, and media keys call a fixed action set on the active WebView | Keyboard shortcuts click fixed Spotify control selectors or use the page's media element |
| Popups | Not opened as new native browser windows | HTTPS popup requests are sent to the system browser to retain one in-app profile |
| Ad filtering | `AdFilter` and bundled host rules; disabled by default; no UI | Request interceptor and bundled host rules; disabled unless the private config setting enables it; no UI |

## Navigation and privacy behavior

Android opens only an exact `open.spotify.com` deep link at startup; other
launch intents fall back to the home page. The WebView blocks cleartext and
non-HTTPS navigation, disables file/content access and mixed content, enables
Safe Browsing, and permits cookies (including third-party cookies) to support
the web sign-in flow. `mailto:` and `tel:` links are handed to Android. Other
HTTPS navigation is allowed, so this is not a Spotify-domain allowlist.

Desktop `BrowserPage` allows HTTPS main-frame navigation and `about:blank`, and
blocks other main-frame schemes. HTTPS popup requests open externally. Both
applications require page JavaScript for Spotify, but neither exposes a
JavaScript interface to remote content. The native code injects only a
read-only media-state probe and closed-set play/pause/next/previous commands.

The Android app disables backup. Desktop cookies and WebEngine state live in
the one per-user app-data profile. Clearing app data removes the local profile
and signs the user out. The wrapper does not read Spotify credentials into
native code or write them to logs.

## Media, widget, and filtering behavior

Android's `PlaybackService` exposes a platform `MediaSession` and notification
while the web player is active. The 4x1 widget issues the same fixed controls
through `MainActivity`; it does not play independently after the WebView closes.
The service is a control/metadata bridge, not a streaming engine. Desktop uses
the embedded Spotify controls plus keyboard shortcuts and has no separate
native media session.

The filtering feature is deliberately hidden and off by default. Android's
compile-time setting remains false. Desktop checks the private application
settings file for `adFilteringEnabled`; no control is rendered. Host matching
uses exact names and dot-boundary subdomains to avoid matching a suffix such as
`notexample.org` for a rule named `example.org`. Filtering may break portions
of the web player and cannot guarantee ad-free playback.

## Issues addressed in this rewrite

- Android previously treated the page title as a media item before a track was
  available, which could create an empty notification. It now checks for media
  metadata or a timed media element before publishing media state.
- Android now stops the control service and restores the widget placeholder
  when media disappears, navigation leaves Spotify, or the user finishes the
  Activity. Configuration changes do not stop the service.
- Widget play/pause and skip controls now have accessibility descriptions.
- Desktop search now rejects an empty `QUrl` as well as an invalid one, so an
  empty query cannot load a blank page.
- Desktop load failures now show a visible status message. The app window uses
  the included icon, Linux installs include a launcher entry and icon, and
  macOS shortcuts use the Command modifier.
- Qt's standard CMake setup and generated deployment script are used for
  runtime packaging. CMake builds the executable as `Spotifree` on each
  desktop platform.
- A GitHub Actions workflow now builds/tests each source tree and publishes
  preview artifacts only for `v*` tags.

## Build and release limits

The preview workflow produces `Spotifree-windows-x64.zip`,
`Spotifree-macos-universal.dmg`, `Spotifree-linux-x64.tar.gz`, and
`app-debug.apk`. The Android APK uses the debug signing key. Desktop packages
are unsigned. GitHub Actions action references use major tags and must be
reviewed and pinned to verified commit SHAs before production releases.

Qt WebEngine does not ship Widevine. Android relies on the system WebView and
its supported codecs/DRM. Protected playback, authentication, codecs, and
account access therefore require testing on real devices and target operating
systems. This source review cannot establish that Spotify login or playback
will work in a particular embedded browser. Do not copy Spotify executables,
CDMs, user data, or other proprietary files into a release.

## Local verification

Source checks run for this review:

- `python tests/test_source_contract.py`
- Java compilation plus `tests/HostRulesSmoke.java`
- The Qt C++ review linter over the five desktop `.cpp` files

The local environment did not have Qt, CMake, Gradle, Android SDK, or platform
build runners available for a complete native build. The GitHub workflow is
the configured cross-platform build path; its result and real-device playback
remain to be verified after pushing the source and creating a `v*` tag.

## Primary references

- [Qt CMake deployment API](https://doc.qt.io/qt-6/cmake-deployment.html)
- [Deploying Qt WebEngine applications](https://doc.qt.io/QT-6/qtwebengine-deploying.html)
- [Qt WebEngine DRM and Widevine behavior](https://doc.qt.io/qt-6/qtwebengine-features.html)
