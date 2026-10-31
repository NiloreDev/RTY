// =====================================================================
//  Sidebar.cpp
// =====================================================================
#include "Sidebar.h"

#include <QButtonGroup>
#include <QEasingCurve>
#include <QFontMetricsF>
#include <QGraphicsDropShadowEffect>
#include <QLabel>
#include <QLinearGradient>
#include <QPainter>
#include <QPainterPath>
#include <QPushButton>
#include <QVariantAnimation>
#include <QVBoxLayout>

namespace loader {

namespace {
constexpr int kSidebarWidth = 200;

// -------------------------------------------------------------
// ShimmerBrand —— 粉→紫渐变文字 + 循环扫光
// -------------------------------------------------------------
class ShimmerBrand final : public QWidget {
public:
    explicit ShimmerBrand(QWidget* parent = nullptr) : QWidget(parent) {
        setAttribute(Qt::WA_TranslucentBackground);
        setAttribute(Qt::WA_TransparentForMouseEvents);

        QFont f = font();
        f.setPointSizeF(11.0);
        f.setWeight(QFont::Bold);
        f.setLetterSpacing(QFont::AbsoluteSpacing, 2.0);
        setFont(f);

        QFontMetricsF fm(f);
        setFixedHeight(int(fm.height() + 22));
        setMinimumWidth(140);

        auto* a = new QVariantAnimation(this);
        a->setStartValue(0.0);
        a->setEndValue(1.0);
        a->setDuration(3800);
        a->setLoopCount(-1);
        a->setEasingCurve(QEasingCurve::Linear);
        connect(a, &QVariantAnimation::valueChanged, this,
                [this](const QVariant& v) { t_ = v.toReal(); update(); });
        a->start();
    }

protected:
    void paintEvent(QPaintEvent*) override {
        QPainter p(this);
        p.setRenderHint(QPainter::Antialiasing);
        p.setRenderHint(QPainter::TextAntialiasing);

        const QString text = QStringLiteral("Nebula");
        QFontMetricsF fm(font());

        QPainterPath path;
        path.addText(8.0, 6.0 + fm.ascent(), font(), text);

        // 主体：粉 → 紫 渐变
        QLinearGradient base(0.0, 0.0, width(), 0.0);
        base.setColorAt(0.0, QColor("#ffb3dc"));
        base.setColorAt(1.0, QColor("#c59dff"));
        p.fillPath(path, base);

        // 循环扫光只画在文字内部
        p.save();
        p.setClipPath(path);
        const qreal w = width();
        const qreal x = -80.0 + (w + 160.0) * t_;
        QLinearGradient g(x - 55.0, 0.0, x + 55.0, 0.0);
        g.setColorAt(0.0, QColor(255, 255, 255, 0));
        g.setColorAt(0.5, QColor(255, 255, 255, 220));
        g.setColorAt(1.0, QColor(255, 255, 255, 0));
        p.fillRect(rect(), g);
        p.restore();
    }

private:
    qreal t_ = 0.0;
};

// -------------------------------------------------------------
// 给选中的导航按钮装一颗脉冲光晕
// -------------------------------------------------------------
void installPulseGlow(QPushButton* btn) {
    if (!btn) return;

    auto* glow = new QGraphicsDropShadowEffect(btn);
    glow->setOffset(0.0, 0.0);
    glow->setBlurRadius(0.0);
    glow->setColor(QColor(255, 140, 210, 0));
    btn->setGraphicsEffect(glow);

    auto* pulse = new QVariantAnimation(btn);
    pulse->setStartValue(0.0);
    pulse->setEndValue(1.0);
    pulse->setDuration(2000);
    pulse->setLoopCount(-1);
    pulse->setEasingCurve(QEasingCurve::InOutSine);
    QObject::connect(pulse, &QVariantAnimation::valueChanged, btn,
                     [glow, btn](const QVariant& v) {
                         if (!btn->isChecked()) {
                             glow->setBlurRadius(0.0);
                             glow->setColor(QColor(255, 140, 210, 0));
                             return;
                         }
                         const qreal k = v.toReal();
                         glow->setBlurRadius(10.0 + 8.0 * k);
                         glow->setColor(QColor(255, 140, 210,
                                               int(90 + 70 * k)));
                     });
    pulse->start();
}

// -------------------------------------------------------------
// 侧栏样式 —— 背景彻底透明，只保留右侧淡紫分隔线
// -------------------------------------------------------------
const char* kSidebarQss = R"qss(
    QWidget#sidebar {
        background: transparent;
        border-right: 1px solid rgba(178, 148, 255, 22);
    }

    QPushButton#navBtn {
        background: transparent;
        border: none;
        border-left: 2px solid transparent;
        text-align: left;
        padding: 10px 16px 10px 14px;
        border-radius: 8px;
        color: #b6aed0;
        font-size: 13px;
        font-weight: 650;
        font-family: "Segoe UI", "Microsoft YaHei UI", sans-serif;
    }
    QPushButton#navBtn:hover {
        background: rgba(180, 140, 255, 0.14);
        color: #e8dcff;
    }
    QPushButton#navBtn:checked {
        background: qlineargradient(x1:0, y1:0, x2:1, y2:0,
            stop:0 rgba(255, 126, 204, 0.28),
            stop:1 rgba(140, 110, 255, 0.10));
        border-left: 2px solid #ff8eca;
        color: #fff5ff;
        font-weight: 750;
    }
)qss";

} // namespace

// =====================================================================
Sidebar::Sidebar(QWidget* parent)
        : QWidget(parent) {
    setObjectName("sidebar");
    setAttribute(Qt::WA_StyledBackground, true);
    setStyleSheet(QString::fromUtf8(kSidebarQss));
    setFixedWidth(kSidebarWidth);
    buildUi();
}

void Sidebar::buildUi() {
    auto* col = new QVBoxLayout(this);
    col->setContentsMargins(10, 12, 10, 12);
    col->setSpacing(4);

    auto* brand = new ShimmerBrand(this);
    col->addWidget(brand);

    auto* inject = new QPushButton(QStringLiteral("Inject"), this);
    inject->setObjectName("navBtn");
    inject->setCheckable(true);
    inject->setChecked(true);

    auto* settings = new QPushButton(QStringLiteral("Settings"), this);
    settings->setObjectName("navBtn");
    settings->setCheckable(true);

    for (auto* b : {inject, settings}) {
        b->setCursor(Qt::PointingHandCursor);
        installPulseGlow(b);
    }

    col->addWidget(inject);
    col->addWidget(settings);
    col->addStretch(1);

    group_ = new QButtonGroup(this);
    group_->setExclusive(true);
    group_->addButton(inject, 0);
    group_->addButton(settings, 1);

    connect(inject,   &QPushButton::clicked, this, &Sidebar::injectClicked);
    connect(settings, &QPushButton::clicked, this, &Sidebar::settingsClicked);
}

} // namespace loader