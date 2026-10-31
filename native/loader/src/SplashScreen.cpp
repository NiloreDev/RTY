#include "SplashScreen.h"

#include <QApplication>
#include <QConicalGradient>
#include <QEasingCurve>
#include <QFont>
#include <QFontMetricsF>
#include <QLinearGradient>
#include <QPainter>
#include <QPainterPath>
#include <QParallelAnimationGroup>
#include <QPropertyAnimation>
#include <QRadialGradient>
#include <QRandomGenerator>
#include <QScreen>
#include <QSequentialAnimationGroup>
#include <QTimer>

#include <algorithm>
#include <cmath>

namespace loader {

namespace {
constexpr int   kWidth         = 560;
constexpr int   kHeight        = 330;
constexpr int   kCornerRadius  = 22;
constexpr int   kParticleCount = 64;
constexpr qreal kTwoPi         = 6.283185307179586;
}

SplashScreen::SplashScreen(QWidget* parent) : QWidget(parent) {
    setWindowFlags(Qt::FramelessWindowHint | Qt::Tool | Qt::WindowStaysOnTopHint);
    setAttribute(Qt::WA_TranslucentBackground);
    setAttribute(Qt::WA_DeleteOnClose);
    setAttribute(Qt::WA_NoSystemBackground);
    resize(kWidth, kHeight);

    if (auto* scr = QApplication::primaryScreen()) {
        const QRect g = scr->availableGeometry();
        move(g.center() - QPoint(kWidth / 2, kHeight / 2));
    }

    setWindowOpacity(0.0);

    frameTimer_ = new QTimer(this);
    frameTimer_->setTimerType(Qt::PreciseTimer);
    connect(frameTimer_, &QTimer::timeout, this, &SplashScreen::tick);

    seedParticles();
}

void SplashScreen::seedParticles() {
    particles_.clear();
    particles_.reserve(kParticleCount);

    auto* rng = QRandomGenerator::global();
    const QRectF area = QRectF(rect()).adjusted(14, 14, -14, -14);

    for (int i = 0; i < kParticleCount; ++i) {
        Particle p;
        p.pos = QPointF(area.left() + rng->generateDouble() * area.width(),
                        area.top()  + rng->generateDouble() * area.height());
        p.vel = QPointF((rng->generateDouble() - 0.5) * 16.0,
                        -(6.0 + rng->generateDouble() * 26.0));
        p.radius  = 0.6 + rng->generateDouble() * 1.7;
        p.maxLife = 3.5 + rng->generateDouble() * 4.5;
        p.life    = rng->generateDouble() * p.maxLife;
        p.phase   = rng->generateDouble() * kTwoPi;
        p.color   = QColor(150 + rng->bounded(90), 190 + rng->bounded(60), 255);
        particles_.append(p);
    }
}

void SplashScreen::respawn(Particle& p) {
    auto* rng = QRandomGenerator::global();
    const QRectF area = QRectF(rect()).adjusted(14, 14, -14, -14);

    p.pos = QPointF(area.left() + rng->generateDouble() * area.width(),
                    area.bottom() - rng->generateDouble() * 46.0);
    p.vel = QPointF((rng->generateDouble() - 0.5) * 16.0,
                    -(6.0 + rng->generateDouble() * 26.0));
    p.radius  = 0.6 + rng->generateDouble() * 1.7;
    p.maxLife = 3.5 + rng->generateDouble() * 4.5;
    p.life    = 0.0;
    p.phase   = rng->generateDouble() * kTwoPi;
    p.color   = QColor(150 + rng->bounded(90), 190 + rng->bounded(60), 255);
}

void SplashScreen::tick() {
    const qreal dt = clock_.isValid() ? std::min(clock_.restart() / 1000.0, 0.05) : 0.016;
    time_ += dt;

    for (auto& p : particles_) {
        p.life += dt;
        p.pos  += p.vel * dt;
        p.vel.setX(p.vel.x() + std::sin(time_ * 1.6 + p.phase) * 10.0 * dt);

        if (p.life >= p.maxLife || p.pos.y() < -12.0) {
            respawn(p);
        }
    }
    update();
}

void SplashScreen::start() {
    show(); raise(); activateWindow();

    clock_.start();
    frameTimer_->start(16);

    auto* winIn = new QPropertyAnimation(this, "windowOpacity", this);
    winIn->setDuration(240); winIn->setStartValue(0.0); winIn->setEndValue(1.0); winIn->setEasingCurve(QEasingCurve::OutCubic);

    auto* pop = new QPropertyAnimation(this, "panelScale", this);
    pop->setDuration(660); pop->setStartValue(0.84); pop->setEndValue(1.0); pop->setEasingCurve(QEasingCurve::OutBack);

    auto* accent = new QPropertyAnimation(this, "accentGlow", this);
    accent->setDuration(820); accent->setStartValue(0.0); accent->setEndValue(1.0); accent->setEasingCurve(QEasingCurve::OutCubic);

    auto* ring = new QPropertyAnimation(this, "ringSpread", this);
    ring->setDuration(1150); ring->setStartValue(0.0); ring->setEndValue(1.0); ring->setEasingCurve(QEasingCurve::OutCubic);

    auto* intro = new QParallelAnimationGroup(this);
    intro->addAnimation(winIn); intro->addAnimation(pop); intro->addAnimation(accent); intro->addAnimation(ring);

    auto* logoA = new QPropertyAnimation(this, "logoOpacity", this);
    logoA->setDuration(420); logoA->setStartValue(0.0); logoA->setEndValue(1.0); logoA->setEasingCurve(QEasingCurve::OutCubic);

    auto* logoS = new QPropertyAnimation(this, "logoScale", this);
    logoS->setDuration(840); logoS->setStartValue(0.62); logoS->setEndValue(1.0); logoS->setEasingCurve(QEasingCurve::OutBack);

    auto* reveal = new QPropertyAnimation(this, "textReveal", this);
    reveal->setDuration(980); reveal->setStartValue(0.0); reveal->setEndValue(1.0); reveal->setEasingCurve(QEasingCurve::OutCubic);

    auto* glow = new QPropertyAnimation(this, "glowPulse", this);
    glow->setDuration(1400); glow->setStartValue(0.0); glow->setKeyValueAt(0.45, 1.0); glow->setEndValue(0.55); glow->setEasingCurve(QEasingCurve::InOutSine);

    auto* shimmer = new QPropertyAnimation(this, "shimmer", this);
    shimmer->setDuration(1150); shimmer->setStartValue(-0.35); shimmer->setEndValue(1.35); shimmer->setEasingCurve(QEasingCurve::InOutSine);

    auto* sweep = new QPropertyAnimation(this, "sweepPos", this);
    sweep->setDuration(1250); sweep->setStartValue(-0.35); sweep->setEndValue(1.35); sweep->setEasingCurve(QEasingCurve::InOutQuad);

    auto* scan = new QPropertyAnimation(this, "scanProgress", this);
    scan->setDuration(1600); scan->setStartValue(0.0); scan->setEndValue(1.0); scan->setEasingCurve(QEasingCurve::InOutQuad);

    auto* body = new QParallelAnimationGroup(this);
    body->addAnimation(logoA); body->addAnimation(logoS); body->addAnimation(reveal); body->addAnimation(glow);
    body->addAnimation(shimmer); body->addAnimation(sweep); body->addAnimation(scan);

    auto* winOut = new QPropertyAnimation(this, "windowOpacity", this);
    winOut->setDuration(320); winOut->setStartValue(1.0); winOut->setEndValue(0.0); winOut->setEasingCurve(QEasingCurve::InCubic);

    auto* popOut = new QPropertyAnimation(this, "panelScale", this);
    popOut->setDuration(320); popOut->setStartValue(1.0); popOut->setEndValue(1.09); popOut->setEasingCurve(QEasingCurve::InCubic);

    auto* glowOut = new QPropertyAnimation(this, "accentGlow", this);
    glowOut->setDuration(320); glowOut->setStartValue(1.0); glowOut->setEndValue(1.7); glowOut->setEasingCurve(QEasingCurve::InCubic);

    auto* outro = new QParallelAnimationGroup(this);
    outro->addAnimation(winOut); outro->addAnimation(popOut); outro->addAnimation(glowOut);

    auto* seq = new QSequentialAnimationGroup(this);
    seq->addAnimation(intro); seq->addAnimation(body); seq->addPause(360); seq->addAnimation(outro);

    connect(seq, &QAbstractAnimation::finished, this, [this]() {
        if (frameTimer_) frameTimer_->stop();
        emit finished();
        close();
    });
    seq->start(QAbstractAnimation::DeleteWhenStopped);
}

void SplashScreen::paintEvent(QPaintEvent*) {
    QPainter p(this);
    p.setRenderHint(QPainter::Antialiasing, true);
    p.setRenderHint(QPainter::TextAntialiasing, true);

    const QRectF widgetRect = QRectF(rect());

    p.save();
    p.translate(widgetRect.center());
    p.scale(panelScale_, panelScale_);
    p.translate(-widgetRect.center());

    const QRectF r = widgetRect.adjusted(10.0, 10.0, -10.0, -10.0);
    QPainterPath panelPath;
    panelPath.addRoundedRect(r, kCornerRadius, kCornerRadius);

    // 外发光
    {
        p.setPen(Qt::NoPen);
        for (int i = 10; i >= 1; --i) {
            const qreal off = i * 2.6;
            const qreal a = accentGlow_ * 24.0 * (1.0 - i / 12.0) * (0.60 + 0.40 * glowPulse_);
            p.setBrush(QColor(78, 148, 255, qBound(0, int(a), 255)));
            p.drawRoundedRect(r.adjusted(-off, -off, off, off), kCornerRadius + off, kCornerRadius + off);
        }
    }

    p.save();
    p.setClipPath(panelPath);

    // 底色
    {
        QLinearGradient bg(r.topLeft(), r.bottomRight());
        bg.setColorAt(0.0, QColor("#151a2b")); bg.setColorAt(0.5, QColor("#0b0e17")); bg.setColorAt(1.0, QColor("#06070b"));
        p.fillRect(r, bg);
    }

    // 极光 (加入色相偏移)
    {
        const qreal t = time_;
        qreal hueShift = std::fmod(t * 0.05, 1.0);
        auto blob = [&](const QPointF& c, qreal rad, const QColor& base) {
            QRadialGradient g(c, rad);
            QColor inner = base; g.setColorAt(0.0, inner); inner.setAlpha(0); g.setColorAt(1.0, inner);
            p.fillRect(r, g);
        };
        blob(r.center() + QPointF(std::cos(t * 0.55) * r.width() * 0.30, std::sin(t * 0.83) * r.height() * 0.34), r.width() * 0.55, QColor::fromHsvF(hueShift, 0.8, 1.0, 0.32));
        blob(r.center() + QPointF(std::cos(-t * 0.47 + 2.1) * r.width() * 0.34, std::sin(-t * 0.61 + 1.3) * r.height() * 0.38), r.width() * 0.50, QColor::fromHsvF(std::fmod(hueShift + 0.3, 1.0), 0.8, 1.0, 0.27));
        blob(r.center() + QPointF(std::cos(t * 0.71 + 4.0) * r.width() * 0.36, std::sin(t * 0.39 + 3.0) * r.height() * 0.42), r.width() * 0.44, QColor::fromHsvF(std::fmod(hueShift + 0.6, 1.0), 0.8, 1.0, 0.2));
    }

    // 网格
    {
        p.save(); p.setPen(QPen(QColor(255, 255, 255, 8), 1.0));
        for (qreal x = r.left() + 16.0; x < r.right(); x += 36.0) p.drawLine(QPointF(x, r.top()), QPointF(x, r.bottom()));
        for (qreal y = r.top() + 16.0; y < r.bottom(); y += 36.0) p.drawLine(QPointF(r.left(), y), QPointF(r.right(), y));
        p.restore();
    }

    // 粒子
    {
        p.setPen(Qt::NoPen);
        for (const auto& pt : particles_) {
            const qreal k = 1.0 - pt.life / pt.maxLife;
            if (k <= 0.0) continue;
            QColor c = pt.color; c.setAlphaF(qBound(0.0, 0.60 * k, 1.0));
            p.setBrush(c); p.drawEllipse(pt.pos, pt.radius, pt.radius);
        }
    }

    // 扩散光环
    if (ringSpread_ < 1.0) {
        p.setBrush(Qt::NoBrush);
        for (int i = 0; i < 3; ++i) {
            const qreal s = qBound(0.0, ringSpread_ * 1.30 - i * 0.14, 1.0);
            if (s <= 0.001) continue;
            const qreal rad = 26.0 + s * 250.0;
            QColor c(118, 176, 255); c.setAlphaF(0.55 * (1.0 - s) * (1.0 - s));
            p.setPen(QPen(c, 1.6));
            p.drawEllipse(QRectF(r.center() - QPointF(rad, rad * 0.55), QSizeF(rad * 2.0, rad * 1.10)));
        }
    }

    // 旋转弧线
    {
        const qreal spin = std::fmod(time_ * 0.42, 1.0) * 360.0;
        const qreal rad  = 132.0;
        const QRectF er(r.center() - QPointF(rad, rad * 0.60), QSizeF(rad * 2.0, rad * 1.20));
        QConicalGradient cg(r.center(), -spin);
        cg.setColorAt(0.00, QColor(120, 190, 255, 0)); cg.setColorAt(0.18, QColor(120, 190, 255, 170));
        cg.setColorAt(0.42, QColor(190, 150, 255, 210)); cg.setColorAt(0.66, QColor(90, 160, 255, 120));
        cg.setColorAt(1.00, QColor(120, 190, 255, 0));
        p.setBrush(Qt::NoBrush); p.setPen(QPen(QBrush(cg), 1.8, Qt::SolidLine, Qt::RoundCap));
        p.drawArc(er, int(-spin * 16.0), int(210 * 16));
        p.setPen(QPen(QColor(120, 180, 255, 58), 1.2, Qt::SolidLine, Qt::RoundCap));
        p.drawArc(er.adjusted(-14, -10, 14, 10), int(spin * 16.0), int(120 * 16));
    }

    // 中心辉光
    {
        const qreal rad = r.width() * (0.32 + 0.10 * glowPulse_);
        QRadialGradient g(r.center() + QPointF(0, -8), rad);
        const int peak = int(150.0 * (0.30 + 0.70 * glowPulse_));
        g.setColorAt(0.00, QColor(92, 152, 255, peak)); g.setColorAt(0.40, QColor(80, 130, 245, peak / 3));
        g.setColorAt(1.00, QColor(80, 130, 245, 0)); p.fillRect(r, g);
    }

    // LOGO 文字 (加入色差故障感)
    {
        QFont f(QStringLiteral("Segoe UI"));
        f.setPointSizeF(41.0); f.setBold(true); f.setLetterSpacing(QFont::PercentageSpacing, 103.0);
        const QString text = QStringLiteral("Nebula");
        QFontMetricsF fm(f);
        const qreal total = fm.horizontalAdvance(text);
        const qreal baseY = r.center().y() + fm.ascent() * 0.30;
        p.setFont(f);

        const qreal revealed = textReveal_ * (text.size() + 1);
        const qreal gx0 = r.center().x() - total / 2.0;
        const qreal gx1 = r.center().x() + total / 2.0;

        QLinearGradient tg(QPointF(gx0, 0.0), QPointF(gx1, 0.0));
        tg.setColorAt(0.00, QColor(176, 204, 255)); tg.setColorAt(0.35, QColor(240, 247, 255));
        tg.setColorAt(0.62, QColor(255, 255, 255)); tg.setColorAt(1.00, QColor(158, 190, 255));

        if (shimmer_ > 0.0 && shimmer_ < 1.0) {
            if (shimmer_ - 0.12 > 0.0) tg.setColorAt(shimmer_ - 0.12, QColor(205, 228, 255));
            tg.setColorAt(shimmer_, QColor(255, 255, 255));
            if (shimmer_ + 0.12 < 1.0) tg.setColorAt(shimmer_ + 0.12, QColor(205, 228, 255));
        }

        qreal x = r.center().x() - total / 2.0;
        for (int i = 0; i < text.size(); ++i) {
            const QString ch = text.mid(i, 1); const qreal w  = fm.horizontalAdvance(ch);
            const qreal a  = qBound(0.0, revealed - i, 1.0);
            if (a > 0.005) {
                QPainterPath gp; gp.addText(x, baseY, f, ch);
                p.save();
                p.translate(r.center().x(), baseY); p.scale(logoScale_, logoScale_); p.translate(-r.center().x(), -baseY);
                p.translate(0.0, (1.0 - a) * 26.0); p.setOpacity(logoOpacity_ * a);
                
                // 红蓝色差边缘
                QPainterPath pRed = gp; pRed.translate(-1.5, 0);
                QPainterPath pBlue = gp; pBlue.translate(1.5, 0);
                p.fillPath(pRed, QColor(255, 0, 80, 100));
                p.fillPath(pBlue, QColor(0, 150, 255, 100));

                p.strokePath(gp, QPen(QColor(112, 172, 255, 105), 6.0, Qt::SolidLine, Qt::RoundCap, Qt::RoundJoin));
                p.strokePath(gp, QPen(QColor(155, 205, 255, 70), 2.4, Qt::SolidLine, Qt::RoundCap, Qt::RoundJoin));
                p.fillPath(gp, tg); p.restore();
            }
            x += w;
        }

        if (textReveal_ > 0.0) {
            const qreal uw = total * 0.66 * textReveal_; const qreal uy = baseY + 20.0;
            QLinearGradient lg(QPointF(-uw / 2.0, 0.0), QPointF(uw / 2.0, 0.0));
            lg.setColorAt(0.0, QColor(90, 150, 255, 0)); lg.setColorAt(0.5, QColor(140, 195, 255, 200)); lg.setColorAt(1.0, QColor(90, 150, 255, 0));
            p.setPen(Qt::NoPen); p.setBrush(lg); p.drawRoundedRect(QRectF(r.center().x() - uw / 2.0, uy, uw, 1.6), 0.8, 0.8);
        }
    }

    // 进度条
    {
        const qreal trackY = r.bottom() - 44.0; const qreal trackL = r.left() + 86.0; const qreal trackR = r.right() - 86.0;
        const qreal trackW = trackR - trackL;
        p.setPen(Qt::NoPen); p.setBrush(QColor(255, 255, 255, 24)); p.drawRoundedRect(QRectF(trackL, trackY - 1.5, trackW, 3.0), 1.5, 1.5);
        const qreal headX = trackL + trackW * qBound(0.0, scanProgress_, 1.0);
        if (headX > trackL + 1.0) {
            QLinearGradient fill(trackL, 0.0, headX, 0.0);
            fill.setColorAt(0.0, QColor(64, 122, 255, 60)); fill.setColorAt(0.55, QColor(112, 176, 255, 190)); fill.setColorAt(1.0, QColor(188, 224, 255, 255));
            p.setBrush(fill); p.drawRoundedRect(QRectF(trackL, trackY - 1.5, headX - trackL, 3.0), 1.5, 1.5);
            for (int i = 1; i <= 6; ++i) {
                const qreal tx = headX - i * 9.5; if (tx <= trackL) break;
                QColor c(150, 198, 255); c.setAlphaF(0.55 * (1.0 - i / 7.0) * (0.6 + 0.4 * glowPulse_));
                p.setBrush(c); p.drawEllipse(QPointF(tx, trackY), 1.9, 1.9);
            }
            QRadialGradient hg(QPointF(headX, trackY), 18.0);
            hg.setColorAt(0.00, QColor(220, 240, 255, 220)); hg.setColorAt(0.35, QColor(130, 185, 255, 110)); hg.setColorAt(1.00, QColor(130, 185, 255, 0));
            p.setBrush(hg); p.drawEllipse(QPointF(headX, trackY), 18.0, 18.0);
            p.setBrush(QColor(245, 251, 255)); p.drawEllipse(QPointF(headX, trackY), 3.2, 3.2);
        }
    }

    // 扫光
    {
        const qreal sw = r.width() * 0.42; const qreal cx = r.left() - sw + (r.width() + sw * 2.0) * sweepPos_;
        QLinearGradient sg(QPointF(cx - sw * 0.5, r.top()), QPointF(cx + sw * 0.5, r.bottom()));
        sg.setColorAt(0.0, QColor(255, 255, 255, 0)); sg.setColorAt(0.5, QColor(190, 220, 255, 38)); sg.setColorAt(1.0, QColor(255, 255, 255, 0));
        p.setPen(Qt::NoPen); p.fillRect(r, sg);
    }

    // 暗角
    {
        QRadialGradient vg(r.center(), r.width() * 0.80);
        vg.setColorAt(0.00, QColor(0, 0, 0, 0)); vg.setColorAt(0.62, QColor(0, 0, 0, 0)); vg.setColorAt(1.00, QColor(0, 0, 0, 130));
        p.setPen(Qt::NoPen); p.fillRect(r, vg);
    }

    p.restore(); // 取消裁剪

    // 边框
    {
        QLinearGradient bg(QPointF(r.left(), r.top()), QPointF(r.right(), r.bottom()));
        bg.setColorAt(0.0, QColor(168, 204, 255, 110)); bg.setColorAt(0.45, QColor(120, 160, 255, 36)); bg.setColorAt(1.0, QColor(178, 138, 255, 96));
        p.setPen(QPen(QBrush(bg), 1.2)); p.setBrush(Qt::NoBrush); p.drawPath(panelPath);
    }

    p.restore(); // 取消面板缩放
}

} // namespace loader