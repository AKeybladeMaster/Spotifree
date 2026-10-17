#include "HostFilter.h"
#include <QFile>
#include <QTextStream>

namespace spotifree {
bool blockedHost(const QString &hostname, const QSet<QString> &rules)
{
    const QString host = hostname.toLower();
    for (const QString &rule : rules) {
        if (host == rule || host.endsWith(QLatin1Char('.') + rule)) return true;
    }
    return false;
}

HostFilter::HostFilter(bool enabled, QObject *parent)
    : QWebEngineUrlRequestInterceptor(parent), enabled_(enabled)
{
    if (!enabled_) return;
    QFile list(QStringLiteral(":/spotifree/blocked-hosts.txt"));
    if (!list.open(QIODevice::ReadOnly | QIODevice::Text)) return;
    QTextStream stream(&list);
    while (!stream.atEnd()) {
        const QString line = stream.readLine().section(QLatin1Char('#'), 0, 0).trimmed().toLower();
        if (line.isEmpty() || line.contains(QLatin1Char('/')) || line.contains(QLatin1Char(' '))) continue;
        rules_.insert(line);
    }
}

void HostFilter::interceptRequest(QWebEngineUrlRequestInfo &info)
{
    // Immutable policy: Qt may invoke this on a worker thread.
    if (enabled_ && blockedHost(info.requestUrl().host(), rules_)) {
        info.block(true);
    }
}
}
