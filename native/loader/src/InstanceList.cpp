// =====================================================================
//  InstanceList.cpp
// =====================================================================
#include "InstanceList.h"

#include "InstanceRow.h"
#include "loader.h"

#include <QEasingCurve>
#include <QGraphicsOpacityEffect>
#include <QLabel>
#include <QPropertyAnimation>
#include <QPushButton>
#include <QScrollArea>
#include <QSet>
#include <QVBoxLayout>

namespace loader {

InstanceList::InstanceList(QWidget* parent)
        : QWidget(parent) {
    auto* root = new QVBoxLayout(this);
    root->setContentsMargins(0, 0, 0, 0);
    root->setSpacing(0);

    scroll_ = new QScrollArea(this);
    scroll_->setFrameShape(QFrame::NoFrame);
    scroll_->setWidgetResizable(true);
    scroll_->setHorizontalScrollBarPolicy(Qt::ScrollBarAlwaysOff);
    scroll_->setVerticalScrollBarPolicy(Qt::ScrollBarAsNeeded);
    scroll_->setStyleSheet(QStringLiteral(
        "QScrollArea { background: transparent; border: none; }"

        "QScrollBar:vertical {"
        "  background: transparent;"
        "  width: 8px;"
        "  margin: 4px 1px;"
        "}"
        "QScrollBar::handle:vertical {"
        "  background: qlineargradient(x1:0, y1:0, x2:1, y2:0,"
        "      stop:0 rgba(178, 148, 255, 150),"
        "      stop:1 rgba(255, 147, 204, 120));"
        "  border-radius: 4px;"
        "  min-height: 28px;"
        "}"
        "QScrollBar::handle:vertical:hover {"
        "  background: qlineargradient(x1:0, y1:0, x2:1, y2:0,"
        "      stop:0 rgba(200, 170, 255, 210),"
        "      stop:1 rgba(255, 170, 220, 190));"
        "}"
        "QScrollBar::add-line:vertical,"
        "QScrollBar::sub-line:vertical { height: 0; }"
        "QScrollBar::add-page:vertical,"
        "QScrollBar::sub-page:vertical { background: transparent; }"));

    container_ = new QWidget(scroll_);
    container_->setObjectName("instanceContainer");
    container_->setAttribute(Qt::WA_TranslucentBackground);
    container_->setStyleSheet(QStringLiteral(
        "#instanceContainer { background: transparent; }"));

    containerLayout_ = new QVBoxLayout(container_);
    containerLayout_->setContentsMargins(2, 2, 2, 2);
    containerLayout_->setSpacing(0);

    // 支持环境说明 —— 换成与卡片同系的浅紫文字
    note_ = new QLabel(
        QStringLiteral("Minecraft 1.20.1 · Forge 47.4.20 · JDK 17"),
        container_);
    note_->setAlignment(Qt::AlignLeft);
    note_->setStyleSheet(QStringLiteral(
        "color: rgba(185, 168, 220, 170);"
        "font-size: 11px;"
        "letter-spacing: 0.6px;"
        "padding: 0 10px 10px 10px;"));
    containerLayout_->addWidget(note_);

    // 空状态文字 —— 换成更柔和的浅紫，并加呼吸脉冲
    emptyLabel_ = new QLabel(
        QStringLiteral("No Minecraft instances detected.\n"
                       "Start the game and it will show up here."),
        container_);
    emptyLabel_->setAlignment(Qt::AlignCenter);
    emptyLabel_->setStyleSheet(QStringLiteral(
        "color: rgba(190, 178, 220, 200);"
        "font-size: 12px;"
        "font-style: italic;"
        "letter-spacing: 0.4px;"
        "padding: 40px 12px;"));
    containerLayout_->addWidget(emptyLabel_);

    // 空状态呼吸动画：透明度在 0.5 ↔ 1.0 之间缓慢循环
    {
        auto* effect = new QGraphicsOpacityEffect(emptyLabel_);
        effect->setOpacity(1.0);
        emptyLabel_->setGraphicsEffect(effect);

        auto* pulse = new QPropertyAnimation(effect, "opacity", this);
        pulse->setDuration(2400);
        pulse->setStartValue(0.5);
        pulse->setKeyValueAt(0.5, 1.0);
        pulse->setEndValue(0.5);
        pulse->setEasingCurve(QEasingCurve::InOutSine);
        pulse->setLoopCount(-1);
        pulse->start();
    }

    containerLayout_->addStretch(1);

    scroll_->setWidget(container_);
    root->addWidget(scroll_);

    updateEmptyState();
}

void InstanceList::setInstances(const QVector<Instance>& list) {
    // 计算传入的 pid 集合
    QSet<unsigned long> incoming;
    incoming.reserve(list.size());
    for (const auto& it : list) incoming.insert(it.pid);

    // 移除已消失的行
    QList<unsigned long> toRemove;
    toRemove.reserve(rows_.size());
    for (auto it = rows_.constBegin(); it != rows_.constEnd(); ++it) {
        if (!incoming.contains(it.key())) toRemove.push_back(it.key());
    }
    for (auto pid : toRemove) {
        InstanceRow* r = rows_.take(pid);
        containerLayout_->removeWidget(r);
        r->deleteLater();
    }

    // 索引 0 是 note，所以行从 1 开始
    for (int i = 0; i < list.size(); ++i) {
        const auto& it = list[i];
        InstanceRow* r = rows_.value(it.pid, nullptr);
        if (!r) {
            r = new InstanceRow(it.pid, it.title, it.commandLine, container_);
            connect(r, &InstanceRow::injectClicked,
                    this, &InstanceList::injectRequested);
            rows_.insert(it.pid, r);
            containerLayout_->insertWidget(i + 1, r);
            r->playEntrance();
        } else {
            r->updateTitle(it.title);
            int currentIndex = containerLayout_->indexOf(r);
            if (currentIndex != i + 1) {
                containerLayout_->removeWidget(r);
                containerLayout_->insertWidget(i + 1, r);
            }
        }
    }

    setInteractive(interactive_);
    updateEmptyState();
}

void InstanceList::setInteractive(bool on) {
    interactive_ = on;
    for (auto* r : rows_) {
        if (auto* btn = r->findChild<QPushButton*>()) {
            btn->setEnabled(on);
        }
    }
}

void InstanceList::markInjected(unsigned long pid) {
    if (auto* r = rows_.value(pid, nullptr)) {
        r->flashHighlight();
    }
}

Instance InstanceList::instanceForPid(unsigned long pid) const {
    Instance out;
    out.pid = 0;
    if (auto* r = rows_.value(pid, nullptr)) {
        out.pid = r->pid();
        out.title = r->title();
        out.commandLine = r->commandLine();
    }
    return out;
}

void InstanceList::updateEmptyState() {
    const bool empty = rows_.isEmpty();
    emptyLabel_->setVisible(empty);
}

} // namespace loader