#pragma once

#include <QString>
#include <QWidget>
#include <QVector>
#include <QColor>
#include <QPointF>

class QPropertyAnimation;
class QVariantAnimation;

namespace loader {

// 注入过程浮层 —— 深空星云主题 (增强版)
//
// 渲染层次 (从底到顶):
//   1. 深空径向渐变底色
//   2. 三层缓慢流动的紫/粉/青星云
//   3. 48 颗闪烁星点 (固定 seed)
//   4. 两条对数螺旋星臂
//   5. 全息网格 (双向缓慢滚动)
//   6. 三层同心六边形能量护盾 (旋转 + 呼吸)
//   7. 14 条辐射能量射线 (脉动 + 旋转)
//   8. 80 个发光小点沿圆周流动 (环形数据流)
//   9. 10 条垂直下落的数据流雨
//  10. 周期性扩散冲击波
//  11. 中心五重旋转环 + 脉冲核心 (色相缓慢漂移)
//  12. 扫描线
//  13. 漂浮粒子 (带渐隐拖尾)
//  14. 成功: 绿冲击 3 圈 + 白光爆闪 + 中心爆发光斑
//      失败: 红冲击 3 圈 + 中心爆发光斑
//  15. 状态文字 (彩色外发光)
//  16. 副标题
//  17. 进度条 (渐变 + 头部大光点 + 尾迹 + 色相随进度漂移)
//  18. 玻璃反射高光 + 呼吸边框 + 四角 L 形装饰
class InjectionOverlay : public QWidget {
    Q_OBJECT
    Q_PROPERTY(qreal spinnerAngle      READ spinnerAngle      WRITE setSpinnerAngle)
    Q_PROPERTY(qreal progress          READ progress          WRITE setProgress)
    Q_PROPERTY(qreal panelOpacity      READ panelOpacity      WRITE setPanelOpacity)
    Q_PROPERTY(qreal checkmarkProgress READ checkmarkProgress WRITE setCheckmarkProgress)
    Q_PROPERTY(qreal phase             READ phase             WRITE setPhase)
    Q_PROPERTY(qreal impactGlow        READ impactGlow        WRITE setImpactGlow)

public:
    InjectionOverlay(unsigned long pid, const QString& target,
                     const QString& commandLine,
                     QWidget* parent = nullptr);

    void start();

    qreal spinnerAngle()      const { return spinnerAngle_; }
    qreal progress()          const { return progress_; }
    qreal panelOpacity()      const { return panelOpacity_; }
    qreal checkmarkProgress() const { return checkmarkProgress_; }
    qreal phase()             const { return phase_; }
    qreal impactGlow()        const { return impactGlow_; }

    void setSpinnerAngle(qreal v)      { spinnerAngle_      = v; update(); }
    void setProgress(qreal v)          { progress_          = v; update(); }
    void setPanelOpacity(qreal v)      { panelOpacity_      = v; update(); }
    void setCheckmarkProgress(qreal v) { checkmarkProgress_ = v; update(); }
    void setPhase(qreal v)             { phase_             = v; update(); }
    void setImpactGlow(qreal v)        { impactGlow_        = v; update(); }

signals:
    void completed(bool success, unsigned long pid);

protected:
    void paintEvent(QPaintEvent*) override;

private:
    void onInjectResult(QString err);

    void drawNebula(QPainter& p, const QRectF& r, qreal t);
    void drawStars(QPainter& p, const QRectF& r, qreal t);
    void drawHelixArms(QPainter& p, const QPointF& c, qreal t);
    void drawGrid(QPainter& p, const QRectF& r, qreal t);
    void drawHexShield(QPainter& p, const QPointF& c, qreal t);
    void drawEnergyRays(QPainter& p, const QPointF& c, qreal t);
    void drawRingStream(QPainter& p, const QPointF& c, qreal t);
    void drawDataRain(QPainter& p, const QRectF& r, qreal t);
    void drawShockwaves(QPainter& p, const QPointF& c, qreal t);
    void drawSpinner(QPainter& p, const QPointF& c, qreal t);
    void drawScanLine(QPainter& p, const QRectF& r, qreal t);
    void drawParticles(QPainter& p);
    void drawImpact(QPainter& p, const QPointF& c);
    void drawProgressBar(QPainter& p, const QRectF& r, qreal t);
    void drawGlassAndBorder(QPainter& p, const QRectF& r, qreal t);

    struct Particle {
        qreal x, y, vx, vy;
        qreal life, maxLife;
        qreal size;
        qreal hue;
        qreal trailLen;
    };
    void spawnParticle();
    void tickParticles();

    unsigned long pid_;
    QString       target_;
    QString       commandLine_;
    QString       statusText_ = QStringLiteral("Injecting…");

    qreal spinnerAngle_      = 0.0;
    qreal progress_          = 0.0;
    qreal panelOpacity_      = 0.0;
    qreal checkmarkProgress_ = 0.0;
    qreal phase_             = 0.0;
    qreal impactGlow_        = 0.0;

    bool    injectDone_ = false;
    bool    injectOk_   = false;
    QString injectErr_;

    QVector<Particle> particles_;

    QPropertyAnimation* spinnerAnim_  = nullptr;
    QPropertyAnimation* progressAnim_ = nullptr;
    QVariantAnimation*  phaseAnim_    = nullptr;
    QPropertyAnimation* impactAnim_   = nullptr;
};

} // namespace loader