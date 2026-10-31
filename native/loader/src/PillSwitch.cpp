#include "PillSwitch.h"
#include <QMouseEvent>
#include <QPainter>
#include <QPropertyAnimation>
#include <QPainterPath>
#include <QLinearGradient>
#include <QRadialGradient>
#include <QtMath>

namespace loader {
namespace {
constexpr int kTrackW = 50;
constexpr int kTrackH = 26;
constexpr int kKnob   = 22;
constexpr int kPadX   = 2;
}

PillSwitch::PillSwitch(QWidget* parent) : QWidget(parent) {
    setFixedSize(kTrackW, kTrackH);
    setCursor(Qt::PointingHandCursor);

    m_animTimer = new QTimer(this);
    m_animTimer->setInterval(16);
    connect(m_animTimer, &QTimer::timeout, this, &PillSwitch::onTick);
    m_animTimer->start();
}

void PillSwitch::onTick() {
    if (checked_) { time_ += 0.016; update(); }
}

void PillSwitch::setChecked(bool on) {
    if (on == checked_) return;
    checked_ = on;
    animateTo(on ? 1.0 : 0.0);
    emit toggled(checked_);
}

void PillSwitch::setKnobPos(qreal v) {
    knobPos_ = v;
    update();
}

void PillSwitch::animateTo(qreal target) {
    auto* anim = new QPropertyAnimation(this, "knobPos", this);
    anim->setDuration(250);
    anim->setStartValue(knobPos_);
    anim->setEndValue(target);
    anim->setEasingCurve(QEasingCurve::OutBack);
    anim->start(QAbstractAnimation::DeleteWhenStopped);
}

void PillSwitch::mousePressEvent(QMouseEvent* e) {
    if (e->button() != Qt::LeftButton) { QWidget::mousePressEvent(e); return; }
    setChecked(!checked_);
    e->accept();
}

void PillSwitch::paintEvent(QPaintEvent*) {
    QPainter p(this);
    p.setRenderHint(QPainter::Antialiasing);

    const QRectF track(0.5, 0.5, kTrackW - 1, kTrackH - 1);

    // 1. 轨道底色
    p.setPen(Qt::NoPen);
    p.setBrush(checked_ ? QColor("#0a1526") : QColor("#1a1c22"));
    p.drawRoundedRect(track, kTrackH / 2.0, kTrackH / 2.0);

    // 2. 开启时的等离子流光
    if (checked_) {
        p.save();
        QPainterPath clipPath;
        clipPath.addRoundedRect(track, kTrackH / 2.0, kTrackH / 2.0);
        p.setClipPath(clipPath);

        qreal shift = std::fmod(time_ * 0.8, 1.0);
        QLinearGradient dynamicFlow(-kTrackW + kTrackW * shift, 0, kTrackW * shift, 0);
        dynamicFlow.setColorAt(0.0, QColor(100, 200, 255, 100));
        dynamicFlow.setColorAt(0.5, QColor(180, 100, 255, 180));
        dynamicFlow.setColorAt(1.0, QColor(100, 200, 255, 100));
        p.fillRect(track, dynamicFlow);
        p.restore();

        p.setPen(QPen(QColor(100, 200, 255, 150), 1));
        p.setBrush(Qt::NoBrush);
        p.drawRoundedRect(track, kTrackH / 2.0, kTrackH / 2.0);
    } else {
        p.setPen(QPen(QColor(255, 255, 255, 20), 1));
        p.setBrush(Qt::NoBrush);
        p.drawRoundedRect(track, kTrackH / 2.0, kTrackH / 2.0);
    }

    // 3. 滑块
    const qreal x = kPadX + knobPos_ * (kTrackW - 2 * kPadX - kKnob);
    const QRectF knob(x, (kTrackH - kKnob) / 2.0, kKnob, kKnob);

    // 滑块拖尾光晕
    if (checked_) {
        QRadialGradient glow(knob.center(), kKnob * 1.5);
        glow.setColorAt(0.0, QColor(150, 230, 255, 220));
        glow.setColorAt(0.4, QColor(100, 200, 255, 80));
        glow.setColorAt(1.0, QColor(100, 200, 255, 0));
        p.setBrush(glow);
        p.drawEllipse(knob.center(), kKnob * 1.5, kKnob * 1.5);
    }

    p.setBrush(QColor("#ffffff"));
    p.drawEllipse(knob);

    QLinearGradient knobGrad(knob.topLeft(), knob.bottomLeft());
    if (checked_) {
        knobGrad.setColorAt(0.0, QColor(255, 255, 255, 255));
        knobGrad.setColorAt(1.0, QColor(200, 240, 255, 255));
    } else {
        knobGrad.setColorAt(0.0, QColor(255, 255, 255, 255));
        knobGrad.setColorAt(1.0, QColor(220, 224, 232, 255));
    }
    p.setBrush(knobGrad);
    p.drawEllipse(knob);

    p.setBrush(QColor(255, 255, 255, 255));
    p.drawEllipse(knob.center(), kKnob * 0.2, kKnob * 0.2);
}
} // namespace loader