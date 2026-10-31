#include "InjectionOverlay.h"

#include "loader.h"
#include "loader_settings.h"

#include <QApplication>
#include <QEasingCurve>
#include <QFont>
#include <QFontMetricsF>
#include <QLinearGradient>
#include <QPainter>
#include <QPainterPath>
#include <QPropertyAnimation>
#include <QRadialGradient>
#include <QConicalGradient>
#include <QScreen>
#include <QSequentialAnimationGroup>
#include <QtMath>

#include <algorithm>
#include <thread>
#include <cmath>

// 安全的 PI 定义
#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

namespace loader {

namespace {
constexpr int kW = 460;
constexpr int kH = 280;
constexpr int kCornerRadius = 16;
}

InjectionOverlay::InjectionOverlay(unsigned long pid, const QString& target,
                                   const QString& commandLine,
                                   QWidget* parent)
        : QWidget(parent), pid_(pid), target_(target), commandLine_(commandLine) {
    setWindowFlags(Qt::FramelessWindowHint | Qt::Tool | Qt::WindowStaysOnTopHint);
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

    frameTimer_ = new QTimer(this);
    frameTimer_->setInterval(16);
    connect(frameTimer_, &QTimer::timeout, this, &InjectionOverlay::onTick);
}

void InjectionOverlay::start() {
    show();
    raise();
    activateWindow();

    auto* panelFade = new QPropertyAnimation(this, "panelOpacity", this);
    panelFade->setDuration(300);
    panelFade->setStartValue(0.0);
    panelFade->setEndValue(1.0);
    panelFade->setEasingCurve(QEasingCurve::OutCubic);
    panelFade->start(QAbstractAnimation::DeleteWhenStopped);

    auto* progAnim = new QPropertyAnimation(this, "progress", this);
    progAnim->setDuration(1200);
    progAnim->setStartValue(0.0);
    progAnim->setEndValue(0.7);
    progAnim->setEasingCurve(QEasingCurve::OutCubic);
    progAnim->start(QAbstractAnimation::DeleteWhenStopped);

    frameTimer_->start();

    std::thread([this]() {
        std::wstring err = inject(static_cast<DWORD>(pid_));
        QString qerr = QString::fromWCharArray(err.c_str(), static_cast<int>(err.size()));
        QMetaObject::invokeMethod(this, [this, qerr]() {
            onInjectResult(qerr);
        }, Qt::QueuedConnection);
    }).detach();
}

void InjectionOverlay::onTick() {
    time_ += 0.016;
    update();
}

void InjectionOverlay::onInjectResult(QString err) {
    injectDone_ = true;
    injectOk_   = err.isEmpty();
    injectErr_  = err;

    statusText_ = injectOk_ ? QStringLiteral("Payload Active") : QStringLiteral("Injection Failed");
    update();

    auto* burst = new QPropertyAnimation(this, "energyBurst", this);
    burst->setDuration(600);
    burst->setStartValue(0.0);
    burst->setEndValue(1.0);
    burst->setEasingCurve(QEasingCurve::OutQuint);

    auto* fadeOut = new QPropertyAnimation(this, "panelOpacity", this);
    fadeOut->setDuration(300);
    fadeOut->setStartValue(1.0);
    fadeOut->setEndValue(0.0);
    fadeOut->setEasingCurve(QEasingCurve::InCubic);

    auto* seq = new QSequentialAnimationGroup(this);
    seq->addAnimation(burst);
    seq->addPause(500);
    seq->addAnimation(fadeOut);

    connect(seq, &QAbstractAnimation::finished, this, [this]() {
        frameTimer_->stop();
        emit completed(injectOk_, pid_);
        close();
    });
    seq->start(QAbstractAnimation::DeleteWhenStopped);
}

void InjectionOverlay::paintEvent(QPaintEvent*) {
    QPainter p(this);
    p.setRenderHint(QPainter::Antialiasing);
    p.setRenderHint(QPainter::TextAntialiasing);
    p.setOpacity(panelOpacity_);

    // 如果能量爆裂，触发屏幕横向撕裂错位特效
    if (energyBurst_ > 0.0 && energyBurst_ < 0.8) {
        p.translate(std::sin(time_ * 100.0) * 15.0 * (1.0 - energyBurst_), 0);
    }

    QRectF rect = QRectF(this->rect()).adjusted(0.5, 0.5, -0.5, -0.5);
    QPainterPath panel;
    panel.addRoundedRect(rect, kCornerRadius, kCornerRadius);
    p.setClipPath(panel);

    QLinearGradient bg(rect.topLeft(), rect.bottomLeft());
    bg.setColorAt(0.0, QColor("#111318"));
    bg.setColorAt(1.0, QColor("#06070a"));
    p.fillRect(rect, bg);

    QPointF center = rect.center() + QPointF(0, -15);

    qreal heartBeat = 0.0;
    if (!injectDone_) {
        qreal beatCycle = std::fmod(time_, 1.2);
        if (beatCycle < 0.15) heartBeat = std::sin(beatCycle / 0.15 * M_PI) * 0.1;
        else if (beatCycle > 0.25 && beatCycle < 0.4) heartBeat = std::sin((beatCycle - 0.25) / 0.15 * M_PI) * 0.06;
    }
    qreal baseRadius = 48.0 * (1.0 + heartBeat);

    QColor themeColor = injectDone_ ? (injectOk_ ? QColor(62, 207, 107) : QColor(226, 86, 86)) : QColor(100, 200, 255);
    QColor themeColor2 = injectDone_ ? (injectOk_ ? QColor(150, 255, 180) : QColor(255, 150, 150)) : QColor(180, 100, 255);

    QRadialGradient coreGlow(center, baseRadius * (3.0 + progress_ * 1.5));
    coreGlow.setColorAt(0.0, QColor(themeColor.red(), themeColor.green(), themeColor.blue(), (int)(80 * (1.0 - energyBurst_))));
    coreGlow.setColorAt(0.4, QColor(themeColor.red(), themeColor.green(), themeColor.blue(), (int)(30 * (1.0 - energyBurst_))));
    coreGlow.setColorAt(1.0, QColor(0, 0, 0, 0));
    p.fillRect(rect, coreGlow);

    if (!injectDone_) {
        p.setPen(Qt::NoPen);
        for (int i = 0; i < 12; ++i) {
            qreal angle = i * (360.0 / 12) + time_ * 50.0;
            qreal dist = 100.0 - std::fmod(time_ * 40.0 + i * 15.0, 90.0);
            qreal px = center.x() + dist * std::cos(qDegreesToRadians(angle));
            qreal py = center.y() + dist * std::sin(qDegreesToRadians(angle));
            qreal alpha = (1.0 - (dist - 10.0) / 90.0) * 255;
            p.setBrush(QColor(255, 255, 255, (int)alpha));
            p.drawEllipse(QPointF(px, py), 1.5, 1.5);
        }
    }

    if (energyBurst_ > 0.0) {
        p.setBrush(Qt::NoBrush);
        for (int i = 0; i < 3; ++i) {
            qreal b = std::max(0.0, energyBurst_ - i * 0.15) / (1.0 - i * 0.15);
            if (b <= 0) continue;
            qreal r = baseRadius + b * 200.0;
            QColor c = themeColor;
            c.setAlpha((int)(255 * (1.0 - b)));
            p.setPen(QPen(c, 3.0 * (1.0 - b)));
            p.drawEllipse(center, r, r);
        }
    }

    if (!injectDone_) {
        p.save();
        p.translate(center);
        p.rotate(time_ * 120.0);

        QConicalGradient g1(0, 0, time_ * -180.0);
        g1.setColorAt(0.0, themeColor);
        g1.setColorAt(0.5, themeColor2);
        g1.setColorAt(1.0, themeColor);
        p.setPen(QPen(QBrush(g1), 5.0, Qt::SolidLine, Qt::RoundCap));
        p.drawArc(QRectF(-baseRadius, -baseRadius, baseRadius*2, baseRadius*2), 0, 270 * 16);

        qreal hx = baseRadius * std::cos(qDegreesToRadians(270.0));
        qreal hy = baseRadius * std::sin(qDegreesToRadians(270.0));
        p.setPen(Qt::NoPen);
        p.setBrush(Qt::white);
        p.drawEllipse(QPointF(hx, hy), 4, 4);
        p.restore();

        p.save();
        p.translate(center);
        p.rotate(time_ * -80.0);
        for (int i = 0; i < 36; ++i) {
            p.rotate(10.0);
            qreal len = (i % 3 == 0) ? 8.0 : 4.0;
            len += std::sin(time_ * 8.0 + i * 0.5) * 4.0;
            p.setPen(QPen(QColor(255, 255, 255, 150), 1.5));
            p.drawLine(QPointF(baseRadius + 20, 0), QPointF(baseRadius + 20 + len, 0));
        }
        p.restore();

        p.setPen(Qt::NoPen);
        QRadialGradient core(center, 15 * (1.0 + heartBeat * 1.5));
        core.setColorAt(0, QColor(255, 255, 255, 255));
        core.setColorAt(1, QColor(themeColor.red(), themeColor.green(), themeColor.blue(), 0));
        p.setBrush(core);
        p.drawEllipse(center, 15 * (1.0 + heartBeat * 1.5), 15 * (1.0 + heartBeat * 1.5));
    } else {
        p.setPen(Qt::NoPen);
        QRadialGradient finalCore(center, baseRadius * 0.8);
        finalCore.setColorAt(0, QColor(255, 255, 255, 255));
        finalCore.setColorAt(0.5, themeColor);
        finalCore.setColorAt(1, QColor(themeColor.red(), themeColor.green(), themeColor.blue(), 0));
        p.setBrush(finalCore);
        p.drawEllipse(center, baseRadius * 0.8, baseRadius * 0.8);
    }

    {
        QFont f(QStringLiteral("Segoe UI"));
        f.setPointSize(12);
        f.setBold(true);
        p.setFont(f);
        p.setPen(QColor("#e3e7ef"));
        QFontMetricsF fm(f);
        qreal tw = fm.horizontalAdvance(statusText_);
        p.drawText(QPointF(rect.center().x() - tw / 2, rect.bottom() - 55), statusText_);

        f.setPointSize(9);
        f.setBold(false);
        p.setFont(f);
        p.setPen(QColor("#8a90a0"));
        QFontMetricsF fm2(f);
        QString sub = QStringLiteral("PID %1 · %2").arg(pid_).arg(target_);
        const qreal maxW = rect.width() - 60;
        if (fm2.horizontalAdvance(sub) > maxW) {
            sub = fm2.elidedText(sub, Qt::ElideMiddle, static_cast<int>(maxW));
        }
        qreal sw = fm2.horizontalAdvance(sub);
        p.drawText(QPointF(rect.center().x() - sw / 2, rect.bottom() - 32), sub);
    }

    p.setClipping(false);
    QLinearGradient borderBg(rect.topLeft(), rect.bottomRight());
    borderBg.setColorAt(0.0, QColor(themeColor.red(), themeColor.green(), themeColor.blue(), 150));
    borderBg.setColorAt(1.0, QColor(255, 255, 255, 30));
    p.setPen(QPen(QBrush(borderBg), 1.0));
    p.setBrush(Qt::NoBrush);
    p.drawPath(panel);
}

} // namespace loader