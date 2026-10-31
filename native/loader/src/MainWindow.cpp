#include "MainWindow.h"

#include "InjectionOverlay.h"
#include "InstanceList.h"
#include "Sidebar.h"
#include "SettingsPage.h"
#include "TitleBar.h"
#include "ToastNotification.h"
#include "loader.h"
#include "loader_settings.h"

#include <QApplication>
#include <QCloseEvent>
#include <QCursor>
#include <QDebug>
#include <QEasingCurve>
#include <QEnterEvent>
#include <QFrame>
#include <QGraphicsDropShadowEffect>
#include <QHBoxLayout>
#include <QLabel>
#include <QLinearGradient>
#include <QMouseEvent>
#include <QPainter>
#include <QPainterPath>
#include <QParallelAnimationGroup>
#include <QPixmap>
#include <QPushButton>
#include <QPropertyAnimation>
#include <QGraphicsOpacityEffect>
#include <QRadialGradient>
#include <QConicalGradient>
#include <QRandomGenerator>
#include <QString>
#include <QTimer>
#include <QVariantAnimation>
#include <QVBoxLayout>
#include <QVector>
#include <QWidget>

#ifdef Q_OS_WIN
#  include <windows.h>
#  include <dwmapi.h>
#endif

#include <cmath>
#include <string>

namespace loader {

namespace {
constexpr int kCornerRadius = 28;
constexpr int kBaseWidth    = 1280;
constexpr int kBaseHeight   = 700;
constexpr qreal kTau        = 6.2831853071795864769;

bool startsWithMinecraft(const std::wstring& title) {
    static const std::wstring prefix = L"Minecraft";
    if (title.size() < prefix.size()) return false;
    return title.compare(0, prefix.size(), prefix) == 0;
}

bool isMinecraft(const std::wstring& title, const std::wstring& cls) {
    if (startsWithMinecraft(title)) return true;
#ifdef Q_OS_WIN
    return _wcsicmp(cls.c_str(), L"GLFW30") == 0;
#else
    return cls == L"GLFW30";
#endif
}

QFrame* makeDivider(QWidget* parent) {
    auto* line = new QFrame(parent);
    line->setObjectName("divider");
    line->setFrameShape(QFrame::HLine);
    line->setFixedHeight(1);
    return line;
}

// 稳定伪随机 0..1
inline qreal hash01(int i, int salt) {
    uint32_t x = uint32_t(i) * 2654435761u + uint32_t(salt) * 40503u;
    x ^= x >> 16; x *= 0x7feb352du; x ^= x >> 15;
    x *= 0x846ca68bu; x ^= x >> 16;
    return (x & 0xFFFFFFu) / qreal(0xFFFFFFu);
}

// ==========================================================
// GlowOrb —— Hero 卡右侧的旋转发光球
// ==========================================================
class GlowOrb final : public QWidget {
public:
    explicit GlowOrb(QWidget* parent = nullptr) : QWidget(parent) {
        setFixedSize(78, 78);
        setAttribute(Qt::WA_TranslucentBackground);

        auto* pulse = new QVariantAnimation(this);
        pulse->setStartValue(0.0);
        pulse->setEndValue(1.0);
        pulse->setDuration(2400);
        pulse->setLoopCount(-1);
        pulse->setEasingCurve(QEasingCurve::InOutSine);
        connect(pulse, &QVariantAnimation::valueChanged, this,
                [this](const QVariant& v) { pulseV_ = v.toReal(); update(); });
        pulse->start();

        auto* rot = new QVariantAnimation(this);
        rot->setStartValue(0.0);
        rot->setEndValue(1.0);
        rot->setDuration(8000);
        rot->setLoopCount(-1);
        rot->setEasingCurve(QEasingCurve::Linear);
        connect(rot, &QVariantAnimation::valueChanged, this,
                [this](const QVariant& v) { rotV_ = v.toReal(); update(); });
        rot->start();
    }

protected:
    void paintEvent(QPaintEvent*) override {
        QPainter p(this);
        p.setRenderHint(QPainter::Antialiasing);

        const QPointF c = QRectF(rect()).center();
        const qreal R = 26.0;

        // 外层虚线旋转环
        {
            const qreal r1 = R + 8.0;
            QPen pen(QColor(200, 160, 255, 130), 1.2);
            pen.setDashPattern({3, 6});
            p.setPen(pen);
            p.setBrush(Qt::NoBrush);
            p.save();
            p.translate(c);
            p.rotate(rotV_ * 360.0);
            p.drawEllipse(QPointF(0, 0), r1, r1);
            p.restore();
        }

        // 中层双向弧
        {
            QPen arc(QColor(255, 130, 200, 210), 2.0);
            arc.setCapStyle(Qt::RoundCap);
            p.setPen(arc);
            const qreal r2 = R + 3.0;
            QRectF rr(c.x() - r2, c.y() - r2, r2 * 2, r2 * 2);
            const int start = int(-rotV_ * 360.0 * 16.0);
            p.drawArc(rr, start, 90 * 16);
            p.drawArc(rr, start + 180 * 16, 50 * 16);
        }

        // 内层虚线反向环
        {
            const qreal r3 = R - 4.0;
            QPen pen(QColor(140, 220, 255, 170), 1.0);
            pen.setDashPattern({2, 4});
            p.setPen(pen);
            p.setBrush(Qt::NoBrush);
            p.save();
            p.translate(c);
            p.rotate(-rotV_ * 360.0 * 1.5);
            p.drawEllipse(QPointF(0, 0), r3, r3);
            p.restore();
        }

        // 光晕
        {
            const qreal rHalo = R * (1.05 + 0.25 * pulseV_);
            QRadialGradient g(c, rHalo);
            const int a = int(120 + 80 * pulseV_);
            g.setColorAt(0.0, QColor(255, 130, 220, a));
            g.setColorAt(0.5, QColor(150, 100, 255, a / 3));
            g.setColorAt(1.0, QColor(150, 100, 255, 0));
            p.setPen(Qt::NoPen);
            p.setBrush(g);
            p.drawEllipse(c, rHalo, rHalo);
        }

        // 核心球
        {
            const qreal coreR = R * (0.55 + 0.06 * pulseV_);
            QRadialGradient core(c - QPointF(coreR * 0.3, coreR * 0.3), coreR * 1.6);
            core.setColorAt(0.0, QColor(255, 255, 255, 255));
            core.setColorAt(0.3, QColor(255, 200, 240, 250));
            core.setColorAt(0.6, QColor(200, 130, 255, 225));
            core.setColorAt(1.0, QColor(100, 60, 180, 0));
            p.setBrush(core);
            p.drawEllipse(c, coreR * 1.4, coreR * 1.4);

            p.setBrush(QColor(255, 255, 255, int(220 + 35 * pulseV_)));
            p.drawEllipse(c - QPointF(coreR * 0.3, coreR * 0.3),
                          coreR * 0.3, coreR * 0.3);
        }

        // 环绕粒子
        {
            const int N = 6;
            for (int i = 0; i < N; ++i) {
                const qreal a = rotV_ * kTau + i / qreal(N) * kTau;
                const qreal rr = R + 6.0
                                 + 3.0 * std::sin(rotV_ * kTau * 4.0 + i);
                const QPointF pt = c + QPointF(std::cos(a), std::sin(a)) * rr;
                p.setPen(Qt::NoPen);
                p.setBrush(QColor(255, 180, 230, 220));
                p.drawEllipse(pt, 2.0, 2.0);
            }
        }
    }

private:
    qreal pulseV_ = 0.0;
    qreal rotV_   = 0.0;
};

// ==========================================================
// ScanCard —— 带扫光 + 边框旋转光点 + 角标的 QFrame
// ==========================================================
class ScanCard final : public QFrame {
public:
    explicit ScanCard(QWidget* parent = nullptr) : QFrame(parent) {
        setAttribute(Qt::WA_StyledBackground, false);

        auto* scan = new QVariantAnimation(this);
        scan->setStartValue(-0.3);
        scan->setEndValue(1.3);
        scan->setDuration(4500);
        scan->setLoopCount(-1);
        scan->setEasingCurve(QEasingCurve::InOutSine);
        connect(scan, &QVariantAnimation::valueChanged, this,
                [this](const QVariant& v) { scanV_ = v.toReal(); update(); });
        scan->start();

        auto* rot = new QVariantAnimation(this);
        rot->setStartValue(0.0);
        rot->setEndValue(1.0);
        rot->setDuration(6000);
        rot->setLoopCount(-1);
        rot->setEasingCurve(QEasingCurve::Linear);
        connect(rot, &QVariantAnimation::valueChanged, this,
                [this](const QVariant& v) { rotV_ = v.toReal(); update(); });
        rot->start();
    }

protected:
    void paintEvent(QPaintEvent* e) override {
        QFrame::paintEvent(e);   // 让 QSS 背景 + 边框先画

        QPainter p(this);
        p.setRenderHint(QPainter::Antialiasing);

        const QRectF r = QRectF(rect()).adjusted(0.5, 0.5, -0.5, -0.5);
        QPainterPath panel;
        panel.addRoundedRect(r, 22, 22);

        // 扫光 (裁剪到卡片内)
        p.save();
        p.setClipPath(panel);
        {
            const qreal sw = r.width() * 0.30;
            const qreal cx = r.left() - sw
                             + (r.width() + sw * 2.0) * scanV_;
            QLinearGradient sg(QPointF(cx - sw * 0.5, r.top()),
                               QPointF(cx + sw * 0.5, r.bottom()));
            sg.setColorAt(0.0, QColor(255, 255, 255, 0));
            sg.setColorAt(0.5, QColor(220, 200, 255, 48));
            sg.setColorAt(1.0, QColor(255, 255, 255, 0));
            p.setPen(Qt::NoPen);
            p.fillRect(r, sg);
        }
        p.restore();

        // 边框旋转光点 (4 个, 沿周长移动)
        {
            const qreal peri = 2.0 * (r.width() + r.height());
            for (int k = 0; k < 4; ++k) {
                const qreal u = std::fmod(rotV_ + k / 4.0, 1.0);
                const qreal d = u * peri;
                QPointF pt;
                if (d < r.width()) {
                    pt = QPointF(r.left() + d, r.top());
                } else if (d < r.width() + r.height()) {
                    pt = QPointF(r.right(), r.top() + (d - r.width()));
                } else if (d < 2 * r.width() + r.height()) {
                    pt = QPointF(r.right() - (d - r.width() - r.height()),
                                 r.bottom());
                } else {
                    pt = QPointF(r.left(),
                                 r.bottom() - (d - 2 * r.width() - r.height()));
                }

                QRadialGradient g(pt, 9.0);
                g.setColorAt(0.0, QColor(255, 220, 255, 220));
                g.setColorAt(0.45, QColor(200, 130, 255, 90));
                g.setColorAt(1.0, QColor(200, 130, 255, 0));
                p.setPen(Qt::NoPen);
                p.setBrush(g);
                p.drawEllipse(pt, 9.0, 9.0);
            }
        }

        // 角标 L 形 (左上 + 右下)
        {
            QPen pen(QColor(180, 210, 255, 120), 1.4);
            pen.setCapStyle(Qt::RoundCap);
            p.setPen(pen);
            p.setBrush(Qt::NoBrush);
            const qreal L = 14.0;
            const qreal i = 4.0;

            p.drawLine(QPointF(r.left() + i, r.top() + i + L),
                       QPointF(r.left() + i, r.top() + i));
            p.drawLine(QPointF(r.left() + i, r.top() + i),
                       QPointF(r.left() + i + L, r.top() + i));

            p.drawLine(QPointF(r.right() - i, r.bottom() - i - L),
                       QPointF(r.right() - i, r.bottom() - i));
            p.drawLine(QPointF(r.right() - i, r.bottom() - i),
                       QPointF(r.right() - i - L, r.bottom() - i));
        }
    }

private:
    qreal scanV_ = -0.3;
    qreal rotV_  = 0.0;
};

// ==========================================================
// StatusPanel —— 与主卡片同色系 + 多层动态效果 (增强版)
// ==========================================================
class StatusPanel final : public QWidget {
public:
    explicit StatusPanel(QWidget* parent = nullptr) : QWidget(parent) {
        setMinimumSize(260, 360);
        setSizePolicy(QSizePolicy::Preferred, QSizePolicy::Expanding);
        setAttribute(Qt::WA_TranslucentBackground);

        auto makeRot = [this](int duration, qreal* target) {
            auto* a = new QVariantAnimation(this);
            a->setStartValue(0.0);
            a->setEndValue(1.0);
            a->setDuration(duration);
            a->setLoopCount(-1);
            a->setEasingCurve(QEasingCurve::Linear);
            connect(a, &QVariantAnimation::valueChanged, this,
                    [this, target](const QVariant& v) {
                        *target = v.toReal();
                        update();
                    });
            a->start();
        };
        makeRot(14000, &rot1_);
        makeRot( 9000, &rot2_);
        makeRot(20000, &rot3_);

        auto* pulse = new QVariantAnimation(this);
        pulse->setStartValue(0.0);
        pulse->setEndValue(1.0);
        pulse->setDuration(2400);
        pulse->setLoopCount(-1);
        pulse->setEasingCurve(QEasingCurve::InOutSine);
        connect(pulse, &QVariantAnimation::valueChanged, this,
                [this](const QVariant& v) {
                    pulse_ = v.toReal();
                    update();
                });
        pulse->start();

        QRandomGenerator* rng = QRandomGenerator::global();
        for (int i = 0; i < 18; ++i) {
            Particle pt;
            pt.a   = (rng->bounded(1000) / 1000.0) * kTau;
            pt.r   = 0.85 + (rng->bounded(500) / 1000.0) * 0.85;
            pt.spd = 0.5  + (rng->bounded(700) / 1000.0) * 1.2;
            pt.sz  = 1.0  + (rng->bounded(22)  / 10.0);
            pt.ph  = (rng->bounded(1000) / 1000.0) * kTau;
            pt.c   = rng->bounded(3);
            particles_.append(pt);
        }
    }

protected:
    struct Particle {
        qreal a, r, spd, sz, ph;
        int   c;
    };

