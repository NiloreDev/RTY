#ifndef MAINWINDOW_H
#define MAINWINDOW_H

#include <QMainWindow>
#include <QPoint>
#include <QPointF>
#include <QVector>
#include <QTimer>
#include <QRect>
#include <QColor>
#include <QPainter>
#include <QPropertyAnimation>
#include <QGraphicsDropShadowEffect>
#include <vector>
#include <cmath>

#include "loader.h"

// Qt 前置声明
class QLabel;
class QSystemTrayIcon;
class TitleBar;
class Sidebar;
class InstanceList;
class SettingsPage;
class QMouseEvent;
class QShowEvent;
class QCloseEvent;
class QPaintEvent;

namespace loader {

// ==============================================================================
// 1. 基础数据结构与数学工具
// ==============================================================================
struct Vector2D {
    float x, y;
    Vector2D() : x(0.0f), y(0.0f) {}
    Vector2D(float ix, float iy) : x(ix), y(iy) {}
    Vector2D operator+(const Vector2D& o) const { return Vector2D(x + o.x, y + o.y); }
    Vector2D operator-(const Vector2D& o) const { return Vector2D(x - o.x, y - o.y); }
    Vector2D operator*(float s) const { return Vector2D(x * s, y * s); }
    Vector2D& operator+=(const Vector2D& o) { x += o.x; y += o.y; return *this; }
    Vector2D& operator*=(float s) { x *= s; y *= s; return *this; }
    float lengthSquared() const { return x * x + y * y; }
    float length() const { return std::sqrt(lengthSquared()); }
    Vector2D normalized() const {
        float len = length();
        if (len > 0.0001f) return Vector2D(x / len, y / len);
        return Vector2D(0, 0);
    }
};

class MathUtil {
public:
    static float clamp(float val, float min, float max);
    static float lerp(float a, float b, float t);
    static float randomFloat(float min, float max);
    static int randomInt(int min, int max);
    static bool randomChance(float probability);
    static QPointF lerpPoint(const QPointF& a, const QPointF& b, float t);
};

// ==============================================================================
// 2. 核心引擎前向声明 (具体实现全在 MainWindow.cpp 中)
// ==============================================================================
class ParticleEngine3D;
class AtmosphereEngine;
class TerrainGridEngine;
class LightningEngine;
class PostProcessEngine;

// ==============================================================================
// 3. 主窗口 (MainWindow)
// ==============================================================================
class MainWindow : public QMainWindow {
    Q_OBJECT

public:
    explicit MainWindow(QWidget* parent = nullptr);
    ~MainWindow();

    void playEntrance();

protected:
    void paintEvent(QPaintEvent* e) override;
    void showEvent(QShowEvent* e) override;
    void closeEvent(QCloseEvent* e) override;
    void mouseMoveEvent(QMouseEvent* e) override;
    void mousePressEvent(QMouseEvent* e) override;
    void resizeEvent(QResizeEvent* e) override;

private slots:
    void onTick();
    void onProcessRefresh();

private:
    void buildUi();
    void styleApp();
    void enableMicaBlur();
    void refreshNow();
    void maybeAutoInject(const std::vector<JavaProcess>& procs);
    void notify(const QString& title, const QString& body);
    void onInjectRequested(unsigned long pid, const QString& title, const QString& commandLine);
    void playExitThenQuit();

    QString fromW(const std::wstring& w);
    bool isMinecraft(const std::wstring& title, const std::wstring& cls);

private:
    // 五大核心引擎指针
    ParticleEngine3D* m_particleEngine;
    AtmosphereEngine* m_atmosphereEngine;
    TerrainGridEngine* m_terrainEngine;
    LightningEngine* m_lightningEngine;
    PostProcessEngine* m_postProcessEngine;

    // 状态变量
    float m_time;
    Vector2D m_mousePos;
    Vector2D m_smoothMouse;
    bool m_entrancePlayed;
    bool m_exiting;
    bool m_injectionInFlight;
    QVector<unsigned long> m_earlyHandled;

    // UI 控件指针
    TitleBar* m_titleBar;
    Sidebar* m_sidebar;
    InstanceList* m_list;
    SettingsPage* m_settingsPage;
    QLabel* m_statusLabel;
    QSystemTrayIcon* m_trayIcon;

    // 定时器
    QTimer* m_renderTimer;
    QTimer* m_processTimer;
    QTimer* m_glitchTimer;
};

} // namespace loader

#endif // MAINWINDOW_H