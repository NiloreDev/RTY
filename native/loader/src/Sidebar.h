#pragma once

#include <QWidget>
#include <QPushButton>
#include <QTimer>
#include <QPropertyAnimation>

class QButtonGroup;
class QVBoxLayout;

namespace loader {

class SidebarButton : public QPushButton {
    Q_OBJECT
    Q_PROPERTY(qreal glowProgress READ glowProgress WRITE setGlowProgress)
    Q_PROPERTY(qreal hoverProgress READ hoverProgress WRITE setHoverProgress)

public:
    SidebarButton(const QString& text, bool isSettings, QWidget* parent = nullptr);

    qreal glowProgress() const { return m_glowProgress; }
    void setGlowProgress(qreal v) { m_glowProgress = v; update(); }

    qreal hoverProgress() const { return m_hoverProgress; }
    void setHoverProgress(qreal v) { m_hoverProgress = v; update(); }

protected:
    void paintEvent(QPaintEvent* e) override;
    void enterEvent(QEnterEvent* e) override;
    void leaveEvent(QEvent* e) override;
    void checkStateSet() override;

private:
    void animateGlow(bool on);
    void animateHover(bool on);
    void drawIcon(QPainter& p, const QRectF& rect, const QColor& color);

    bool m_isSettings;
    qreal m_glowProgress = 0.0;
    qreal m_hoverProgress = 0.0;
    QPropertyAnimation* m_glowAnim = nullptr;
    QPropertyAnimation* m_hoverAnim = nullptr;
};

class Sidebar : public QWidget {
    Q_OBJECT
public:
    explicit Sidebar(QWidget* parent = nullptr);

signals:
    void injectClicked();
    void settingsClicked();

protected:
    void paintEvent(QPaintEvent* event) override;

private:
    void buildUi();
    void tickAnim();

    QButtonGroup* group_ = nullptr;
    QTimer* m_animTimer = nullptr;
    qreal m_time = 0.0;
};

} // namespace loader