#include "hwid_spoof_settings.h"

#include "hwid_spoof.h"
#include "loader_settings.h"

namespace loader {

void apply_hwid_spoof() {
    hwidspoof::Config cfg;
    cfg.enabled       = loader_settings::is_hwid_spoof_enabled();
    cfg.machine_guid  = loader_settings::hwid_custom_value(loader_settings::kKeyMachineGuid()).toStdWString();
    cfg.volume_serial = loader_settings::hwid_custom_value(loader_settings::kKeyVolumeSerial()).toStdWString();
    cfg.mac           = loader_settings::hwid_custom_value(loader_settings::kKeyMac()).toStdWString();
    cfg.computer_name = loader_settings::hwid_custom_value(loader_settings::kKeyComputerName()).toStdWString();
    cfg.product_id    = loader_settings::hwid_custom_value(loader_settings::kKeyProductId()).toStdWString();
    hwidspoof::init(cfg);
}

} // namespace loader
