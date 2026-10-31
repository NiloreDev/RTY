#include "TitleBar.h"

#include <QEasingCurve>
#include <QHBoxLayout>
#include <QLabel>
#include <QLinearGradient>
#include <QMouseEvent>
#include <QPainter>
#include <QPropertyAnimation>
#include <QRadialGradient>
#include <QToolButton>
#include <QWindow>

#include <cmath>

namespace loader {

namespace {
constexpr int kBarHeight = 52;

const char* kWindowButton = R"qss(
QToolButton {
    background: rgba(255,255,255,8);
    border: 1px solid rgba(255,255,255,14);
    border-radius: 10px;
    color: #cfc7dd;
    min-width: 34px;
    max-width: 34px;
    min-height: 30px;
    max-height: 30px;
    font: 600 15px "Segoe UI";
}
QToolButton:hover {
    color: white;
    background: rgba(150,105,255,42);
    border-color: rgba(203,174,255,70);
}
QToolButton:pressed { background: rgba(150,105,255,70); }
)qss";

const char* kCloseButton = R"qss(
QToolButton {
    background: rgba(255,255,255,8);
    border: 1px solid rgba(255,255,255,14);
    border-radius: 10px;
    color: #cfc7dd;
    min-width: 34px;
    max-width: 34px;
    min-height: 30px;
    max-height: 30px;
    font: 600 14px "Segoe UI";
}
QToolButton:hover {
    color: white;
    background: rgba(255,76,126,62);
    border-color: rgba(255,128,163,90);
}
QToolButton:pressed { background: rgba(235,55,105,88); }
)qss";
}

TitleBar::TitleBar(QWidget* parent) : QWidget(parent) {
    setFixedHeight(kBarHeight);
    setAttribute(Qt::WA_StyledBackground, false);
    setAutoFillBackground(false);

    auto* layout = new QHBoxLayout(this);
    layout->setContentsMargins(18, 0, 14, 0);
    layout->setSpacing(10);

    auto* orbSpace = new QWidget(this);
    orbSpace->setFixedSize(24, 24);
    layout->addWidget(orbSpace);

    auto* titleStack = new QWidget(this);
    auto* titleLayout = new QVBoxLayout(titleStack);
    titleLayout->setContentsMargins(0, 5, 0, 5);
    titleLayout->setSpacing(0);

    titleLabel_ = new QLabel(QStringLiteral("Nebula"), titleStack);
    titleLabel_->setStyleSheet(QStringLiteral(
        "color:#fffaff; font:800 13px 'Segoe UI'; letter-spacing:.7px;"));

    auto* sub = new QLabel(QStringLiteral("AURORA EDITION"), titleStack);
    sub->setStyleSheet(QStringLiteral(
        "color:#887b9e; font:700 8px 'Segoe UI'; letter-spacing:1.6px;"));

    titleLayout->addWidget(titleLabel_);
    titleLayout->addWidget(sub);
    layout->addWidget(titleStack);
    layout->addStretch(1);

    onlineLabel_ = new QLabel(QStringLiteral("●  ONLINE"), this);
    onlineLabel_->setStyleSheet(QStringLiteral(
        "QLabel{color:#a8ffd5;background:rgba(62,224,157,16);"
        "border:1px solid rgba(89,240,181,40);border-radius:9px;"
        "padding:5px 9px;font:800 8px 'Segoe UI';letter-spacing:.8px;}"));
    layout->addWidget(onlineLabel_);
    layout->addSpacing(2);

    minBtn_ = new QToolButton(this);
    minBtn_->setText(QStringLiteral("−"));
    minBtn_->setCursor(Qt::PointingHandCursor);
    minBtn_->setStyleSheet(QString::fromUtf8(kWindowButton));
    connect(minBtn_, &QToolButton::clicked, this, [this] {
        if (auto* w = window()) w->showMinimized();
    });
    layout->addWidget(minBtn_);

    closeBtn_ = new QToolButton(this);
    closeBtn_->setText(QStringLiteral("×"));
    closeBtn_->setCursor(Qt::PointingHandCursor);
    closeBtn_->setStyleSheet(QString::fromUtf8(kCloseButton));
    connect(closeBtn_, &QToolButton::clicked, this, [this] {
        if (auto* w = window()) w->close();
    });
    layout->addWidget(closeBtn_);

    auto* pulseAnim = new QPropertyAnimation(this, "pulse", this);
    pulseAnim->setDuration(3000);
    pulseAnim->setStartValue(0.0);
    pulseAnim->setKeyValueAt(0.5, 1.0);
    pulseAnim->setEndValue(0.0);
    pulseAnim->setEasingCurve(QEasingCurve::InOutSine);
    pulseAnim->setLoopCount(-1);
    pulseAnim->start();
}

void TitleBar::setTitleText(const QString& text) {
    titleLabel_->setText(text);
}

void TitleBar::setPulse(qreal value) {
    pulse_ = value;
    update();
}

void TitleBar::mousePressEvent(QMouseEvent* e) {
    if (e->button() == Qt::LeftButton) {
        if (auto* w = window()) {
            if (auto* handle = w->windowHandle()) {
                handle->startSystemMove();
                e->accept();
                return;
            }
        }
    }
    QWidget::mousePressEvent(e);
}

void TitleBar::paintEvent(QPaintEvent*) {
    QPainter p(this);
    p.setRenderHint(QPainter::Antialiasing);

    const QRectF r(rect());
    QLinearGradient bg(r.topLeft(), r.topRight());
    bg.setColorAt(0.0, QColor(13, 9, 23, 247));
    bg.setColorAt(0.48, QColor(20, 13, 34, 242));
    bg.setColorAt(1.0, QColor(8, 18, 27, 244));
    p.fillRect(r, bg);

    const qreal sweepX = width() * (0.08 + pulse_ * 0.72);
    QRadialGradient sweep(QPointF(sweepX, height() * 0.5), 175.0);
    sweep.setColorAt(0.0, QColor(255, 102, 192, 30));
    sweep.setColorAt(0.45, QColor(128, 98, 255, 18));
    sweep.setColorAt(1.0, QColor(0,0,0,0));
    p.fillRect(rect(), sweep);

    p.setPen(QColor(255,255,255,14));
    p.drawLine(0, height()-1, width(), height()-1);

    const QPointF c(29.0, height() * 0.5);
    const qreal ring = 8.2 + pulse_ * 2.2;

    QRadialGradient orb(c, ring + 8.0);
    orb.setColorAt(0.0, QColor(255,255,255,255));
    orb.setColorAt(0.18, QColor(255,132,204,245));
    orb.setColorAt(0.55, QColor(142,106,255,180));
    orb.setColorAt(1.0, QColor(0,0,0,0));
    p.setPen(Qt::NoPen);
    p.setBrush(orb);
    p.drawEllipse(c, ring + 8.0, ring + 8.0);

    p.setBrush(QColor(255,248,255));
    p.drawEllipse(c, 2.8, 2.8);

    p.setBrush(Qt::NoBrush);
    p.setPen(QPen(QColor(110,225,255,100), 1));
    p.save();
    p.translate(c);
    p.rotate(-14.0 + pulse_ * 28.0);
    p.drawEllipse(QRectF(-10.0, -4.2, 20.0, 8.4));
    p.restore();
}

} // namespace loader