# Spotifree

Spotifree is a lightweight Spotify Web Player wrapper for Windows and Android. The Windows app uses C# and WebView2; the Android app uses Java and Android WebView. Both provide search, media controls, isolated or saved account profiles, and toggleable host-based ad filtering. Android also has a media notification and home-screen widget.

Download the Windows package and Android debug APK from [Releases](https://github.com/AKeybladeMaster/Spotifree/releases). Source for the two apps is on the [dev branch](https://github.com/AKeybladeMaster/Spotifree/tree/dev).

Spotify playback requires an account and a compatible embedded browser. Ad filtering does not guarantee ad-free playback and can be turned off. The Android APK is debug-signed for testing; a release signing key is needed for production distribution. The Windows package includes its WebView2 libraries and needs the Microsoft Edge WebView2 Runtime.
