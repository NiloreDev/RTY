#include "ToastNotification.h"
#include <QPainter>
#include <QPainterPath>
#include <QPropertyAnimation>
#include <QScreen>
#include <QApplication>
#include <QTimer>

ToastNotification::ToastNotification(const QString& title, const QString& message, QWidget* parent)
    : QWidget(parent), m_title(title), m_message(message), m_dotOpacity(1.0)
{
    m_parentWindow = parent;
    setWindowFlags(Qt::FramelessWindowHint | Qt::Tool | Qt::WindowStaysOnTopHint);
    setAttribute(Qt::WA_TranslucentBackground);
    setAttribute(Qt::WA_ShowWithoutActivating);
    setAttribute(Qt::WA_DeleteOnClose);
    resize(320, 84);

    // 修复崩溃问题：先判断 parent 是否为空，再计算位置
    if (m_parentWindow) {
        QPoint topRight = m_parentWindow->mapToGlobal(QPoint(m_parentWindow->width(), m_parentWindow->height()));
        move(topRight.x() - width(), topRight.y() - height() - 20);
    } else {
        QRect screen = QApplication::primaryScreen()->availableGeometry();
        move(screen.right() - width() - 20, screen.bottom() - height() - 60);
    }

    // 呼吸红点动画
    auto* dotAnim = new QPropertyAnimation(this, "dotOpacity", this);
    dotAnim->setDuration(800);
    dotAnim->setStartValue(1.0);
    dotAnim->setEndValue(0.2);
    dotAnim->setEasingCurve(QEasingCurve::InOutSine);
    dotAnim->setLoopCount(-1);
    dotAnim->start();

    // 倒计时退出逻辑
    m_lifeTimer = new QTimer(this);
    m_lifeTimer->setSingleShot(true);
    connect(m_lifeTimer, &QTimer::timeout, this, [this]() {
        // 真正的退场动画：向右滑出屏幕 + 透明度降为0
        QPoint endPos = pos() + QPoint(350, 0);

        auto* posAnim = new QPropertyAnimation(this, "pos");
        posAnim->setDuration(400);
        posAnim->setStartValue(pos());
        posAnim->setEndValue(endPos);
        posAnim->setEasingCurve(QEasingCurve::InCubic);

        auto* opacityAnim = new QPropertyAnimation(this, "windowOpacity");
        opacityAnim->setDuration(400);
        opacityAnim->setStartValue(1.0);
        opacityAnim->setEndValue(0.0);
        opacityAnim->setEasingCurve(QEasingCurve::InCubic);

        connect(opacityAnim, &QPropertyAnimation::finished, this, &QWidget::close);

        posAnim->start(QAbstractAnimation::DeleteWhenStopped);
        opacityAnim->start(QAbstractAnimation::DeleteWhenStopped);
    });

    // 驱动底部倒计时进度条
    auto* ticker = new QTimer(this);
    ticker->setInterval(16);
    connect(ticker, &QTimer::timeout, this, [this]() { update(); });
    ticker->start();
}

void ToastNotification::showToast() {
    // 设置初始透明度为0
    setWindowOpacity(0.0);
    show();

    // 真正的入场动画：向右上角滑入 + 渐显
    QPoint startPos = pos() - QPoint(350, 0); // 从屏幕右侧外滑入
    QPoint targetPos = pos();

    setGeometry(startPos.x(), targetPos.y(), width(), height());

    auto* posAnim = new QPropertyAnimation(this, "pos");
    posAnim->setDuration(500);
    posAnim->setStartValue(startPos);
    posAnim->setEndValue(targetPos);
    posAnim->setEasingCurve(QEasingCurve::OutBack); // 果冻回弹感

    auto* opacityAnim = new QPropertyAnimation(this, "windowOpacity");
    opacityAnim->setDuration(300);
    opacityAnim->setStartValue(0.0);
    opacityAnim->setEndValue(1.0);
    opacityAnim->setEasingCurve(QEasingCurve::OutCubic);

    posAnim->start(QAbstractAnimation::DeleteWhenStopped);
    opacityAnim->start(QAbstractAnimation::DeleteWhenStopped);

    m_lifeTimer->start(4000); // 4秒倒计时
}

void ToastNotification::paintEvent(QPaintEvent*) {
    QPainter p(this);
    p.setRenderHint(QPainter::Antialiasing);

    QRectF rect = this->rect().adjusted(0.5, 0.5, -0.5, -0.5);
    QPainterPath path;
    path.addRoundedRect(rect, 12, 12);

    // 背景
    QLinearGradient bg(rect.topLeft(), rect.bottomRight());
    bg.setColorAt(0.0, QColor(20, 24, 30, 245));
    bg.setColorAt(1.0, QColor(10, 12, 16, 250));
    p.fillPath(path, bg);

    // 左侧发光条
    QLinearGradient accent(0, 0, 0, height());
    accent.setColorAt(0, QColor("#64d2ff"));
    accent.setColorAt(1, QColor("#3ddc84"));
    p.fillRect(QRectF(0, 0, 4, height()), accent);

    // 边框
    p.setPen(QPen(QColor(100, 200, 255, 60), 1));
    p.drawPath(path);

    // 文字
    p.setPen(QColor("#ffffff"));
    p.setFont(QFont("Segoe UI", 10, QFont::Bold));
    p.drawText(QRect(16, 12, width() - 20, 20), Qt::AlignLeft | Qt::AlignVCenter, m_title);

    p.setPen(QColor("#a0c4ff"));
    p.setFont(QFont("Segoe UI", 9));
    p.drawText(QRect(16, 36, width() - 20, 40), Qt::AlignLeft | Qt::TextWordWrap, m_message);

    // 底部倒计时进度条
    int remaining = m_lifeTimer->remainingTime();
    if (remaining > 0) {
        qreal progress = (qreal)remaining / 4000.0;
        QRectF barRect = rect.adjusted(16, rect.height() - 6, -16, -3);
        p.setPen(Qt::NoPen);
        p.setBrush(QColor(255, 255, 255, 20));
        p.drawRoundedRect(barRect, 1.5, 1.5);

        QRectF fillRect = barRect;
        fillRect.setWidth(barRect.width() * progress);

        QLinearGradient barGrad(fillRect.topLeft(), fillRect.topRight());
        barGrad.setColorAt(0, QColor("#3ddc84"));
        barGrad.setColorAt(1, QColor("#64d2ff"));
        p.setBrush(barGrad);
        p.drawRoundedRect(fillRect, 1.5, 1.5);
    }

    // 右上角呼吸红点（透明度会随着定时器抖动）
    p.setPen(Qt::NoPen);
    p.setBrush(QColor(255, 75, 75, (int)(255 * m_dotOpacity)));
    p.drawEllipse(QPointF(width() - 15, 16), 3.0, 3.0);

    // 给红点加个光晕
    QRadialGradient dotGlow(QPointF(width() - 15, 16), 8);
    dotGlow.setColorAt(0, QColor(255, 75, 75, (int)(120 * m_dotOpacity)));
    dotGlow.setColorAt(1, QColor(255, 75, 75, 0));
    p.setBrush(dotGlow);
    p.drawEllipse(QPointF(width() - 15, 16), 8, 8);
}