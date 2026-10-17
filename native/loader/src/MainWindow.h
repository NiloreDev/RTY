#pragma once

#include <QMainWindow>
#include <QPointF>
#include <QSet>
#include <QVariantAnimation>

#include "loader.h"        // struct JavaProcess

class QLabel;
class QPushButton;
class QSystemTrayIcon;
class QTimer;

namespace loader {

class TitleBar;
class InstanceList;
class Sidebar;
class SettingsPage;

class MainWindow : public QMainWindow {
    Q_OBJECT

public:
    explicit MainWindow(QWidget* parent = nullptr);

    void playEntrance();
    void playExitThenQuit();

private slots:
    void refreshNow(bool animate = false);
    void onInjectRequested(unsigned long pid, const QString& title,
                           const QString& commandLine);

    // 侧边栏页面切换
    void showInjectPage();
    void showSettingsPage();

protected:
    void paintEvent(QPaintEvent*) override;
    void showEvent(QShowEvent*) override;
    void closeEvent(QCloseEvent*) override;

private:
    void buildUi();
    void styleApp();
    void enableWin11RoundedCorners();
    void animateRefreshButton();
    void scheduleNextMeteor();
    void launchMeteor();

    // Early Mode 自动注入: 检测到尚未处理的 Minecraft java 进程就立刻注入。
    // 驱动信号来自原始 java 进程列表(命令行), 而不是窗口列表 —— 窗口要等
    // Forge/mod 加载完才出来, 那已经太晚。
    void maybeAutoInject(const std::vector<JavaProcess>& procs);

    // 托盘气泡: overlay 只活几秒, 没有这个用户容易错过注入结果。
    void notify(const QString& title, const QString& body);

    TitleBar*     titleBar_ = nullptr;
    InstanceList* list_     = nullptr;
    QTimer*       timer_    = nullptr;
    QLabel*       status_   = nullptr;
    QLabel*       hint_     = nullptr;
    QPushButton*  refreshButton_    = nullptr;
    QPushButton*  autoInjectButton_ = nullptr;

    // 侧边栏 + 页面
    Sidebar*      sidebar_      = nullptr;
    SettingsPage* settingsPage_ = nullptr;
    QWidget*      injectPage_   = nullptr;

    qreal backgroundPhase_ = 0.0;

    bool          autoInject_       = false;
    unsigned long lastInjectedPid_  = 0;
    bool entrancePlayed_    = false;
    bool injectionInFlight_ = false;
    bool exiting_           = false;

    // Early Mode: 已经处理过的 pid (含我们自己重启产生的), 防止自喂。
    QSet<unsigned long> earlyHandled_;
    QSystemTrayIcon*    tray_ = nullptr;

    // 流星状态
    QVariantAnimation* meteorAnim_ = nullptr;
    qreal              meteorProgress_ = -1.0;
    QPointF            meteorStart_;
    QPointF            meteorEnd_;

    // 黑洞吸积盘旋转相位
    qreal blackHoleSpin_ = 0.0;
};

} // namespace loader