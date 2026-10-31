#include "SettingsPage.h"

#include "PillSwitch.h"
#include "hwid_spoof_settings.h"     // ← 新增
#include "loader_settings.h"

#include <QFrame>
#include <QHBoxLayout>
#include <QLabel>
#include <QVBoxLayout>

namespace loader {

namespace {
const char* kSettingsQss = R"qss(
    QFrame#toggleRow {
        background: rgba(38, 40, 46, 0.85);
        border: 1px solid #2c2e35;
        border-radius: 8px;
    }
    QLabel#toggleName {
        color: #dfe3ea;
        font-size: 13px;
        font-weight: 600;
    }
    QLabel#toggleDesc {
        color: #868c98;
        font-size: 12px;
    }
)qss";
} // namespace

SettingsPage::SettingsPage(QWidget* parent)
        : QWidget(parent) {
    setStyleSheet(QString::fromUtf8(kSettingsQss));
    buildUi();

    // 启动时把持久化的设置一次性灌进 hwidspoof::g_config，
    // 否则拦截路径拿到的永远是最初的默认值。
    apply_hwid_spoof();
}

bool SettingsPage::hwidSpoofEnabled() const {
    return hwidSwitch_ && hwidSwitch_->isChecked();
}

QWidget* SettingsPage::makeToggleRow(const QString& name, const QString& desc,
                                     PillSwitch* sw, QWidget* parent) {
    auto* row = new QFrame(parent);
    row->setObjectName("toggleRow");
    row->setMinimumHeight(58);
    auto* rowLayout = new QHBoxLayout(row);
    rowLayout->setContentsMargins(14, 10, 14, 10);
    rowLayout->setSpacing(10);

    auto* names = new QVBoxLayout();
    auto* nameLabel = new QLabel(name, row);
    nameLabel->setObjectName("toggleName");
    auto* descLabel = new QLabel(desc, row);
    descLabel->setObjectName("toggleDesc");
    descLabel->setWordWrap(true);
    names->addWidget(nameLabel);
    names->addWidget(descLabel);

    rowLayout->addLayout(names, 1);
    rowLayout->addWidget(sw, 0, Qt::AlignVCenter);
    return row;
}

void SettingsPage::buildUi() {
    auto* col = new QVBoxLayout(this);
    col->setContentsMargins(18, 18, 18, 18);
    col->setSpacing(10);

    auto* title = new QLabel(QStringLiteral("Settings"), this);
    title->setObjectName("title");
    title->setAlignment(Qt::AlignLeft);
    title->setStyleSheet(QStringLiteral("font-size:18px;font-weight:600;color:#ffffff;"));
    col->addWidget(title);

    // ---- HWID Spoof (default OFF) ----
    hwidSwitch_ = new PillSwitch(this);
    hwidSwitch_->setChecked(loader_settings::is_hwid_spoof_enabled());
    connect(hwidSwitch_, &PillSwitch::toggled, this, [](bool on) {
        loader_settings::save_value(loader_settings::kKeyHwidSpoof(), on);
        apply_hwid_spoof();      // ← 关键：立刻把开关写进 hwidspoof::g_config
    });
    col->addWidget(makeToggleRow(
        QStringLiteral("HWID Spoof"),
        QStringLiteral("Spoofs machine fingerprint surfaces (MachineGuid, volume "
                       "serial, MAC, computer name). Default OFF."),
        hwidSwitch_, this));

    // Native Bypass
    nativeBypassSwitch_ = new PillSwitch(this);
    nativeBypassSwitch_->setChecked(loader_settings::is_native_bypass_enabled());
    connect(nativeBypassSwitch_, &PillSwitch::toggled, this, [](bool on) {
        loader_settings::save_value(loader_settings::kKeyNativeBypass(), on);
    });
    col->addWidget(makeToggleRow(
        QStringLiteral("Native Bypass"),
        QStringLiteral("Auto: as soon as a Minecraft instance shows up, restart "
                       "it suspended from its own command line, map the DLL in "
                       "before it resumes, then let it go. The game window reopens "
                       "and you rejoin the server. Clicking Inject does the same "
                       "on demand. Default OFF."),
        nativeBypassSwitch_, this));

    col->addStretch(1);
}

} // namespace loader