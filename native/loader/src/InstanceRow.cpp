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
#include <QVariantAnimation>

#include <cmath>

namespace loader {

namespace {
constexpr int kRowHeight    = 56;
constexpr int kCornerRadius = 9;
constexpr qreal kTau        = 6.2831853071795864769;

const char* kInjectBtnQss = R"qss(
    QPushButton {
        background: qlineargradient(x1:0, y1:0, x2:0, y2:1,
                                    stop:0 #4a83e0, stop:1 #3260bd);
        color: white;
        border: 1px solid rgba(255, 255, 255, 22);
        border-radius: 6px;
        padding: 6px 18px;
        font-weight: 600;
        font-size: 12px;
        font-family: "Segoe UI", "Microsoft YaHei UI", sans-serif;
    }
    QPushButton:hover {
        background: qlineargradient(x1:0, y1:0, x2:0, y2:1,
                                    stop:0 #5a93f0, stop:1 #4275d8);
        border: 1px solid rgba(255, 255, 255, 60);
    }
    QPushButton:pressed {
        background: qlineargradient(x1:0, y1:0, x2:0, y2:1,
                                    stop:0 #2c5db0, stop:1 #1e468f);
    }
    QPushButton:disabled {
        background: #2c2f37;
        color: #6a6f7a;
        border: 1px solid #2c2f37;
    }
)qss";
} // namespace

InstanceRow::InstanceRow(unsigned long pid, const QString& title,
                         const QString& commandLine, QWidget* parent)
        : QFrame(parent), pid_(pid), title_(title), commandLine_(commandLine) {
    setFixedHeight(kRowHeight);
    setAttribute(Qt::WA_StyledBackground, false);
    setMouseTracking(true);

    auto* layout = new QHBoxLayout(this);
    layout->setContentsMargins(14, 6, 12, 6);
    layout->setSpacing(12);

    pidLabel_ = new QLabel(QString::number(pid_), this);
    pidLabel_->setMinimumWidth(72);
    pidLabel_->setAlignment(Qt::AlignVCenter | Qt::AlignLeft);
    pidLabel_->setStyleSheet(QStringLiteral(
        "color: #9aa3b2;"
        "font-family: 'JetBrains Mono', 'Consolas', 'Segoe UI', monospace;"
        "font-size: 12px; font-weight: 600;"));
    layout->addWidget(pidLabel_);

    titleLabel_ = new QLabel(title_, this);
    titleLabel_->setAlignment(Qt::AlignVCenter | Qt::AlignLeft);
    titleLabel_->setStyleSheet(QStringLiteral(
        "color: #e7eaf2; font-size: 13px;"
        "font-family: 'Segoe UI', 'Microsoft YaHei UI', sans-serif;"));
    titleLabel_->setTextInteractionFlags(Qt::NoTextInteraction);
    layout->addWidget(titleLabel_, 1);

    injectBtn_ = new QPushButton(QStringLiteral("Inject"), this);
    injectBtn_->setCursor(Qt::PointingHandCursor);
    injectBtn_->setStyleSheet(QString::fromUtf8(kInjectBtnQss));
    injectBtn_->setFixedHeight(30);
    connect(injectBtn_, &QPushButton::clicked, this, [this] {
        emit injectClicked(pid_, title_, commandLine_);
    });
    layout->addWidget(injectBtn_);

    // ================================================================
    // 流动背景相位动画：0 → 1 无限循环，驱动 paintEvent 里渐变位置
    // ================================================================
    phaseAnim_ = new QVariantAnimation(this);
    phaseAnim_->setStartValue(0.0);
    phaseAnim_->setEndValue(1.0);
    phaseAnim_->setDuration(6500);            // 6.5 秒一个周期，缓慢流动
    phaseAnim_->setLoopCount(-1);
    phaseAnim_->setEasingCurve(QEasingCurve::Linear);
    connect(phaseAnim_, &QVariantAnimation::valueChanged, this, [this](const QVariant& v) {
        phase_ = v.toReal();
        update();
    });
    phaseAnim_->start();
}

void InstanceRow::updateTitle(const QString& title) {
    if (title == title_) return;
    title_ = title;
    titleLabel_->setText(title_);
}

void InstanceRow::setEntrance(qreal v) {
    entrance_ = v;
    update();
}

void InstanceRow::playEntrance() {
    entrance_ = 0.0;
    update();

    auto* anim = new QPropertyAnimation(this, "entrance", this);
    anim->setDuration(320);
    anim->setStartValue(0.0);
    anim->setEndValue(1.0);
    anim->setEasingCurve(QEasingCurve::OutCubic);
    anim->start(QAbstractAnimation::DeleteWhenStopped);
}

void InstanceRow::animateHover(qreal target) {
    if (hoverAnim_) {
        hoverAnim_->stop();
        hoverAnim_->deleteLater();
    }
    hoverAnim_ = new QPropertyAnimation(this, "hoverIntensity", this);
    hoverAnim_->setDuration(160);
    hoverAnim_->setStartValue(hoverIntensity_);
    hoverAnim_->setEndValue(target);
    hoverAnim_->setEasingCurve(QEasingCurve::OutCubic);
    hoverAnim_->start();
}

// ================================================================
// 注入成功动画：
//   injectGlow_ 0 → 1 → 1.6 → 0，前半段用 OutCubic 爆发，
//   中段保持，末段缓出。paintEvent 里用它画绿色脉冲 + 扩散圆环。
// ================================================================
void InstanceRow::playInjectAnimation() {
    if (injectAnim_) {
        injectAnim_->stop();
        injectAnim_->deleteLater();
    }
    injectAnim_ = new QPropertyAnimation(this, "injectGlow", this);
    injectAnim_->setDuration(1800);
    injectAnim_->setStartValue(0.0);
    injectAnim_->setKeyValueAt(0.15, 1.0);
    injectAnim_->setKeyValueAt(0.55, 0.85);
    injectAnim_->setEndValue(0.0);
    injectAnim_->setEasingCurve(QEasingCurve::OutCubic);
    injectAnim_->start();
    update();
}

void InstanceRow::flashHighlight() {
    // 保留旧 API，行为改为注入动画
    playInjectAnimation();
}

void InstanceRow::enterEvent(QEnterEvent*) {
    animateHover(1.0);
}

void InstanceRow::leaveEvent(QEvent*) {
    animateHover(0.0);
}

void InstanceRow::paintEvent(QPaintEvent*) {
    QPainter p(this);
    p.setRenderHint(QPainter::Antialiasing);

    // 入场：轻微向上滑入 + 透明度
    qreal slideDy     = (1.0 - entrance_) * 8.0;
    qreal panelAlpha  = entrance_;

    QRectF rect = QRectF(this->rect()).adjusted(4, 4 + slideDy, -4, -4 + slideDy);
    QPainterPath panel;
    panel.addRoundedRect(rect, kCornerRadius, kCornerRadius);

    // ================================================================
    // 【动态背景】流动渐变
    //   phase_ 决定渐变中心位置；phase 从 0 → 1 循环，渐变缓慢左右移动，
    //   形成"光带在行内流动"的效果。
    // ================================================================
    const qreal flow = 0.5 + 0.5 * std::sin(phase_ * kTau);   // 0..1，平滑
    const qreal c1x  = 0.1 + 0.5 * flow;
    const qreal c2x  = 0.9 - 0.5 * flow;

    // 底色：深灰蓝
    QLinearGradient base(rect.topLeft(), rect.bottomLeft());
    base.setColorAt(0.0, QColor(38, 41, 48, int(255 * panelAlpha)));
    base.setColorAt(1.0, QColor(30, 32, 38, int(255 * panelAlpha)));
    p.fillPath(panel, base);

    // 流动光带：淡蓝紫色，随 phase 移动
    {
        QLinearGradient wave(rect.left(), rect.top(),
                             rect.right(), rect.bottom());
        wave.setColorAt(std::max(0.0, c1x - 0.35),
                        QColor(60, 90, 180, 0));
        wave.setColorAt(c1x,
                        QColor(74, 131, 224, int(28 * panelAlpha)));
        wave.setColorAt(c2x,
                        QColor(120, 90, 200, int(20 * panelAlpha)));
        wave.setColorAt(std::min(1.0, c2x + 0.35),
                        QColor(60, 90, 180, 0));
        p.fillPath(panel, wave);
    }

    // 悬停高亮（原有）
    if (hoverIntensity_ > 0.001) {
        QLinearGradient hover(rect.topLeft(), rect.bottomLeft());
        hover.setColorAt(0.0,
            QColor(74, 131, 224, int(45 * hoverIntensity_ * panelAlpha)));
        hover.setColorAt(1.0,
            QColor(50, 96, 189, int(28 * hoverIntensity_ * panelAlpha)));
        p.fillPath(panel, hover);
    }

    // ================================================================
    // 【注入动画】绿色脉冲 + 扩散圆环
    // ================================================================
    if (injectGlow_ > 0.001) {
        const qreal g = injectGlow_;

        // 1) 底色发光：淡绿色覆盖
        QLinearGradient glow(rect.topLeft(), rect.bottomLeft());
        glow.setColorAt(0.0,
            QColor(62, 207, 107, int(80 * g * panelAlpha)));
        glow.setColorAt(1.0,
            QColor(40, 160, 90, int(50 * g * panelAlpha)));
        p.fillPath(panel, glow);

        // 2) 外边框光环：绿色描边
        QPen ring(QColor(80, 235, 130, int(220 * g)), 2.0);
        ring.setJoinStyle(Qt::RoundJoin);
        p.setPen(ring);
        p.setBrush(Qt::NoBrush);
        QRectF outer = rect.adjusted(-1.5, -1.5, 1.5, 1.5);
        p.drawRoundedRect(outer, kCornerRadius + 2, kCornerRadius + 2);

        // 3) 中心扩散圆环：随 g 从 0 到 1 往外扩散 + 变淡
        //    用 g 的反向进度（1-g）画一个从小到大的圈
        const qreal expand = 1.0 - g;
        const qreal radius = rect.height() * 0.4 + expand * rect.width() * 0.6;
        const int ringAlpha = int(160 * g);
        if (ringAlpha > 4) {
            QPen spread(QColor(120, 255, 160, ringAlpha), 1.8);
            p.setPen(spread);
            p.setBrush(Qt::NoBrush);
            QPointF c = rect.center();
            p.drawEllipse(c, radius, radius);
        }

        // 4) 中心光点：注入瞬间（g 接近 1 时）一个亮绿光斑
        if (g > 0.7) {
            const qreal headGlow = (g - 0.7) / 0.3;   // 0..1
            QRadialGradient dot(rect.center(), rect.height() * 0.9);
            dot.setColorAt(0.0,
                QColor(140, 255, 180, int(180 * headGlow)));
            dot.setColorAt(1.0,
                QColor(140, 255, 180, 0));
            p.fillPath(panel, dot);
        }
    }

    // 旧版黄色光环保留（现在实际不再触发，兼容遗留调用）
    if (highlight_ > 0.001) {
        QRectF hr = rect;
        hr.adjust(-1.0, -1.0, 1.0, 1.0);
        QColor hl(248, 194, 52, int(255 * highlight_));
        QPen hp(hl, 2.0);
        hp.setCapStyle(Qt::RoundCap);
        hp.setJoinStyle(Qt::RoundJoin);
        p.setPen(hp);
        p.setBrush(Qt::NoBrush);
        p.drawRoundedRect(hr, kCornerRadius + 1, kCornerRadius + 1);
    }

    // 常规边框：随 hover 变亮
    QPen border(QColor(255, 255, 255,
        int((22 + 36 * hoverIntensity_) * panelAlpha)), 1);
    p.setPen(border);
    p.setBrush(Qt::NoBrush);
    p.drawPath(panel);
}

} // namespace loader