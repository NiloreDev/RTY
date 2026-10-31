#include "Sidebar.h"
#include <QButtonGroup>
#include <QLabel>
#include <QVBoxLayout>
#include <QPainter>
#include <QPainterPath>
#include <QLinearGradient>
#include <QRadialGradient>
#include <QMouseEvent>
#include <QPropertyAnimation>

namespace loader {
namespace { constexpr int kSidebarWidth = 150; }

SidebarButton::SidebarButton(const QString& text, bool isSettings, QWidget* parent)
    : QPushButton(text, parent), m_isSettings(isSettings) {
    setCheckable(true); setCursor(Qt::PointingHandCursor); setFixedHeight(44);
    setStyleSheet("QPushButton { border: none; background: transparent; outline: none; }");

    m_glowAnim = new QPropertyAnimation(this, "glowProgress", this);
    m_glowAnim->setDuration(350); m_glowAnim->setEasingCurve(QEasingCurve::OutCubic);
    m_hoverAnim = new QPropertyAnimation(this, "hoverProgress", this);
    m_hoverAnim->setDuration(200); m_hoverAnim->setEasingCurve(QEasingCurve::OutCubic);
}

void SidebarButton::animateGlow(bool on) {
    m_glowAnim->stop(); m_glowAnim->setStartValue(m_glowProgress); m_glowAnim->setEndValue(on ? 1.0 : 0.0); m_glowAnim->start();
}
void SidebarButton::animateHover(bool on) {
    m_hoverAnim->stop(); m_hoverAnim->setStartValue(m_hoverProgress); m_hoverAnim->setEndValue(on ? 1.0 : 0.0); m_hoverAnim->start();
}
void SidebarButton::enterEvent(QEnterEvent* e) { animateHover(true); QPushButton::enterEvent(e); }
void SidebarButton::leaveEvent(QEvent* e) { animateHover(false); QPushButton::leaveEvent(e); }
void SidebarButton::checkStateSet() { animateGlow(isChecked()); QPushButton::checkStateSet(); }

void SidebarButton::paintEvent(QPaintEvent*) {
    QPainter p(this); p.setRenderHint(QPainter::Antialiasing); p.setRenderHint(QPainter::TextAntialiasing);
    QRectF r = rect().adjusted(6, 2, -6, -2);

    if (m_hoverProgress > 0.01 || m_glowProgress > 0.01) {
        QColor hoverBg(255, 255, 255, (int)(15 * m_hoverProgress));
        QLinearGradient glowBg(r.topLeft(), r.bottomRight());
        glowBg.setColorAt(0, QColor(100, 200, 255, (int)(40 * m_glowProgress)));
        glowBg.setColorAt(1, QColor(180, 100, 255, (int)(15 * m_glowProgress)));
        QPainterPath path; path.addRoundedRect(r, 8, 8);
        p.fillPath(path, hoverBg); if (m_glowProgress > 0.01) p.fillPath(path, glowBg);
    }

    if (m_glowProgress > 0.01) {
        QRectF barRect(r.left(), r.top() + r.height() * (1.0 - m_glowProgress) / 2.0, 3.0, r.height() * m_glowProgress);
        QLinearGradient barGrad(barRect.topLeft(), barRect.bottomLeft());
        barGrad.setColorAt(0, QColor("#64d2ff")); barGrad.setColorAt(1, QColor("#b464ff"));
        p.setPen(Qt::NoPen); p.setBrush(barGrad); p.drawRoundedRect(barRect, 1.5, 1.5);
        QRadialGradient glow(barRect.center() + QPointF(5, 0), 15);
        glow.setColorAt(0, QColor(100, 200, 255, (int)(80 * m_glowProgress))); glow.setColorAt(1, QColor(100, 200, 255, 0));
        p.fillRect(r, glow);
    }

    int rBase = 154 + (100 - 154) * m_glowProgress + (255 - 154) * m_hoverProgress * (1-m_glowProgress);
    int gBase = 162 + (200 - 162) * m_glowProgress + (255 - 162) * m_hoverProgress * (1-m_glowProgress);
    int bBase = 176 + (255 - 176) * m_glowProgress + (255 - 176) * m_hoverProgress * (1-m_glowProgress);
    QColor textColor(rBase, gBase, bBase);

    drawIcon(p, QRectF(r.left() + 12, r.top() + (r.height() - 16)/2.0, 16, 16), textColor);
    p.setFont(QFont("Segoe UI", 10, QFont::DemiBold)); p.setPen(textColor);
    p.drawText(r.adjusted(38, 0, 0, 0), Qt::AlignLeft | Qt::AlignVCenter, text());
}

void SidebarButton::drawIcon(QPainter& p, const QRectF& rect, const QColor& color) {
    p.setPen(QPen(color, 1.8, Qt::SolidLine, Qt::RoundCap, Qt::RoundJoin)); p.setBrush(Qt::NoBrush);
    p.save(); p.translate(rect.center());
    if (!m_isSettings) {
        QPainterPath path;
        path.moveTo(-2, -7); path.lineTo(4, -1); path.lineTo(-1, -1);
        path.lineTo(2, 7); path.lineTo(-4, 1); path.lineTo(1, 1); path.closeSubpath();
        p.drawPath(path);
    } else {
        for (int i = 0; i < 6; ++i) { p.rotate(60); p.drawLine(QPointF(0, -5), QPointF(0, -8)); }
        p.drawEllipse(QPointF(0, 0), 5, 5); p.drawEllipse(QPointF(0, 0), 2, 2);
    }
    p.restore();
}

Sidebar::Sidebar(QWidget* parent) : QWidget(parent) {
    setFixedWidth(kSidebarWidth); setAttribute(Qt::WA_TranslucentBackground); buildUi();
    m_animTimer = new QTimer(this); m_animTimer->setInterval(16);
    connect(m_animTimer, &QTimer::timeout, this, &Sidebar::tickAnim); m_animTimer->start();
}
void Sidebar::tickAnim() { m_time += 0.016; update(); }

void Sidebar::buildUi() {
    auto* col = new QVBoxLayout(this); col->setContentsMargins(10, 15, 10, 15); col->setSpacing(6);
    auto* brand = new QLabel(QStringLiteral("NEBULA"), this);
    brand->setStyleSheet("color: qlineargradient(x1:0, y1:0, x2:1, y2:0, stop:0 #64d2ff, stop:1 #b464ff); font-size: 16px; font-weight: 900; letter-spacing: 2px; padding: 5px 8px 15px 8px;");
    col->addWidget(brand);
    auto* inject = new SidebarButton(QStringLiteral("Inject"), false, this); inject->setChecked(true);
    auto* settings = new SidebarButton(QStringLiteral("Settings"), true, this);
    col->addWidget(inject); col->addWidget(settings); col->addStretch(1);

    group_ = new QButtonGroup(this); group_->setExclusive(true);
    group_->addButton(inject, 0); group_->addButton(settings, 1);
    connect(inject,   &QPushButton::clicked, this, &Sidebar::injectClicked);
    connect(settings, &QPushButton::clicked, this, &Sidebar::settingsClicked);
}

void Sidebar::paintEvent(QPaintEvent*) {
    QPainter p(this); p.setRenderHint(QPainter::Antialiasing);
    QRectF r = rect().adjusted(0.5, 0.5, -0.5, -0.5);
    QPainterPath path; path.addRoundedRect(r, 12, 12);

    QLinearGradient bgGrad(r.topLeft(), r.bottomLeft());
    bgGrad.setColorAt(0.0, QColor(25, 28, 36, 180)); bgGrad.setColorAt(1.0, QColor(15, 18, 24, 200));
    p.fillPath(path, bgGrad);

    QLinearGradient topEdge(r.topLeft(), QPointF(r.left(), r.top() + 15));
    topEdge.setColorAt(0.0, QColor(255, 255, 255, 80)); topEdge.setColorAt(1.0, QColor(255, 255, 255, 0));
    p.fillRect(QRectF(r.left(), r.top(), r.width(), 15), topEdge);

    p.setClipPath(path);
    qreal scanY = std::fmod(m_time * 120.0, r.height() + 80) - 40;
    QLinearGradient scanGrad(0, r.top() + scanY, 0, r.top() + scanY + 60);
    scanGrad.setColorAt(0.0, QColor(100, 200, 255, 0)); scanGrad.setColorAt(0.5, QColor(100, 200, 255, 25)); scanGrad.setColorAt(1.0, QColor(100, 200, 255, 0));
    p.fillRect(r, scanGrad);
    QRadialGradient bottomGlow(QPointF(r.center().x(), r.bottom()), r.width() * 0.8);
    bottomGlow.setColorAt(0.0, QColor(100, 200, 255, 50)); bottomGlow.setColorAt(1.0, QColor(0, 0, 0, 0));
    p.fillRect(r, bottomGlow);
    p.setClipping(false);

    // 流光边框
    QConicalGradient borderGrad(r.center(), -m_time * 50.0);
    borderGrad.setColorAt(0.0, QColor(100, 200, 255, 150));
    borderGrad.setColorAt(0.5, QColor(180, 100, 255, 50));
    borderGrad.setColorAt(1.0, QColor(100, 200, 255, 150));
    p.setPen(QPen(QBrush(borderGrad), 1.0)); p.setBrush(Qt::NoBrush); p.drawPath(path);
}
} // namespace loader