    void paintEvent(QPaintEvent*) override {
        QPainter p(this);
        p.setRenderHint(QPainter::Antialiasing);
        p.setRenderHint(QPainter::TextAntialiasing);

        const QRectF outer = QRectF(rect()).adjusted(1, 1, -1, -1);
        QPainterPath card;
        card.addRoundedRect(outer, 21, 21);

        QLinearGradient bg(outer.topLeft(), outer.bottomRight());
        bg.setColorAt(0.0, QColor(55, 48, 95, 245));
        bg.setColorAt(1.0, QColor(35, 60, 85, 245));
        p.fillPath(card, bg);

        p.save();
        p.setClipPath(card);

        // 环境紫光
        {
            QRadialGradient amb(outer.center(), outer.width() * 0.85);
            amb.setColorAt(0.0, QColor(255, 100, 200, int(16 + 18 * pulse_)));
            amb.setColorAt(0.45, QColor(140, 100, 255, 10));
            amb.setColorAt(1.0, QColor(0, 0, 0, 0));
            p.fillRect(outer, amb);
        }

        const QPointF c(outer.center().x(), outer.center().y() - 22);
        const qreal baseR = std::min(outer.width(), outer.height()) * 0.25;

        // 外虚线环
        {
            QPen pen(QColor(178, 148, 255, 70), 1.0);
            pen.setDashPattern({3, 6});
            p.setPen(pen);
            p.setBrush(Qt::NoBrush);
            const qreal r = baseR * 1.95 + 3.0 * std::sin(pulse_ * kTau);
            p.drawEllipse(c, r, r);
        }

        // 固定青环
        {
            QPen pen(QColor(120, 200, 255, 40), 1.0);
            p.setPen(pen);
            p.setBrush(Qt::NoBrush);
            p.drawEllipse(c, baseR * 1.68, baseR * 1.68);
        }

        // 螺旋星臂 (环绕)
        for (int a = 0; a < 2; ++a) {
            const qreal baseAng = a * kTau / 2.0;
            for (int s = 0; s < 30; ++s) {
                const qreal u = s / 30.0;
                const qreal ang = baseAng + u * 2.5 + rot3_ * kTau;
                const qreal rr = baseR * (0.35 + u * 1.65);
                const QPointF pt = c + QPointF(std::cos(ang), std::sin(ang)) * rr;
                const qreal br = (1.0 - u);
                QColor col(180, 140, 255, int(180 * br));
                p.setPen(Qt::NoPen);
                p.setBrush(col);
                const qreal sz = 0.8 + u * 1.2;
                p.drawEllipse(pt, sz, sz);
            }
        }

        // 两层六边形护盾
        for (int layer = 0; layer < 2; ++layer) {
            const qreal hexR = baseR * (1.75 + layer * 0.35)
                               + 4.0 * std::sin(pulse_ * kTau + layer);
            const qreal hexRot = rot3_ * kTau * (layer ? -1 : 1);
            QPainterPath hex;
            for (int i = 0; i <= 6; ++i) {
                const qreal a = i / 6.0 * kTau + hexRot;
                const QPointF pt = c + QPointF(std::cos(a), std::sin(a)) * hexR;
                if (i == 0) hex.moveTo(pt);
                else hex.lineTo(pt);
            }
            QColor col(150, 200, 255, 90 - layer * 30);
            QPen pen(col, 1.2);
            if (layer == 1) pen.setDashPattern({5, 4});
            p.setPen(pen);
            p.setBrush(Qt::NoBrush);
            p.drawPath(hex);
        }

        // 数据流雨 (垂直)
        for (int i = 0; i < 6; ++i) {
            const qreal sx = outer.left() + (i + 0.5) * outer.width() / 6.0;
            const qreal speed = 40.0 + (i % 3) * 15.0;
            const qreal headY = outer.top()
                                + std::fmod(rot3_ * speed * 20.0 + i * 90.0,
                                            outer.height() + 60)
                                - 30.0;
            const qreal trailH = 30.0 + (i % 2) * 20.0;

            QLinearGradient lg(sx, headY - trailH, sx, headY);
            lg.setColorAt(0.0, QColor(140, 225, 255, 0));
            lg.setColorAt(1.0, QColor(140, 225, 255, 55));
            p.setPen(QPen(QBrush(lg), 1.4));
            p.drawLine(QPointF(sx, headY - trailH), QPointF(sx, headY));

            p.setPen(Qt::NoPen);
            p.setBrush(QColor(210, 245, 255, 180));
            p.drawEllipse(QPointF(sx, headY), 1.4, 1.4);
        }

        // 三弧 (原)
        {
            QPen arc(QColor(120, 200, 255, 210), 2.2);
            arc.setCapStyle(Qt::RoundCap);
            p.setPen(arc);
            p.setBrush(Qt::NoBrush);
            const qreal r = baseR * 1.38;
            QRectF rr(c.x() - r, c.y() - r, r * 2, r * 2);
            const int start = int(-rot1_ * 360.0 * 16.0);
            p.drawArc(rr, start,             72 * 16);
            p.drawArc(rr, start + 140 * 16,  45 * 16);
            p.drawArc(rr, start + 250 * 16,  32 * 16);
        }

        {
            QPen arc(QColor(255, 130, 200, 220), 2.0);
            arc.setCapStyle(Qt::RoundCap);
            p.setPen(arc);
            p.setBrush(Qt::NoBrush);
            const qreal r = baseR * 1.16;
            QRectF rr(c.x() - r, c.y() - r, r * 2, r * 2);
            const int start = int(rot2_ * 360.0 * 16.0);
            p.drawArc(rr, start,            88 * 16);
            p.drawArc(rr, start + 180 * 16, 55 * 16);
        }

        // 内虚线环
        {
            QPen pen(QColor(178, 148, 255, 140), 1.2);
            pen.setDashPattern({2, 3});
            p.setPen(pen);
            p.setBrush(Qt::NoBrush);
            p.drawEllipse(c, baseR * 0.96, baseR * 0.96);
        }

        // 光晕
        {
            const qreal r = baseR * (0.78 + 0.22 * pulse_);
            QRadialGradient halo(c, r);
            const int a = int(70 + 80 * pulse_);
            halo.setColorAt(0.0, QColor(255, 130, 220, a));
            halo.setColorAt(0.40, QColor(150, 100, 255, a / 3));
            halo.setColorAt(1.0, QColor(0, 0, 0, 0));
            p.setPen(Qt::NoPen);
            p.setBrush(halo);
            p.drawEllipse(c, r, r);
        }

        // 核心
        {
            const qreal r = baseR * (0.42 + 0.06 * pulse_);
            QRadialGradient core(c - QPointF(r * 0.15, r * 0.15), r * 1.45);
            core.setColorAt(0.00, QColor(255, 255, 255, 255));
            core.setColorAt(0.25, QColor(255, 200, 240, 250));
            core.setColorAt(0.55, QColor(200, 130, 255, 225));
            core.setColorAt(0.85, QColor(140, 90, 230, 180));
            core.setColorAt(1.00, QColor(100, 60, 180, 0));
            p.setPen(Qt::NoPen);
            p.setBrush(core);
            p.drawEllipse(c, r * 1.45, r * 1.45);

            p.setBrush(QColor(255, 255, 255, int(200 + 55 * pulse_)));
            p.drawEllipse(c - QPointF(r * 0.28, r * 0.28), r * 0.30, r * 0.30);
        }

        // 环绕粒子
        {
            p.setPen(Qt::NoPen);
            for (const auto& pt : particles_) {
                const qreal ang = pt.a + rot3_ * kTau * pt.spd;
                const qreal dist = baseR * (pt.r
                                    + 0.08 * std::sin(pulse_ * kTau + pt.ph));
                const qreal x = c.x() + std::cos(ang) * dist * 1.42;
                const qreal y = c.y() + std::sin(ang) * dist * 1.42;
                const qreal sz = pt.sz
                    * (0.55 + 0.45 * std::sin(rot1_ * kTau * 2.0 + pt.ph));
                const qreal br = 0.5
                    + 0.5 * std::sin(rot3_ * kTau * 3.0 + pt.ph);

                QColor col;
                if (pt.c == 0)      col = QColor(255, 180, 230, int(190 * br));
                else if (pt.c == 1) col = QColor(150, 200, 255, int(190 * br));
                else                col = QColor(200, 180, 255, int(190 * br));
                p.setBrush(col);
                p.drawEllipse(QPointF(x, y), sz, sz);
            }
        }

        // 扫光点 (绕椭圆轨道)
        {
            const qreal sweep = std::fmod(rot3_ * 2.0, 1.0);
            if (sweep < 0.72) {
                const qreal s = sweep / 0.72;
                const qreal alpha = (1.0 - s) * 200.0;
                const qreal ang = s * kTau;
                const qreal dist = baseR * 1.55;
                const QPointF pt = c + QPointF(std::cos(ang), std::sin(ang)) * dist;

                QRadialGradient sg(pt, 14.0);
                sg.setColorAt(0.0, QColor(255, 255, 255, int(alpha)));
                sg.setColorAt(0.35, QColor(255, 180, 230, int(alpha * 0.5)));
                sg.setColorAt(1.0, QColor(255, 180, 230, 0));
                p.setPen(Qt::NoPen);
                p.setBrush(sg);
                p.drawEllipse(pt, 14.0, 14.0);
            }
        }

        // 水平细线
        {
            p.setPen(QPen(QColor(255, 255, 255, 6), 1.0));
            for (qreal y = outer.top() + 60; y < outer.bottom() - 80; y += 32) {
                p.drawLine(QPointF(outer.left() + 1, y),
                           QPointF(outer.right() - 1, y));
            }
        }

        p.restore();

        // 顶部 + 底部文字
        {
            QFont f = font();
            f.setPointSizeF(8.0);
            f.setWeight(QFont::Bold);
            f.setLetterSpacing(QFont::AbsoluteSpacing, 1.6);
            p.setFont(f);
            p.setPen(QColor(255, 147, 204));
            p.drawText(QRectF(outer.left() + 16, outer.top() + 14,
                              outer.width() - 32, 16),
                       Qt::AlignLeft | Qt::AlignVCenter, "SYSTEM STATUS");
        }

        {
            QFont f = font();
            f.setPointSizeF(14.0);
            f.setWeight(QFont::DemiBold);
            p.setFont(f);
            p.setPen(QColor("#fff8ff"));
            p.drawText(QRectF(outer.left() + 16, outer.bottom() - 68,
                              outer.width() - 32, 22),
                       Qt::AlignLeft | Qt::AlignVCenter, "Nebula ready");

            QFont sub = font();
            sub.setPointSizeF(9.0);
            p.setFont(sub);
            p.setPen(QColor(220, 199, 232, 205));
            p.drawText(QRectF(outer.left() + 16, outer.bottom() - 42,
                              outer.width() - 32, 16),
                       Qt::AlignLeft | Qt::AlignVCenter,
                       "aurora link  /  online");
        }

        // 边框
        {
            QLinearGradient bd(outer.topLeft(), outer.bottomRight());
            bd.setColorAt(0.0, QColor(178, 148, 255, 110));
            bd.setColorAt(0.5, QColor(255, 147, 204, 65));
            bd.setColorAt(1.0, QColor(120, 200, 255, 90));

            p.setPen(QPen(QBrush(bd), 1.2));
            p.setBrush(Qt::NoBrush);
            p.drawPath(card);
        }
    }

private:
    qreal rot1_  = 0.0;
    qreal rot2_  = 0.0;
    qreal rot3_  = 0.0;
    qreal pulse_ = 0.0;

