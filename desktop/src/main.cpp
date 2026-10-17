#include "AppWindow.h"
#include <QApplication>
#include <QCoreApplication>

int main(int argc, char *argv[])
{
    QApplication app(argc, argv);
    QCoreApplication::setOrganizationName(QStringLiteral("Spotifree"));
    QCoreApplication::setApplicationName(QStringLiteral("Spotifree"));
    QCoreApplication::setApplicationVersion(QStringLiteral("1.0.0"));
    spotifree::AppWindow window;
    window.show();
    return app.exec();
}
