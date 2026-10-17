#include "BrowserPage.h"
#include "UrlPolicy.h"
#include <QDebug>

namespace spotifree {
BrowserPage::BrowserPage(QWebEngineProfile *profile, QObject *parent)
    : QWebEnginePage(profile, parent) {}

bool BrowserPage::acceptNavigationRequest(
    const QUrl &url, NavigationType type, bool isMainFrame)
{
    Q_UNUSED(type);
    if (!isMainFrame) return true;
    if (allowedNavigation(url)) return true;
    qWarning().noquote() << "Blocked unsupported navigation scheme:" << url.scheme();
    return false;
}
}