    QVector<Particle> particles_;
};

} // namespace

// =====================================================================
// MainWindow
// =====================================================================
MainWindow::MainWindow(QWidget* parent) : QMainWindow(parent) {
    setWindowFlags(Qt::FramelessWindowHint | Qt::Window);
    setAttribute(Qt::WA_TranslucentBackground);
    setAttribute(Qt::WA_NoSystemBackground);
    setMinimumSize(1100, 620);
    resize(kBaseWidth, kBaseHeight);

    styleApp();
    buildUi();

    auto* ambient = new QVariantAnimation(this);
    ambient->setStartValue(0.0);
    ambient->setEndValue(1.0);
    ambient->setDuration(7200);
    ambient->setLoopCount(-1);
    ambient->setEasingCurve(QEasingCurve::InOutSine);
    connect(ambient, &QVariantAnimation::valueChanged, this, [this](const QVariant& v) {
        setProperty("nebulaAmbient", v);
        update();
    });
    ambient->start();

    meteorAnim_ = new QVariantAnimation(this);
    meteorAnim_->setStartValue(0.0);
    meteorAnim_->setEndValue(1.0);
    meteorAnim_->setEasingCurve(QEasingCurve::OutQuad);
    connect(meteorAnim_, &QVariantAnimation::valueChanged, this, [this](const QVariant& v) {
        meteorProgress_ = v.toReal();
        update();
    });
    connect(meteorAnim_, &QVariantAnimation::finished, this, [this]() {
        meteorProgress_ = -1.0;
        update();
    });
    scheduleNextMeteor();

    auto* blackHoleAnim = new QVariantAnimation(this);
    blackHoleAnim->setStartValue(0.0);
    blackHoleAnim->setEndValue(1.0);
    blackHoleAnim->setDuration(36000);
    blackHoleAnim->setLoopCount(-1);
    blackHoleAnim->setEasingCurve(QEasingCurve::Linear);
    connect(blackHoleAnim, &QVariantAnimation::valueChanged, this,
            [this](const QVariant& v) {
                blackHoleSpin_ = v.toReal();
                update();
            });
    blackHoleAnim->start();

    timer_ = new QTimer(this);
    timer_->setInterval(1000);
    connect(timer_, &QTimer::timeout, this, [this]() {
        refreshNow(false);
    });
    timer_->start();
    refreshNow();
}

void MainWindow::scheduleNextMeteor() {
    const int delay = 10000 + (QRandomGenerator::global()->bounded(8000));
    QTimer::singleShot(delay, this, [this]() {
        if (exiting_) return;
        launchMeteor();
    });
}

void MainWindow::launchMeteor() {
    QRandomGenerator* rng = QRandomGenerator::global();
    const bool fromLeft = (rng->bounded(2) == 0);

    const qreal startXRatio = fromLeft
        ? (rng->bounded(30)) / 100.0
        : 0.70 + (rng->bounded(30)) / 100.0;
    const qreal endXRatio   = fromLeft
        ? 0.65 + (rng->bounded(30)) / 100.0
        : 0.05 + (rng->bounded(30)) / 100.0;

    meteorStart_ = QPointF(width() * startXRatio, -40.0);
    meteorEnd_   = QPointF(width() * endXRatio,
                           height() * (0.55 + (rng->bounded(30)) / 100.0));

    meteorAnim_->stop();
    meteorAnim_->setDuration(1500 + rng->bounded(800));
    meteorAnim_->start();

    QTimer::singleShot(meteorAnim_->duration() + 300, this, [this]() {
        if (!exiting_) scheduleNextMeteor();
    });
}

void MainWindow::buildUi() {
#ifdef OPENZEN_BUILD_REVISION
    const QString displayTitle = QStringLiteral("Nebula  •  %1")
        .arg(QString::fromLatin1(OPENZEN_BUILD_REVISION).left(7));
#else
    const QString displayTitle = QStringLiteral("Nebula");
#endif

    setWindowTitle(QStringLiteral("Nebula"));

    auto* central = new QWidget(this);
    central->setObjectName("centralRoot");
    central->setAttribute(Qt::WA_TranslucentBackground);

    auto* root = new QVBoxLayout(central);
    root->setContentsMargins(0, 0, 0, 0);
    root->setSpacing(0);

    titleBar_ = new TitleBar(central);
    titleBar_->setTitleText(displayTitle);
    root->addWidget(titleBar_);

    auto* shell = new QWidget(central);
    shell->setObjectName("shell");
    auto* shellLayout = new QHBoxLayout(shell);
    shellLayout->setContentsMargins(20, 16, 20, 20);
    shellLayout->setSpacing(18);

    auto* main = new QWidget(shell);
    main->setObjectName("mainArea");
    auto* mainLayout = new QVBoxLayout(main);
    mainLayout->setContentsMargins(0, 0, 0, 0);
    mainLayout->setSpacing(14);

    // ★ Hero 换 ScanCard
    auto* hero = new ScanCard(main);
    hero->setObjectName("heroCard");
    hero->setMinimumHeight(148);
    auto* heroLayout = new QHBoxLayout(hero);
    heroLayout->setContentsMargins(22, 18, 18, 18);
    heroLayout->setSpacing(16);

    auto* heroText = new QWidget(hero);
    auto* heroTextLayout = new QVBoxLayout(heroText);
    heroTextLayout->setContentsMargins(0, 0, 0, 0);
    heroTextLayout->setSpacing(3);

    auto* eyebrow = new QLabel(QStringLiteral("✦  WELCOME BACK"), heroText);
    eyebrow->setObjectName("eyebrow");
    auto* title = new QLabel(QStringLiteral("Nebula"), heroText);
    title->setObjectName("pageTitle");
    hint_ = new QLabel(QStringLiteral("Choose a detected Minecraft instance when you are ready."), heroText);
    hint_->setObjectName("pageHint");
    hint_->setWordWrap(true);

    auto* chips = new QWidget(heroText);
    chips->setObjectName("chips");
    auto* chipsLayout = new QHBoxLayout(chips);
    chipsLayout->setContentsMargins(0, 8, 0, 0);
    chipsLayout->setSpacing(8);

    auto* chip1 = new QLabel(QStringLiteral("AURORA EDITION"), chips);
    chip1->setObjectName("heroChip");
    auto* chip2 = new QLabel(QStringLiteral("AUTO SYNC  1s"), chips);
    chip2->setObjectName("heroChip");
    chipsLayout->addWidget(chip1);
    chipsLayout->addWidget(chip2);
    chipsLayout->addStretch(1);

    heroTextLayout->addWidget(eyebrow);
    heroTextLayout->addWidget(title);
    heroTextLayout->addWidget(hint_);
    heroTextLayout->addWidget(chips);

    // ★ Hero orb 换成 GlowOrb
    auto* heroOrb = new GlowOrb(hero);

    heroLayout->addWidget(heroText, 1);
    heroLayout->addWidget(heroOrb, 0, Qt::AlignVCenter);

    // ★ List 换 ScanCard
    auto* listCard = new ScanCard(main);
    listCard->setObjectName("listCard");
    auto* cardLayout = new QVBoxLayout(listCard);
    cardLayout->setContentsMargins(16, 15, 16, 15);
    cardLayout->setSpacing(10);

    auto* cardHeader = new QWidget(listCard);
    auto* cardHeaderLayout = new QHBoxLayout(cardHeader);
    cardHeaderLayout->setContentsMargins(0, 0, 0, 0);
    cardHeaderLayout->setSpacing(8);

    auto* cardTitleBox = new QWidget(cardHeader);
    auto* cardTitleLayout = new QVBoxLayout(cardTitleBox);
    cardTitleLayout->setContentsMargins(0, 0, 0, 0);
    cardTitleLayout->setSpacing(1);
    auto* cardTitle = new QLabel(QStringLiteral("Available instances"), cardTitleBox);
    cardTitle->setObjectName("cardTitle");
    auto* cardSub = new QLabel(QStringLiteral("Select one to continue"), cardTitleBox);
    cardSub->setObjectName("cardSub");
    cardTitleLayout->addWidget(cardTitle);
    cardTitleLayout->addWidget(cardSub);

    autoInjectButton_ = new QPushButton(QStringLiteral("⚡ Auto Inject"), cardHeader);
    autoInjectButton_->setObjectName("autoInjectButton");
    autoInjectButton_->setCursor(Qt::PointingHandCursor);
    autoInjectButton_->setFixedHeight(28);
    autoInjectButton_->setCheckable(true);
    autoInjectButton_->setToolTip(QStringLiteral("开启后，检测到 Minecraft 进程会自动注入"));
    connect(autoInjectButton_, &QPushButton::toggled, this, [this](bool checked) {
        autoInject_ = checked;
        autoInjectButton_->setText(checked
            ? QStringLiteral("⚡ Auto Inject  ON")
            : QStringLiteral("⚡ Auto Inject"));
        refreshNow(false);
    });

    refreshButton_ = new QPushButton(QStringLiteral("↻ Refresh"), cardHeader);
    refreshButton_->setObjectName("refreshButton");
    refreshButton_->setCursor(Qt::PointingHandCursor);
    refreshButton_->setFixedHeight(28);
    connect(refreshButton_, &QPushButton::clicked, this, [this]() {
        refreshNow(true);
    });

    auto* liveBadge = new QLabel(QStringLiteral("●  LIVE"), cardHeader);
    liveBadge->setObjectName("liveBadge");
    liveBadge->setAlignment(Qt::AlignCenter);
    liveBadge->setFixedSize(72, 28);

    cardHeaderLayout->addWidget(cardTitleBox);
    cardHeaderLayout->addStretch(1);
    cardHeaderLayout->addWidget(autoInjectButton_);
    cardHeaderLayout->addWidget(refreshButton_);
    cardHeaderLayout->addWidget(liveBadge);

    list_ = new InstanceList(listCard);
    list_->setObjectName("instanceList");
    connect(list_, &InstanceList::injectRequested, this, &MainWindow::onInjectRequested);

    cardLayout->addWidget(cardHeader);
    cardLayout->addWidget(makeDivider(listCard));
    cardLayout->addWidget(list_, 1);

    auto* footer = new QFrame(main);
    footer->setObjectName("footerCard");
    auto* footerLayout = new QHBoxLayout(footer);
    footerLayout->setContentsMargins(12, 8, 12, 8);
    footerLayout->setSpacing(10);

    status_ = new QLabel(QStringLiteral("Searching for Minecraft sessions..."), footer);
    status_->setObjectName("statusPill");
    status_->setWordWrap(false);

    auto* dividerDot = new QLabel(QStringLiteral("•"), footer);
    dividerDot->setObjectName("footerDot");
    auto* footerNote = new QLabel(QStringLiteral("Nebula ready"), footer);
    footerNote->setObjectName("footerNote");
    auto* buildLabel = new QLabel(QStringLiteral("BETA"), footer);
    buildLabel->setObjectName("buildBadge");

    footerLayout->addWidget(status_);
    footerLayout->addStretch(1);
    footerLayout->addWidget(footerNote);
    footerLayout->addWidget(dividerDot);
    footerLayout->addWidget(buildLabel);

    mainLayout->addWidget(hero);
    mainLayout->addWidget(listCard, 1);
    mainLayout->addWidget(footer);

    auto* mascotColumn = new QWidget(shell);
    mascotColumn->setObjectName("mascotColumn");
    mascotColumn->setFixedWidth(315);
    auto* mascotLayout = new QVBoxLayout(mascotColumn);
    mascotLayout->setContentsMargins(0, 0, 0, 0);
    mascotLayout->setSpacing(10);

    auto* mascotHeader = new QFrame(mascotColumn);
    mascotHeader->setObjectName("mascotHeader");
    auto* mascotHeaderLayout = new QHBoxLayout(mascotHeader);
    mascotHeaderLayout->setContentsMargins(12, 8, 12, 8);
    auto* mascotLabel = new QLabel(QStringLiteral("NEBULA COMPANION"), mascotHeader);
    mascotLabel->setObjectName("mascotLabel");
    auto* sparkle = new QLabel(QStringLiteral("✦"), mascotHeader);
    sparkle->setObjectName("mascotSparkle");
    mascotHeaderLayout->addWidget(mascotLabel);
    mascotHeaderLayout->addStretch(1);
    mascotHeaderLayout->addWidget(sparkle);

    auto* statusPanel = new StatusPanel(mascotColumn);
    statusPanel->setObjectName("statusPanel");

    auto* quote = new QLabel(QStringLiteral("“Welcome back to Nebula.”"), mascotColumn);
    quote->setObjectName("mascotQuote");
    quote->setAlignment(Qt::AlignCenter);

    mascotLayout->addWidget(mascotHeader);
    mascotLayout->addWidget(statusPanel, 1);
    mascotLayout->addWidget(quote);

    sidebar_ = new Sidebar(shell);
    shellLayout->addWidget(sidebar_, 0);

    injectPage_ = main;
    shellLayout->addWidget(injectPage_, 1);

    settingsPage_ = new SettingsPage(shell);
    settingsPage_->setVisible(false);
    shellLayout->addWidget(settingsPage_, 1);

    shellLayout->addWidget(mascotColumn, 0);

    root->addWidget(shell, 1);

    connect(sidebar_, &Sidebar::injectClicked,
            this, &MainWindow::showInjectPage);
    connect(sidebar_, &Sidebar::settingsClicked,
            this, &MainWindow::showSettingsPage);

    auto addShadow = [](QWidget* widget, qreal blur, qreal y, QColor color) {
        auto* shadow = new QGraphicsDropShadowEffect(widget);
        shadow->setBlurRadius(blur);
        shadow->setOffset(0.0, y);
        shadow->setColor(color);
        widget->setGraphicsEffect(shadow);
    };
    addShadow(hero, 34.0, 10.0, QColor(18, 0, 40, 100));
    addShadow(listCard, 38.0, 12.0, QColor(0, 0, 0, 115));
    addShadow(footer, 24.0, 7.0, QColor(0, 0, 0, 70));

    setCentralWidget(central);
}

void MainWindow::styleApp() {
    static const char* kQss = R"qss(
QWidget {
    font-family: "Segoe UI", "Microsoft YaHei UI", sans-serif;
    color: #f5f0ff;
    font-size: 13px;
}
QWidget#shell, QWidget#mainArea, QWidget#centralRoot,
QWidget#mascotColumn, QWidget#chips { background: transparent; }

QFrame#heroCard {
    background: qlineargradient(x1:0,y1:0,x2:1,y2:1,
        stop:0 rgba(62,31,88,220), stop:.50 rgba(30,27,58,225), stop:1 rgba(17,42,57,218));
    border: 1px solid rgba(238,195,255,34);
    border-radius: 22px;
}
QLabel#eyebrow { color:#ff93cc; font-size:11px; font-weight:850; letter-spacing:2px; }
QLabel#pageTitle { color:#fffaff; font-size:36px; font-weight:800; padding:0; }
QLabel#pageHint { color:#c4b8d6; font-size:13px; }
QLabel#heroChip {
    color:#e0d5f0; background:rgba(255,255,255,10);
    border:1px solid rgba(255,255,255,20); border-radius:8px;
    padding:5px 8px; font-size:10px; font-weight:750; letter-spacing:.8px;
}

