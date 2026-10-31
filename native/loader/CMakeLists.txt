# Qt-side toggles: AUTOMOC for our Q_OBJECT classes, AUTORCC for any .qrc.
set(CMAKE_AUTOMOC ON)
set(CMAKE_AUTORCC ON)
set(CMAKE_AUTOUIC ON)

find_package(Qt6 6.4 COMPONENTS Widgets REQUIRED)

# Stage the freshly-built OpenZen.dll into the loader's binary dir so rc.exe
# can pick it up as an RCDATA resource (embedded in the .exe).
set(EMBED_DIR "${CMAKE_CURRENT_BINARY_DIR}/embedded_dll")
add_custom_command(
    OUTPUT  "${EMBED_DIR}/OpenZen.dll"
    COMMAND ${CMAKE_COMMAND} -E make_directory "${EMBED_DIR}"
    COMMAND ${CMAKE_COMMAND} -E copy $<TARGET_FILE:OpenZen> "${EMBED_DIR}/OpenZen.dll"
    DEPENDS OpenZen
    COMMENT "Staging OpenZen.dll into loader resources"
)
add_custom_target(stage_dll_for_loader DEPENDS "${EMBED_DIR}/OpenZen.dll")

qt_add_executable(OpenNiloreLoader WIN32
    src/main.cpp
    src/autoquit.cpp
    src/autoquit.h
    src/MainWindow.cpp
    src/MainWindow.h
    src/SplashScreen.cpp
    src/SplashScreen.h
    src/TitleBar.cpp
    src/TitleBar.h
    src/InstanceList.cpp
    src/InstanceList.h
    src/InstanceRow.cpp
    src/InstanceRow.h
    src/InjectionOverlay.cpp
    src/InjectionOverlay.h
    src/Sidebar.cpp
    src/Sidebar.h
    src/SettingsPage.cpp
    src/SettingsPage.h
    src/PillSwitch.cpp
    src/PillSwitch.h

    src/ToastNotification.cpp
    src/ToastNotification.h

    src/loader_settings.cpp
    src/loader_settings.h
    src/hwid_spoof.cpp
    src/hwid_spoof.h

    src/hwid_spoof_settings.cpp
    src/hwid_spoof_settings.h
    src/hwid_wmi.cpp
    src/hwid_wmi.h

    src/spoof_values.cpp
    src/spoof_values.h

    src/process_list.cpp
    src/injector.cpp
    src/early_inject.cpp
    src/early_inject.h
    src/manual_map.cpp

    src/syscalls.cpp
    src/syscalls.h

    src/embedded_dll.cpp
    src/window_title.cpp
    res/loader.rc
)

add_dependencies(OpenNiloreLoader stage_dll_for_loader)

target_include_directories(OpenNiloreLoader PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/src
    ${CMAKE_CURRENT_SOURCE_DIR}/res
)

# Tell rc.exe to look in EMBED_DIR (where OpenZen.dll was staged) when
# resolving the RCDATA "OpenZen.dll" reference in loader.rc.
# NOTE: COMPILE_FLAGS 是把字符串原样拼进命令行。如果 EMBED_DIR 含空格，
# 双引号能挡一次，但 CMake 拼接时仍可能把路径切碎。若你的构建路径有
# 空格，改成 INCLUDE_DIRECTORIES 属性更稳（CMake 3.6+ 对 .rc 也支持）。
set_source_files_properties(res/loader.rc PROPERTIES
    COMPILE_FLAGS "/I\"${EMBED_DIR}\""
)

target_link_libraries(OpenNiloreLoader PRIVATE
    Qt6::Widgets
    psapi
    shlwapi
    user32
    dwmapi
    ole32
    oleaut32
    wbemuuid

    # --- 显式补全（MSVC 默认库已含，但显式声明跨工具链更安全） ---
    # shell32   : SHGetKnownFolderPath          (loader_settings.cpp)
    # advapi32  : RegOpenKeyExW / RegQueryValueExW / RegCloseKey (hwid_spoof.cpp)
    shell32
    advapi32
)

if(DEFINED OPENZEN_BUILD_REVISION AND NOT OPENZEN_BUILD_REVISION STREQUAL "")
    target_compile_definitions(OpenNiloreLoader PRIVATE
        OPENZEN_BUILD_REVISION="${OPENZEN_BUILD_REVISION}")
endif()

target_compile_definitions(OpenNiloreLoader PRIVATE NOMINMAX)

set_target_properties(OpenNiloreLoader PROPERTIES
    OUTPUT_NAME "OpenNiloreLoader"
)

if(MSVC)
    # requireAdministrator: lets the loader OpenProcess / VirtualAllocEx /
    # CreateRemoteThread against javaw.exe even when the launcher was
    # itself started elevated. UAC consent prompts once on launch.
    #
    # 用 target_link_options 替代 LINK_FLAGS：LINK_FLAGS 是字符串，里面有
    # 引号（MANIFESTUAC 值）时容易和 CMake 的命令行拼接规则打架。
    target_link_options(OpenNiloreLoader PRIVATE
        "/SUBSYSTEM:WINDOWS"
        "/MANIFESTUAC:level='requireAdministrator' uiAccess='false'"
    )
endif()