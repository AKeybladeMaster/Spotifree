#include "UrlPolicy.h"
#include <QUrl>

namespace spotifree {
bool allowedNavigation(const QUrl &url)
{
    if (!url.isValid()) return false;
    // HTTP navigation is intentionally blocked; OAuth redirects use HTTPS.
    return url.scheme() == QStringLiteral("https")
        || url == QUrl(QStringLiteral("about:blank"));
}

QUrl searchUrl(const QString &query)
{
    const QString trimmed = query.trimmed();
    if (trimmed.isEmpty() || trimmed.size() > 512) return QUrl();
    return QUrl(QStringLiteral("https://open.spotify.com/search/")
        + QString::fromLatin1(QUrl::toPercentEncoding(trimmed)));
}

const QUrl &homeUrl()
{
    static const QUrl HOME(QStringLiteral("https://open.spotify.com/"));
    return HOME;
}
}
