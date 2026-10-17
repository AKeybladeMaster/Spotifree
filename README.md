# Spotifree v1

**Unofficial** open-source Spotify web-player wrapper. Not affiliated with
Spotify. This is a fresh implementation, not a redistribution of Spotify binaries.

## Scope

- **Windows, Linux, macOS:** C++17 / Qt 6 WebEngine. The application window
  displays the Spotify Web Player without a custom profile header. One persistent
  browser profile per OS user. Search and playback commands are available through
  shortcuts (see below), with the Spotify UI handling ordinary navigation.
- **Android:** Native Java `WebView` in a fullscreen Activity, Android
  media notification and 4x1 home-screen widget. No custom overlay header.
- Ad-host filtering has **no UI**. Disabled by default for compatibility.
  Desktop: developer-controlled setting file. Android: build-time constant.
  Filtering cannot guarantee ad-free playback and may break streaming.
- No Rust backend: `Spotify.zip` is proprietary packaged Windows binaries, not
  source for a portable playback engine. No DRM circumvention.

## Desktop

Requires CMake 3.21+, a C++17 toolchain and Qt 6.8+ with **WebEngineCore**,
**WebEngineWidgets**, and Qt Test. Release CI builds with Qt 6.11.2. Linux
packages include Qt's deployed libraries and WebEngine resources, and still
depend on the host system's graphics and multimedia libraries.
```sh
cmake -S desktop -B build/desktop -DCMAKE_BUILD_TYPE=Release
cmake --build build/desktop --config Release
ctest --test-dir build/desktop --output-on-failure
```

| Shortcut | Command |
| --- | --- |
| Ctrl/Cmd + K | Search (native dialog to Spotify search page) |
| Ctrl/Cmd + H | Home |
| Ctrl/Cmd + R | Reload |
| Alt + Left / Right | Back / Forward |
| Ctrl/Cmd + Shift + Space | Play/pause |
| Ctrl/Cmd + Shift + Right / Left | Next / Previous |

The Spotify web UI itself contains its usual player controls. The desktop
wrapper stores one browser profile under the OS app-data directory.

To enable experimental filtering, create `<AppConfigLocation>/settings.json`
(e.g. via `QStandardPaths::AppConfigLocation`) containing:
```json
{"adFilteringEnabled": true}
```
The app reads it at startup. There is no user-facing toggle.

**DRM warning:** Qt WebEngine does not distribute Widevine; Spotify playback
may refuse to start without separately installed supported DRM components and
suitable codecs. We do not bundle, copy, or bypass CDMs. Login through some
third-party providers can also reject embedded browsers.

## Android

Requires Android SDK 35, JDK 17, and Gradle 8.13 (AGP 8.13.2).
```sh
cd android
gradle :app:assembleDebug
gradle :app:testDebugUnitTest
# output: app/build/outputs/apk/debug/app-debug.apk
```
The debug APK is signed with the standard local debug key; do not promote
that signature to production. Only a properly provisioned release key should
sign a production APK/AAB. Android WebView support for protected Spotify
playback depends on the device's system WebView and Spotify's policies.
Background playback depends on the Activity/WebView remaining alive.

## Release automation

`.github/workflows/release.yml` tests the platform sources on pushes and pull
requests to `new_spotify_v1`. A `v*` tag builds Windows ZIP, universal macOS
DMG, Linux tarball, and Android debug APK artifacts, then publishes them as a
GitHub prerelease. The APK uses Android's debug key; desktop packages are
unsigned. This is a release pipeline, not proof of account login, DRM support,
or playback on physical devices.

The provided archive is a **source release**. The current sandbox has no
Qt, Android SDK, Gradle or platform build agents; native binaries cannot
be produced locally here. All platforms require build and playback smoke tests
on their target OS, including DRM/login checks.

## Security and privacy

- No user credentials or tokens in source or logs.
- One browser profile per OS user; clearing the app's user data logs out.
- All HTTP links must be HTTPS; native navigation guards restrict schemes.
- Original `Spotify.zip` user data, executable, and CEF assets are excluded.
- No unofficial API endpoints, authentication bypass, or DRM changes.

See [architecture](docs/SPEC-1-spotifree-v1.md) and
[forensic notes](docs/FORENSICS.md) for detailed constraints.
