# Spotifree Android

An original compact Java/Android WebView app informed by the supplied reference APK. It includes Spotify Web Player navigation and search, native play/pause/previous/next controls, a foreground playback service with Android media session and notification actions, a home-screen widget, conservative built-in ad-host filtering, and encrypted cookie-snapshot profiles.

Build on Windows with `build.ps1` after installing Android SDK platform 35 and build-tools 35.0.0. Set `ANDROID_SDK_ROOT` (or `ANDROID_HOME`); this workspace also has a temporary SDK fallback at `%TEMP%\spotifree-tools-20261012\android-sdk`. The output is `dist/Spotifree-debug.apk`, signed with a local debug key. For distribution, sign a release build using your own protected release key.

The wrapper loads `https://open.spotify.com/`. It does not stream music independently, bypass DRM, or include a Spotify account. Playback depends on Spotify's site, account eligibility, Android System WebView and device codecs. DOM controls can change when Spotify updates the player. The filter targets ad-specific hosts in `assets/blocked-hosts.txt`; general Spotify media/CDN hosts are not blocked because that can break playback. The switch can be turned off in-app. Cookie-snapshot profiles are stored encrypted with Android Keystore, but some identity-provider cookies may not be exportable through WebView; reauthentication can be required when switching.

The supplied APK's decompiled code and scripts are not embedded in this implementation. The requested full analysis is in `../spotifree-decompilation/`.
