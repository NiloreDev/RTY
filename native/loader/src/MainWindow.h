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

// Qt 前置声明 (放在全局)
class QLabel;
class QSystemTrayIcon;
class QMouseEvent;
class QShowEvent;
class QCloseEvent;
class QPaintEvent;

namespace loader {

// 将 UI 组件的前置声明移到 namespace loader 内部！
class TitleBar;
class Sidebar;
class InstanceList;
class SettingsPage;

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
// 2. 核心粒子系统类声明
// ==============================================================================
enum class ParticleType { Dust, Spark };

struct Particle3D {
    Vector2D position, velocity;
    float z;          
    float baseRadius, currentRadius;
    float baseAlpha, currentAlpha;
    float life, maxLife;
    float hue;
    ParticleType type;
    int pulsePhase;
};

class ParticleEngine3D {
public:
    ParticleEngine3D();
    ~ParticleEngine3D();
    void initialize(int w, int h);
    void resize(int w, int h);
    void spawnSpark(const Vector2D& pos);
    void update(float dt, const Vector2D& mousePos);
    void render(QPainter& painter, float time);
private:
    void spawnParticle(bool spreadRandom);
    void updateZScale(Particle3D& p);
    void drawNeuralNetwork(QPainter& painter, float time);
    QVector<Particle3D> m_particles;
    int m_width, m_height, m_maxParticles;
};

// ==============================================================================
// 3. 大气与气象引擎类声明
// ==============================================================================
struct AuroraWave { float frequency, amplitude, speed, phase; QColor color; };
struct Meteor { Vector2D current, direction; float speed, length, alpha, thickness; };
struct MatrixDrop { float x, y, speed, length; QString text; };

class AtmosphereEngine {
public:
    AtmosphereEngine();
    ~AtmosphereEngine();
    void initialize(int w, int h);
    void update(float dt);
    void renderBackground(QPainter& painter, float time);
    void renderForeground(QPainter& painter, float time);
private:
    QVector<AuroraWave> m_auroras;
    QVector<Meteor> m_meteors;
    QVector<MatrixDrop> m_matrix;
    int m_width, m_height;
    float m_meteorTimer;
};

// ==============================================================================
// 4. 三维地形网格类声明
// ==============================================================================
class TerrainGridEngine {
public:
    TerrainGridEngine();
    void initialize(int w, int h);
    void triggerRipple(float x, float y);
    void update(float dt);
    void render(QPainter& painter, float time);
private:
    int m_width, m_height;
    float m_rippleX, m_rippleY, m_mouseRippleZ;
};

// ==============================================================================
// 5. 分形闪电类声明
// ==============================================================================
struct LightningBolt {
    QVector<QPointF> points;
    float alpha, life;
};

class LightningEngine {
public:
    LightningEngine();
    void trigger(float startX, float startY, float endX, float endY);
    void update(float dt);
    void render(QPainter& painter);
private:
    void generateSegment(QVector<QPointF>& points, QPointF start, QPointF end, int depth);
    QVector<LightningBolt> m_bolts;
    float m_timer;
};

// ==============================================================================
// 6. 后处理特效类声明
// ==============================================================================
class PostProcessEngine {
public:
    PostProcessEngine();
    void triggerGlitch();
    void update(float dt);
    void render(QPainter& painter, float time, int w, int h, const Vector2D& mouse, float breath);
private:
    float m_glitchTimer;
    float m_scanlineY;
};

// ==============================================================================
// 7. 主窗口
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
    ParticleEngine3D* m_particleEngine;
    AtmosphereEngine* m_atmosphereEngine;
    TerrainGridEngine* m_terrainEngine;
    LightningEngine* m_lightningEngine;
    PostProcessEngine* m_postProcessEngine;

    float m_time;
    Vector2D m_mousePos;
    Vector2D m_smoothMouse;
    bool m_entrancePlayed;
    bool m_exiting;
    bool m_injectionInFlight;
    QVector<unsigned long> m_earlyHandled;

    TitleBar* m_titleBar;
    Sidebar* m_sidebar;
    InstanceList* m_list;
    SettingsPage* m_settingsPage;
    QLabel* m_statusLabel;
    QSystemTrayIcon* m_trayIcon;

    QTimer* m_renderTimer;
    QTimer* m_processTimer;
    QTimer* m_glitchTimer;
};

} // namespace loader

#endif // MAINWINDOW_H
