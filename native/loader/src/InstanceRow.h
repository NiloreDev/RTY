#pragma once

#include <QFrame>
#include <QString>
#include <QTimer>

class QLabel;
class QPushButton;
class QPropertyAnimation;

namespace loader {

class InstanceRow : public QFrame {
    Q_OBJECT
    Q_PROPERTY(qreal hoverIntensity READ hoverIntensity WRITE setHoverIntensity)
    Q_PROPERTY(qreal entrance       READ entrance       WRITE setEntrance)
    Q_PROPERTY(qreal highlight      READ highlight      WRITE setHighlight)

public:
    InstanceRow(unsigned long pid, const QString& title, const QString& commandLine,
                QWidget* parent = nullptr);

    unsigned long pid() const { return pid_; }
    const QString& commandLine() const { return commandLine_; }
    const QString& title() const { return title_; }
    void updateTitle(const QString& title);
    void setNativeBypassed(bool on);

    qreal hoverIntensity() const { return hoverIntensity_; }
    void  setHoverIntensity(qreal v) { hoverIntensity_ = v; update(); }

    qreal entrance() const { return entrance_; }
    void  setEntrance(qreal v);

    qreal highlight() const { return highlight_; }
    void  setHighlight(qreal v) { highlight_ = v; update(); }

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
    void onTick(); // 驱动流光动画

    unsigned long pid_;
    QString       title_;
    QString       commandLine_;
    QLabel*       pidLabel_   = nullptr;
    QLabel*       titleLabel_ = nullptr;
    QLabel*       nativeBadge_ = nullptr;
    QPushButton*  injectBtn_  = nullptr;

    qreal hoverIntensity_ = 0.0;
    qreal entrance_       = 1.0;
    qreal highlight_      = 0.0;
    qreal time_           = 0.0;  // 全局时间，驱动扫描线和流光

    QTimer* m_animTimer = nullptr;
    QPropertyAnimation* hoverAnim_     = nullptr;
    QPropertyAnimation* highlightAnim_ = nullptr;
};

} // namespace loader