#pragma once

// Loader-side wiring for the HWID spoof.
//
// Maps the persisted loader settings (loader_settings, Qt-backed) onto
// hwidspoof::init(). Kept in its own translation unit so hwid_spoof.cpp itself
// stays Qt-free (it can also be compiled into the DLL if ever needed).

namespace loader {

// Read the persisted HWID settings and (re)apply them via hwidspoof::init().
// Safe to call repeatedly; used once at startup and whenever the HWID Spoof
// toggle changes.
void apply_hwid_spoof();

} // namespace loader
