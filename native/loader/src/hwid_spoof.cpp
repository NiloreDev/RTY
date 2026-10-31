#include "hwid_spoof.h"

#include <windows.h>
#include <winreg.h>

#include <algorithm>
#include <cwchar>
#include <cwctype>
#include <map>
#include <mutex>
#include <random>
#include <sstream>

namespace hwidspoof {

namespace {

std::mutex g_hook_mutex;
hwid_spoof_hook_fn g_hook = nullptr;
void* g_hook_user = nullptr;

std::mutex g_config_mutex;
Config g_config;

bool ci_equal(const std::wstring& a, const wchar_t* b) {
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
    using RtlGenRandom_t = BOOLEAN(WINAPI*)(PVOID, ULONG);
    static RtlGenRandom_t pRtlGenRandom = reinterpret_cast<RtlGenRandom_t>(
        GetProcAddress(GetModuleHandleA("advapi32.dll"), "SystemFunction036"));
    if (pRtlGenRandom) {
        pRtlGenRandom(out.data(), static_cast<ULONG>(n));
        return out;
    }
    // Fallback: the previous version reconstructed a fresh std::mt19937_64
    // from the same `seed` on every call, so consecutive random_bytes(n)
    // calls returned identical byte sequences. Keep the engine alive for the
    // life of the thread instead.
    static thread_local std::mt19937_64 rng(
        (static_cast<uint64_t>(GetTickCount64())
         ^ reinterpret_cast<uintptr_t>(&g_hook))
        * 0x9E3779B97F4A7C15ULL);
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

std::wstring generate_volume_serial() { return random_hex(4); }
std::wstring generate_mac()           { return random_hex(6); }
std::wstring generate_computer_name() { return L"PC" + random_hex(2); }

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

// ---- config / init --------------------------------------------------------

namespace {
std::mutex g_cache_mutex;
std::map<std::wstring, std::wstring> g_cache;
} // namespace

bool init(const Config& cfg) {
    std::lock_guard<std::mutex> lk(g_config_mutex);
    g_config = cfg;
    // Drop auto-generated values so the "empty custom value = regenerate"
    // contract actually works after the user clears a custom value.
    {
        std::lock_guard<std::mutex> clk(g_cache_mutex);
        g_cache.clear();
    }
    return true;
}

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
bool spoof_reg_read(const wchar_t* key_path, const wchar_t* value_name,
                    wchar_t* out_buf, DWORD* inout_size) {
    (void)key_path; (void)value_name; (void)out_buf; (void)inout_size;
    return false;
}

std::wstring generate_product_id() {
    auto digits = [](size_t n) {
        std::wstring s;
        s.reserve(n);
        for (auto b : random_bytes(n)) s.push_back(static_cast<wchar_t>(L'0' + b % 10));
        return s;
    };
    return digits(5) + L"-OEM-" + digits(7) + L"-" + digits(5);
}

struct FingerprintKey {
    const wchar_t* key_suffix;
    const wchar_t* value_name;
};

const FingerprintKey kFingerprints[] = {
    { L"SOFTWARE\\MICROSOFT\\CRYPTOGRAPHY",   L"MachineGuid" },
    { L"WINDOWS NT\\CURRENTVERSION",          L"ProductId" },
    { L"CONTROL\\COMPUTERNAME\\COMPUTERNAME", L"ComputerName" },
    { nullptr, nullptr },
};

bool ci_ends_with(const std::wstring& s, const wchar_t* suffix) {
    size_t n = std::wcslen(suffix);
    if (s.size() < n) return false;
    for (size_t i = 0; i < n; ++i) {
        if (std::towlower(s[s.size() - n + i]) != std::towlower(suffix[i])) return false;
    }
    return true;
}

std::wstring cached(const wchar_t* name, std::wstring (*gen)()) {
    std::lock_guard<std::mutex> lk(g_cache_mutex);
    auto it = g_cache.find(name);
    if (it == g_cache.end()) {
        it = g_cache.emplace(name, gen()).first;
    }
    return it->second;
}

} // namespace

// 先看 g_config.enabled；自定义值非空时优先使用。
bool intercept(const wchar_t* key, const wchar_t* value, std::wstring& out_spoof) {
    if (!value) return false;

    Config cfg;
    {
        std::lock_guard<std::mutex> lk(g_config_mutex);
        cfg = g_config;
    }
    if (!cfg.enabled) return false;   // ← 开关真正生效

    std::wstring keyStr = key ? key : L"";
    for (const FingerprintKey* f = kFingerprints; f->value_name; ++f) {
        if (!ci_equal(keyStr, f->key_suffix) && !ci_ends_with(keyStr, f->key_suffix)) continue;
        if (!ci_equal(value, f->value_name)) continue;

        if (ci_equal(value, L"MachineGuid")) {
            out_spoof = cfg.machine_guid.empty()
                ? cached(L"MachineGuid", generate_machine_guid)
                : cfg.machine_guid;
        } else if (ci_equal(value, L"ProductId")) {
            out_spoof = cfg.product_id.empty()
                ? cached(L"ProductId", generate_product_id)
                : cfg.product_id;
        } else {
            out_spoof = cfg.computer_name.empty()
                ? cached(L"ComputerName", generate_computer_name)
                : cfg.computer_name;
        }
        return true;
    }
    return false;
}

const char* version() { return "hwid-spoof 0.1"; }

} // namespace hwidspoof