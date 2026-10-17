#pragma once

#include <QSet>
#include <QString>
#include <QWebEngineUrlRequestInterceptor>
#include <QWebEngineUrlRequestInfo>

namespace spotifree {
bool blockedHost(const QString &hostname, const QSet<QString> &rules);

class HostFilter final : public QWebEngineUrlRequestInterceptor {
public:
    explicit HostFilter(bool enabled, QObject *parent = nullptr);
    void interceptRequest(QWebEngineUrlRequestInfo &info) override;
private:
    const bool enabled_;
    QSet<QString> rules_;
};
}
