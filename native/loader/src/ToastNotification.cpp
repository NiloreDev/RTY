#include "ToastNotification.h"

#include <QApplication>
#include <QScreen>
#include <QPainter>
#include <QPainterPath>
#include <QLinearGradient>
#include <QRadialGradient>
#include <QPropertyAnimation>
#include <QParallelAnimationGroup>
#include <QTimer>
#include <QMouseEvent>
#include <QPointer>
#include <QRandomGenerator>

#include <algorithm>
#include <cmath>

namespace loader {

namespace {
constexpr int kW = 400;
constexpr int kH = 96;
constexpr int kCornerRadius = 16;
constexpr int kScreenMargin = 26;
constexpr qreal kTau = 6.2831853071795864769;

// 同一时刻只保留一个 toast, 避免右下角堆叠。
QPointer<ToastNotification> g_current;
} // namespace

// =====================================================================
// 构造 / 生命周期
// =====================================================================
ToastNotification::ToastNotification(const QString& title, const QString& body,
                                     Kind kind, int /*durationMs*/)
        : QWidget(nullptr), title_(title), body_(body), kind_(kind) {
    setWindowFlags(Qt::FramelessWindowHint
                   | Qt::Tool
                   | Qt::WindowStaysOnTopHint);
    setAttribute(Qt::WA_TranslucentBackground);
    setAttribute(Qt::WA_ShowWithoutActivating);   // 不抢焦点
    setAttribute(Qt::WA_DeleteOnClose);
    resize(kW, kH);

    QScreen* scr = QApplication::primaryScreen();
    if (scr) {
        const QRect avail = scr->availableGeometry();
        basePos_ = QPoint(avail.right()  - kW - kScreenMargin,
                          avail.bottom() - kH - kScreenMargin);
    }
    move(basePos_.x(), basePos_.y() + int(slideOffset_));
}

void ToastNotification::setPanelOpacity(qreal v) {
    panelOpacity_ = v;
    update();
}

void ToastNotification::setSlideOffset(qreal v) {
    slideOffset_ = v;
    move(basePos_.x(), basePos_.y() + int(v));
    update();
}

void ToastNotification::show(const QString& title, const QString& body,
                             Kind kind, int durationMs) {
    if (g_current) {
        g_current->close();
        g_current = nullptr;
    }

    auto* t = new ToastNotification(title, body, kind, durationMs);
    g_current = t;

    // 【修复 1】: 因为类内部定义了静态的 show(...) 方法，
    // 它会覆盖（隐藏）QWidget 原本的 show() 方法。
    // 这里必须显式指定调用父类 QWidget 的 show()。
    t->QWidget::show();

    t->raise();
    t->startAnimations(durationMs);
}

// =====================================================================
// 动画编排
// =====================================================================
void ToastNotification::startAnimations(int durationMs) {
    // ---- 粒子系统: ~60fps ----
    particleTimer_ = new QTimer(this);
    particleTimer_->setTimerType(Qt::PreciseTimer);
    connect(particleTimer_, &QTimer::timeout, this, [this]() {
        time_ += 0.016;
        update();
    });
    particleTimer_->start(16);

    // 初始化环绕粒子
    {
        QRandomGenerator* rng = QRandomGenerator::global();
        particles_.clear();
        particles_.reserve(24);
        for (int i = 0; i < 24; ++i) {
            Particle p;
            p.angle  = rng->generateDouble() * kTau;
            p.radius = 27.0 + rng->generateDouble() * 15.0;
            p.speed  = 0.35 + rng->generateDouble() * 1.4;
            p.size   = 0.8  + rng->generateDouble() * 1.9;
            p.phase  = rng->generateDouble() * kTau;
            p.alpha  = 0.35 + rng->generateDouble() * 0.6;
            particles_.append(p);
        }
    }

    // ---- 进场: 淡入 + 滑入 + 图标绘制 + 光扫 ----
    auto* fadeIn = new QPropertyAnimation(this, "panelOpacity", this);
    fadeIn->setDuration(280);
    fadeIn->setStartValue(0.0);
    fadeIn->setEndValue(1.0);
    fadeIn->setEasingCurve(QEasingCurve::OutCubic);

    auto* slideIn = new QPropertyAnimation(this, "slideOffset", this);
    slideIn->setDuration(440);
    slideIn->setStartValue(30.0);
    slideIn->setEndValue(0.0);
    slideIn->setEasingCurve(QEasingCurve::OutBack);

    auto* iconIn = new QPropertyAnimation(this, "iconProgress", this);
    iconIn->setDuration(540);
    iconIn->setStartValue(0.0);
    iconIn->setEndValue(1.0);
    iconIn->setEasingCurve(QEasingCurve::OutBack);

    auto* shine = new QPropertyAnimation(this, "shinePos", this);
    shine->setDuration(900);
    shine->setStartValue(-0.3);
    shine->setEndValue(1.3);
    shine->setEasingCurve(QEasingCurve::InOutQuad);

    auto* intro = new QParallelAnimationGroup(this);
    intro->addAnimation(fadeIn);
    intro->addAnimation(slideIn);
    intro->addAnimation(iconIn);
    intro->addAnimation(shine);
    intro->start(QAbstractAnimation::DeleteWhenStopped);

    // ---- 停留: 进度条 + 呼吸 ----
    auto* lifeAnim = new QPropertyAnimation(this, "lifeProgress", this);
    lifeAnim->setDuration(durationMs);
    lifeAnim->setStartValue(0.0);
    lifeAnim->setEndValue(1.0);
    lifeAnim->setEasingCurve(QEasingCurve::Linear);
    lifeAnim->start(QAbstractAnimation::DeleteWhenStopped);

    auto* pulseAnim = new QPropertyAnimation(this, "pulse", this);
    pulseAnim->setDuration(1700);
    pulseAnim->setStartValue(0.0);
    pulseAnim->setKeyValueAt(0.5, 1.0);
    pulseAnim->setEndValue(0.0);
    pulseAnim->setEasingCurve(QEasingCurve::InOutSine);
    pulseAnim->setLoopCount(-1);
    pulseAnim->start(QAbstractAnimation::DeleteWhenStopped);

    // ---- 退场 ----
    QTimer::singleShot(durationMs, this, [this]() {
        auto* fadeOut = new QPropertyAnimation(this, "panelOpacity", this);
        fadeOut->setDuration(340);
        fadeOut->setStartValue(panelOpacity_);
        fadeOut->setEndValue(0.0);
        fadeOut->setEasingCurve(QEasingCurve::InCubic);

        auto* slideOut = new QPropertyAnimation(this, "slideOffset", this);
        slideOut->setDuration(340);
        slideOut->setStartValue(0.0);
        slideOut->setEndValue(30.0);
        slideOut->setEasingCurve(QEasingCurve::InCubic);

        auto* outro = new QParallelAnimationGroup(this);
        outro->addAnimation(fadeOut);
        outro->addAnimation(slideOut);
        connect(outro, &QAbstractAnimation::finished, this, [this]() {
            if (particleTimer_) particleTimer_->stop();
            close();
        });
        outro->start(QAbstractAnimation::DeleteWhenStopped);
    });
}

// =====================================================================
// 颜色
// =====================================================================
QColor ToastNotification::kindColor() const {
    switch (kind_) {
        case Success: return QColor(62, 220, 120);
        case Error:   return QColor(240, 92, 92);
        default:      return QColor(110, 160, 255);
    }
}

QColor ToastNotification::kindColor2() const {
    switch (kind_) {
        case Success: return QColor(40, 200, 220);
        case Error:   return QColor(255, 140, 90);
        default:      return QColor(170, 130, 255);
    }
}

// =====================================================================
// 交互
// =====================================================================
void ToastNotification::mousePressEvent(QMouseEvent* e) {
    if (e->button() == Qt::LeftButton) {
        close();
        e->accept();
        return;
    }
    QWidget::mousePressEvent(e);
}

// =====================================================================
// 绘制
// =====================================================================
void ToastNotification::paintEvent(QPaintEvent*) {
    QPainter p(this);
    p.setRenderHint(QPainter::Antialiasing);
    p.setRenderHint(QPainter::TextAntialiasing);
    p.setOpacity(panelOpacity_);

    const QRectF r = QRectF(rect()).adjusted(0.5, 0.5, -0.5, -0.5);
    QPainterPath card;
    card.addRoundedRect(r, kCornerRadius, kCornerRadius);

    const QColor accent  = kindColor();
    const QColor accent2 = kindColor2();

    // ---------- 1. 外侧多层呼吸发光 ----------
    {
        const qreal breath = 1.0 + 0.45 * pulse_;
        p.setBrush(Qt::NoBrush);
        for (int i = 6; i >= 1; --i) {
            QColor c = accent;
            c.setAlpha(int((58 / i) * breath));
            p.setPen(QPen(c, 1.4));
            const qreal off = i * 0.55;
            p.drawRoundedRect(r.adjusted(-off, -off, off, off),
                              kCornerRadius + off, kCornerRadius + off);
        }
    }

    // ---------- 2. 玻璃背景 + 主色光晕 ----------
    {
        QLinearGradient bg(r.topLeft(), r.bottomRight());
        bg.setColorAt(0.00, QColor(30, 24, 52, 250));
        bg.setColorAt(0.35, QColor(22, 20, 42, 250));
        bg.setColorAt(0.65, QColor(18, 26, 46, 250));
        bg.setColorAt(1.00, QColor(12, 16, 30, 250));
        p.fillPath(card, bg);

        QRadialGradient glow(r.center(), r.width() * 0.7);
        QColor gc = accent;
        gc.setAlpha(int(28 * (0.5 + 0.5 * pulse_)));
        glow.setColorAt(0.0, gc);
        QColor gc0 = accent;
        gc0.setAlpha(0);
        glow.setColorAt(1.0, gc0);
        p.fillPath(card, glow);
    }

    // ---------- 3. 内部裁剪层: 光扫 + 顶部高光 ----------
    p.save();
    p.setClipPath(card);
    {
        const qreal sw = r.width() * 0.45;
        const qreal cx = r.left() - sw + (r.width() + sw * 2.0) * shinePos_;
        QLinearGradient sg(QPointF(cx - sw * 0.5, r.top()),
                           QPointF(cx + sw * 0.5, r.bottom()));
        sg.setColorAt(0.0, QColor(255, 255, 255, 0));
        sg.setColorAt(0.5, QColor(220, 230, 255, 62));
        sg.setColorAt(1.0, QColor(255, 255, 255, 0));
        p.setPen(Qt::NoPen);
        p.fillRect(r, sg);

        QLinearGradient top(QPointF(r.left(), r.top()),
                            QPointF(r.left(), r.top() + 26));
        top.setColorAt(0.0, QColor(255, 255, 255, 34));
        top.setColorAt(1.0, QColor(255, 255, 255, 0));
        p.fillRect(QRectF(r.left(), r.top(), r.width(), 26), top);
    }
    p.restore();

    // ---------- 4. 左侧强调竖条 ----------
    {
        QLinearGradient sg(QPointF(0, r.top()), QPointF(0, r.bottom()));
        sg.setColorAt(0.0, accent.lighter(140));
        sg.setColorAt(1.0, accent2);

        QPainterPath stripe;
        stripe.addRoundedRect(
                QRectF(r.left() + 1, r.top() + 14, 3.5, r.height() - 28),
                1.75, 1.75);
        p.setPen(Qt::NoPen);
        p.setBrush(sg);
        p.drawPath(stripe);

        // 竖条外发光随 pulse 呼吸
        QPen gp(accent.lighter(140), 2.0);
        p.setPen(gp);
        p.setBrush(Qt::NoBrush);
        p.setOpacity(panelOpacity_ * (0.45 + 0.55 * pulse_));
        p.drawLine(QPointF(r.left() + 2.75, r.top() + 14),
                   QPointF(r.left() + 2.75, r.bottom() - 14));
        p.setOpacity(panelOpacity_);
    }

    // ---------- 5. 图标区域 ----------
    const QPointF iconC(r.left() + 56.0, r.center().y());
    const qreal   iconR = 21.0;

    // 5a. 呼吸光晕
    {
        const qreal haloR = iconR * (2.0 + 0.35 * pulse_);
        QRadialGradient halo(iconC, haloR);
        halo.setColorAt(0.55, QColor(accent.red(), accent.green(), accent.blue(), 0));
        QColor h1 = accent;
        h1.setAlpha(int(90 * (0.6 + 0.4 * pulse_)));
        halo.setColorAt(0.72, h1);
        QColor h2 = accent;
        h2.setAlpha(0);
        halo.setColorAt(1.0, h2);
        p.setPen(Qt::NoPen);
        p.setBrush(halo);
        p.drawEllipse(iconC, haloR, haloR);
    }

    // 5b. 两层反向旋转的虚线弧
    {
        p.setBrush(Qt::NoBrush);
        {
            const qreal r1 = iconR + 6.0;
            QPen pen(QColor(accent.red(), accent.green(), accent.blue(), 170), 1.2);
            pen.setDashPattern({3, 5});
            p.setPen(pen);
            p.save();
            p.translate(iconC);
            p.rotate(std::fmod(time_ * 40.0, 360.0));
            p.drawArc(QRectF(-r1, -r1, r1 * 2, r1 * 2), 0, 360 * 16);
            p.restore();
        }
        {
            const qreal r2 = iconR + 10.0;
            QPen pen(QColor(accent2.red(), accent2.green(), accent2.blue(), 130), 1.0);
            pen.setDashPattern({2, 8});
            p.setPen(pen);
            p.save();
            p.translate(iconC);
            p.rotate(-std::fmod(time_ * 25.0, 360.0));
            p.drawArc(QRectF(-r2, -r2, r2 * 2, r2 * 2), 0, 360 * 16);
            p.restore();
        }
    }

    // 5c. 主圆 (径向渐变) + 高光
    {
        QRadialGradient ig(iconC - QPointF(iconR * 0.32, iconR * 0.32), iconR * 1.6);
        ig.setColorAt(0.0, accent.lighter(170));
        ig.setColorAt(0.5, accent);
        ig.setColorAt(1.0, accent.darker(170));
        p.setPen(Qt::NoPen);
        p.setBrush(ig);
        p.drawEllipse(iconC, iconR, iconR);

        QRadialGradient hg(iconC - QPointF(iconR * 0.4, iconR * 0.4), iconR * 0.9);
        hg.setColorAt(0.0, QColor(255, 255, 255, 140));
        hg.setColorAt(1.0, QColor(255, 255, 255, 0));
        p.setBrush(hg);
        p.drawEllipse(iconC, iconR, iconR);
    }

    // 5d. 图标符号 (对勾 / 叉 / 圆点) —— 双层: 外发光 + 实线
    {
        const qreal t = std::clamp(iconProgress_, 0.0, 1.0);
        QPen glowPen(QColor(255, 255, 255, 100), 6.5, Qt::SolidLine,
                     Qt::RoundCap, Qt::RoundJoin);
        QPen pen(QColor(255, 255, 255), 3.0, Qt::SolidLine,
                 Qt::RoundCap, Qt::RoundJoin);
        p.setBrush(Qt::NoBrush);

        auto drawMark = [&](const QPen& usePen) {
            p.setPen(usePen);
            if (kind_ == Success) {
                QPointF a(iconC.x() - 9.0, iconC.y() + 0.5);
                QPointF b(iconC.x() - 2.5, iconC.y() + 7.5);
                QPointF d(iconC.x() + 10.0, iconC.y() - 6.5);
                if (t <= 0.5) {
                    qreal tt = t / 0.5;
                    p.drawLine(a, a + (b - a) * tt);
                } else {
                    p.drawLine(a, b);
                    qreal tt = (t - 0.5) / 0.5;
                    p.drawLine(b, b + (d - b) * tt);
                }
            } else if (kind_ == Error) {
                const qreal off = 8.5 * t;
                p.drawLine(QPointF(iconC.x() - off, iconC.y() - off),
                           QPointF(iconC.x() + off, iconC.y() + off));
                p.drawLine(QPointF(iconC.x() + off, iconC.y() - off),
                           QPointF(iconC.x() - off, iconC.y() + off));
            } else {
                p.setPen(Qt::NoPen);
                p.setBrush(QColor(255, 255, 255, int(255 * t)));
                p.drawEllipse(iconC, 3.2, 3.2);
            }
        };
        drawMark(glowPen);
        drawMark(pen);
    }

    // ---------- 6. 环绕粒子 ----------
    {
        p.setPen(Qt::NoPen);
        for (const auto& pt : particles_) {
            const qreal ang  = pt.angle + time_ * pt.speed;
            const qreal dist = pt.radius;
            const qreal x = iconC.x() + std::cos(ang) * dist;
            const qreal y = iconC.y() + std::sin(ang) * dist * 0.85;
            const qreal a = pt.alpha * (0.5 + 0.5 * std::sin(time_ * 3.0 + pt.phase));
            QColor c = accent.lighter(140);
            c.setAlphaF(std::clamp(a, 0.0, 1.0));
            p.setBrush(c);
            p.drawEllipse(QPointF(x, y), pt.size, pt.size);
        }
    }

    // ---------- 7. 标题 (逐字渐变) ----------
    {
        QFont f = font();
        f.setPointSizeF(11.0);
        f.setWeight(QFont::Bold);
        p.setFont(f);
        QFontMetricsF fm(f);

        const QString text = title_;

        // 【修复 2】: Qt6 中 text.size() 返回 qsizetype (64位)，而 1 是 int (32位)
        // std::max 无法推导两个不同类型，导致模板报错。
        // 这里将其强转为 int 即可解决。
        const int n = std::max(1, static_cast<int>(text.size()) - 1);

        qreal x = r.left() + 92;
        const qreal baseY = r.top() + 40;

        for (int i = 0; i < text.size(); ++i) {
            const qreal t = i / qreal(n);
            QColor c1 = accent.lighter(130);
            QColor c2 = accent2.lighter(140);
            int rr = int(c1.red()   * (1.0 - t) + c2.red()   * t);
            int gg = int(c1.green() * (1.0 - t) + c2.green() * t);
            int bb = int(c1.blue()  * (1.0 - t) + c2.blue()  * t);
            p.setPen(QColor(rr, gg, bb));
            const QString ch = text.mid(i, 1);
            p.drawText(QPointF(x, baseY), ch);
            x += fm.horizontalAdvance(ch);
        }
    }

    // ---------- 8. 副标题 ----------
    {
        QFont f = font();
        f.setPointSizeF(9.0);
        p.setFont(f);
        p.setPen(QColor(180, 170, 208));
        QFontMetricsF fm(f);
        const qreal maxW = r.width() - 92 - 22;
        const QString elided = fm.elidedText(body_, Qt::ElideRight, int(maxW));
        p.drawText(QRectF(r.left() + 92, r.top() + 56, maxW, 24),
                   Qt::AlignLeft | Qt::AlignVCenter, elided);
    }

    // ---------- 9. 底部倒计时进度条 ----------
    {
        const qreal barX = r.left() + 12;
        const qreal barY = r.bottom() - 8;
        const qreal barW = r.width() - 24;
        const qreal barH = 2.2;

        QPainterPath track;
        track.addRoundedRect(QRectF(barX, barY - barH / 2, barW, barH),
                             barH / 2, barH / 2);
        p.setPen(Qt::NoPen);
        p.setBrush(QColor(255, 255, 255, 26));
        p.drawPath(track);

        const qreal remain = std::clamp(1.0 - lifeProgress_, 0.0, 1.0);
        const qreal fillW = barW * remain;
        if (fillW > 0.5) {
            QLinearGradient pg(QPointF(barX, 0), QPointF(barX + barW, 0));
            pg.setColorAt(0.0, accent);
            pg.setColorAt(1.0, accent2);
            QPainterPath fill;
            fill.addRoundedRect(QRectF(barX, barY - barH / 2, fillW, barH),
                                barH / 2, barH / 2);
            p.setBrush(pg);
            p.drawPath(fill);

            // 条头部的小光点
            QRadialGradient hg(QPointF(barX + fillW, barY), 9.0);
            QColor hc = accent.lighter(150);
            hc.setAlpha(200);
            hg.setColorAt(0.0, hc);
            hc.setAlpha(0);
            hg.setColorAt(1.0, hc);
            p.setBrush(hg);
            p.drawEllipse(QPointF(barX + fillW, barY), 9.0, 9.0);
        }
    }

    // ---------- 10. 卡片渐变描边 ----------
    {
        QLinearGradient border(QPointF(r.left(), r.top()),
                               QPointF(r.right(), r.bottom()));
        border.setColorAt(0.0, QColor(accent.red(), accent.green(), accent.blue(), 190));
        border.setColorAt(0.5, QColor(accent2.red(), accent2.green(), accent2.blue(), 140));
        border.setColorAt(1.0, QColor(accent.red(), accent.green(), accent.blue(), 190));
        p.setPen(QPen(QBrush(border), 1.0));
        p.setBrush(Qt::NoBrush);
        p.drawPath(card);
    }
}

} // namespace loader