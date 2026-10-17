#pragma once

#include <QWidget>
#include <QPointF>
#include <QColor>
#include <QVector>

class QTimer;

namespace loader {

// 右下角自绘 Toast 通知。替代 QSystemTrayIcon::showMessage 的原因:
// 系统托盘气泡样式由 Windows 控制, 无法做成 Nebula 主题动画。
//
// 动效:
//   · 从右下角滑入 (OutBack 回弹)
//   · 图标圆周围的粒子持续环绕 + 呼吸发光
//   · 图标圆外两层虚线弧反向旋转
//   · 入场时一道高光扫过整个卡片
//   · 底边进度条倒计时
//   · 停留结束下滑淡出并自毁
//   · 全局只保留一个 toast, 新出现的直接关闭旧的
class ToastNotification : public QWidget {
    Q_OBJECT
    Q_PROPERTY(qreal panelOpacity READ panelOpacity WRITE setPanelOpacity)
    Q_PROPERTY(qreal slideOffset  READ slideOffset  WRITE setSlideOffset)
    Q_PROPERTY(qreal iconProgress READ iconProgress WRITE setIconProgress)
    Q_PROPERTY(qreal shinePos     READ shinePos     WRITE setShinePos)
    Q_PROPERTY(qreal lifeProgress READ lifeProgress WRITE setLifeProgress)
    Q_PROPERTY(qreal pulse        READ pulse        WRITE setPulse)

public:
    enum Kind { Success, Error, Info };

    // 弹出通知, 自动管理生命周期。
    static void show(const QString& title,
                     const QString& body,
                     Kind kind = Success,
                     int durationMs = 2800);

    qreal panelOpacity() const { return panelOpacity_; }
    qreal slideOffset()  const { return slideOffset_;  }
    qreal iconProgress() const { return iconProgress_; }
    qreal shinePos()     const { return shinePos_;     }
    qreal lifeProgress() const { return lifeProgress_; }
    qreal pulse()        const { return pulse_;        }

    void setPanelOpacity(qreal v);
    void setSlideOffset(qreal v);
    void setIconProgress(qreal v) { iconProgress_ = v; update(); }
    void setShinePos(qreal v)     { shinePos_     = v; update(); }
    void setLifeProgress(qreal v) { lifeProgress_ = v; update(); }
    void setPulse(qreal v)        { pulse_        = v; update(); }

protected:
    void paintEvent(QPaintEvent*) override;
    void mousePressEvent(QMouseEvent*) override;

private:
    ToastNotification(const QString& title, const QString& body,
                      Kind kind, int durationMs);
    void startAnimations(int durationMs);
    QColor kindColor()  const;
    QColor kindColor2() const;

    struct Particle {
        qreal angle;
        qreal radius;
        qreal speed;
        qreal size;
        qreal phase;
        qreal alpha;
    };

    QString title_;
    QString body_;
    Kind    kind_;

    qreal panelOpacity_ = 0.0;
    qreal slideOffset_  = 30.0;
    qreal iconProgress_ = 0.0;
    qreal shinePos_     = -0.3;
    qreal lifeProgress_ = 0.0;
    qreal pulse_        = 0.0;
    qreal time_         = 0.0;

    QPoint basePos_;

    QTimer*           particleTimer_ = nullptr;
    QVector<Particle> particles_;
};

} // namespace loader