QFrame#listCard {
    background: qlineargradient(x1:0, y1:0, x2:1, y2:1,
        stop:0 rgba(55, 48, 95, 245), stop:1 rgba(35, 60, 85, 245));
    border: 1px solid rgba(178, 148, 255, 70);
    border-radius: 21px;
}
QLabel#cardTitle { color:#fff9ff; font-size:16px; font-weight:760; }
QLabel#cardSub { color:#c9bedd; font-size:12px; }
QPushButton#refreshButton {
    color:#e7d9ff;
    background:rgba(139,101,255,18);
    border:1px solid rgba(178,148,255,32);
    border-radius:9px;
    padding:3px 10px;
    font-size:11px;
    font-weight:800;
}
QPushButton#refreshButton:hover { background:rgba(180,130,255,35); }
QPushButton#refreshButton:pressed { background:rgba(100,80,180,45); }

QPushButton#autoInjectButton {
    color:#e7d9ff;
    background:rgba(255,255,255,8);
    border:1px solid rgba(178,148,255,32);
    border-radius:9px;
    padding:3px 12px;
    font-size:11px;
    font-weight:800;
}
QPushButton#autoInjectButton:hover { background:rgba(180,130,255,28); }
QPushButton#autoInjectButton:checked {
    color:#0d0618;
    background: qlineargradient(x1:0, y1:0, x2:1, y2:0,
        stop:0 rgba(255, 126, 204, 235), stop:1 rgba(142, 104, 255, 235));
    border:1px solid rgba(255, 200, 240, 140);
}
QPushButton#autoInjectButton:checked:hover {
    background: qlineargradient(x1:0, y1:0, x2:1, y2:0,
        stop:0 rgba(255, 150, 220, 255), stop:1 rgba(165, 130, 255, 255));
}

