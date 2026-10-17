#include "hwid_spoof.h"

#include <windows.h>
#include <winreg.h>

#include <algorithm>
#include <cwctype>
#include <mutex>
#include <random>
#include <sstream>

namespace hwidspoof {

namespace {

std::mutex g_hook_mutex;
hwid_spoof_hook_fn g_hook = nullptr;
void* g_hook_user = nullptr;

Config g_cfg;
bool g_enabled = false;
std::wstring g_machine_guid;
std::wstring g_volume_serial;
std::wstring g_mac;
std::wstring g_computer_name;

bool ci_equal(const std::wstring& a, const wchar_t* b) {
    if (!b) return false;
    size_t n = std::wcslen(b);
    if (a.size() != n) return false;
    for (size_t i = 0; i < n; ++i)
        if (std::towlower(a[i]) != std::towlower(b[i])) return false;
    return true;
}

} // namespace

// ---- randomness -----------------------------------------------------------

std::vector<uint8_t> random_bytes(size_t n) {
    std::vector<uint8_t> out(n);
    if (n == 0) return out;

    // Prefer Windows CNG (RtlGenRandom): no seeding, no state, crypto-grade.
    using RtlGenRandom_t = BOOLEAN(WINAPI*)(PVOID, ULONG);
    static RtlGenRandom_t pRtlGenRandom = reinterpret_cast<RtlGenRandom_t>(
        GetProcAddress(GetModuleHandleA("advapi32.dll"), "SystemFunction036"));
    if (pRtlGenRandom) {
        pRtlGenRandom(out.data(), static_cast<ULONG>(n));
        return out;
    }

    // Fallback: std::mt19937_64 seeded from a *fresh* random_device each
    // call. Previously the seed was `static`, which meant every subsequent
    // call produced the identical byte stream — i.e. the "random" HWID was
    // the same value every time the fallback path was taken.
    static std::random_device rd;
    std::mt19937_64 rng(
        (static_cast<uint64_t>(rd()) << 32) ^ rd() ^
        (static_cast<uint64_t>(GetTickCount64()) << 16) ^
        reinterpret_cast<uintptr_t>(&g_hook));
    for (size_t i = 0; i < n; ++i)
        out[i] = static_cast<uint8_t>(rng() & 0xFF);
    return out;
}

std::wstring random_hex(size_t bytes) {
    static const wchar_t kHex[] = L"0123456789ABCDEF";
    auto b = random_bytes(bytes);
    std::wstring s;
    s.reserve(bytes * 2);
    for (uint8_t byte : b) {
        s.push_back(kHex[byte >> 4]);
        s.push_back(kHex[byte & 0xF]);
    }
    return s;
}

std::wstring generate_machine_guid() {
    auto b = random_bytes(16);
    static const wchar_t* kHex = L"0123456789ABCDEF";
    auto hex = [&](size_t from, size_t count) {
        std::wstring s;
        for (size_t i = 0; i < count; ++i) {
            uint8_t v = b[from + i];
            s.push_back(kHex[v >> 4]);
            s.push_back(kHex[v & 0xF]);
        }
        return s;
    };
    return L"{" + hex(0, 4) + L"-" + hex(4, 2) + L"-" + hex(6, 2) + L"-"
           + hex(8, 2) + L"-" + hex(10, 6) + L"}";
}

std::wstring generate_volume_serial() {
    return random_hex(4);
}

std::wstring generate_mac() {
    return random_hex(6);
}

std::wstring generate_computer_name() {
    return L"PC" + random_hex(2);
}

// ---- classic surfaces -----------------------------------------------------

bool read_machine_guid(std::wstring& out) {
    HKEY key = nullptr;
    if (RegOpenKeyExW(HKEY_LOCAL_MACHINE,
                      L"SOFTWARE\\Microsoft\\Cryptography",
                      0, KEY_READ | KEY_WOW64_64KEY, &key) != ERROR_SUCCESS)
        return false;
    wchar_t buf[64] = {0};
    DWORD size = sizeof buf;
    LONG rc = RegQueryValueExW(key, L"MachineGuid", nullptr, nullptr,
                               reinterpret_cast<LPBYTE>(buf), &size);
    RegCloseKey(key);
    if (rc != ERROR_SUCCESS) return false;
    out = buf;
    return !out.empty();
}

bool get_volume_serial(std::wstring& out) {
    wchar_t root[MAX_PATH] = {0};
    if (GetWindowsDirectoryW(root, MAX_PATH) == 0) return false;
    std::wstring rootStr(root);
    size_t c = rootStr.find(L":\\");
    if (c == std::wstring::npos) return false;
    rootStr.resize(c + 2);
    DWORD serial = 0;
    if (!GetVolumeInformationW(rootStr.c_str(), nullptr, 0,
                               &serial, nullptr, nullptr, nullptr, 0))
        return false;
    wchar_t buf[16];
    swprintf(buf, 16, L"%08lX", (unsigned long)serial);
    out = buf;
    return true;
}

// ---- init -----------------------------------------------------------------

bool init(const Config& cfg) {
    std::lock_guard<std::mutex> lk(g_hook_mutex);
    g_cfg = cfg;
    g_enabled = cfg.enabled;
    g_machine_guid  = cfg.machine_guid.empty()  ? generate_machine_guid()  : cfg.machine_guid;
    g_volume_serial = cfg.volume_serial.empty() ? generate_volume_serial() : cfg.volume_serial;
    g_mac           = cfg.mac.empty()           ? generate_mac()           : cfg.mac;
    g_computer_name = cfg.computer_name.empty() ? generate_computer_name() : cfg.computer_name;
    return g_enabled;
}

bool enabled() { return g_enabled; }
const std::wstring& machine_guid()  { return g_machine_guid; }
const std::wstring& volume_serial() { return g_volume_serial; }
const std::wstring& mac()           { return g_mac; }
const std::wstring& computer_name() { return g_computer_name; }

// ---- hook plumbing --------------------------------------------------------

void set_spoof_hook(hwid_spoof_hook_fn fn, void* user) {
    std::lock_guard<std::mutex> lk(g_hook_mutex);
    g_hook = fn;
    g_hook_user = user;
}

// ---- native-path utilities (loader side) ----------------------------------

bool patch_remote_computer_name(DWORD pid, const std::wstring& name) {
    (void)pid; (void)name;
    return false;
}

bool patch_remote_machine_guid(DWORD pid, const std::wstring& guid) {
    (void)pid; (void)guid;
    return false;
}

namespace {
const wchar_t* kFingerprintKeys[] = {
    L"HKLM\\SOFTWARE\\Microsoft\\Cryptography\\MachineGuid",
    L"HKLM\\Software\\Microsoft\\Windows NT\\CurrentVersion\\ProductId",
    L"HKLM\\SYSTEM\\CurrentControlSet\\Control\\ComputerName\\ComputerName\\ComputerName",
    nullptr,
};
} // namespace

bool intercept(const wchar_t* key, const wchar_t* value, std::wstring& out_spoof) {
    // 防御：ci_equal 内部会构造 std::wstring，nullptr 是 UB。
    if (!key || !value) return false;

    // 与 init() 同步：init 可被设置页随时重入，读 g_* 必须持锁。
    std::lock_guard<std::mutex> lk(g_hook_mutex);

    // 关闭 spoof 时不该拦截任何读取，否则关掉开关反而更容易被检测。
    if (!g_enabled) return false;

    for (const wchar_t* const* k = kFingerprintKeys; *k; ++k) {
        if (!ci_equal(key, *k)) continue;

        // 键路径命中，再看 value 名。返回的都是 init() 时生成/自定义的
        // 稳定值，绝不在此处重新生成。
        if (ci_equal(value, L"MachineGuid")) {
            if (g_machine_guid.empty()) return false;   // 未初始化：放行原路径
            out_spoof = g_machine_guid;
            return true;
        }
        if (ci_equal(value, L"VolumeSerialNumber")) {
            if (g_volume_serial.empty()) return false;
            out_spoof = g_volume_serial;
            return true;
        }
        if (ci_equal(value, L"ComputerName")) {
            if (g_computer_name.empty()) return false;
            out_spoof = g_computer_name;
            return true;
        }
        if (ci_equal(value, L"ProductId")) {
            // ProductId 是可选字段：用户没填就不 spoof，避免把有效值抹成空。
            if (g_cfg.product_id.empty()) return false;
            out_spoof = g_cfg.product_id;
            return true;
        }

        // 路径命中但没有对应分支（例如以后又加了条目），保守放行。
        return false;
    }
    return false;
}

const char* version() { return "hwid-spoof 0.1"; }

} // namespace hwidspoof