# Spotifree Android v2

Native Java WebView wrapper for the official Spotify Web Player. This project keeps one persistent WebView data profile per install, HTTPS-only navigation, Spotify search, native navigation and playback buttons, media-session notification controls, and a 4x1 home-screen widget. It has no profile-switching controls and no visible ad-filter settings. The host filter is off in source by default.

Build from the repository root with `powershell -ExecutionPolicy Bypass -File .\spotifree-android-v2\build.ps1`, or run this folder's `build.ps1`. The output is `dist\spotifree-android.apk`. The local Android SDK must include platform 35 and build-tools 35.0.0; `ANDROID_SDK_ROOT` or `ANDROID_HOME` may select it. The script checks `%TEMP%\spotifree-tools-20261017\android-sdk` and falls back to `%TEMP%\spotifree-tools-20261012\android-sdk`.

The APK is signed with the local build key. Spotify playback runs in Android System WebView and depends on account eligibility, WebView version, codecs, and Spotify policy. No Spotify backend, credentials, DRM keys, or native playback engine are included.