QLabel#liveBadge {
    color:#a8ffd4; background:rgba(65,229,163,17);
    border:1px solid rgba(92,242,184,45); border-radius:9px;
    font-size:10px; font-weight:850; letter-spacing:.8px;
}
QFrame#divider { background:rgba(255,255,255,13); border:none; max-height:1px; }

QFrame#footerCard {
    background:rgba(255,255,255,6);
    border:1px solid rgba(255,255,255,12);
    border-radius:13px;
}
QLabel#statusPill { color:#e0d5f0; font-size:14px; }
QLabel#footerNote { color:#c0b4d4; font-size:12px; }
QLabel#footerDot { color:#8a7a9e; font-size:14px; }
QLabel#buildBadge {
    color:#e7d9ff; background:rgba(139,101,255,18);
    border:1px solid rgba(178,148,255,32); border-radius:7px;
    padding:4px 8px; font-size:11px; font-weight:800; letter-spacing:1px;
}

QFrame#mascotHeader {
    background:rgba(255,255,255,6);
    border:1px solid rgba(255,255,255,12);
    border-radius:13px;
}
QLabel#mascotLabel { color:#d0c4e4; font-size:11px; font-weight:800; letter-spacing:1.8px; }
QLabel#mascotSparkle { color:#ff8eca; font-size:16px; }
QLabel#mascotQuote {
    color:#c9bedd; background:rgba(255,255,255,6);
    border:1px solid rgba(255,255,255,12); border-radius:12px;
    padding:10px 12px; font-size:13px; font-style:italic;
}
QToolTip {
    color:#fff9ff; background:#1a1428; border:1px solid #443656; padding:6px 8px;
}
)qss";

    qApp->setStyleSheet(QString::fromUtf8(kQss));
}

