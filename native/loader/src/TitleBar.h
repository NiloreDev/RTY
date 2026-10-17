#pragma once

#include <QWidget>

class QLabel;
class QToolButton;

namespace loader {

class TitleBar : public QWidget {
    Q_OBJECT
    Q_PROPERTY(qreal pulse READ pulse WRITE setPulse)

public:
    explicit TitleBar(QWidget* parent = nullptr);

    void setTitleText(const QString& text);

    qreal pulse() const { return pulse_; }
    void setPulse(qreal value);

protected:
    void mousePressEvent(QMouseEvent*) override;
    void paintEvent(QPaintEvent*) override;

private:
    QLabel*      titleLabel_ = nullptr;
    QLabel*      onlineLabel_ = nullptr;
    QToolButton* minBtn_ = nullptr;
    QToolButton* closeBtn_ = nullptr;
    qreal        pulse_ = 0.0;
};

} // namespace loader
