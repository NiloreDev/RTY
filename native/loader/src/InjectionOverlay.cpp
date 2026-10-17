#include "InjectionOverlay.h"

#include "loader.h"

#include <QApplication>
#include <QEasingCurve>
#include <QFont>
#include <QFontMetricsF>
#include <QLinearGradient>
#include <QPainter>
#include <QPainterPath>
#include <QPointer>
#include <QPropertyAnimation>
#include <QRadialGradient>
#include <QConicalGradient>
#include <QScreen>
#include <QSequentialAnimationGroup>
#include <QVariantAnimation>
#include <QRandomGenerator>

#include <algorithm>
#include <cmath>
#include <thread>

namespace loader {

namespace {
constexpr int    kW            = 520;
constexpr int    kH            = 300;
constexpr int    kCornerRadius = 20;
constexpr qreal  kTau          = 6.2831853071795864769;
constexpr int    kMaxParticles = 180;
constexpr qreal  kPhaseSec     = 3.0;

// 稳定伪随机 0..1 (同一 i/salt 恒定)
inline qreal hash01(int i, int salt) {
    uint32_t x = uint32_t(i) * 2654435761u + uint32_t(salt) * 40503u;
    x ^= x >> 16; x *= 0x7feb352du; x ^= x >> 15;
    x *= 0x846ca68bu; x ^= x >> 16;
    return (x & 0xFFFFFFu) / qreal(0xFFFFFFu);
}

// 色相偏移 (deg, 加减模)
inline QColor hueShift(const QColor& c, qreal deg) {
    int h, s, l, a;
    c.getHsl(&h, &s, &l, &a);
    int nh = h + int(deg);
    nh = ((nh % 360) + 360) % 360;
    return QColor::fromHsl(nh, s, l, a);
}
} // namespace

// =====================================================================
// 构造
// =====================================================================
InjectionOverlay::InjectionOverlay(unsigned long pid, const QString& target,
                                   const QString& commandLine,
                                   QWidget* parent)
        : QWidget(parent), pid_(pid), target_(target), commandLine_(commandLine) {
    setWindowFlags(Qt::FramelessWindowHint
                   | Qt::Tool
                   | Qt::WindowStaysOnTopHint);
    setAttribute(Qt::WA_TranslucentBackground);
    setAttribute(Qt::WA_DeleteOnClose);
    resize(kW, kH);

    if (parent) {
        QPoint pcentre = parent->geometry().center();
        move(pcentre - QPoint(kW / 2, kH / 2));
    } else if (auto* scr = QApplication::primaryScreen()) {
        QRect g = scr->availableGeometry();
        move(g.center() - QPoint(kW / 2, kH / 2));
    }
}

// =====================================================================
// 粒子系统
// =====================================================================
void InjectionOverlay::spawnParticle() {
    if (particles_.size() >= kMaxParticles) return;
    QRandomGenerator* rng = QRandomGenerator::global();

    Particle p;
    const qreal angle = rng->bounded(kTau);
    const qreal r0 = 12.0 + rng->bounded(26.0);
    p.x = kW / 2.0 + std::cos(angle) * r0;
    p.y = (kH / 2.0 - 20.0) + std::sin(angle) * r0;

    const qreal speed = 0.5 + rng->bounded(2.2);
    p.vx = std::cos(angle) * speed;
    p.vy = std::sin(angle) * speed;

    p.maxLife = 0.9 + rng->bounded(1.6);
    p.life = p.maxLife;
    p.size = 0.9 + rng->bounded(2.4);
    p.hue  = rng->bounded(1000) / 1000.0;
    p.trailLen = 5.0 + rng->bounded(8.0);

    particles_.append(p);
}

void InjectionOverlay::tickParticles() {
    if (!injectDone_ && QRandomGenerator::global()->bounded(100) < 82) {
        spawnParticle();
        spawnParticle();
    }

    for (int i = particles_.size() - 1; i >= 0; --i) {
        auto& p = particles_[i];
        p.x += p.vx;
        p.y += p.vy;
        p.vy += 0.018;
        p.vx *= 0.992;
        p.vy *= 0.992;
        p.life -= 0.018;

        if (p.life <= 0 || p.x < -24 || p.x > kW + 24 ||
            p.y < -24 || p.y > kH + 24) {
            particles_.removeAt(i);
        }
    }
    update();
}

// =====================================================================
// 启动
// =====================================================================
void InjectionOverlay::start() {
    show();
    raise();
    activateWindow();

    auto* panelFade = new QPropertyAnimation(this, "panelOpacity", this);
    panelFade->setDuration(320);
    panelFade->setStartValue(0.0);
    panelFade->setEndValue(1.0);
    panelFade->setEasingCurve(QEasingCurve::OutCubic);
    panelFade->start(QAbstractAnimation::DeleteWhenStopped);

    spinnerAnim_ = new QPropertyAnimation(this, "spinnerAngle", this);
    spinnerAnim_->setDuration(1100);
    spinnerAnim_->setStartValue(0.0);
    spinnerAnim_->setEndValue(360.0);
    spinnerAnim_->setLoopCount(-1);
    spinnerAnim_->start();

    phaseAnim_ = new QVariantAnimation(this);
    phaseAnim_->setStartValue(0.0);
    phaseAnim_->setEndValue(1.0);
    phaseAnim_->setDuration(int(kPhaseSec * 1000.0));
    phaseAnim_->setLoopCount(-1);
    phaseAnim_->setEasingCurve(QEasingCurve::Linear);
    connect(phaseAnim_, &QVariantAnimation::valueChanged, this,
            [this](const QVariant& v) {
                phase_ = v.toReal();
                tickParticles();
                update();
            });
    phaseAnim_->start();

    progressAnim_ = new QPropertyAnimation(this, "progress", this);
    progressAnim_->setDuration(900);
    progressAnim_->setStartValue(0.0);
    progressAnim_->setEndValue(0.7);
    progressAnim_->setEasingCurve(QEasingCurve::OutCubic);
    progressAnim_->start();

    QPointer<InjectionOverlay> self = this;
    const unsigned long pid = pid_;
    std::thread([self, pid]() {
        std::wstring err = inject(pid);
        QString qerr = QString::fromWCharArray(err.c_str(),
                                               static_cast<int>(err.size()));
        if (!self) return;
        QMetaObject::invokeMethod(self, [self, qerr]() {
            if (self) self->onInjectResult(qerr);
        }, Qt::QueuedConnection);
    }).detach();
}

// =====================================================================
// 结果
// =====================================================================
void InjectionOverlay::onInjectResult(QString err) {
    injectDone_ = true;
    injectOk_   = err.isEmpty();
    injectErr_  = err;

    if (spinnerAnim_)  spinnerAnim_->stop();
    if (progressAnim_) progressAnim_->stop();

    statusText_ = injectOk_
        ? QStringLiteral("Injection complete")
        : QStringLiteral("Failed: ") + injectErr_;

    impactAnim_ = new QPropertyAnimation(this, "impactGlow", this);
    impactAnim_->setDuration(1100);
    impactAnim_->setStartValue(0.0);
    impactAnim_->setEndValue(1.0);
    impactAnim_->setEasingCurve(QEasingCurve::OutCubic);
    impactAnim_->start(QAbstractAnimation::DeleteWhenStopped);

    update();

    auto* finish = new QPropertyAnimation(this, "progress", this);
    finish->setDuration(280);
    finish->setStartValue(progress_);
    finish->setEndValue(1.0);
    finish->setEasingCurve(QEasingCurve::OutCubic);

    auto* check = new QPropertyAnimation(this, "checkmarkProgress", this);
    check->setDuration(440);
    check->setStartValue(0.0);
    check->setEndValue(1.0);
    check->setEasingCurve(QEasingCurve::OutBack);

    auto* fadeOut = new QPropertyAnimation(this, "panelOpacity", this);
    fadeOut->setDuration(340);
    fadeOut->setStartValue(1.0);
    fadeOut->setEndValue(0.0);
    fadeOut->setEasingCurve(QEasingCurve::InCubic);

    auto* seq = new QSequentialAnimationGroup(this);
    seq->addAnimation(finish);
    seq->addAnimation(check);
    seq->addPause(950);
    seq->addAnimation(fadeOut);

    connect(seq, &QAbstractAnimation::finished, this, [this]() {
        emit completed(injectOk_, pid_);
        close();
    });
    seq->start(QAbstractAnimation::DeleteWhenStopped);
}

// =====================================================================
// 视觉层
// =====================================================================

// 1. 星云
void InjectionOverlay::drawNebula(QPainter& p, const QRectF& r, qreal t) {
    auto blob = [&](QPointF c, qreal rad, QColor base) {
        QRadialGradient g(c, rad);
        g.setColorAt(0.0, base);
        QColor mid = base; mid.setAlphaF(base.alphaF() * 0.35);
        g.setColorAt(0.5, mid);
        QColor outer = base; outer.setAlphaF(0);
        g.setColorAt(1.0, outer);
        p.fillRect(r, g);
    };

    blob(r.center() + QPointF(std::cos(t * 0.60) * r.width()  * 0.22,
                              std::sin(t * 0.47) * r.height() * 0.24),
         r.width() * 0.62, QColor(150, 80, 230, 105));

    blob(r.center() + QPointF(std::cos(t * 0.38 + 2.1) * r.width()  * 0.26,
                              std::sin(t * 0.72 + 1.3) * r.height() * 0.26),
         r.width() * 0.52, QColor(255, 96, 190, 88));

    blob(r.center() + QPointF(std::cos(t * 0.83 + 4.0) * r.width()  * 0.24,
                              std::sin(t * 0.55 + 3.0) * r.height() * 0.28),
         r.width() * 0.46, QColor(80, 220, 255, 66));
}

// 2. 星点
void InjectionOverlay::drawStars(QPainter& p, const QRectF& r, qreal t) {
    p.setPen(Qt::NoPen);
    for (int i = 0; i < 52; ++i) {
        const qreal sx = r.left() + hash01(i, 1) * r.width();
        const qreal sy = r.top()  + hash01(i, 2) * r.height();
        const qreal tw = 0.35 + 0.65 * std::sin(t * 2.4 + i * 0.7);
        const qreal sz = 0.5 + 0.95 * tw;
        QColor c(255, 240, 255, int(190 * tw));
        p.setBrush(c);
        p.drawEllipse(QPointF(sx, sy), sz, sz);
    }
}

// 3. 两条对数螺旋星臂
void InjectionOverlay::drawHelixArms(QPainter& p, const QPointF& c, qreal t) {
    const int arms = 2;
    const int segs = 70;
    const qreal rot = t * 18.0 * kTau / 360.0;  // 缓慢旋转

    p.setBrush(Qt::NoBrush);

    for (int a = 0; a < arms; ++a) {
        const qreal baseAng = a * kTau / arms;
        QPainterPath path;
        bool first = true;

        for (int s = 0; s < segs; ++s) {
            const qreal u = s / qreal(segs - 1);   // 0..1
            const qreal ang = baseAng + u * 2.6 * kTau + rot;
            const qreal rad = 15.0 + u * 220.0;

            const QPointF pt = c + QPointF(std::cos(ang), std::sin(ang)) * rad;
            if (first) { path.moveTo(pt); first = false; }
            else path.lineTo(pt);
        }

        // 沿路径点撒小光点
        for (int s = 0; s < 30; ++s) {
            const qreal u = hash01(s, 100 + a);
            const qreal ang = baseAng + u * 2.6 * kTau + rot;
            const qreal rad = 15.0 + u * 220.0;
            const QPointF pt = c + QPointF(std::cos(ang), std::sin(ang)) * rad;
            const qreal size = 0.6 + u * 1.6;
            const int alpha = int(180 * (1.0 - u) * (0.6 + 0.4 * std::sin(t * 3.0 + s)));
            QColor col = hueShift(QColor(180, 200, 255), t * 40.0);
            col.setAlpha(alpha);
            p.setPen(Qt::NoPen);
            p.setBrush(col);
            p.drawEllipse(pt, size, size);
        }
    }
}

// 4. 全息网格
void InjectionOverlay::drawGrid(QPainter& p, const QRectF& r, qreal t) {
    const qreal step = 34.0;
    const qreal ox = std::fmod(t * 6.0, step);
    const qreal oy = std::fmod(t * 4.0, step);

    QPen g(QColor(120, 180, 255, 16), 1);
    p.setPen(g);
    for (qreal x = r.left() - step + ox; x < r.right() + step; x += step)
        p.drawLine(QPointF(x, r.top()), QPointF(x, r.bottom()));
    for (qreal y = r.top() - step + oy; y < r.bottom() + step; y += step)
        p.drawLine(QPointF(r.left(), y), QPointF(r.right(), y));
}

// 5. 六边形能量护盾 (三层同心)
void InjectionOverlay::drawHexShield(QPainter& p, const QPointF& c, qreal t) {
    const int sides = 6;
    for (int layer = 0; layer < 3; ++layer) {
        const qreal baseR = 62.0 + layer * 26.0;
        const qreal breathR = baseR + 6.0 * std::sin(t * kTau * 0.8 + layer * 1.1);
        const qreal rot = t * (12.0 + layer * 6.0) * kTau / 360.0
                          * (layer % 2 ? -1 : 1);

        QPainterPath hex;
        for (int i = 0; i <= sides; ++i) {
            const qreal a = i / qreal(sides) * kTau + rot;
            const QPointF pt = c + QPointF(std::cos(a), std::sin(a)) * breathR;
            if (i == 0) hex.moveTo(pt);
            else hex.lineTo(pt);
        }

        QColor col = hueShift(QColor(150, 210, 255),
                              t * 30.0 + layer * 40.0);
        const int alpha = 100 - layer * 25;
        col.setAlpha(alpha);

        QPen pen(col, 1.3);
        pen.setCapStyle(Qt::RoundCap);
        pen.setJoinStyle(Qt::RoundJoin);
        if (layer == 1) pen.setDashPattern({6, 4});
        p.setPen(pen);
        p.setBrush(Qt::NoBrush);
        p.drawPath(hex);

        // 每个顶点一个发光点
        for (int i = 0; i < sides; ++i) {
            const qreal a = i / qreal(sides) * kTau + rot;
            const QPointF pt = c + QPointF(std::cos(a), std::sin(a)) * breathR;
            const qreal pr = 2.4 + 1.0 * std::sin(t * 6.0 + i + layer);
            QColor pc = hueShift(col, 40.0);
            pc.setAlpha(std::min(255, alpha + 60));
            p.setPen(Qt::NoPen);
            p.setBrush(pc);
            p.drawEllipse(pt, pr, pr);
        }
    }
}

// 6. 辐射能量射线
void InjectionOverlay::drawEnergyRays(QPainter& p, const QPointF& c, qreal t) {
    const int rays = 14;
    const qreal baseRot = t * 22.0;
    for (int i = 0; i < rays; ++i) {
        const qreal ang = (i / qreal(rays)) * kTau + baseRot * kTau / 360.0;
        const qreal startR = 56.0;
        const qreal endR   = startR + 22.0 + 18.0 * std::sin(t * 5.0 + i * 0.9);

        QPointF p1 = c + QPointF(std::cos(ang), std::sin(ang)) * startR;
        QPointF p2 = c + QPointF(std::cos(ang), std::sin(ang)) * endR;

        const int a = 70 + int(60 * (0.5 + 0.5 * std::sin(t * 7.0 + i * 0.8)));

        QColor col = hueShift(QColor(140, 210, 255), t * 50.0 + i * 6.0);
        QLinearGradient lg(p1, p2);
        QColor c1 = col; c1.setAlpha(a);
        QColor c2 = col; c2.setAlpha(0);
        lg.setColorAt(0.0, c1);
        lg.setColorAt(1.0, c2);

        p.setPen(QPen(QBrush(lg), 1.6, Qt::SolidLine, Qt::RoundCap));
        p.drawLine(p1, p2);
    }
}

// 7. 环形数据流 (80 个点沿圆周流动)
void InjectionOverlay::drawRingStream(QPainter& p, const QPointF& c, qreal t) {
    const int N = 80;
    const qreal R = 78.0;
    p.setPen(Qt::NoPen);

    for (int i = 0; i < N; ++i) {
        const qreal baseAng = (i / qreal(N)) * kTau;
        const qreal flow = t * 0.9 + hash01(i, 5) * 0.4;
        const qreal ang = baseAng + flow;

        const qreal jitter = 6.0 * std::sin(t * 4.0 + i * 0.9);
        const qreal rr = R + jitter;

        const QPointF pt = c + QPointF(std::cos(ang), std::sin(ang)) * rr;

        // 亮度随 i 和时间做波动
        const qreal bright = 0.35 + 0.65 * std::abs(std::sin(t * 3.0 + i * 0.5));
        QColor col = hueShift(QColor(140, 220, 255), t * 60.0 + i * 3.0);
        col.setAlphaF(bright * 0.85);

        p.setBrush(col);
        const qreal s = 1.0 + 0.9 * bright;
        p.drawEllipse(pt, s, s);
    }
}

// 8. 数据流雨
void InjectionOverlay::drawDataRain(QPainter& p, const QRectF& r, qreal t) {
    const int streams = 10;
    for (int i = 0; i < streams; ++i) {
        const qreal x = r.left() + (i + 0.5) * r.width() / streams
                        + std::sin(t + i) * 4.0;
        const qreal speed = 34.0 + (i % 4) * 12.0;
        const qreal headY = r.top() +
                std::fmod(t * speed + i * 90.0, r.height() + 60) - 30.0;
        const qreal trailH = 46.0 + (i % 3) * 18.0;

        QColor col = hueShift(QColor(140, 225, 255), t * 40.0 + i * 8.0);
        QLinearGradient lg(x, headY - trailH, x, headY);
        QColor c0 = col; c0.setAlpha(0);
        QColor c1 = col; c1.setAlpha(75);
        lg.setColorAt(0.0, c0);
        lg.setColorAt(1.0, c1);

        p.setPen(QPen(QBrush(lg), 1.6));
        p.drawLine(QPointF(x, headY - trailH), QPointF(x, headY));

        p.setPen(Qt::NoPen);
        QColor hc = col; hc.setAlpha(210);
        p.setBrush(hc);
        p.drawEllipse(QPointF(x, headY), 1.6, 1.6);
    }
}

// 9. 冲击波
void InjectionOverlay::drawShockwaves(QPainter& p, const QPointF& c, qreal t) {
    for (int i = 0; i < 3; ++i) {
        const qreal cycle = 1.4;
        const qreal ph = std::fmod(t / cycle + i / 3.0, 1.0);
        const qreal rad = 42.0 + ph * 170.0;
        const int a = int(115 * (1.0 - ph) * (1.0 - ph));
        if (a > 2) {
            QColor col = hueShift(QColor(130, 200, 255), t * 30.0 + i * 50.0);
            col.setAlpha(a);
            p.setPen(QPen(col, 1.2));
            p.setBrush(Qt::NoBrush);
            p.drawEllipse(c, rad, rad);
        }
    }
}

// 10. 五重旋转环
void InjectionOverlay::drawSpinner(QPainter& p, const QPointF& c, qreal t) {
    const qreal R = 42.0;

    if (!injectDone_) {
        // 外层虚线环
        {
            const qreal r1 = R + 18.0;
            QColor col = hueShift(QColor(120, 190, 255), t * 25.0);
            col.setAlpha(135);
            QPen pen(col, 1.2);
            pen.setDashPattern({4, 7});
            p.setPen(pen);
            p.setBrush(Qt::NoBrush);
            p.save();
            p.translate(c);
            p.rotate(std::fmod(spinnerAngle_ * 0.5, 360.0));
            p.drawEllipse(QPointF(0, 0), r1, r1);
            p.restore();
        }

        // 主环 (三段弧)
        {
            QColor col = hueShift(QColor(120, 190, 255), t * 30.0);
            col.setAlpha(240);
            QPen arc(col, 3.2);
            arc.setCapStyle(Qt::RoundCap);
            p.setPen(arc);
            QRectF rect(c.x() - R, c.y() - R, R * 2, R * 2);
            const int start = int(-spinnerAngle_ * 16);
            p.drawArc(rect, start,              110 * 16);
            p.drawArc(rect, start + 140 * 16,    70 * 16);
            p.drawArc(rect, start + 240 * 16,    50 * 16);
        }

        // 副环 (粉)
        {
            const qreal r2 = R * 0.72;
            QColor col = hueShift(QColor(255, 130, 200), t * -20.0);
            col.setAlpha(200);
            QPen arc(col, 2.0);
            arc.setCapStyle(Qt::RoundCap);
            p.setPen(arc);
            QRectF rect(c.x() - r2, c.y() - r2, r2 * 2, r2 * 2);
            const int start = int(spinnerAngle_ * 16);
            p.drawArc(rect, start,             80 * 16);
            p.drawArc(rect, start + 180 * 16,  60 * 16);
        }

        // 内虚线环
        {
            const qreal r3 = R * 0.42;
            QColor col = hueShift(QColor(180, 220, 255), t * 45.0);
            col.setAlpha(165);
            QPen pen(col, 1.0);
            pen.setDashPattern({2, 4});
            p.setPen(pen);
            p.setBrush(Qt::NoBrush);
            p.save();
            p.translate(c);
            p.rotate(std::fmod(-spinnerAngle_ * 2.0, 360.0));
            p.drawEllipse(QPointF(0, 0), r3, r3);
            p.restore();
        }

        // 核心脉冲
        {
            const qreal pulse = 0.5 + 0.5 * std::sin(t * kTau * 1.4);
            const qreal coreR = 6.0 + pulse * 4.0;
            QRadialGradient core(c, coreR * 2.6);
            core.setColorAt(0.0, QColor(255, 255, 255, 255));
            core.setColorAt(0.30, QColor(190, 210, 255, 220));
            core.setColorAt(0.65, QColor(120, 140, 255, 80));
            core.setColorAt(1.0, QColor(120, 140, 255, 0));
            p.setPen(Qt::NoPen);
            p.setBrush(core);
            p.drawEllipse(c, coreR * 2.6, coreR * 2.6);

            p.setBrush(QColor(255, 255, 255, int(230 + 25 * pulse)));
            p.drawEllipse(c, 2.4, 2.4);
        }
    } else {
        // ---- 完成状态 ----
        const QColor accent = injectOk_ ? QColor(62, 220, 120) : QColor(240, 92, 92);
        const qreal tt = std::clamp(checkmarkProgress_, 0.0, 1.0);

        for (int i = 0; i < 2; ++i) {
            const qreal rr = R + tt * (26 + i * 24);
            const int a = int((1.0 - tt) * (95 - i * 30));
            if (a > 0) {
                QColor c2 = accent; c2.setAlpha(a);
                p.setPen(QPen(c2, 2));
                p.setBrush(Qt::NoBrush);
                p.drawEllipse(c, rr, rr);
            }
        }

        QRadialGradient g(c - QPointF(R * 0.3, R * 0.3), R * 1.6);
        g.setColorAt(0.0, accent.lighter(165));
        g.setColorAt(0.5, accent);
        g.setColorAt(1.0, accent.darker(165));
        p.setPen(Qt::NoPen);
        p.setBrush(g);
        p.drawEllipse(c, R, R);

        QRadialGradient hit(c - QPointF(R * 0.35, R * 0.35), R * 0.9);
        hit.setColorAt(0.0, QColor(255, 255, 255, 140));
        hit.setColorAt(1.0, QColor(255, 255, 255, 0));
        p.setBrush(hit);
        p.drawEllipse(c, R, R);

        QPen glowPen(QColor(255, 255, 255, 110), 7.0, Qt::SolidLine,
                     Qt::RoundCap, Qt::RoundJoin);
        QPen mark   (QColor(255, 255, 255, 255), 3.6, Qt::SolidLine,
                     Qt::RoundCap, Qt::RoundJoin);

        auto drawMark = [&](const QPen& usePen) {
            p.setPen(usePen);
            p.setBrush(Qt::NoBrush);
            if (injectOk_) {
                QPointF a(c.x() - 14, c.y() + 1);
                QPointF b(c.x() - 3,  c.y() + 12);
                QPointF d(c.x() + 15, c.y() - 9);
                if (tt <= 0.5) {
                    p.drawLine(a, a + (b - a) * (tt / 0.5));
                } else {
                    p.drawLine(a, b);
                    p.drawLine(b, b + (d - b) * ((tt - 0.5) / 0.5));
                }
            } else {
                const qreal off = 14.0 * tt;
                p.drawLine(QPointF(c.x() - off, c.y() - off),
                           QPointF(c.x() + off, c.y() + off));
                p.drawLine(QPointF(c.x() + off, c.y() - off),
                           QPointF(c.x() - off, c.y() + off));
            }
        };
        drawMark(glowPen);
        drawMark(mark);
    }
}

// 11. 扫描线
void InjectionOverlay::drawScanLine(QPainter& p, const QRectF& r, qreal t) {
    const qreal scanY = r.top() + r.height() * std::fmod(t / kPhaseSec, 1.0);
    QColor col = hueShift(QColor(160, 220, 255), t * 50.0);
    QLinearGradient scan(0, scanY - 36, 0, scanY + 36);
    QColor c0 = col; c0.setAlpha(0);
    QColor c1 = col; c1.setAlpha(50);
    scan.setColorAt(0.0, c0);
    scan.setColorAt(0.5, c1);
    scan.setColorAt(1.0, c0);
    p.fillRect(QRectF(r.left(), scanY - 36, r.width(), 72), scan);
}

// 12. 粒子
void InjectionOverlay::drawParticles(QPainter& p) {
    p.setPen(Qt::NoPen);
    for (const auto& pt : particles_) {
        const qreal a = pt.life / pt.maxLife;

        const qreal tailX = pt.x - pt.vx * pt.trailLen;
        const qreal tailY = pt.y - pt.vy * pt.trailLen;
        QLinearGradient lg(tailX, tailY, pt.x, pt.y);

        QColor base;
        if (pt.hue < 0.33)      base = QColor(140, 200, 255);
        else if (pt.hue < 0.66) base = QColor(200, 150, 255);
        else                    base = QColor(255, 160, 210);

        QColor c0 = base; c0.setAlphaF(0);
        QColor c1 = base; c1.setAlphaF(a * 0.88);

        lg.setColorAt(0.0, c0);
        lg.setColorAt(1.0, c1);

        p.setPen(QPen(QBrush(lg), pt.size * 0.9, Qt::SolidLine, Qt::RoundCap));
        p.drawLine(QPointF(tailX, tailY), QPointF(pt.x, pt.y));

        p.setPen(Qt::NoPen);
        p.setBrush(c1);
        p.drawEllipse(QPointF(pt.x, pt.y), pt.size * a, pt.size * a);
    }
}

// 13. 爆炸 (含白光爆闪)
void InjectionOverlay::drawImpact(QPainter& p, const QPointF& c) {
    if (impactGlow_ <= 0.001) return;

    const QColor accent = injectOk_ ? QColor(62, 220, 120) : QColor(240, 92, 92);
    const qreal g = impactGlow_;

    // 扩散冲击波 3 圈
    for (int i = 0; i < 3; ++i) {
        const qreal off = i * 0.18;
        const qreal s = std::clamp((g - off) / (1.0 - off), 0.0, 1.0);
        if (s <= 0.001) continue;
        const qreal rad = 30.0 + s * 280.0;
        const int a = int(165 * (1.0 - s) * (1.0 - s));
        if (a > 2) {
            QColor col = accent; col.setAlpha(a);
            p.setPen(QPen(col, 2.0));
            p.setBrush(Qt::NoBrush);
            p.drawEllipse(c, rad, rad);
        }
    }

    // 中心爆发光斑
    if (g < 0.7) {
        const qreal bl = 1.0 - g / 0.7;
        QRadialGradient halo(c, 200.0);
        QColor c1 = accent; c1.setAlpha(int(140 * bl));
        QColor c2 = accent; c2.setAlpha(int(60 * bl));
        QColor c3 = accent; c3.setAlpha(0);
        halo.setColorAt(0.0, c1);
        halo.setColorAt(0.5, c2);
        halo.setColorAt(1.0, c3);
        p.setPen(Qt::NoPen);
        p.setBrush(halo);
        p.drawEllipse(c, 200.0, 200.0);
    }

    // ★ 成功时短暂白光爆闪 (g < 0.25 时最亮)
    if (injectOk_ && g < 0.25) {
        const qreal flash = 1.0 - g / 0.25;
        const QRectF r = QRectF(rect());
        QRadialGradient wf(c, r.width() * 0.55);
        wf.setColorAt(0.0, QColor(255, 255, 255, int(200 * flash)));
        wf.setColorAt(0.55, QColor(230, 250, 255, int(90 * flash)));
        wf.setColorAt(1.0, QColor(230, 250, 255, 0));
        p.setBrush(wf);
        p.drawEllipse(c, r.width() * 0.55, r.width() * 0.55);
    }
}

// 14. 进度条
void InjectionOverlay::drawProgressBar(QPainter& p, const QRectF& r, qreal t) {
    const qreal pbY = r.bottom() - 42;
    const qreal pbL = r.left() + 60;
    const qreal pbR = r.right() - 60;
    const qreal pbH = 7.0;

    QPainterPath track;
    track.addRoundedRect(QRectF(pbL, pbY - pbH / 2, pbR - pbL, pbH),
                         pbH / 2, pbH / 2);
    p.setPen(Qt::NoPen);
    p.setBrush(QColor(255, 255, 255, 22));
    p.drawPath(track);

    {
        QLinearGradient inner(pbL, 0, pbR, 0);
        inner.setColorAt(0.0, QColor(120, 190, 255, 0));
        inner.setColorAt(0.5, QColor(120, 190, 255, 22));
        inner.setColorAt(1.0, QColor(120, 190, 255, 0));
        p.setBrush(inner);
        p.drawPath(track);
    }

    const qreal fillW = (pbR - pbL) * std::clamp(progress_, 0.0, 1.0);
    if (fillW > 0.5) {
        QPainterPath fill;
        fill.addRoundedRect(QRectF(pbL, pbY - pbH / 2, fillW, pbH),
                            pbH / 2, pbH / 2);

        QLinearGradient g(QPointF(pbL, 0), QPointF(pbL + fillW, 0));
        if (injectDone_ && !injectOk_) {
            g.setColorAt(0.0, QColor("#ff5050"));
            g.setColorAt(0.5, QColor("#ff8080"));
            g.setColorAt(1.0, QColor("#ffb0b0"));
        } else if (injectDone_ && injectOk_) {
            g.setColorAt(0.0, QColor("#40cf70"));
            g.setColorAt(0.5, QColor("#60e090"));
            g.setColorAt(1.0, QColor("#90ffb8"));
        } else {
            // 色相随进度漂移
            const QColor c0 = hueShift(QColor("#4066d8"), progress_ * 80.0);
            const QColor c1 = hueShift(QColor("#70a8ff"), progress_ * 80.0);
            const QColor c2 = hueShift(QColor("#b0d8ff"), progress_ * 80.0);
            g.setColorAt(0.0, c0);
            g.setColorAt(0.5, c1);
            g.setColorAt(1.0, c2);
        }
        p.setBrush(g);
        p.drawPath(fill);

        const qreal headX = pbL + fillW;
        if (!injectDone_) {
            const qreal pulse = 0.5 + 0.5 * std::sin(t * kTau * 2.0);
            const qreal headR = 14.0 + 8.0 * pulse;
            QRadialGradient head(QPointF(headX, pbY), headR);
            head.setColorAt(0.0, QColor(255, 255, 255, int(230 * pulse)));
            head.setColorAt(0.35, QColor(180, 220, 255, int(130 * pulse)));
            head.setColorAt(1.0, QColor(180, 220, 255, 0));
            p.setBrush(head);
            p.drawEllipse(QPointF(headX, pbY), headR, headR);
        }

        for (int i = 1; i <= 8; ++i) {
            const qreal tx = headX - i * 7.5;
            if (tx <= pbL) break;
            QColor c(180, 220, 255);
            c.setAlphaF(0.6 * (1.0 - i / 9.0));
            p.setBrush(c);
            p.drawEllipse(QPointF(tx, pbY), 1.8, 1.8);
        }

        p.setBrush(QColor(245, 250, 255));
        p.drawEllipse(QPointF(headX, pbY), 3.2, 3.2);
    }
}

// 15. 玻璃 + 边框 + 四角
void InjectionOverlay::drawGlassAndBorder(QPainter& p, const QRectF& r, qreal t) {
    {
        QLinearGradient glass(r.left(), r.top(),
                              r.left() + r.width() * 0.7, r.top() + 60);
        glass.setColorAt(0.0, QColor(255, 255, 255, 28));
        glass.setColorAt(0.5, QColor(255, 255, 255, 12));
        glass.setColorAt(1.0, QColor(255, 255, 255, 0));
        QPainterPath topBand;
        topBand.addRoundedRect(QRectF(r.left() + 1, r.top() + 1,
                                      r.width() - 2, 60),
                               kCornerRadius - 1, kCornerRadius - 1);
        p.fillPath(topBand, glass);
    }

    {
        const qreal pulse = 0.5 + 0.5 * std::sin(t * kTau * 1.2);
        QColor a = hueShift(QColor(150, 190, 255), t * 30.0);
        QColor b = hueShift(QColor(200, 140, 255), t * 30.0 + 60.0);
        QColor c = hueShift(QColor(120, 220, 255), t * 30.0 + 120.0);

        a.setAlpha(int(85 + 45 * pulse));
        b.setAlpha(int(55 + 35 * pulse));
        c.setAlpha(int(85 + 45 * pulse));

        QLinearGradient bd(QPointF(r.left(), r.top()),
                           QPointF(r.right(), r.bottom()));
        bd.setColorAt(0.0, a);
        bd.setColorAt(0.5, b);
        bd.setColorAt(1.0, c);
        p.setPen(QPen(QBrush(bd), 1.2));
        p.setBrush(Qt::NoBrush);
        QPainterPath card;
        card.addRoundedRect(r, kCornerRadius, kCornerRadius);
        p.drawPath(card);
    }

    {
        const qreal L = 18.0;
        QColor corner = hueShift(QColor(160, 210, 255), t * 40.0);
        corner.setAlpha(190);
        QPen pen(corner, 1.6);
        pen.setCapStyle(Qt::RoundCap);
        p.setPen(pen);
        p.setBrush(Qt::NoBrush);

        p.drawLine(QPointF(r.left() + 8, r.top() + 8 + L),
                   QPointF(r.left() + 8, r.top() + 8));
        p.drawLine(QPointF(r.left() + 8, r.top() + 8),
                   QPointF(r.left() + 8 + L, r.top() + 8));

        p.drawLine(QPointF(r.right() - 8, r.top() + 8 + L),
                   QPointF(r.right() - 8, r.top() + 8));
        p.drawLine(QPointF(r.right() - 8, r.top() + 8),
                   QPointF(r.right() - 8 - L, r.top() + 8));

        p.drawLine(QPointF(r.left() + 8, r.bottom() - 8 - L),
                   QPointF(r.left() + 8, r.bottom() - 8));
        p.drawLine(QPointF(r.left() + 8, r.bottom() - 8),
                   QPointF(r.left() + 8 + L, r.bottom() - 8));

        p.drawLine(QPointF(r.right() - 8, r.bottom() - 8 - L),
                   QPointF(r.right() - 8, r.bottom() - 8));
        p.drawLine(QPointF(r.right() - 8, r.bottom() - 8),
                   QPointF(r.right() - 8 - L, r.bottom() - 8));
    }
}

// =====================================================================
// 主绘制
// =====================================================================
void InjectionOverlay::paintEvent(QPaintEvent*) {
    QPainter p(this);
    p.setRenderHint(QPainter::Antialiasing);
    p.setRenderHint(QPainter::TextAntialiasing);

    const QRectF rect = QRectF(this->rect()).adjusted(0.5, 0.5, -0.5, -0.5);
    const qreal t = phase_ * kPhaseSec;

    QPainterPath panel;
    panel.addRoundedRect(rect, kCornerRadius, kCornerRadius);

    p.setOpacity(panelOpacity_);
    p.setClipPath(panel);

    // 1. 深空底色
    {
        QRadialGradient bg(rect.center() + QPointF(0, 30), rect.width() * 0.8);
        bg.setColorAt(0.0, QColor("#1a1530"));
        bg.setColorAt(0.45, QColor("#100c1e"));
        bg.setColorAt(1.0, QColor("#05050b"));
        p.fillPath(panel, bg);
    }

    // 2. 各层
    drawNebula(p, rect, t);
    drawStars(p, rect, t);

    const QPointF center(rect.center().x(), rect.top() + 130);

    drawHelixArms(p, center, t);   // 螺旋星臂
    drawGrid(p, rect, t);
    drawHexShield(p, center, t);   // 六边形护盾
    drawDataRain(p, rect, t);
    drawEnergyRays(p, center, t);
    drawRingStream(p, center, t);  // 环形数据流
    drawShockwaves(p, center, t);
    drawSpinner(p, center, t);
    drawScanLine(p, rect, t);

    // 粒子
    drawParticles(p);

    // 爆炸
    drawImpact(p, center);

    // 状态文字
    {
        QFont f(QStringLiteral("Segoe UI"));
        f.setPointSize(13);
        f.setBold(true);
        p.setFont(f);
        QFontMetricsF fm(f);
        const qreal tw = fm.horizontalAdvance(statusText_);
        const QPointF base(rect.center().x() - tw / 2, rect.top() + 202);

        QColor glowC = injectDone_
            ? (injectOk_ ? QColor(80, 230, 140) : QColor(240, 92, 92))
            : hueShift(QColor(120, 180, 255), t * 40.0);
        glowC.setAlpha(95);
        p.setPen(glowC);
        for (int dx = -1; dx <= 1; ++dx)
            for (int dy = -1; dy <= 1; ++dy)
                if (dx || dy) p.drawText(base + QPointF(dx, dy), statusText_);

        p.setPen(QColor("#eef2ff"));
        p.drawText(base, statusText_);
    }

    // 副标题
    {
        QFont f(QStringLiteral("Segoe UI"));
        f.setPointSize(9);
        p.setFont(f);
        p.setPen(QColor(160, 150, 195));
        QFontMetricsF fm(f);

        QString sub = QStringLiteral("PID %1 · %2").arg(pid_).arg(target_);
        if (!commandLine_.isEmpty())
            sub += QStringLiteral("  ·  ") + commandLine_.right(48);

        const qreal maxW = rect.width() - 60;
        if (fm.horizontalAdvance(sub) > maxW)
            sub = fm.elidedText(sub, Qt::ElideMiddle, int(maxW));

        const qreal sw = fm.horizontalAdvance(sub);
        p.drawText(QPointF(rect.center().x() - sw / 2, rect.top() + 224), sub);
    }

    // 进度条
    drawProgressBar(p, rect, t);

    // 玻璃 + 边框 + 四角
    p.setClipping(false);
    drawGlassAndBorder(p, rect, t);
}

} // namespace loader