void MainWindow::enableWin11RoundedCorners() {
#ifdef Q_OS_WIN
    enum { DWMWA_WINDOW_CORNER_PREFERENCE_LOCAL = 33 };
    enum { DWMWCP_ROUND_LOCAL = 2 };
    HWND hwnd = reinterpret_cast<HWND>(winId());
    if (!hwnd) return;
    int pref = DWMWCP_ROUND_LOCAL;
    DwmSetWindowAttribute(hwnd, DWMWA_WINDOW_CORNER_PREFERENCE_LOCAL, &pref, sizeof(pref));
#endif
}

void MainWindow::paintEvent(QPaintEvent*) {
    QPainter p(this);
    p.setRenderHint(QPainter::Antialiasing);

    const QRectF r = QRectF(rect()).adjusted(0.5, 0.5, -0.5, -0.5);
    QPainterPath panel;
    panel.addRoundedRect(r, kCornerRadius, kCornerRadius);

    // 深空底色
    QLinearGradient bg(r.topLeft(), r.bottomRight());
    bg.setColorAt(0.0, QColor("#080711"));
    bg.setColorAt(0.33, QColor("#151027"));
    bg.setColorAt(0.68, QColor("#0c1523"));
    bg.setColorAt(1.0, QColor("#071018"));
    p.fillPath(panel, bg);

    const qreal t = property("nebulaAmbient").isValid() ? property("nebulaAmbient").toReal() : 0.0;
    const qreal s = std::sin(t * kTau);
    const qreal c = std::cos(t * kTau);

    // 粉光晕
    QRadialGradient pink(QPointF(width() * (0.18 + s * 0.035),
                                 height() * (0.16 + c * 0.018)),
                         width() * 0.52);
    pink.setColorAt(0.0, QColor(255, 88, 181, 58));
    pink.setColorAt(0.42, QColor(149, 94, 255, 25));
    pink.setColorAt(1.0, QColor(0,0,0,0));
    p.fillPath(panel, pink);

    // 青光晕
    QRadialGradient cyan(QPointF(width() * (0.88 - c * 0.025),
                                 height() * (0.82 - s * 0.02)),
                         width() * 0.44);
    cyan.setColorAt(0.0, QColor(67, 216, 255, 42));
    cyan.setColorAt(0.50, QColor(86, 117, 255, 16));
    cyan.setColorAt(1.0, QColor(0,0,0,0));
    p.fillPath(panel, cyan);

    p.save();
    p.setClipPath(panel);

    // ★ 顶部极光带
    {
        QLinearGradient auroraTop(r.topLeft(),
                                  QPointF(r.left(), r.top() + 260));
        auroraTop.setColorAt(0.0, QColor(140, 70, 240, 55));
        auroraTop.setColorAt(0.5, QColor(120, 60, 220, 20));
        auroraTop.setColorAt(1.0, QColor(120, 60, 220, 0));
        p.setPen(Qt::NoPen);
        p.fillRect(r, auroraTop);
    }

    // ★ 底部极光带
    {
        QLinearGradient auroraBot(QPointF(r.left(), r.bottom() - 260),
                                  r.bottomLeft());
        auroraBot.setColorAt(0.0, QColor(60, 220, 200, 0));
        auroraBot.setColorAt(0.5, QColor(60, 220, 200, 18));
        auroraBot.setColorAt(1.0, QColor(60, 220, 200, 48));
        p.setPen(Qt::NoPen);
        p.fillRect(r, auroraBot);
    }

    // ★ 全息网格
    {
        const qreal step = 46.0;
        const qreal ox = std::fmod(t * 6.0, step);
        const qreal oy = std::fmod(t * 4.0, step);

        QPen g(QColor(120, 180, 255, 12), 1);
        p.setPen(g);
        for (qreal x = -step + ox; x < r.width() + step; x += step)
            p.drawLine(QPointF(x, 0), QPointF(x, r.height()));
        for (qreal y = -step + oy; y < r.height() + step; y += step)
            p.drawLine(QPointF(0, y), QPointF(r.width(), y));
    }

    // ★ 右侧螺旋星系
    {
        const QPointF gx(r.width() * 0.86, r.height() * 0.30);
        const int arms = 3;
        for (int a = 0; a < arms; ++a) {
            const qreal baseAng = a * kTau / arms;
            for (int sIdx = 0; sIdx < 36; ++sIdx) {
                const qreal u = sIdx / 36.0;
                const qreal ang = baseAng + u * 3.2 + t * 0.4;
                const qreal rad = 20.0 + u * 190.0;
                const QPointF pt = gx
                    + QPointF(std::cos(ang), std::sin(ang) * 0.65) * rad;
                const qreal br = (1.0 - u) * 0.55;
                QColor col(180, 140, 255, int(180 * br));
                p.setPen(Qt::NoPen);
                p.setBrush(col);
                const qreal sz = 0.6 + u * 1.3;
                p.drawEllipse(pt, sz, sz);
            }
        }
    }

    // 鼠标柔光
    const QPoint cursor = mapFromGlobal(QCursor::pos());
    if (rect().contains(cursor)) {
        QRadialGradient mouseGlow(cursor, 200.0);
        mouseGlow.setColorAt(0.0, QColor(255, 210, 255, 22));
        mouseGlow.setColorAt(0.40, QColor(143, 113, 255, 12));
        mouseGlow.setColorAt(1.0, QColor(0,0,0,0));
        p.fillPath(panel, mouseGlow);
    }

    // ===== 黑洞吸积盘（超炫增强版） =====
    {
        const QPointF bhC(width() * 0.14, height() * 0.76);
        const qreal coreR = 42.0;
        const qreal spin = blackHoleSpin_ * kTau;
        const qreal pulse = 0.5 + 0.5 * std::sin(spin * 2.0);

        // 1. 超大范围引力光晕
        {
            QRadialGradient halo(bhC, coreR * 9.0);
            halo.setColorAt(0.00, QColor(180, 140, 255, 0));
            halo.setColorAt(0.15, QColor(160, 120, 255, 20 + 10 * pulse));
            halo.setColorAt(0.35, QColor(100, 80, 220, 12));
            halo.setColorAt(0.60, QColor(60, 80, 200, 6));
            halo.setColorAt(1.00, QColor(0, 0, 0, 0));
            p.setPen(Qt::NoPen);
            p.setBrush(halo);
            p.drawEllipse(bhC, coreR * 9.0, coreR * 9.0);
        }

        // 2. 相对论喷流（上下锥形 + 粒子）
        for (int dir = -1; dir <= 1; dir += 2) {
            QPointF base = bhC + QPointF(0, dir * coreR * 0.8);
            QPointF tip  = bhC + QPointF(0, dir * coreR * 6.5);
            QLinearGradient jet(base, tip);
            jet.setColorAt(0.0, QColor(180, 200, 255, 0));
            jet.setColorAt(0.15, QColor(140, 180, 255, 30));
            jet.setColorAt(0.5, QColor(100, 150, 255, 80));
            jet.setColorAt(0.85, QColor(60, 100, 220, 20));
            jet.setColorAt(1.0, QColor(0, 0, 0, 0));
            QPainterPath jetPath;
            jetPath.moveTo(base - QPointF(coreR * 0.4, 0));
            jetPath.lineTo(tip - QPointF(coreR * 1.2, 0));
            jetPath.lineTo(tip + QPointF(coreR * 1.2, 0));
            jetPath.lineTo(base + QPointF(coreR * 0.4, 0));
            jetPath.closeSubpath();
            p.setPen(Qt::NoPen);
            p.setBrush(jet);
            p.drawPath(jetPath);

            for (int i = 0; i < 12; ++i) {
                qreal tJet = std::fmod(blackHoleSpin_ * 3.0 + i * 0.083, 1.0);
                qreal y = base.y() + (tip.y() - base.y()) * tJet;
                qreal x = bhC.x() + (hash01(i, 42) - 0.5) * coreR * 2.0
                          * (0.3 + tJet * 1.5) * dir;
                qreal sz = 1.0 + 2.0 * (1.0 - tJet) * pulse;
                p.setBrush(QColor(200, 220, 255, int(200 * (1.0 - tJet))));
                p.drawEllipse(QPointF(x, y), sz, sz);
            }
        }

        // 3. 多层倾斜吸积盘
        struct DiskLayer {
            qreal radius;
            qreal tiltY;
            qreal speed;
            QColor c1, c2, c3;
        };
        QVector<DiskLayer> layers = {
            {coreR * 2.2, 0.22, 1.0,
             QColor(80, 60, 180, 0), QColor(200, 180, 255, 180), QColor(255, 240, 255, 220)},
            {coreR * 3.0, 0.28, 0.7,
             QColor(60, 80, 200, 0), QColor(140, 200, 255, 160), QColor(220, 240, 255, 200)},
            {coreR * 3.8, 0.34, 0.5,
             QColor(40, 60, 160, 0), QColor(100, 160, 240, 100), QColor(180, 220, 255, 150)},
            {coreR * 4.8, 0.42, 0.3,
             QColor(30, 40, 120, 0), QColor(80, 120, 200, 60), QColor(140, 180, 255, 90)},
        };
        for (int li = 0; li < layers.size(); ++li) {
            const auto& L = layers[li];
            qreal phase = spin * L.speed;
            p.save();
            p.translate(bhC);
            p.rotate(std::sin(phase) * 5.0);
            p.scale(1.0, L.tiltY);

            QConicalGradient cg(QPointF(0, 0),
                                -phase * 180.0 / (kTau / 2.0));
            cg.setColorAt(0.00, L.c1);
            cg.setColorAt(0.12, L.c2);
            cg.setColorAt(0.25, L.c3);
            cg.setColorAt(0.37, L.c2);
            cg.setColorAt(0.50, L.c1);
            cg.setColorAt(0.62, L.c2);
            cg.setColorAt(0.75, L.c3);
            cg.setColorAt(0.87, L.c2);
            cg.setColorAt(1.00, L.c1);

            p.setPen(Qt::NoPen);
            p.setBrush(cg);
            p.drawEllipse(QPointF(0, 0), L.radius, L.radius);
            p.restore();
        }

        // 4. 多普勒增亮（一侧更亮）
        {
            p.save();
            p.translate(bhC);
            p.scale(1.0, 0.28);
            QConicalGradient dop(0, 0, -spin * 180.0 / (kTau / 2.0));
            dop.setColorAt(0.0, QColor(255, 255, 255, 0));
            dop.setColorAt(0.25, QColor(255, 240, 255, 180));
            dop.setColorAt(0.5, QColor(255, 255, 255, 0));
            dop.setColorAt(0.75, QColor(255, 240, 255, 60));
            dop.setColorAt(1.0, QColor(255, 255, 255, 0));
            p.setPen(Qt::NoPen);
            p.setBrush(dop);
            p.drawEllipse(QPointF(0, 0), coreR * 3.5, coreR * 3.5);
            p.restore();
        }

        // 5. 光子环
        {
            qreal photonR = coreR * 1.55 + 2.0 * std::sin(spin * 3.0);
            QRadialGradient ring(bhC, photonR * 1.2);
            ring.setColorAt(0.6, QColor(200, 180, 255, 0));
            ring.setColorAt(0.82, QColor(220, 200, 255, 180));
            ring.setColorAt(0.90, QColor(255, 255, 255, 240));
            ring.setColorAt(0.96, QColor(200, 180, 255, 100));
            ring.setColorAt(1.0, QColor(180, 160, 255, 0));
            p.setPen(Qt::NoPen);
            p.setBrush(ring);
            p.drawEllipse(bhC, photonR * 1.2, photonR * 1.2);
        }

        // 6. 事件视界（纯黑 + 紫边）
        {
            p.setPen(QPen(QColor(100, 80, 200, 80), 1.2));
            p.setBrush(QColor(0, 0, 0, 255));
            p.drawEllipse(bhC, coreR, coreR);
        }

        // 7. 吸积盘内粒子流
        {
            for (int i = 0; i < 40; ++i) {
                qreal a = spin * 2.0 + i * 0.157;
                qreal rad = coreR * (1.8 + hash01(i, 7) * 3.5);
                qreal yScale = 0.22 + hash01(i, 8) * 0.3;
                qreal x = bhC.x() + std::cos(a) * rad;
                qreal y = bhC.y() + std::sin(a) * rad * yScale;
                qreal sz = 0.8 + hash01(i, 9) * 1.8;
                int alpha = int(120 + 100 * std::sin(a * 3.0 + spin));
                p.setPen(Qt::NoPen);
                p.setBrush(QColor(220, 200, 255, alpha));
                p.drawEllipse(QPointF(x, y), sz, sz);
            }
        }

        // 8. 引力透镜星光弧
        {
            p.setPen(Qt::NoPen);
            for (int i = 0; i < 24; ++i) {
                qreal ang = i * (kTau / 24.0) + spin * 0.5;
                qreal r1 = coreR * 1.8;
                qreal r2 = coreR * 3.2;
                QRadialGradient arcGrad(bhC, r2);
                arcGrad.setColorAt(0.0, QColor(255, 255, 255, 0));
                arcGrad.setColorAt(0.7, QColor(200, 220, 255, 60));
                arcGrad.setColorAt(0.9, QColor(255, 255, 255, 120));
                arcGrad.setColorAt(1.0, QColor(255, 255, 255, 0));
                p.setBrush(arcGrad);
                QPainterPath arc;
                arc.moveTo(bhC + QPointF(std::cos(ang),
                                         std::sin(ang) * 0.3) * r1);
                arc.quadTo(bhC + QPointF(std::cos(ang + 0.3),
                                         std::sin(ang + 0.3) * 0.3) * r2,
                           bhC + QPointF(std::cos(ang + 0.6),
                                         std::sin(ang + 0.6) * 0.3) * r1);
                p.drawPath(arc);
            }
        }
    }

    // ★ 60 颗稳定星点 (原 16 颗)
    for (int i = 0; i < 60; ++i) {
        const qreal sx = hash01(i, 1) * width();
        const qreal sy = hash01(i, 2) * height();
        const qreal tw = 0.35 + 0.65 * std::sin(t * 2.4 + i * 0.7);
        const qreal sz = 0.4 + 1.1 * tw;
        QColor c2(255, 240, 255, int(180 * tw));
        p.setPen(Qt::NoPen);
        p.setBrush(c2);
        p.drawEllipse(QPointF(sx, sy), sz, sz);
    }

    // 星痕 (左侧斜线)
    p.setPen(QPen(QColor(255,255,255,8), 1));
    for (int i = 0; i < 5; ++i) {
        const qreal y = 115.0 + i * 102.0 + s * (i % 2 == 0 ? 3.0 : -3.0);
        p.drawLine(QPointF(26.0, y), QPointF(86.0, y - 18.0));
    }

    // 流星
    if (meteorProgress_ >= 0.0 && meteorProgress_ <= 1.0) {
        const qreal px = meteorStart_.x()
                         + (meteorEnd_.x() - meteorStart_.x()) * meteorProgress_;
        const qreal py = meteorStart_.y()
                         + (meteorEnd_.y() - meteorStart_.y()) * meteorProgress_;

        const qreal dx = meteorEnd_.x() - meteorStart_.x();
        const qreal dy = meteorEnd_.y() - meteorStart_.y();
        const qreal len = std::sqrt(dx * dx + dy * dy);
        if (len > 1.0) {
            const qreal ux = dx / len;
            const qreal uy = dy / len;

            const qreal trailLen = 220.0;
            const qreal tailX = px - ux * trailLen;
            const qreal tailY = py - uy * trailLen;

            qreal fade = 1.0;
            if (meteorProgress_ < 0.12)      fade = meteorProgress_ / 0.12;
            else if (meteorProgress_ > 0.85) fade = (1.0 - meteorProgress_) / 0.15;

            QLinearGradient trail(tailX, tailY, px, py);
            trail.setColorAt(0.0, QColor(255, 200, 240, 0));
            trail.setColorAt(0.55, QColor(255, 180, 230, int(120 * fade)));
            trail.setColorAt(1.0, QColor(255, 240, 255, int(220 * fade)));

            p.setPen(QPen(QBrush(trail), 2.2, Qt::SolidLine, Qt::RoundCap));
            p.setBrush(Qt::NoBrush);
            p.drawLine(QPointF(tailX, tailY), QPointF(px, py));

            QRadialGradient headGlow(QPointF(px, py), 14.0);
            headGlow.setColorAt(0.0, QColor(255, 255, 255, int(230 * fade)));
            headGlow.setColorAt(0.35, QColor(255, 180, 230, int(130 * fade)));
            headGlow.setColorAt(1.0, QColor(255, 180, 230, 0));
            p.setPen(Qt::NoPen);
            p.setBrush(headGlow);
            p.drawEllipse(QPointF(px, py), 14.0, 14.0);

            p.setBrush(QColor(255, 255, 255, int(240 * fade)));
            p.drawEllipse(QPointF(px, py), 2.0, 2.0);
        }
    }

    p.restore();

    // 外边框
    p.setPen(QPen(QColor(255,255,255,24), 1));
    p.setBrush(Qt::NoBrush);
    p.drawPath(panel);
}

