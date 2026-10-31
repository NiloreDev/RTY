#pragma once

#include <QWidget>
#include <QTimer>
#include <QPropertyAnimation>

class ToastNotification : public QWidget {
    Q_OBJECT
    Q_PROPERTY(qreal dotOpacity READ dotOpacity WRITE setDotOpacity)

public:
    explicit ToastNotification(const QString& title, const QString& message, QWidget* parent = nullptr);
    void showToast();

    qreal dotOpacity() const { return m_dotOpacity; }
    void setDotOpacity(qreal v) { m_dotOpacity = v; update(); }

protected:
    void paintEvent(QPaintEvent* event) override;

private:
    QString m_title;
    QString m_message;
    qreal m_dotOpacity;
    QTimer* m_lifeTimer;
    QWidget* m_parentWindow;
};