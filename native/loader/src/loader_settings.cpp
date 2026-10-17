#include "loader_settings.h"

#include <windows.h>
#include <shlobj.h>

#include <QDir>
#include <QFile>
#include <QJsonDocument>
#include <QJsonObject>
#include <QStandardPaths>
#include <QVariantMap>

#include <cstdio>

namespace loader_settings {

namespace {

QHash<QString, QVariant> g_cache;

// Compute %APPDATA%\OpenNilore\settings.json (respects FOLDERID_RoamingAppData
// via SHGetKnownFolderPath - not the legacy CSIDL fallback so it works in
// non-roaming scenarios too).
//
// NOTE: this used to be a function-local `static const QString` that was
// initialized exactly once. If the first call happened before COM was ready
// (or before the AppData folder existed), the empty result was cached for the
// entire process lifetime — every subsequent read/write silently no-op'd.
// Now we resolve on each call and only cache on success.
QString settings_file() {
    static QString cached;
    if (!cached.isEmpty()) return cached;

    wchar_t* raw = nullptr;
    HRESULT hr = SHGetKnownFolderPath(FOLDERID_RoamingAppData,
                                      KF_FLAG_CREATE, nullptr, &raw);
    if (FAILED(hr) || !raw) return QString();
    QString base = QString::fromWCharArray(raw);
    CoTaskMemFree(raw);
    if (base.isEmpty()) return QString();

    QDir d(base);
    if (!d.mkpath(QStringLiteral("OpenNilore"))) return QString();
    cached = d.filePath(QStringLiteral("OpenNilore")) +
             QStringLiteral("/settings.json");
    return cached;
}

QJsonObject read_json() {
    const QString file = settings_file();
    if (file.isEmpty()) return {};
    QFile f(file);
    if (!f.open(QIODevice::ReadOnly)) return {};
    QByteArray data = f.readAll();
    QJsonParseError err{};
    QJsonDocument doc = QJsonDocument::fromJson(data, &err);
    if (err.error != QJsonParseError::NoError || !doc.isObject()) return {};
    return doc.object();
}

bool write_json(const QJsonObject& obj) {
    const QString file = settings_file();
    if (file.isEmpty()) return false;
    QFile f(file);
    if (!f.open(QIODevice::WriteOnly | QIODevice::Truncate)) return false;
    QJsonDocument doc(obj);
    f.write(doc.toJson(QJsonDocument::Indented));
    f.close();
    return true;
}

} // namespace

std::string settings_path() {
    return settings_file().toStdString();
}

QHash<QString, QVariant> load_all() {
    if (!g_cache.isEmpty()) return g_cache;
    QJsonObject obj = read_json();
    for (auto it = obj.begin(); it != obj.end(); ++it) {
        g_cache.insert(it.key(), it.value().toVariant());
    }
    return g_cache;
}

void save_value(const QString& key, const QVariant& value) {
    QJsonObject obj = read_json();
    obj.insert(key, QJsonValue::fromVariant(value));
    if (write_json(obj)) {
        load_all();
        g_cache.insert(key, value);
    }
}

bool is_hwid_spoof_enabled() {
    QVariant v = load_all().value(kKeyHwidSpoof());
    return v.isValid() && v.toBool();
}

bool is_syscall_thread_enabled() {
    QVariant v = load_all().value(kKeySyscallThread());
    return v.isValid() && v.toBool();
}

// Early Mode: 检测到 Minecraft java 进程一出现就立即注入(不等游戏窗口)。
// 默认 OFF。
bool is_early_mode_enabled() {
    QVariant v = load_all().value(kKeyEarlyMode());
    return v.isValid() && v.toBool();
}

QString hwid_custom_value(const QString& key) {
    QVariant v = load_all().value(key);
    return v.isValid() ? v.toString() : QString();
}

} // namespace loader_settings