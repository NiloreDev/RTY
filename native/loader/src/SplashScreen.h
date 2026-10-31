#pragma once

#include <QColor>
#include <QElapsedTimer>
#include <QPointF>
#include <QVector>
#include <QWidget>

class QTimer;

namespace loader {

class SplashScreen : public QWidget {
    Q_OBJECT

    Q_PROPERTY(qreal logoOpacity  READ logoOpacity  WRITE setLogoOpacity)
    Q_PROPERTY(qreal logoScale    READ logoScale    WRITE setLogoScale)
    Q_PROPERTY(qreal glowPulse    READ glowPulse    WRITE setGlowPulse)
    Q_PROPERTY(qreal scanProgress READ scanProgress WRITE setScanProgress)
    Q_PROPERTY(qreal ringSpread   READ ringSpread   WRITE setRingSpread)
    Q_PROPERTY(qreal textReveal   READ textReveal   WRITE setTextReveal)
    Q_PROPERTY(qreal shimmer      READ shimmer      WRITE setShimmer)
    Q_PROPERTY(qreal panelScale   READ panelScale   WRITE setPanelScale)
    Q_PROPERTY(qreal accentGlow   READ accentGlow   WRITE setAccentGlow)
    Q_PROPERTY(qreal sweepPos     READ sweepPos     WRITE setSweepPos)

public:
    explicit SplashScreen(QWidget* parent = nullptr);

    void start();

    qreal logoOpacity()  const { return logoOpacity_; }
    void  setLogoOpacity(qreal v)  { logoOpacity_  = v; update(); }

    qreal logoScale()    const { return logoScale_; }
    void  setLogoScale(qreal v)    { logoScale_    = v; update(); }

    qreal glowPulse()    const { return glowPulse_; }
    void  setGlowPulse(qreal v)    { glowPulse_    = v; update(); }

    qreal scanProgress() const { return scanProgress_; }
    void  setScanProgress(qreal v) { scanProgress_ = v; update(); }

    qreal ringSpread()   const { return ringSpread_; }
    void  setRingSpread(qreal v)   { ringSpread_   = v; update(); }

    qreal textReveal()   const { return textReveal_; }
    void  setTextReveal(qreal v)   { textReveal_   = v; update(); }

    qreal shimmer()      const { return shimmer_; }
    void  setShimmer(qreal v)      { shimmer_      = v; update(); }

    qreal panelScale()   const { return panelScale_; }
    void  setPanelScale(qreal v)   { panelScale_   = v; update(); }

    qreal accentGlow()   const { return accentGlow_; }
    void  setAccentGlow(qreal v)   { accentGlow_   = v; update(); }

    qreal sweepPos()     const { return sweepPos_; }
    void  setSweepPos(qreal v)     { sweepPos_     = v; update(); }

signals:
    void finished();

protected:
    void paintEvent(QPaintEvent* event) override;

private:
    struct Particle {
        QPointF pos;
        QPointF vel;
        qreal   radius  = 1.0;
        qreal   life    = 0.0;
        qreal   maxLife = 3.0;
        qreal   phase   = 0.0;
        QColor  color;
    };

    void tick();
    void seedParticles();
    void respawn(Particle& p);

    qreal logoOpacity_  = 0.0;
    qreal logoScale_    = 0.65;
    qreal glowPulse_    = 0.0;
    qreal scanProgress_ = 0.0;
    qreal ringSpread_   = 0.0;
    qreal textReveal_   = 0.0;
    qreal shimmer_      = -0.35;
    qreal panelScale_   = 1.0;
    qreal accentGlow_   = 0.0;
    qreal sweepPos_     = -0.35;

    qreal          time_ = 0.0;
    QElapsedTimer  clock_;
    QTimer*        frameTimer_ = nullptr;
    QVector<Particle> particles_;
};

} // namespace loader