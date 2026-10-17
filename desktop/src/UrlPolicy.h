#pragma once
#include <QUrl>
#include <QString>

namespace spotifree {
bool allowedNavigation(const QUrl &url);
QUrl searchUrl(const QString &query);
const QUrl &homeUrl();
}
