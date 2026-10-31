#include "InstanceRow.h"
#include <QEasingCurve>
#include <QEnterEvent>
#include <QHBoxLayout>
#include <QLabel>
#include <QLinearGradient>
#include <QPainter>
#include <QPainterPath>
#include <QPropertyAnimation>
#include <QPushButton>
#include <QRadialGradient>
#include <QTimer>

namespace loader {
namespace {
constexpr int kRowHeight   = 64;
constexpr int kCornerRadius = 10;
}

InstanceRow::InstanceRow(unsigned long pid, const QString& title, const QString& commandLine, QWidget* parent)
    : QFrame(parent), pid_(pid), title_(title), commandLine_(commandLine) {
    setFixedHeight(kRowHeight); setAttribute(Qt::WA_StyledBackground, false); setMouseTracking(true);

    auto* layout = new QHBoxLayout(this);
    layout->setContentsMargins(18, 8, 16, 8); layout->setSpacing(14);

    pidLabel_ = new QLabel(QString::number(pid_), this);
    pidLabel_->setMinimumWidth(72); pidLabel_->setAlignment(Qt::AlignVCenter | Qt::AlignLeft);
    pidLabel_->setStyleSheet("color: #64d2ff; font-family: 'JetBrains Mono', 'Consolas', monospace; font-size: 13px; font-weight: 700;");
    layout->addWidget(pidLabel_);

    titleLabel_ = new QLabel(title_, this);
    titleLabel_->setAlignment(Qt::AlignVCenter | Qt::AlignLeft);
    titleLabel_->setStyleSheet("color: #ffffff; font-size: 14px; font-family: 'Segoe UI', 'Microsoft YaHei UI', sans-serif;");
    layout->addWidget(titleLabel_, 1);

    nativeBadge_ = new QLabel(QStringLiteral("BYPASS ACTIVE"), this);
    nativeBadge_->setAlignment(Qt::AlignVCenter | Qt::AlignCenter);
    nativeBadge_->setStyleSheet("color: #0a0b0e; background: #3ddc84; border-radius: 6px; padding: 2px 8px; font-size: 10px; font-weight: 800;");
    nativeBadge_->setVisible(false); layout->addWidget(nativeBadge_);

    injectBtn_ = new QPushButton(QStringLiteral("INJECT"), this);
    injectBtn_->setCursor(Qt::PointingHandCursor); injectBtn_->setFixedHeight(32); injectBtn_->setFixedWidth(82);
    injectBtn_->setStyleSheet(
        "QPushButton {"
        "  background: qlineargradient(x1:0, y1:0, x2:1, y2:1, stop:0 #3d6fd1, stop:1 #64d2ff);"
        "  color: white; border: 1px solid #64d2ff; border-radius: 6px;"
        "  font-weight: 800; font-size: 11px; letter-spacing: 1px;"
        "}"
        "QPushButton:hover { background: qlineargradient(x1:0, y1:0, x2:1, y2:1, stop:0 #64d2ff, stop:1 #b464ff); border: 1px solid #b464ff; }"
        "QPushButton:pressed { background: #1e468f; }"
    );
    connect(injectBtn_, &QPushButton::clicked, this, [this] { emit injectClicked(pid_, title_, commandLine_); });
    layout->addWidget(injectBtn_);

    m_animTimer = new QTimer(this); m_animTimer->setInterval(16);
    connect(m_animTimer, &QTimer::timeout, this, &InstanceRow::onTick); m_animTimer->start();
}

void InstanceRow::onTick() { time_ += 0.016; update(); }
void InstanceRow::updateTitle(const QString& title) { if (title == title_) return; title_ = title; titleLabel_->setText(title_); }
void InstanceRow::setNativeBypassed(bool on) { if (nativeBadge_) nativeBadge_->setVisible(on); }
void InstanceRow::setEntrance(qreal v) { entrance_ = v; update(); }

void InstanceRow::playEntrance() {
    entrance_ = 0.0; update();
    auto* anim = new QPropertyAnimation(this, "entrance", this);
    anim->setDuration(400); anim->setStartValue(0.0); anim->setEndValue(1.0); anim->setEasingCurve(QEasingCurve::OutQuint);
    anim->start(QAbstractAnimation::DeleteWhenStopped);
}

void InstanceRow::animateHover(qreal target) {
    if (hoverAnim_) { hoverAnim_->stop(); hoverAnim_->deleteLater(); }
    hoverAnim_ = new QPropertyAnimation(this, "hoverIntensity", this);
    hoverAnim_->setDuration(200); hoverAnim_->setStartValue(hoverIntensity_); hoverAnim_->setEndValue(target); hoverAnim_->setEasingCurve(QEasingCurve::OutCubic);
    hoverAnim_->start();
}

void InstanceRow::flashHighlight() {
    if (highlightAnim_) { highlightAnim_->stop(); highlightAnim_->deleteLater(); }
    highlightAnim_ = new QPropertyAnimation(this, "highlight", this);
    highlightAnim_->setDuration(2000); highlightAnim_->setStartValue(0.0);
    highlightAnim_->setKeyValueAt(0.15, 1.0); highlightAnim_->setKeyValueAt(0.7, 1.0); highlightAnim_->setEndValue(0.0);
    highlightAnim_->setEasingCurve(QEasingCurve::OutCubic); highlightAnim_->start(); update();
}

void InstanceRow::enterEvent(QEnterEvent*) { animateHover(1.0); }
void InstanceRow::leaveEvent(QEvent*) { animateHover(0.0); }

void InstanceRow::paintEvent(QPaintEvent*) {
    QPainter p(this); p.setRenderHint(QPainter::Antialiasing);
    qreal slideDy = (1.0 - entrance_) * 20.0;
    qreal panelAlpha = entrance_;

    QRectF rect = QRectF(this->rect()).adjusted(4, 4 + slideDy, -4, -4 + slideDy);
    QPainterPath panel; panel.addRoundedRect(rect, kCornerRadius, kCornerRadius);

    QLinearGradient base(rect.topLeft(), rect.bottomLeft());
    base.setColorAt(0.0, QColor(20, 24, 30, int(255 * panelAlpha)));
    base.setColorAt(1.0, QColor(14, 16, 22, int(255 * panelAlpha)));
    p.fillPath(panel, base);

    if (hoverIntensity_ > 0.001) {
        QLinearGradient hover(rect.topLeft(), rect.bottomRight());
        hover.setColorAt(0.0, QColor(100, 200, 255, int(25 * hoverIntensity_ * panelAlpha)));
        hover.setColorAt(1.0, QColor(180, 100, 255, int(10 * hoverIntensity_ * panelAlpha)));
        p.fillPath(panel, hover);
    }

    p.save(); p.setClipPath(panel);
    if (hoverIntensity_ > 0.01 || highlight_ > 0.01) {
        qreal speed = 200.0;
        qreal scanY = std::fmod(time_ * speed, rect.height() + 60) - 30;
        QLinearGradient scan(0, rect.top() + scanY, 0, rect.top() + scanY + 40);
        scan.setColorAt(0.0, QColor(100, 200, 255, 0)); scan.setColorAt(0.5, QColor(100, 200, 255, 40)); scan.setColorAt(1.0, QColor(100, 200, 255, 0));
        p.fillRect(rect, scan);
    }
    if (highlight_ > 0.001) {
        QRadialGradient wave(rect.center(), rect.width() * 0.6);
        wave.setColorAt(0.0, QColor(248, 194, 52, int(60 * highlight_))); wave.setColorAt(1.0, QColor(248, 194, 52, 0));
        p.fillRect(rect, wave);
    }
    p.restore();

    // 悬浮流光边框
    if (hoverIntensity_ > 0.01 || highlight_ > 0.01 || entrance_ < 1.0) {
        QConicalGradient borderGrad(rect.center(), -time_ * 150.0);
        borderGrad.setColorAt(0.0, QColor(100, 200, 255, 255)); borderGrad.setColorAt(0.3, QColor(180, 100, 255, 150));
        borderGrad.setColorAt(0.6, QColor(50, 255, 200, 150)); borderGrad.setColorAt(1.0, QColor(100, 200, 255, 255));
        qreal borderAlpha = std::max(hoverIntensity_, highlight_);
        if (entrance_ < 1.0) borderAlpha = entrance_;
        p.setOpacity(borderAlpha); p.setPen(QPen(QBrush(borderGrad), 1.5)); p.setBrush(Qt::NoBrush); p.drawPath(panel); p.setOpacity(1.0);
    } else {
        p.setPen(QPen(QColor(255, 255, 255, int(20 * panelAlpha)), 1)); p.setBrush(Qt::NoBrush); p.drawPath(panel);
    }
}
} // namespace loader