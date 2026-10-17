#pragma once

#include <QFrame>
#include <QString>

class QLabel;
class QPushButton;
class QPropertyAnimation;
class QVariantAnimation;

namespace loader {

// A single instance row inside InstanceList. Self-paints a rounded panel,
// hosts PID / title labels and an Inject button on the right. Hover
// transitions are driven by a QPropertyAnimation on hoverIntensity_; the
// row's own entrance is driven by entrance_ (opacity * vertical offset).
// phase_ drives the flowing background; injectGlow_ is the green pulse
// played once an injection succeeds.
class InstanceRow : public QFrame {
    Q_OBJECT
    Q_PROPERTY(qreal hoverIntensity READ hoverIntensity WRITE setHoverIntensity)
    Q_PROPERTY(qreal entrance       READ entrance       WRITE setEntrance)
    Q_PROPERTY(qreal highlight      READ highlight      WRITE setHighlight)
    Q_PROPERTY(qreal phase          READ phase          WRITE setPhase)
    Q_PROPERTY(qreal injectGlow     READ injectGlow     WRITE setInjectGlow)

public:
    InstanceRow(unsigned long pid, const QString& title, const QString& commandLine,
                QWidget* parent = nullptr);

    unsigned long pid() const { return pid_; }
    const QString& commandLine() const { return commandLine_; }
    const QString& title() const { return title_; }
    void updateTitle(const QString& title);

    qreal hoverIntensity() const { return hoverIntensity_; }
    void  setHoverIntensity(qreal v) { hoverIntensity_ = v; update(); }

    qreal entrance() const { return entrance_; }
    void  setEntrance(qreal v);

    qreal highlight() const { return highlight_; }
    void  setHighlight(qreal v) { highlight_ = v; update(); }

    qreal phase() const { return phase_; }
    void  setPhase(qreal v) { phase_ = v; update(); }

    qreal injectGlow() const { return injectGlow_; }
    void  setInjectGlow(qreal v) { injectGlow_ = v; update(); }

    void playInjectAnimation();
    void flashHighlight();
    void playEntrance();

signals:
    void injectClicked(unsigned long pid, const QString& title, const QString& commandLine);

protected:
    void enterEvent(QEnterEvent*) override;
    void leaveEvent(QEvent*) override;
    void paintEvent(QPaintEvent*) override;

private:
    void animateHover(qreal target);

    unsigned long pid_;
    QString       title_;
    QString       commandLine_;
    QLabel*       pidLabel_   = nullptr;
    QLabel*       titleLabel_ = nullptr;
    QPushButton*  injectBtn_  = nullptr;

    qreal hoverIntensity_ = 0.0;  // 0..1, drives background tint
    qreal entrance_       = 1.0;  // 0..1, drives opacity + slide-in offset
    qreal highlight_      = 0.0;  // 0..1, drives yellow indicator strength
    qreal phase_          = 0.0;  // 0..1, flowing background phase
    qreal injectGlow_     = 0.0;  // 0..1, green pulse after successful inject

    QVariantAnimation*  phaseAnim_  = nullptr;
    QPropertyAnimation* hoverAnim_  = nullptr;
    QPropertyAnimation* injectAnim_ = nullptr;
    // highlightAnim_ removed — never constructed, never used.
};

} // namespace loader