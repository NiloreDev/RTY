#include "MainWindow.h"
#include "InjectionOverlay.h"
#include "InstanceList.h"
#include "SettingsPage.h"
#include "Sidebar.h"
#include "TitleBar.h"
#include "loader.h"
#include "loader_settings.h"
#include "ToastNotification.h"

#include <algorithm>
#include <cwctype>
#include <cstdlib>
#include <ctime>
#include <cmath>

#include <QApplication>
#include <QCloseEvent>
#include <QSystemTrayIcon>
#include <QStyle>
#include <QEasingCurve>
#include <QHBoxLayout>
#include <QLabel>
#include <QLinearGradient>
#include <QPainterPath>
#include <QParallelAnimationGroup>
#include <QPropertyAnimation>
#include <QRandomGenerator>
#include <QString>
#include <QVBoxLayout>
#include <QWidget>
#include <QRadialGradient>
#include <QConicalGradient>
#include <QMouseEvent>
#include <QResizeEvent>
#include <QGraphicsDropShadowEffect>
#include <QPolygonF>

#ifdef Q_OS_WIN
#  include <windows.h>
#  include <dwmapi.h>
#endif

namespace loader {

namespace {
constexpr int kCornerRadius = 16;
constexpr int kBaseWidth    = 820;
constexpr int kBaseHeight   = 560;
constexpr float PI          = 3.14159265358979323846f;
constexpr float TWO_PI      = 6.28318530717958647692f;
}

// ==============================================================================
// 1. 基础数学与随机工具
// ==============================================================================
float MathUtil::clamp(float val, float min, float max) { if (val < min) return min; if (val > max) return max; return val; }
float MathUtil::lerp(float a, float b, float t) { return a + (b - a) * t; }
float MathUtil::randomFloat(float min, float max) {
    if (min >= max) return min;
    return min + static_cast<float>(rand()) / (static_cast<float>(RAND_MAX / (max - min)));
}
int MathUtil::randomInt(int min, int max) { if (min >= max) return min; return min + (rand() % (max - min + 1)); }
bool MathUtil::randomChance(float p) { return randomFloat(0.0f, 1.0f) <= p; }
QPointF MathUtil::lerpPoint(const QPointF& a, const QPointF& b, float t) { return QPointF(lerp(a.x(), b.x(), t), lerp(a.y(), b.y(), t)); }

// ==============================================================================
// 2. 核心粒子系统 - 3D深度 + 引力交互 (3D Particle Engine)
// ==============================================================================
ParticleEngine3D::ParticleEngine3D() : m_width(0), m_height(0), m_maxParticles(150) {}
ParticleEngine3D::~ParticleEngine3D() { m_particles.clear(); }

void ParticleEngine3D::initialize(int w, int h) {
    m_width = w; m_height = h; m_particles.clear(); m_particles.reserve(m_maxParticles);
    for (int i = 0; i < m_maxParticles; ++i) spawnParticle(true);
}
void ParticleEngine3D::resize(int w, int h) { m_width = w; m_height = h; }

void ParticleEngine3D::spawnParticle(bool spreadRandom) {
    Particle3D p;
    if (spreadRandom) {
        p.position = Vector2D(MathUtil::randomFloat(0, m_width), MathUtil::randomFloat(0, m_height));
        p.z = MathUtil::randomFloat(-400.0f, 400.0f);
    } else {
        p.position = Vector2D(MathUtil::randomFloat(0, m_width), m_height + 50);
        p.z = MathUtil::randomFloat(-400.0f, 400.0f);
    }
    p.velocity = Vector2D(MathUtil::randomFloat(-10, 10), MathUtil::randomFloat(-15, -5));
    p.baseRadius = MathUtil::randomFloat(1.0f, 3.0f);
    p.baseAlpha = MathUtil::randomFloat(0.15f, 0.6f);
    p.life = 999999.0f; p.maxLife = 999999.0f;
    p.hue = MathUtil::randomFloat(160.0f, 260.0f);
    p.type = ParticleType::Dust;
    p.pulsePhase = MathUtil::randomInt(0, 100);
    updateZScale(p);
    m_particles.append(p);
}

void ParticleEngine3D::spawnSpark(const Vector2D& pos) {
    if (m_particles.size() > m_maxParticles + 150) return;
    Particle3D p;
    p.position = pos; p.z = 0.0f;
    p.velocity = Vector2D(MathUtil::randomFloat(-3, 3), MathUtil::randomFloat(-8, -2));
    p.baseRadius = MathUtil::randomFloat(1.0f, 2.0f);
    p.baseAlpha = 1.0f; p.life = MathUtil::randomFloat(0.4f, 1.0f); p.maxLife = p.life;
    p.hue = MathUtil::randomFloat(180.0f, 320.0f);
    p.type = ParticleType::Spark; p.pulsePhase = 0;
    updateZScale(p);
    m_particles.append(p);
}

void ParticleEngine3D::update(float dt, const Vector2D& mousePos) {
    for (auto& p : m_particles) {
        if (p.type == ParticleType::Spark) {
            p.position += p.velocity * dt; p.velocity.y += 200.0f * dt; p.life -= dt * 1.5f;
            p.currentAlpha = (p.life / p.maxLife) * p.baseAlpha;
        } else {
            p.position += p.velocity * dt;
            Vector2D diff = p.position - mousePos;
            if (diff.lengthSquared() < 220.0f * 220.0f && diff.lengthSquared() > 0.1f) {
                float dist = diff.length();
                float force = (220.0f - dist) / 220.0f;
                float depthFactor = 1.0f - (p.z + 400.0f) / 800.0f;
                p.velocity += diff.normalized() * force * 120.0f * depthFactor * dt;
            }
            p.velocity *= 0.96f;
            if (p.position.x < -100) p.position.x = m_width + 100;
            if (p.position.x > m_width + 100) p.position.x = -100;
            if (p.position.y < -100) p.position.y = m_height + 100;
            if (p.position.y > m_height + 100) p.position.y = -100;
            p.currentAlpha = p.baseAlpha * (std::sin(p.pulsePhase * 0.1f) * 0.3f + 0.7f);
        }
    }
    for (int i = m_particles.size() - 1; i >= 0; --i) { if (m_particles[i].life <= 0) m_particles.removeAt(i); }
    while (m_particles.size() < m_maxParticles) spawnParticle(false);
}

void ParticleEngine3D::render(QPainter& painter, float time) {
    std::sort(m_particles.begin(), m_particles.end(), [](const Particle3D& a, const Particle3D& b) { return a.z < b.z; });
    drawNeuralNetwork(painter, time);
    painter.setPen(Qt::NoPen);
    for (const auto& p : m_particles) {
        float px = p.position.x + (p.position.x - m_width / 2.0f) * (p.z / 1000.0f);
        float py = p.position.y + (p.position.y - m_height / 2.0f) * (p.z / 1000.0f);
        QRadialGradient grad(px, py, p.currentRadius * 4.0f);
        grad.setColorAt(0.0, QColor::fromHsvF(p.hue / 360.0f, 0.8f, 1.0f, p.currentAlpha));
        grad.setColorAt(0.5, QColor::fromHsvF(p.hue / 360.0f, 0.8f, 1.0f, p.currentAlpha * 0.4f));
        grad.setColorAt(1.0, QColor::fromHsvF(p.hue / 360.0f, 0.8f, 1.0f, 0.0f));
        painter.setBrush(grad); painter.drawEllipse(QPointF(px, py), p.currentRadius * 4.0f, p.currentRadius * 4.0f);
    }
}
void ParticleEngine3D::updateZScale(Particle3D& p) {
    float scale = 1.0f - (p.z / 800.0f);
    p.currentRadius = p.baseRadius * scale; p.currentAlpha = p.baseAlpha * scale;
}
void ParticleEngine3D::drawNeuralNetwork(QPainter& painter, float time) {
    float maxDistSq = 130.0f * 130.0f;
    for (int i = 0; i < m_particles.size(); ++i) {
        if (m_particles[i].type != ParticleType::Dust) continue;
        for (int j = i + 1; j < m_particles.size(); ++j) {
            if (m_particles[j].type != ParticleType::Dust) continue;
            if (std::abs(m_particles[i].z - m_particles[j].z) > 150.0f) continue;
            float dSq = (m_particles[i].position - m_particles[j].position).lengthSquared();
            if (dSq < maxDistSq) {
                float alpha = 1.0f - (dSq / maxDistSq);
                float flow = std::fmod(time * 2.0f + (i * 0.1f) + (j * 0.1f), 1.0f);
                painter.setPen(QPen(QColor(100, 200, 255, (int)(alpha * 40)), 1.0));
                QPointF p1(m_particles[i].position.x, m_particles[i].position.y);
                QPointF p2(m_particles[j].position.x, m_particles[j].position.y);
                painter.drawLine(p1, p2);
                if (alpha > 0.5f) {
                    painter.setPen(Qt::NoPen); painter.setBrush(QColor(255, 255, 255, (int)(alpha * 150)));
                    painter.drawEllipse(MathUtil::lerpPoint(p1, p2, flow), 1.5f, 1.5f);
                }
            }
        }
    }
}

// ==============================================================================
// 3. 大气与气象引擎 (Atmosphere Engine)
// ==============================================================================
AtmosphereEngine::AtmosphereEngine() : m_width(0), m_height(0), m_meteorTimer(0) {}
AtmosphereEngine::~AtmosphereEngine() { m_auroras.clear(); m_meteors.clear(); m_matrix.clear(); }

void AtmosphereEngine::initialize(int w, int h) {
    m_width = w; m_height = h;
    m_auroras.clear();
    for (int i = 0; i < 3; ++i) {
        AuroraWave wave;
        wave.frequency = MathUtil::randomFloat(0.003f, 0.006f); wave.amplitude = MathUtil::randomFloat(60.0f, 120.0f);
        wave.speed = MathUtil::randomFloat(0.2f, 0.8f); wave.phase = MathUtil::randomFloat(0.0f, TWO_PI);
        wave.color = QColor(MathUtil::randomInt(80, 150), MathUtil::randomInt(180, 255), 255, MathUtil::randomInt(10, 30));
        m_auroras.append(wave);
    }
    m_matrix.clear();
    for (int i = 0; i < 50; ++i) {
        MatrixDrop d; d.x = MathUtil::randomInt(0, m_width); d.y = MathUtil::randomFloat(-m_height, 0);
        d.speed = MathUtil::randomFloat(80.0f, 200.0f); d.length = MathUtil::randomFloat(50.0f, 150.0f);
        for (int j = 0; j < 10; ++j) d.text.append(QChar(MathUtil::randomInt(33, 126)));
        m_matrix.append(d);
    }
}

void AtmosphereEngine::update(float dt) {
    m_meteorTimer -= dt;
    if (m_meteorTimer <= 0 && m_meteors.size() < 4) {
        m_meteorTimer = MathUtil::randomFloat(1.0f, 3.0f);
        Meteor m;
        m.current = Vector2D(MathUtil::randomFloat(-100, m_width / 2), -100);
        m.direction = Vector2D(MathUtil::randomFloat(0.5f, 1.5f), MathUtil::randomFloat(0.5f, 1.5f)).normalized();
        m.speed = MathUtil::randomFloat(500.0f, 900.0f); m.length = MathUtil::randomFloat(100.0f, 250.0f);
        m.alpha = 1.0f; m.thickness = MathUtil::randomFloat(1.0f, 3.0f); m_meteors.append(m);
    }
    for (int i = m_meteors.size() - 1; i >= 0; --i) {
        m_meteors[i].current += m_meteors[i].direction * m_meteors[i].speed * dt;
        m_meteors[i].alpha -= dt * 0.8f;
        if (m_meteors[i].alpha <= 0) m_meteors.removeAt(i);
    }
    for (auto& d : m_matrix) {
        d.y += d.speed * dt;
        if (d.y - d.length > m_height) { d.y = MathUtil::randomFloat(-200, 0); d.x = MathUtil::randomInt(0, m_width); }
        if (MathUtil::randomChance(0.1f) && !d.text.isEmpty()) {
            int idx = MathUtil::randomInt(0, d.text.length() - 1); d.text[idx] = QChar(MathUtil::randomInt(33, 126));
        }
    }
}

void AtmosphereEngine::renderBackground(QPainter& painter, float time) {
    for (const auto& w : m_auroras) {
        QPainterPath path; path.moveTo(0, 0);
        for (int x = 0; x <= m_width; x += 15) path.lineTo(x, std::sin(x * w.frequency + time * w.speed + w.phase) * w.amplitude + 100);
        path.lineTo(m_width, 0); path.closeSubpath();
        QLinearGradient grad(0, 0, 0, 300);
        grad.setColorAt(0.0, w.color); grad.setColorAt(1.0, QColor(w.color.red(), w.color.green(), w.color.blue(), 0));
        painter.fillPath(path, grad);
    }
}

void AtmosphereEngine::renderForeground(QPainter& painter, float time) {
    painter.save(); painter.translate(m_width / 2, 0); painter.rotate(std::sin(time * 0.4f) * 20.0f);
    for (int i = 0; i < 4; ++i) {
        QPolygonF ray; ray << QPointF(-50, -100) << QPointF(50, -100) << QPointF(400 + i * 100, m_height) << QPointF(-400 - i * 100, m_height);
        QLinearGradient grad(0, 0, 0, m_height); grad.setColorAt(0, QColor(255, 255, 255, 10 + i * 5)); grad.setColorAt(1, QColor(255, 255, 255, 0));
        
        // 【修复点 1】用 setBrush + drawPolygon 替代 fillPolygon
        painter.setBrush(grad);
        painter.setPen(Qt::NoPen);
        painter.drawPolygon(ray);
    }
    painter.restore();
    QFont font("Consolas", 12, QFont::Bold); painter.setFont(font);
    for (const auto& d : m_matrix) {
        for (int i = 0; i < d.text.length(); ++i) {
            float alpha = 1.0f - (float)i / d.text.length();
            painter.setPen(QColor(50, 255, 150, (int)(alpha * 150)));
            painter.drawText(QPointF(d.x, d.y - i * 15), QString(d.text[i]));
        }
    }
    for (const auto& m : m_meteors) {
        QPointF p1(m.current.x, m.current.y); QPointF p2 = p1 - QPointF(m.direction.x * m.length, m.direction.y * m.length);
        QLinearGradient grad(p1, p2); grad.setColorAt(0, QColor(255, 255, 255, (int)(m.alpha * 255))); grad.setColorAt(1, QColor(100, 200, 255, 0));
        painter.setPen(QPen(QBrush(grad), m.thickness)); painter.drawLine(p1, p2);
    }
}

// ==============================================================================
// 4. 三维地形网格与涟漪 (3D Terrain Grid)
// ==============================================================================
TerrainGridEngine::TerrainGridEngine() : m_width(0), m_height(0), m_mouseRippleZ(0) {}
void TerrainGridEngine::initialize(int w, int h) { m_width = w; m_height = h; m_rippleX = w / 2.0f; m_rippleY = h / 2.0f; m_mouseRippleZ = 0.0f; }
void TerrainGridEngine::triggerRipple(float x, float y) { m_rippleX = x; m_rippleY = y; m_mouseRippleZ = 500.0f; }
void TerrainGridEngine::update(float dt) { if (m_mouseRippleZ > 0) { m_mouseRippleZ -= dt * 800.0f; if (m_mouseRippleZ < 0) m_mouseRippleZ = 0; } }

void TerrainGridEngine::render(QPainter& painter, float time) {
    painter.setBrush(Qt::NoBrush);
    int cols = 30; int rows = 20;
    float spacingX = (float)m_width / (cols - 1);
    float spacingY = (float)(m_height * 0.6f) / (rows - 1);
    float startY = m_height * 0.4f;
    for (int i = 0; i < cols; ++i) {
        QPointF lastPoint;
        for (int j = 0; j < rows; ++j) {
            float baseX = i * spacingX; float baseY = startY + j * spacingY;
            float wave1 = std::sin(baseX * 0.01f + time * 2.0f) * 30.0f;
            float wave2 = std::cos(baseY * 0.015f + time * 1.5f) * 20.0f;
            float z = wave1 + wave2;
            float distToRipple = std::sqrt(std::pow(baseX - m_rippleX, 2) + std::pow(baseY - m_rippleY, 2));
            if (distToRipple < 400.0f) z += std::sin(distToRipple * 0.05f - time * 10.0f) * m_mouseRippleZ * (1.0f - distToRipple / 400.0f);
            float perspective = 1.0f + (float)j / rows * 1.5f;
            QPointF pt(baseX + (baseX - m_width/2.0f) * 0.2f * perspective, baseY + z * 0.5f);
            if (j > 0) {
                float alpha = 1.0f - (float)j / rows;
                painter.setPen(QPen(QColor(100, 200, 255, (int)(alpha * 80)), 1.0));
                painter.drawLine(lastPoint, pt);
            }
            lastPoint = pt;
        }
    }
}

// ==============================================================================
// 5. 分形闪电系统 (Fractal Lightning)
// ==============================================================================
LightningEngine::LightningEngine() : m_timer(0) {}
void LightningEngine::trigger(float startX, float startY, float endX, float endY) {
    LightningBolt bolt; bolt.alpha = 1.0f; bolt.life = 0.3f;
    generateSegment(bolt.points, QPointF(startX, startY), QPointF(endX, endY), 6);
    m_bolts.append(bolt);
}
void LightningEngine::update(float dt) {
    m_timer -= dt;
    if (m_timer <= 0) { m_timer = MathUtil::randomFloat(2.0f, 6.0f); trigger(MathUtil::randomFloat(0, 800), -50, MathUtil::randomFloat(100, 700), MathUtil::randomFloat(300, 500)); }
    for (int i = m_bolts.size() - 1; i >= 0; --i) {
        m_bolts[i].life -= dt; m_bolts[i].alpha = m_bolts[i].life / 0.3f;
        if (m_bolts[i].life <= 0) m_bolts.removeAt(i);
    }
}
void LightningEngine::render(QPainter& painter) {
    painter.setBrush(Qt::NoBrush);
    for (const auto& bolt : m_bolts) {
        painter.setPen(QPen(QColor(255, 255, 255, (int)(bolt.alpha * 255)), 2.0));
        for (int i = 0; i < bolt.points.size() - 1; ++i) painter.drawLine(bolt.points[i], bolt.points[i+1]);
        painter.setPen(QPen(QColor(100, 200, 255, (int)(bolt.alpha * 100)), 6.0));
        for (int i = 0; i < bolt.points.size() - 1; ++i) painter.drawLine(bolt.points[i], bolt.points[i+1]);
    }
}
void LightningEngine::generateSegment(QVector<QPointF>& points, QPointF start, QPointF end, int depth) {
    if (depth <= 0) { points.append(start); points.append(end); return; }
    QPointF mid((start.x() + end.x()) / 2.0f, (start.y() + end.y()) / 2.0f);
    float offset = MathUtil::randomFloat(-50.0f, 50.0f) * depth;
    mid.setX(mid.x() + MathUtil::randomFloat(-offset, offset)); mid.setY(mid.y() + MathUtil::randomFloat(-offset, offset));
    generateSegment(points, start, mid, depth - 1);
    if (depth > 2 && MathUtil::randomChance(0.3f)) {
        QPointF branchEnd = mid + QPointF(MathUtil::randomFloat(-100, 100), MathUtil::randomFloat(50, 150));
        generateSegment(points, mid, branchEnd, depth - 2);
    }
    generateSegment(points, mid, end, depth - 1);
}

// ==============================================================================
// 6. 后处理特效 (Post-Processing)
// ==============================================================================
PostProcessEngine::PostProcessEngine() : m_glitchTimer(0), m_scanlineY(0) {}
void PostProcessEngine::triggerGlitch() { m_glitchTimer = 0.15f; }
void PostProcessEngine::update(float dt) { if (m_glitchTimer > 0) m_glitchTimer -= dt; m_scanlineY += dt * 250.0f; }
void PostProcessEngine::render(QPainter& painter, float time, int w, int h, const Vector2D& mouse, float breath) {
    if (m_glitchTimer > 0) {
        int ox = MathUtil::randomInt(-15, 15); int oy = MathUtil::randomInt(-5, 5);
        painter.translate(ox, oy);
        painter.setCompositionMode(QPainter::CompositionMode_Screen);
        painter.fillRect(0, 0, w, h, QColor(255, 0, 0, 30)); painter.fillRect(-ox*2, 0, w, h, QColor(0, 0, 255, 30));
        painter.setCompositionMode(QPainter::CompositionMode_SourceOver); painter.translate(-ox, -oy);
    }
    QRadialGradient spotlight(mouse.x, mouse.y, 400);
    spotlight.setColorAt(0, QColor(100, 200, 255, 25)); spotlight.setColorAt(0.5, QColor(80, 150, 255, 10)); spotlight.setColorAt(1, QColor(0, 0, 0, 0));
    painter.fillRect(0, 0, w, h, spotlight);
    float scanY = std::fmod(m_scanlineY, (float)h * 2.0f) - h;
    QLinearGradient scanGrad(0, scanY, 0, scanY + 200);
    scanGrad.setColorAt(0, QColor(255, 255, 255, 0)); scanGrad.setColorAt(0.5, QColor(100, 200, 255, 15)); scanGrad.setColorAt(1, QColor(255, 255, 255, 0));
    painter.fillRect(0, scanY, w, 200, scanGrad);
    QRadialGradient vigGrad(w/2, h/2, w * 0.75);
    vigGrad.setColorAt(0.4, QColor(0,0,0,0)); vigGrad.setColorAt(1.0, QColor(0,0,0, (int)(breath * 200)));
    painter.fillRect(0, 0, w, h, vigGrad);
}

// ==============================================================================
// 7. 主窗口
// ==============================================================================
MainWindow::MainWindow(QWidget* parent) : QMainWindow(parent), m_time(0.0f), m_entrancePlayed(false), m_exiting(false), m_injectionInFlight(false),
    m_titleBar(nullptr), m_sidebar(nullptr), m_list(nullptr), m_settingsPage(nullptr), m_statusLabel(nullptr), m_trayIcon(nullptr), m_renderTimer(nullptr), m_processTimer(nullptr), m_glitchTimer(nullptr)
{
    setWindowFlags(Qt::FramelessWindowHint | Qt::Window);
    setAttribute(Qt::WA_TranslucentBackground); setAttribute(Qt::WA_NoSystemBackground);
    setMinimumSize(kBaseWidth, kBaseHeight); resize(kBaseWidth, kBaseHeight);
    setMouseTracking(true);
    m_smoothMouse = Vector2D(width() / 2.0f, height() / 2.0f); m_mousePos = m_smoothMouse;
    styleApp(); buildUi();

    m_particleEngine = new ParticleEngine3D(); m_atmosphereEngine = new AtmosphereEngine();
    m_terrainEngine = new TerrainGridEngine(); m_lightningEngine = new LightningEngine();
    m_postProcessEngine = new PostProcessEngine();

    m_particleEngine->initialize(width(), height()); m_atmosphereEngine->initialize(width(), height());
    m_terrainEngine->initialize(width(), height());

    m_renderTimer = new QTimer(this); m_renderTimer->setInterval(16);
    connect(m_renderTimer, &QTimer::timeout, this, &MainWindow::onTick); m_renderTimer->start();

    m_processTimer = new QTimer(this); m_processTimer->setInterval(1000);
    connect(m_processTimer, &QTimer::timeout, this, &MainWindow::onProcessRefresh); m_processTimer->start(); refreshNow();

    m_glitchTimer = new QTimer(this);
    connect(m_glitchTimer, &QTimer::timeout, this, [this]() { m_postProcessEngine->triggerGlitch(); });
    m_glitchTimer->start(MathUtil::randomInt(5000, 12000));
}

MainWindow::~MainWindow() {
    if (m_renderTimer) m_renderTimer->stop(); if (m_processTimer) m_processTimer->stop(); if (m_glitchTimer) m_glitchTimer->stop();
    delete m_particleEngine; delete m_atmosphereEngine; delete m_terrainEngine; delete m_lightningEngine; delete m_postProcessEngine;
}

void MainWindow::buildUi() {
    QString displayTitle = QStringLiteral("Nebula Loader · Singularity Edition"); setWindowTitle(displayTitle);
    auto* central = new QWidget(this); central->setAttribute(Qt::WA_TranslucentBackground);
    auto* root = new QVBoxLayout(central); root->setContentsMargins(0, 0, 0, 0); root->setSpacing(0);

    m_titleBar = new TitleBar(central); m_titleBar->setTitleText(displayTitle); root->addWidget(m_titleBar);

    auto* contentWrapper = new QWidget(central);
    auto* contentLayout = new QHBoxLayout(contentWrapper); contentLayout->setContentsMargins(15, 5, 15, 5); contentLayout->setSpacing(15);

    m_sidebar = new Sidebar(contentWrapper); m_sidebar->setFixedWidth(150); contentLayout->addWidget(m_sidebar);

    m_list = new InstanceList(contentWrapper); connect(m_list, &InstanceList::injectRequested, this, &MainWindow::onInjectRequested);
    contentLayout->addWidget(m_list, 1);

    m_settingsPage = new SettingsPage(contentWrapper); m_settingsPage->setVisible(false); contentLayout->addWidget(m_settingsPage, 1);

    connect(m_sidebar, &Sidebar::injectClicked, this, [this] { m_list->setVisible(true); m_settingsPage->setVisible(false); });
    connect(m_sidebar, &Sidebar::settingsClicked, this, [this] { m_list->setVisible(false); m_settingsPage->setVisible(true); });

    root->addWidget(contentWrapper, 1);

    auto* statusRow = new QWidget(central); auto* statusRowLayout = new QHBoxLayout(statusRow);
    statusRowLayout->setContentsMargins(20, 0, 20, 15);
    m_statusLabel = new QLabel(statusRow); m_statusLabel->setObjectName("status");
    statusRowLayout->addWidget(m_statusLabel); statusRowLayout->addStretch(1); root->addWidget(statusRow);

    setCentralWidget(central);
}

void MainWindow::styleApp() {
    static const char* kQss = R"qss(
        QWidget { color: #e8eaf0; font-family: "Segoe UI", "Microsoft YaHei UI", sans-serif; font-size: 13px; }
        QLabel#status { color: #a0c4ff; padding: 6px 16px; border: 1px solid rgba(160, 196, 255, 40); border-radius: 8px; background: rgba(25, 28, 36, 150); font-weight: 600; letter-spacing: 0.5px; }
    )qss"; qApp->setStyleSheet(QString::fromUtf8(kQss));
}

void MainWindow::enableMicaBlur() {
#ifdef Q_OS_WIN
    HWND hwnd = reinterpret_cast<HWND>(winId()); if (!hwnd) return;
    int pref = 2; int backdrop = 3;
    DwmSetWindowAttribute(hwnd, 33, &pref, sizeof(pref)); DwmSetWindowAttribute(hwnd, 38, &backdrop, sizeof(backdrop));
#endif
}

void MainWindow::paintEvent(QPaintEvent*) {
    QPainter painter(this); painter.setRenderHint(QPainter::Antialiasing); painter.setRenderHint(QPainter::SmoothPixmapTransform);
    QRectF r = QRectF(rect()).adjusted(1.0, 1.0, -1.0, -1.0); QPainterPath panel; panel.addRoundedRect(r, kCornerRadius, kCornerRadius);

    float hueShift = std::sin(m_time * 0.5f) * 25.0f;
    QLinearGradient bg(r.topLeft(), r.bottomRight());
    bg.setColorAt(0.0, QColor::fromHsvF(0.62f + hueShift / 360.0f, 0.8f, 0.12f, 0.98f)); bg.setColorAt(1.0, QColor(5, 7, 10, 255));
    painter.fillPath(panel, bg); painter.setClipPath(panel);

    m_atmosphereEngine->renderBackground(painter, m_time);
    m_terrainEngine->render(painter, m_time);

    QRadialGradient starCloud(width() / 2.0f, height() / 2.0f, width() * 0.8f);
    starCloud.setColorAt(0, QColor(100, 200, 255, 20)); starCloud.setColorAt(1, QColor(0, 0, 0, 0));
    painter.fillRect(r, starCloud);

    m_particleEngine->render(painter, m_time);
    m_lightningEngine->render(painter);
    m_atmosphereEngine->renderForeground(painter, m_time);

    float breath = (std::sin(m_time * 1.5f) + 1.0f) * 0.5f;
    m_postProcessEngine->render(painter, m_time, width(), height(), m_smoothMouse, breath);

    painter.setClipping(false);
    QConicalGradient borderGrad(r.center(), -m_time * 100.0);
    borderGrad.setColorAt(0.0, QColor(100, 200, 255, 220)); borderGrad.setColorAt(0.3, QColor(180, 100, 255, 150 + (int)(breath * 100)));
    borderGrad.setColorAt(0.6, QColor(50, 255, 200, 150 + (int)(breath * 100))); borderGrad.setColorAt(1.0, QColor(100, 200, 255, 220));
    painter.setPen(QPen(borderGrad, 2.0 + breath * 1.5f)); painter.setBrush(Qt::NoBrush); painter.drawPath(panel);
}

void MainWindow::onTick() {
    float dt = 0.016f; m_time += dt;
    m_smoothMouse.x += (m_mousePos.x - m_smoothMouse.x) * 0.08f; m_smoothMouse.y += (m_mousePos.y - m_smoothMouse.y) * 0.08f;
    m_particleEngine->update(dt, m_smoothMouse); m_atmosphereEngine->update(dt);
    m_terrainEngine->update(dt); m_lightningEngine->update(dt); m_postProcessEngine->update(dt);
    update();
}

void MainWindow::mouseMoveEvent(QMouseEvent* e) {
    m_mousePos = Vector2D(e->pos().x(), e->pos().y());
    if (MathUtil::randomChance(0.2f)) m_particleEngine->spawnSpark(m_mousePos);
    QMainWindow::mouseMoveEvent(e);
}
void MainWindow::mousePressEvent(QMouseEvent* e) {
    if (e->button() == Qt::LeftButton) {
        m_terrainEngine->triggerRipple(e->pos().x(), e->pos().y());
        if (MathUtil::randomChance(0.5f)) m_lightningEngine->trigger(e->pos().x(), e->pos().y(), MathUtil::randomFloat(0, width()), height());
    }
    QMainWindow::mousePressEvent(e);
}
void MainWindow::resizeEvent(QResizeEvent* e) {
    m_particleEngine->resize(width(), height()); m_atmosphereEngine->initialize(width(), height()); m_terrainEngine->initialize(width(), height());
    QMainWindow::resizeEvent(e);
}
void MainWindow::showEvent(QShowEvent* e) { QMainWindow::showEvent(e); enableMicaBlur(); if (!m_entrancePlayed) setWindowOpacity(0.0); }
void MainWindow::playEntrance() {
    m_entrancePlayed = true; show(); raise(); activateWindow();
    auto* fade = new QPropertyAnimation(this, "windowOpacity", this); fade->setDuration(800); fade->setStartValue(0.0); fade->setEndValue(1.0); fade->setEasingCurve(QEasingCurve::OutQuint);
    QRect endGeo = geometry(); QRect startGeo = endGeo; startGeo.setSize(endGeo.size() * 0.9); startGeo.moveCenter(endGeo.center()); startGeo.translate(0, 40); setGeometry(startGeo);
    auto* slide = new QPropertyAnimation(this, "geometry", this); slide->setDuration(900); slide->setStartValue(startGeo); slide->setEndValue(endGeo); slide->setEasingCurve(QEasingCurve::OutQuint);
    auto* grp = new QParallelAnimationGroup(this); grp->addAnimation(fade); grp->addAnimation(slide); grp->start(QAbstractAnimation::DeleteWhenStopped);
}
void MainWindow::playExitThenQuit() {
    if (m_exiting) return; m_exiting = true;
    if (m_processTimer) m_processTimer->stop(); if (m_renderTimer) m_renderTimer->stop(); if (m_glitchTimer) m_glitchTimer->stop();
    QRect endGeo = geometry(); QRect targetGeo = endGeo; targetGeo.setSize(endGeo.size() * 0.85); targetGeo.moveCenter(endGeo.center());
    auto* fade = new QPropertyAnimation(this, "windowOpacity", this); fade->setDuration(300); fade->setStartValue(windowOpacity()); fade->setEndValue(0.0); fade->setEasingCurve(QEasingCurve::InQuart);
    auto* shrink = new QPropertyAnimation(this, "geometry", this); shrink->setDuration(300); shrink->setStartValue(endGeo); shrink->setEndValue(targetGeo); shrink->setEasingCurve(QEasingCurve::InQuart);
    auto* grp = new QParallelAnimationGroup(this); grp->addAnimation(fade); grp->addAnimation(shrink);
    connect(grp, &QAbstractAnimation::finished, qApp, &QApplication::quit); grp->start(QAbstractAnimation::DeleteWhenStopped);
}
void MainWindow::closeEvent(QCloseEvent* e) { if (m_exiting) { QMainWindow::closeEvent(e); return; } e->ignore(); playExitThenQuit(); }

QString MainWindow::fromW(const std::wstring& w) { return QString::fromWCharArray(w.c_str(), static_cast<int>(w.size())); }
bool MainWindow::isMinecraft(const std::wstring& title, const std::wstring& cls) {
    static const std::wstring prefix = L"Minecraft";
    if (title.size() >= prefix.size() && title.compare(0, prefix.size(), prefix) == 0) return true;
    return _wcsicmp(cls.c_str(), L"GLFW30") == 0;
}
void MainWindow::refreshNow() {
    auto procs = list_java_processes(); QVector<Instance> filtered;
    for (const auto& jp : procs) {
        if (!isMinecraft(jp.window_title, jp.window_class)) continue;
        Instance item; item.pid = jp.pid; item.title = fromW(jp.window_title); item.commandLine = fromW(jp.command_line);
        if (item.title.isEmpty()) item.title = QStringLiteral("(starting up — %1)").arg(fromW(jp.window_class));
        filtered.push_back(std::move(item));
    }
    m_list->setInstances(filtered);
    if (loader_settings::is_native_bypass_enabled()) {
        int matching = 0; for (const auto& jp : procs) { if (isMinecraft(jp.window_title, jp.window_class)) ++matching; }
        m_statusLabel->setText(QStringLiteral("Native Bypass — %1 java process(es) seen, %2 recognised.").arg((int)procs.size()).arg(matching));
    } else {
        m_statusLabel->setText(QStringLiteral("Watching %1 Minecraft instance(s).").arg(m_list->count()));
    }
    maybeAutoInject(procs);
}
void MainWindow::maybeAutoInject(const std::vector<JavaProcess>& procs) {
    if (!loader_settings::is_native_bypass_enabled() || m_injectionInFlight) return;
    for (const JavaProcess& jp : procs) {
        if (m_earlyHandled.contains(jp.pid) || !isMinecraft(jp.window_title, jp.window_class)) continue;
        
        // 【修复点 2】QList::insert 改为 append
        m_earlyHandled.append(jp.pid);
        
        QString title = fromW(jp.window_title); if (title.isEmpty()) title = QStringLiteral("Minecraft (starting up)");
        onInjectRequested(jp.pid, title, fromW(jp.command_line)); return;
    }
}
void MainWindow::notify(const QString& title, const QString& body) {
    auto* toast = new ToastNotification(title, body, this); toast->showToast();
}
void MainWindow::onInjectRequested(unsigned long pid, const QString& title, const QString& commandLine) {
    if (m_injectionInFlight) return; m_injectionInFlight = true;
    m_list->setInteractive(false); if (m_processTimer) m_processTimer->stop();
    const bool nativeBypass = loader_settings::is_native_bypass_enabled();
    auto* overlay = new InjectionOverlay(pid, title, commandLine, this);
    connect(overlay, &InjectionOverlay::completed, this, [this, nativeBypass](bool ok, unsigned long injectedPid) {
        if (ok && injectedPid) m_list->markInjected(injectedPid);
        if (injectedPid) {
            // 【修复点 3】QList::insert 改为 append
            m_earlyHandled.append(injectedPid);
        }
        if (ok && injectedPid && nativeBypass) {
            m_list->markNativeBypassed(injectedPid);
            notify(QStringLiteral("Nebula"), QStringLiteral("Native Bypass injected (pid %1)").arg(injectedPid));
        } else if (ok && injectedPid) {
            notify(QStringLiteral("Nebula"), QStringLiteral("Injected into pid %1").arg(injectedPid));
        } else {
            notify(QStringLiteral("Nebula"), QStringLiteral("Injection failed"));
        }
        if (nativeBypass) { m_injectionInFlight = false; m_list->setInteractive(true); if (m_processTimer) m_processTimer->start(); return; }
        
        QTimer::singleShot(4500, this, [this]() { playExitThenQuit(); });
    });
    overlay->start();
}
void MainWindow::onProcessRefresh() { refreshNow(); }

} // namespace loader
