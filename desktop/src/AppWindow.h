#pragma once

#include <QMainWindow>
#include <QWebEngineProfile>

class QWebEngineView;

namespace spotifree {
class HostFilter;

class AppWindow final : public QMainWindow {
public:
    AppWindow();
private:
    void bindShortcuts();
    void search();
    void playerControl(const char *action);
    bool adFilterEnabled() const;

    QWebEngineProfile *profile_ = nullptr;
    QWebEngineView *view_ = nullptr;
    HostFilter *hostFilter_ = nullptr;
};
}
