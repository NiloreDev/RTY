#pragma once

#include <QWidget>
#include <QTimer>

namespace loader {

class PillSwitch : public QWidget {
    Q_OBJECT
    Q_PROPERTY(qreal knobPos READ knobPos WRITE setKnobPos)
public:
    explicit PillSwitch(QWidget* parent = nullptr);

    bool isChecked() const { return checked_; }
    void setChecked(bool on);

    qreal knobPos() const { return knobPos_; }
    void  setKnobPos(qreal v);

signals:
    void toggled(bool checked);

protected:
    void paintEvent(QPaintEvent*) override;
    void mousePressEvent(QMouseEvent*) override;

private:
    void animateTo(qreal target);
    void onTick();

    bool  checked_ = false;
    qreal knobPos_ = 0.0;
    qreal time_ = 0.0;
    QTimer* m_animTimer = nullptr;
};

} // namespace loader