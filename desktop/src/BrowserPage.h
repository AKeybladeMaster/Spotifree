#pragma once
#include <QWebEnginePage>

namespace spotifree {
class BrowserPage final : public QWebEnginePage {
public:
    explicit BrowserPage(QWebEngineProfile *profile, QObject *parent = nullptr);
protected:
    bool acceptNavigationRequest(const QUrl &url, NavigationType type, bool isMainFrame) override;
};
}
