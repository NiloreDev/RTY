#pragma once

#include <QString>
#include <QWidget>
#include <QTimer>

namespace loader {

class InjectionOverlay : public QWidget {
    Q_OBJECT
    Q_PROPERTY(qreal panelOpacity      READ panelOpacity      WRITE setPanelOpacity)
    Q_PROPERTY(qreal progress          READ progress          WRITE setProgress)
    Q_PROPERTY(qreal energyBurst       READ energyBurst       WRITE setEnergyBurst)

public:
    InjectionOverlay(unsigned long pid, const QString& target,
                     const QString& commandLine,
                     QWidget* parent = nullptr);

    void start();

    qreal panelOpacity()      const { return panelOpacity_; }
    qreal progress()          const { return progress_; }
    qreal energyBurst()       const { return energyBurst_; }

    void setPanelOpacity(qreal v)      { panelOpacity_      = v; update(); }
    void setProgress(qreal v)          { progress_          = v; update(); }
    void setEnergyBurst(qreal v)       { energyBurst_       = v; update(); }

signals:
    void completed(bool success, unsigned long pid);

protected:
    void paintEvent(QPaintEvent*) override;

private:
    void onInjectResult(QString err);
    void onTick();

    unsigned long pid_;
    QString       target_;
    QString       commandLine_;
    QString       statusText_       = QStringLiteral("Injecting Payload…");

    qreal panelOpacity_      = 0.0;
    qreal progress_          = 0.0;
    qreal energyBurst_       = 0.0;
    qreal time_              = 0.0;

    bool    injectDone_ = false;
    bool    injectOk_   = false;
    QString injectErr_;

    QTimer*            frameTimer_  = nullptr;
};

} // namespace loader