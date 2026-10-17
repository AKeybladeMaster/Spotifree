#include "HostFilter.h"
#include "UrlPolicy.h"
#include <QtTest/QTest>

class UrlPolicyTest : public QObject {
    Q_OBJECT
private slots:
    void mainFrameUrl() {
        QVERIFY(spotifree::allowedNavigation(QUrl("https://open.spotify.com/")));
        QVERIFY(spotifree::allowedNavigation(QUrl("https://accounts.spotify.com/")));
        QVERIFY(!spotifree::allowedNavigation(QUrl("http://open.spotify.com/")));
        QVERIFY(!spotifree::allowedNavigation(QUrl("javascript:alert(1)")));
        QVERIFY(!spotifree::allowedNavigation(QUrl("file:///etc/passwd")));
    }
    void encodedSearch() {
        QCOMPARE(spotifree::searchUrl("  AC/DC & jazz  ").toString(QUrl::FullyEncoded),
                 QString("https://open.spotify.com/search/AC%2FDC%20%26%20jazz"));
        QVERIFY(spotifree::searchUrl(" ").isEmpty());
        QVERIFY(spotifree::searchUrl(QString(513, 'x')).isEmpty());
    }
    void hostRules() {
        const QSet<QString> rules {"example.org"};
        QVERIFY(spotifree::blockedHost("sub.example.org", rules));
        QVERIFY(spotifree::blockedHost("EXAMPLE.ORG", rules));
        QVERIFY(!spotifree::blockedHost("notexample.org", rules));
    }
};
QTEST_GUILESS_MAIN(UrlPolicyTest)
#include "UrlPolicyTest.moc"
