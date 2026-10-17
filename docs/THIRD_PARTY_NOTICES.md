# Third-party distribution guidance

This repository contains original Spotifree wrapper code and project metadata;
it does not embed Spotify software, audio, credentials, or a DRM decoder.

Qt 6 WebEngine is distributed under open-source/commercial license choices;
for released desktop builds, verify the license applicable to each Qt module,
include required license notices, and comply with LGPL obligations for
dynamically linked components. Check `https://www.qt.io/licensing/`.

Spotify, its name and its web service are owned by Spotify; this project is
unofficial. Service functionality depends on their published policies.

Android APKs depend on system-provided Android WebView and Android platform
components. Their licenses are not transferred by this source distribution.

GitHub Actions dependencies include `jurplel/install-qt-action`,
`actions/checkout`, Gradle setup, and optional Linux AppImage deployment tools.
Review licenses/security advisories and pin action SHAs before trusted releases.

Do not package a Widevine CDM or a proprietary Spotify executable from user
machines or the submitted `Spotify.zip`.
