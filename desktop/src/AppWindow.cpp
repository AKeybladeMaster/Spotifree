#include "AppWindow.h"
#include "BrowserPage.h"
#include "HostFilter.h"
#include "UrlPolicy.h"

#include <QApplication>
#include <QDesktopServices>
#include <QDir>
#include <QIcon>
#include <QFile>
#include <QInputDialog>
#include <QLineEdit>
#include <QJsonDocument>
#include <QJsonObject>
#include <QKeySequence>
#include <QMessageBox>
#include <QShortcut>
#include <QStandardPaths>
#include <QStatusBar>
#include <QWebEngineNewWindowRequest>
#include <QWebEnginePage>
#include <QWebEngineSettings>
#include <QWebEngineView>
#include <QtGlobal>
#include <functional>

namespace spotifree {
namespace {
void shortcut(QWidget *parent, const QKeySequence &key, const std::function<void()> &action)
{
    auto *s = new QShortcut(key, parent);
    s->setContext(Qt::WindowShortcut);
    QObject::connect(s, &QShortcut::activated, parent, action);
}
}

AppWindow::AppWindow()
{
    setWindowTitle(QStringLiteral("Spotifree"));
    resize(1170, 790);
    setWindowIcon(QIcon(QStringLiteral(":/spotifree/spotifree.png")));
    // No profile picker, ad toggle, toolbar or custom interactive header.
    const QString data = QStandardPaths::writableLocation(QStandardPaths::AppDataLocation);
    QDir().mkpath(data);

    profile_ = new QWebEngineProfile(QStringLiteral("default"), this);
    profile_->setPersistentStoragePath(data + QStringLiteral("/browser"));
    profile_->setCachePath(data + QStringLiteral("/cache"));
    profile_->setPersistentCookiesPolicy(QWebEngineProfile::ForcePersistentCookies);
    profile_->setHttpCacheType(QWebEngineProfile::DiskHttpCache);

    hostFilter_ = new HostFilter(adFilterEnabled(), profile_);
    profile_->setUrlRequestInterceptor(hostFilter_);

    view_ = new QWebEngineView(this);
    auto *page = new BrowserPage(profile_, view_);
    page->settings()->setAttribute(QWebEngineSettings::PlaybackRequiresUserGesture, false);
    view_->setPage(page);
    setCentralWidget(view_);
    connect(page, &QWebEnginePage::titleChanged, this, [this](const QString &title) {
        setWindowTitle(title.isEmpty() ? QStringLiteral("Spotifree") : title + QStringLiteral(" — Spotifree"));
    });
    connect(page, &QWebEnginePage::newWindowRequested, this,
        [this](QWebEngineNewWindowRequest &request) {
            const QUrl url = request.requestedUrl();
            if (allowedNavigation(url) && url.scheme() == QStringLiteral("https")) {
                // Open popups externally rather than introduce a second browser profile.
                QDesktopServices::openUrl(url);
            }
        });
    connect(view_, &QWebEngineView::loadFinished, this, [this](bool ok) {
        if (!ok) {
            statusBar()->showMessage(QStringLiteral("Page load failed. Check connection, browser compatibility or DRM."), 10000);
            statusBar()->show();
        } else {
            statusBar()->hide();
        }
    });
    statusBar()->hide();
    bindShortcuts();
    view_->load(homeUrl());
}

bool AppWindow::adFilterEnabled() const
{
    const QString path = QStandardPaths::writableLocation(QStandardPaths::AppConfigLocation)
        + QStringLiteral("/settings.json");
    QFile f(path);
    if (!f.open(QIODevice::ReadOnly)) return false;
    const auto doc = QJsonDocument::fromJson(f.readAll());
    return doc.isObject() && doc.object().value(QStringLiteral("adFilteringEnabled")).toBool(false);
}

void AppWindow::search()
{
    bool ok = false;
    const QString query = QInputDialog::getText(
        this, QStringLiteral("Search Spotify"), QStringLiteral("Query"),
        QLineEdit::Normal, QString(), &ok);
    if (!ok) return;
    const QUrl url = searchUrl(query);
    if (!url.isEmpty() && url.isValid()) view_->load(url);
    else if (!query.trimmed().isEmpty()) {
        QMessageBox::warning(this, QStringLiteral("Invalid query"),
                             QStringLiteral("Search must contain no more than 512 characters."));
    }
}

void AppWindow::playerControl(const char *action)
{
    // The only caller input is one of the three hard-coded actions below.
    const QString js = QStringLiteral(R"JS(
        (function() {
            const action = "%1";
            const id = action === "next" ? "control-button-skip-forward"
                     : action === "previous" ? "control-button-skip-back"
                     : "control-button-playpause";
            const button = document.querySelector('[data-testid="' + id + '"]');
            if (button) { button.click(); return true; }
            const media = document.querySelector("audio, video");
            if (action === "toggle" && media) {
                if (media.paused) media.play().catch(() => {});
                else media.pause();
                return true;
            }
            return false;
        })();
    )JS").arg(QString::fromLatin1(action));
    if (view_->url().host() == QStringLiteral("open.spotify.com"))
        view_->page()->runJavaScript(js);
}

void AppWindow::bindShortcuts()
{
#if defined(Q_OS_MACOS)
    const Qt::KeyboardModifier primaryModifier = Qt::META;
#else
    const Qt::KeyboardModifier primaryModifier = Qt::CTRL;
#endif
    shortcut(this, QKeySequence(primaryModifier | Qt::Key_K), [this] { search(); });
    shortcut(this, QKeySequence(primaryModifier | Qt::Key_H), [this] { view_->load(homeUrl()); });
    shortcut(this, QKeySequence::Refresh, [this] { view_->reload(); });
    shortcut(this, QKeySequence::Back, [this] { view_->back(); });
    shortcut(this, QKeySequence::Forward, [this] { view_->forward(); });
    shortcut(this, QKeySequence(primaryModifier | Qt::SHIFT | Qt::Key_Space),
             [this] { playerControl("toggle"); });
    shortcut(this, QKeySequence(primaryModifier | Qt::SHIFT | Qt::Key_Right),
             [this] { playerControl("next"); });
    shortcut(this, QKeySequence(primaryModifier | Qt::SHIFT | Qt::Key_Left),
             [this] { playerControl("previous"); });
}
}
