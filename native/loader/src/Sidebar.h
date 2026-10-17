#ifndef LOADER_SIDEBAR_H
#define LOADER_SIDEBAR_H

#include <QWidget>

class QButtonGroup;

namespace loader {

class Sidebar : public QWidget {
    Q_OBJECT

public:
    explicit Sidebar(QWidget* parent = nullptr);

signals:
    void injectClicked();
    void settingsClicked();

private:
    void buildUi();

    QButtonGroup* group_ = nullptr;
};

} // namespace loader

#endif // LOADER_SIDEBAR_H