void MainWindow::showEvent(QShowEvent* e) {
    QMainWindow::showEvent(e);
    enableWin11RoundedCorners();
    if (!entrancePlayed_) setWindowOpacity(0.0);
}

void MainWindow::playEntrance() {
    entrancePlayed_ = true;
    show();
    raise();
    activateWindow();

    auto* fade = new QPropertyAnimation(this, "windowOpacity", this);
    fade->setDuration(620);
    fade->setStartValue(windowOpacity());
    fade->setKeyValueAt(0.58, 0.96);
    fade->setEndValue(1.0);
    fade->setEasingCurve(QEasingCurve::OutCubic);

    const QRect endGeo = geometry();
    QRect startGeo = endGeo.adjusted(40, 26, -40, -26);
    startGeo.translate(0, 20);
    setGeometry(startGeo);

    auto* expand = new QPropertyAnimation(this, "geometry", this);
    expand->setDuration(760);
    expand->setStartValue(startGeo);
    expand->setEndValue(endGeo);
    expand->setEasingCurve(QEasingCurve::OutBack);

    auto* grp = new QParallelAnimationGroup(this);
    grp->addAnimation(fade);
    grp->addAnimation(expand);
    grp->start(QAbstractAnimation::DeleteWhenStopped);
}

void MainWindow::animateRefreshButton() {
    if (!refreshButton_) return;

    refreshButton_->setEnabled(false);

    auto* rotate = new QVariantAnimation(this);
    rotate->setDuration(650);
    rotate->setStartValue(0);
    rotate->setEndValue(360);

    connect(rotate, &QVariantAnimation::valueChanged, this,
            [this](const QVariant& value) {
                refreshButton_->setText(QStringLiteral("↻ %1%")
                    .arg(static_cast<int>(value.toDouble() / 360.0 * 100.0)));
            });

    auto* pulse = new QPropertyAnimation(refreshButton_, "geometry", this);
    QRect g = refreshButton_->geometry();
    pulse->setDuration(260);
    pulse->setStartValue(g.adjusted(0, 0, 0, 0));
    pulse->setKeyValueAt(0.5, g.adjusted(-4, -2, 4, 2));
    pulse->setEndValue(g);

    auto* effect = new QGraphicsOpacityEffect(refreshButton_);
    refreshButton_->setGraphicsEffect(effect);

    auto* fade = new QPropertyAnimation(effect, "opacity", this);
    fade->setDuration(650);
    fade->setStartValue(1.0);
    fade->setKeyValueAt(0.45, 0.55);
    fade->setEndValue(1.0);

    connect(rotate, &QVariantAnimation::finished, this, [this]() {
        refreshButton_->setText(QStringLiteral("↻ Refresh"));
        refreshButton_->setEnabled(true);
    });

    connect(fade, &QPropertyAnimation::finished, this, [this]() {
        refreshButton_->setGraphicsEffect(nullptr);
    });

    rotate->start(QAbstractAnimation::DeleteWhenStopped);
    pulse->start(QAbstractAnimation::DeleteWhenStopped);
    fade->start(QAbstractAnimation::DeleteWhenStopped);
}

