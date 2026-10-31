#include "hwid_wmi.h"

#include <windows.h>
#include <objbase.h>
#include <oleauto.h>        // ← 新增：VariantInit / VariantClear / SysAllocString / BSTR
#include <wbemidl.h>
#include <wbemuuid.h>       // ← 新增：CLSID_WbemLocator / IID_IWbem*
#include <psapi.h>

#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#include "spoof_values.h"

#pragma comment(lib, "ole32.lib")
#pragma comment(lib, "oleaut32.lib")
#pragma comment(lib, "psapi.lib")
#pragma comment(lib, "wbemuuid.lib")