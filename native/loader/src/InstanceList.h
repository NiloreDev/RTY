#pragma once

#include <QWidget>
#include <QMap>
#include <QVector>
#include <QString>

class QScrollArea;
class QVBoxLayout;
class QLabel;

namespace loader {

class InstanceRow;

// Shared data model for one detected Minecraft instance. Defined here (not
// forward-declared) because InstanceList::setInstances / instanceForPid and
// MainWindow::refreshNow all read .pid/.title/.commandLine directly.
struct Instance {
    unsigned long pid = 0;
    QString       title;
    QString       commandLine;
};

class InstanceList : public QWidget {
    Q_OBJECT

public:
    explicit InstanceList(QWidget* parent = nullptr);

    void setInstances(const QVector<Instance>& list);
    void setInteractive(bool on);
    void markInjected(unsigned long pid);
    Instance instanceForPid(unsigned long pid) const;

    int count() const { return rows_.size(); }

signals:
    void injectRequested(unsigned long pid, const QString& title, const QString& commandLine);

private:
    void updateEmptyState();

    QScrollArea* scroll_          = nullptr;
    QWidget*     container_       = nullptr;
    QVBoxLayout* containerLayout_ = nullptr;
    QLabel*      note_            = nullptr;
    QLabel*      emptyLabel_      = nullptr;

    QMap<unsigned long, InstanceRow*> rows_;
    bool interactive_ = true;
};

} // namespace loader