void MainWindow::maybeAutoInject(const std::vector<JavaProcess>& procs) {
    if (!loader_settings::is_native_bypass_enabled()) return;
    if (injectionInFlight_) return;

    for (const JavaProcess& jp : procs) {
        if (earlyHandled_.contains(jp.pid)) continue;
        if (!isMinecraft(jp.window_title, jp.window_class)) continue;
        earlyHandled_.insert(jp.pid);

        QString title = QString::fromWCharArray(
                jp.window_title.c_str(),
                static_cast<int>(jp.window_title.size()));
        if (title.trimmed().isEmpty()) {
            title = QStringLiteral("Minecraft (starting up)");
        }
        const QString cmdLine = QString::fromWCharArray(
                jp.command_line.c_str(),
                static_cast<int>(jp.command_line.size()));

        onInjectRequested(jp.pid, title, cmdLine);
        return;
    }
}

void MainWindow::notify(const QString& title, const QString& body) {
    ToastNotification::Kind kind = ToastNotification::Success;
    if (title.contains(QStringLiteral("fail"), Qt::CaseInsensitive) ||
        body.contains(QStringLiteral("fail"), Qt::CaseInsensitive) ||
        body.contains(QStringLiteral("error"), Qt::CaseInsensitive)) {
        kind = ToastNotification::Error;
    }
    ToastNotification::show(title, body, kind, 2800);
}

void MainWindow::refreshNow(bool animate) {
    if (animate)
        animateRefreshButton();
    auto procs = list_java_processes();
    QVector<Instance> filtered;
    filtered.reserve(procs.size());

    for (const auto& jp : procs) {
        if (!isMinecraft(jp.window_title, jp.window_class)) continue;

        Instance item;
        item.pid = jp.pid;
        const QString title = QString::fromWCharArray(jp.window_title.c_str(),
                                                       static_cast<int>(jp.window_title.size()));
        item.title = title.trimmed().isEmpty()
            ? QStringLiteral("Minecraft instance  •  PID %1").arg(jp.pid)
            : QStringLiteral("%1  •  PID %2").arg(title).arg(jp.pid);

        item.commandLine = QString::fromWCharArray(
            jp.command_line.c_str(),
            static_cast<int>(jp.command_line.size()));

        filtered.push_back(std::move(item));
    }

    list_->setInstances(filtered);

    if (loader_settings::is_native_bypass_enabled()) {
        int matching = 0;
        for (const auto& jp : procs) {
            if (isMinecraft(jp.window_title, jp.window_class)) ++matching;
        }
        status_->setText(QStringLiteral(
                "Native Bypass — %1 java process(es) seen, %2 recognised.")
                .arg((int)procs.size()).arg(matching));
    } else if (list_->count() == 0) {
        status_->setText(QStringLiteral("No Minecraft sessions detected"));
    } else if (list_->count() == 1) {
        status_->setText(QStringLiteral("1 Minecraft session is ready"));
    } else {
        status_->setText(QStringLiteral("%1 Minecraft sessions are ready").arg(list_->count()));
    }

    if (loader_settings::is_native_bypass_enabled()) {
        maybeAutoInject(procs);
    }

    if (autoInject_ && !injectionInFlight_ && !filtered.isEmpty()) {
        const Instance& first = filtered.first();
        if (first.pid != lastInjectedPid_) {
            status_->setText(QStringLiteral("Auto Injecting  •  %1").arg(first.title));
            onInjectRequested(first.pid, first.title, first.commandLine);
        }
    }
}

void MainWindow::onInjectRequested(unsigned long pid, const QString& title,
                                   const QString& commandLine) {
    if (injectionInFlight_) return;
    injectionInFlight_ = true;

    list_->setInteractive(false);
    if (timer_) timer_->stop();

    const bool early = loader_settings::is_native_bypass_enabled();

    auto* overlay = new InjectionOverlay(pid, title, commandLine, this);
    connect(overlay, &InjectionOverlay::completed, this,
            [this, pid, early](bool ok, unsigned long /*pid_arg*/) {
                if (ok) {
                    lastInjectedPid_ = pid;
                    if (pid) earlyHandled_.insert(pid);

                    if (early) {
                        notify(QStringLiteral("Nebula"),
                               QStringLiteral("Native Bypass injected (pid %1)").arg(pid));
                    } else {
                        notify(QStringLiteral("Nebula"),
                               QStringLiteral("Injected into pid %1").arg(pid));
                    }
                } else {
                    notify(QStringLiteral("Nebula"),
                           QStringLiteral("Injection failed"));
                }

                if (early) {
                    injectionInFlight_ = false;
                    list_->setInteractive(true);
                    if (timer_) timer_->start();
                    return;
                }

                if (ok) {
                    playExitThenQuit();
                } else {
                    injectionInFlight_ = false;
                    list_->setInteractive(true);
                    if (timer_) timer_->start();
                }
            });
    overlay->start();
}

void MainWindow::closeEvent(QCloseEvent* e) {
    if (exiting_) {
        QMainWindow::closeEvent(e);
        return;
    }
    e->ignore();
    playExitThenQuit();
}

void MainWindow::playExitThenQuit() {
    if (exiting_) return;
    exiting_ = true;
    if (timer_) timer_->stop();
    if (meteorAnim_) meteorAnim_->stop();

    auto* fade = new QPropertyAnimation(this, "windowOpacity", this);
    fade->setDuration(360);
    fade->setStartValue(windowOpacity());
    fade->setEndValue(0.0);
    fade->setEasingCurve(QEasingCurve::InCubic);

    const QRect startGeo = geometry();
    QRect endGeo = startGeo.adjusted(26, 16, -26, -16);
    endGeo.translate(0, 12);

    auto* shrink = new QPropertyAnimation(this, "geometry", this);
    shrink->setDuration(360);
    shrink->setStartValue(startGeo);
    shrink->setEndValue(endGeo);
    shrink->setEasingCurve(QEasingCurve::InCubic);

    auto* grp = new QParallelAnimationGroup(this);
    grp->addAnimation(fade);
    grp->addAnimation(shrink);
    connect(grp, &QAbstractAnimation::finished, qApp, &QApplication::quit);
    grp->start(QAbstractAnimation::DeleteWhenStopped);
}

void MainWindow::showInjectPage() {
    if (injectPage_)   injectPage_->setVisible(true);
    if (settingsPage_) settingsPage_->setVisible(false);
}

void MainWindow::showSettingsPage() {
    if (injectPage_)   injectPage_->setVisible(false);
    if (settingsPage_) settingsPage_->setVisible(true);
}

} // namespace loader