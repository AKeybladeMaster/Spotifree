# Spotifree Windows Desktop v2

Windows C# WinForms shell around the official Spotify Web Player using WebView2. It provides one persistent WebView2 profile, HTTPS-only navigation, search, browser navigation, play/pause/previous/next controls, keyboard shortcuts, and Windows media-key handling. It contains no profile-management UI and no ad-filter toggle. Host filtering is disabled unless a developer creates `%LOCALAPPDATA%\SpotifreeDesktopV2\settings.json` with `{"adFilteringEnabled":true}`.

Build from the repository root with `powershell -ExecutionPolicy Bypass -File .\build.ps1 -Target desktop`, or run this folder's `build.ps1`. The output is `dist\Spotifree-v2.exe`, with WebView2 support DLLs beside it. The target computer must have the Microsoft Edge WebView2 Runtime. The build uses the cached WebView2 SDK under `%TEMP%\spotifree-tools-20261012\webview2`, `WEBVIEW2_SDK_DIR`, or downloads the pinned package and verifies its SHA-256 checksum.

Spotify playback is handled by its web client and requires a supported account/browser environment. No Spotify backend, credentials, DRM keys, or proprietary Spotify binaries are included.