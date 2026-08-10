@file:Suppress(
    "UNUSED_PARAMETER",
    "UNUSED_VARIABLE",
    "UNNECESSARY_NOT_NULL_ASSERTION",
    "DEPRECATION",
    "NAME_SHADOWING",
    "FunctionName",
    "PrivatePropertyName",
    "SpellCheckingInspection",
    "RedundantUnitReturnType",
    "RemoveRedundantQualifierName",
    "OPT_IN_USAGE",
    "unused",
    "UnusedImport"
)

package com.suseoaa.locationspoofer.xposed.hooks.network

import com.suseoaa.locationspoofer.xposed.LocationHooker
import com.suseoaa.locationspoofer.xposed.utils.*
import com.suseoaa.locationspoofer.xposed.hooks.*
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Array as ReflectArray
import kotlin.math.*
import io.github.libxposed.api.*

/**
 * WiFi 环境伪造模块 (Wifi Environment Hooker)
 * 
 * 上下文:
 * 除了基站和 GPS，地图 SDK 高度依赖周围的 Wi-Fi 路由器 (BSSID) 来进行精准的室内定位。
 * 
 * 作用:
 * 拦截 `WifiManager.getScanResults()` 和相关接口。
 * 由于无法凭空捏造该地点的真实 WiFi MAC 地址，这里的策略是直接清空扫描结果，
 * 迫使地图 SDK 降级使用纯 GPS 和我们伪造的基站数据进行定位，防止出现回拉或弹跳现象。
 */

internal fun LocationHooker.hookWifiEnvironment(
    classLoader: ClassLoader,
    isCoreSystemProcess: Boolean = false
) {

    // ── 1. WifiInfo getter Hook ──
    try {
        val wifiInfoMethods = listOf(
            "getBSSID", "getMacAddress", "getSSID", "getNetworkId",
            "getRssi", "getLinkSpeed", "getFrequency", "getIpAddress"
        )
        for (method in wifiInfoMethods) {
            try {
                XposedHelpers.hookMethod(
                    "android.net.wifi.WifiInfo", classLoader, method
                ) { chain, method ->
                    val config =
                        readConfig() ?: return@hookMethod chain.proceed(chain.args.toTypedArray())
                    if (!config.optBoolean(
                            "active",
                            false
                        )
                    ) return@hookMethod chain.proceed(chain.args.toTypedArray())
                    val mockWifi = config.optBoolean("mock_wifi", true)
                    val wifiObj = if (mockWifi) config.optJSONObject("wifi_json") else null
                    val isConnected = wifiObj?.optBoolean("isConnected", false) ?: false
                    val connectedWifi =
                        if (isConnected) wifiObj?.optJSONObject("connectedWifi") else null

                    when (method.name) {
                        "getBSSID" -> return@hookMethod connectedWifi?.optString("bssid")
                            ?: "02:00:00:00:00:00"

                        "getMacAddress" -> return@hookMethod connectedWifi?.optString("macAddress")
                            ?: "02:00:00:00:00:00"

                        "getSSID" -> {
                            val ssidVal = connectedWifi?.optString("ssid", "") ?: ""
                            val finalSsid =
                                if (ssidVal.isEmpty() || ssidVal == "<unknown ssid>") "" else ssidVal
                            return@hookMethod if (finalSsid.isEmpty()) "<unknown ssid>" else "\"$finalSsid\""
                        }

                        "getNetworkId" -> return@hookMethod connectedWifi?.optInt("networkId", -1)
                            ?: -1

                        "getRssi" -> return@hookMethod connectedWifi?.optInt("level", -127) ?: -127

                        "getLinkSpeed" -> return@hookMethod connectedWifi?.optInt("linkSpeed", -1)
                            ?: -1

                        "getFrequency" -> return@hookMethod connectedWifi?.optInt("frequency", -1)
                            ?: -1

                        "getIpAddress" -> return@hookMethod if (isConnected) 0x6401A8C0 else 0 // 192.168.1.100 小端序
                    }
                    return@hookMethod chain.proceed(chain.args.toTypedArray())
                }
            } catch (e: Throwable) { /* 部分方法在低版本可能不存在 */
            }
        }
    } catch (e: Throwable) {
        XposedBridge.log(e)
    }

    // ── 1b. WifiInfo.getSupplicantState() ──
    try {
        XposedHelpers.hookMethod(
            "android.net.wifi.WifiInfo", classLoader, "getSupplicantState"
        ) { chain, method ->
            val config = readConfig() ?: return@hookMethod chain.proceed(chain.args.toTypedArray())
            if (!config.optBoolean(
                    "active",
                    false
                )
            ) return@hookMethod chain.proceed(chain.args.toTypedArray())
            val mockWifi = config.optBoolean("mock_wifi", true)
            val wifiObj = if (mockWifi) config.optJSONObject("wifi_json") else null
            val isConnected = wifiObj?.optBoolean("isConnected", false) ?: false
            try {
                val enumClass = XposedHelpers.findClass(
                    "android.net.wifi.SupplicantState", classLoader
                )
                val stateStr =
                    if (mockWifi && isConnected) "COMPLETED" else "DISCONNECTED"
                return@hookMethod enumClass.getField(stateStr).get(null)
            } catch (e: Throwable) { /* 忽略 */
            }
            return@hookMethod chain.proceed(chain.args.toTypedArray())
        }
    } catch (e: Throwable) { /* 忽略 */
    }

    // ── 2. Wi-Fi 扫描结果伪造 (getScanResults) ──
    val realCapabilities = listOf(
        "[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]",
        "[WPA2-PSK-CCMP+TKIP][RSN-PSK-CCMP+TKIP][ESS]",
        "[WPA2-PSK-CCMP][ESS][WPS]",
        "[WPA-PSK-TKIP+CCMP][WPA2-PSK-TKIP+CCMP][ESS]",
        "[RSN-PSK-CCMP][ESS]",
        "[WPA2-EAP-CCMP][RSN-EAP-CCMP][ESS]",
        "[ESS]",
        "[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS][WPS]",
        "[WPA2-SAE-CCMP][RSN-SAE-CCMP][ESS]",
        "[WPA2-PSK+SAE-CCMP][RSN-PSK+SAE-CCMP][ESS]"
    )

    // ── 3. WifiManager 整体 Hook ──
    try {
        XposedHelpers.hookMethod(
            "android.net.wifi.WifiManager", classLoader, "getScanResults"
        ) { chain, method ->
            val config = readConfig() ?: return@hookMethod chain.proceed(chain.args.toTypedArray())
            if (!config.optBoolean(
                    "active",
                    false
                )
            ) return@hookMethod chain.proceed(chain.args.toTypedArray())
            val fakeList = java.util.ArrayList<Any>()
            val mockWifi = config.optBoolean("mock_wifi", true)
            val wifiObj = if (mockWifi) config.optJSONObject("wifi_json") else null
            if (mockWifi) {
                try {
                    val scanResultClass =
                        XposedHelpers.findClass("android.net.wifi.ScanResult", classLoader)
                    val baseTimestamp = android.os.SystemClock.elapsedRealtimeNanos()

                    fun addFakeScanResult(wifi: org.json.JSONObject) {
                        val fakeScanResult = XposedHelpers.newInstance(scanResultClass)
                        val ssidVal = wifi.optString("ssid", "")
                        val bssidVal = wifi.optString("bssid", "")
                        val finalSsid = if (ssidVal.isEmpty() || ssidVal == "<unknown ssid>") {
                            "WIFI_${bssidVal.takeLast(5).replace(":", "")}"
                        } else {
                            ssidVal
                        }
                        XposedHelpers.setObjectField(fakeScanResult, "SSID", finalSsid)
                        XposedHelpers.setObjectField(fakeScanResult, "BSSID", bssidVal)
                        val level = wifi.optInt("level", -65)
                        XposedHelpers.setIntField(fakeScanResult, "level", level)
                        XposedHelpers.setIntField(
                            fakeScanResult, "frequency",
                            wifi.optInt("frequency", 2412)
                        )
                        XposedHelpers.setObjectField(
                            fakeScanResult, "capabilities",
                            wifi.optString("capabilities", realCapabilities.random())
                        )
                        try {
                            val offsetNanos = (rng.nextInt(200_000) * 1000L)
                            XposedHelpers.setLongField(
                                fakeScanResult, "timestamp",
                                (baseTimestamp - offsetNanos) / 1000
                            )
                        } catch (e: Throwable) {
                        }
                        fakeList.add(fakeScanResult)
                    }

                    if (wifiObj != null) {
                        val isConnected = wifiObj.optBoolean("isConnected", false)
                        val connectedWifi =
                            if (isConnected) wifiObj.optJSONObject("connectedWifi") else null
                        if (connectedWifi != null) {
                            addFakeScanResult(connectedWifi)
                        }

                        val nearbyArray = wifiObj.optJSONArray("nearbyWifi")
                        if (nearbyArray != null) {
                            for (i in 0 until nearbyArray.length()) {
                                val wifi = nearbyArray.getJSONObject(i)
                                addFakeScanResult(wifi)
                            }
                        }
                    }

                    if (fakeList.isEmpty()) {
                        val lat = config.optDouble("lat", 0.0)
                        val lng = config.optDouble("lng", 0.0)
                        val seed = ((lat * 100000).toLong() xor (lng * 100000).toLong())
                        val random = java.util.Random(seed)
                        for (i in 0 until 5) {
                            val fakeWifi = org.json.JSONObject()
                            fakeWifi.put("ssid", "WIFI_${random.nextInt(9000) + 1000}")
                            val bssid = String.format(
                                "%02x:%02x:%02x:%02x:%02x:%02x",
                                random.nextInt(256), random.nextInt(256), random.nextInt(256),
                                random.nextInt(256), random.nextInt(256), random.nextInt(256)
                            )
                            fakeWifi.put("bssid", bssid)
                            fakeWifi.put("level", -40 - random.nextInt(50))
                            fakeWifi.put("frequency", if (random.nextBoolean()) 2412 else 5180)
                            fakeWifi.put("capabilities", "[WPA2-PSK-CCMP][ESS]")
                            addFakeScanResult(fakeWifi)
                        }
                    }
                } catch (e: Throwable) { /* 忽略 */
                }
            }
            return@hookMethod fakeList
        }

        // getWifiState()
        XposedHelpers.hookMethod(
            "android.net.wifi.WifiManager", classLoader, "getWifiState"
        ) { chain, method ->
            val config = readConfig() ?: return@hookMethod chain.proceed(chain.args.toTypedArray())
            if (!config.optBoolean(
                    "active",
                    false
                )
            ) return@hookMethod chain.proceed(chain.args.toTypedArray())
            val mockWifi = config.optBoolean("mock_wifi", true)
            if (mockWifi) {
                return@hookMethod 3 // 3 代表 Wi-Fi 已开启状态 (WIFI_STATE_ENABLED)
            }
            return@hookMethod chain.proceed(chain.args.toTypedArray())
        }

        // isWifiEnabled()
        XposedHelpers.hookMethod(
            "android.net.wifi.WifiManager", classLoader, "isWifiEnabled"
        ) { chain, method ->
            val config = readConfig() ?: return@hookMethod chain.proceed(chain.args.toTypedArray())
            if (!config.optBoolean(
                    "active",
                    false
                )
            ) return@hookMethod chain.proceed(chain.args.toTypedArray())
            val mockWifi = config.optBoolean("mock_wifi", true)
            val wifiObj = config.optJSONObject("wifi_json")
            val hasWifiData =
                wifiObj != null && (wifiObj.has("connectedWifi") || wifiObj.optJSONArray(
                    "nearbyWifi"
                )?.length() ?: 0 > 0)
            if (mockWifi) {
                return@hookMethod hasWifiData
            }
            return@hookMethod chain.proceed(chain.args.toTypedArray())
        }

        // getConnectionInfo() — 返回伪造的 WifiInfo 对象
        XposedHelpers.hookMethod(
            "android.net.wifi.WifiManager", classLoader, "getConnectionInfo"
        ) { chain, method ->
            val config = readConfig() ?: return@hookMethod chain.proceed(chain.args.toTypedArray())
            if (!config.optBoolean(
                    "active",
                    false
                )
            ) return@hookMethod chain.proceed(chain.args.toTypedArray())
            val mockWifi = config.optBoolean("mock_wifi", true)
            val wifiObj = if (mockWifi) config.optJSONObject("wifi_json") else null
            val isConnected = wifiObj?.optBoolean("isConnected", false) ?: false
            val connectedWifi =
                if (isConnected) wifiObj?.optJSONObject("connectedWifi") else null

            val currentResult = chain.proceed(chain.args.toTypedArray())
            if (isConnected && connectedWifi != null) {
                try {
                    val ssidVal = connectedWifi.optString("ssid", "")
                    val finalSsid =
                        if (ssidVal.isEmpty() || ssidVal == "<unknown ssid>") "HOME_WIFI" else ssidVal
                    val bssidVal = connectedWifi.optString("bssid", "02:00:00:00:00:00")
                    val freqVal = connectedWifi.optInt("frequency", 2412)
                    val macAddressVal = connectedWifi.optString("macAddress", bssidVal)
                    val linkSpeedVal = connectedWifi.optInt("linkSpeed", 65)
                    val standardVal = connectedWifi.optInt("wifiStandard", 6)
                    val levelVal = connectedWifi.optInt("level", -65)
                    val networkIdVal = connectedWifi.optInt("networkId", 1)

                    if (currentResult != null) {
                        // 就地修改返回值以避免在 ColorOS 的 system_server 中发生 ClassCastException
                        try {
                            XposedHelpers.setObjectField(
                                currentResult,
                                "mSSID",
                                "\"$finalSsid\""
                            )
                        } catch (e: Throwable) {
                        }
                        try {
                            val wifiSsidClass = XposedHelpers.findClassIfExists(
                                "android.net.wifi.WifiSsid",
                                classLoader
                            )
                            if (wifiSsidClass != null) {
                                val createMethod = XposedHelpers.findMethodExact(
                                    wifiSsidClass,
                                    "createFromAsciiEncoded",
                                    String::class.java
                                )
                                val wifiSsid = createMethod.invoke(null, finalSsid)
                                XposedHelpers.setObjectField(
                                    currentResult,
                                    "mWifiSsid",
                                    wifiSsid
                                )
                            }
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setObjectField(currentResult, "mBSSID", bssidVal)
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setObjectField(
                                currentResult,
                                "mMacAddress",
                                macAddressVal
                            )
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setIntField(currentResult, "mRssi", levelVal)
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setIntField(currentResult, "mFrequency", freqVal)
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setIntField(
                                currentResult,
                                "mLinkSpeed",
                                linkSpeedVal
                            )
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setIntField(
                                currentResult,
                                "mNetworkId",
                                networkIdVal
                            )
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setIntField(
                                currentResult,
                                "mWifiStandard",
                                standardVal
                            )
                        } catch (e: Throwable) {
                        }
                    } else {
                        if (isCoreSystemProcess) return@hookMethod currentResult

                        var builtWithBuilder = false
                        var builtInfo: Any? = null
                        try {
                            val builderClass = XposedHelpers.findClass(
                                "android.net.wifi.WifiInfo\$Builder",
                                classLoader
                            )
                            val builder = XposedHelpers.newInstance(builderClass)
                            XposedHelpers.callMethod(builder, "setSsid", finalSsid)
                            XposedHelpers.callMethod(builder, "setBssid", bssidVal)
                            XposedHelpers.callMethod(builder, "setRssi", levelVal)
                            XposedHelpers.callMethod(builder, "setFrequency", freqVal)
                            XposedHelpers.callMethod(builder, "setLinkSpeed", linkSpeedVal)
                            builtInfo = XposedHelpers.callMethod(builder, "build")
                            builtWithBuilder = true
                        } catch (e: Throwable) {
                        }

                        val fakeWifiInfo = if (builtWithBuilder) {
                            builtInfo!!
                        } else {
                            val wifiInfoClass = XposedHelpers.findClass(
                                "android.net.wifi.WifiInfo",
                                classLoader
                            )
                            val info = XposedHelpers.newInstance(wifiInfoClass)
                            try {
                                XposedHelpers.setObjectField(
                                    info,
                                    "mSSID",
                                    "\"$finalSsid\""
                                )
                            } catch (e: Throwable) {
                            }
                            try {
                                XposedHelpers.setObjectField(info, "mBSSID", bssidVal)
                            } catch (e: Throwable) {
                            }
                            try {
                                XposedHelpers.setObjectField(
                                    info,
                                    "mMacAddress",
                                    macAddressVal
                                )
                            } catch (e: Throwable) {
                            }
                            try {
                                XposedHelpers.setIntField(info, "mRssi", levelVal)
                            } catch (e: Throwable) {
                            }
                            try {
                                XposedHelpers.setIntField(info, "mFrequency", freqVal)
                            } catch (e: Throwable) {
                            }
                            try {
                                XposedHelpers.setIntField(info, "mLinkSpeed", linkSpeedVal)
                            } catch (e: Throwable) {
                            }
                            try {
                                XposedHelpers.setIntField(info, "mNetworkId", networkIdVal)
                            } catch (e: Throwable) {
                            }
                            info
                        }
                        return@hookMethod fakeWifiInfo
                    }
                } catch (e: Throwable) {
                }
            } else {
                if (currentResult != null) {
                    try {
                        XposedHelpers.setObjectField(
                            currentResult,
                            "mBSSID",
                            "02:00:00:00:00:00"
                        )
                    } catch (e: Throwable) {
                    }
                    try {
                        XposedHelpers.setObjectField(
                            currentResult,
                            "mMacAddress",
                            "02:00:00:00:00:00"
                        )
                    } catch (e: Throwable) {
                    }
                    try {
                        XposedHelpers.setIntField(currentResult, "mNetworkId", -1)
                    } catch (e: Throwable) {
                    }
                    try {
                        XposedHelpers.setIntField(currentResult, "mRssi", -127)
                    } catch (e: Throwable) {
                    }
                    try {
                        XposedHelpers.setIntField(currentResult, "mLinkSpeed", -1)
                    } catch (e: Throwable) {
                    }
                    try {
                        XposedHelpers.setIntField(currentResult, "mFrequency", -1)
                    } catch (e: Throwable) {
                    }
                } else {
                    if (isCoreSystemProcess) return@hookMethod currentResult
                    try {
                        val wifiInfoClass = XposedHelpers.findClass(
                            "android.net.wifi.WifiInfo",
                            classLoader
                        )
                        val fakeWifiInfo = XposedHelpers.newInstance(wifiInfoClass)
                        try {
                            XposedHelpers.setObjectField(
                                fakeWifiInfo,
                                "mBSSID",
                                "02:00:00:00:00:00"
                            )
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setObjectField(
                                fakeWifiInfo,
                                "mMacAddress",
                                "02:00:00:00:00:00"
                            )
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setIntField(fakeWifiInfo, "mNetworkId", -1)
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setIntField(fakeWifiInfo, "mRssi", -127)
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setIntField(fakeWifiInfo, "mLinkSpeed", -1)
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setIntField(fakeWifiInfo, "mFrequency", -1)
                        } catch (e: Throwable) {
                        }
                        return@hookMethod fakeWifiInfo
                    } catch (e: Throwable) {
                    }
                }
            }
            return@hookMethod currentResult
        }

        // getConfiguredNetworks()
        XposedHelpers.hookMethod(
            "android.net.wifi.WifiManager", classLoader, "getConfiguredNetworks"
        ) { chain, method ->
            val config = readConfig() ?: return@hookMethod chain.proceed(chain.args.toTypedArray())
            if (!config.optBoolean(
                    "active",
                    false
                )
            ) return@hookMethod chain.proceed(chain.args.toTypedArray())
            return@hookMethod java.util.ArrayList<Any>()
        }

        // getDhcpInfo()
        XposedHelpers.hookMethod(
            "android.net.wifi.WifiManager", classLoader, "getDhcpInfo"
        ) { chain, method ->
            val config = readConfig() ?: return@hookMethod chain.proceed(chain.args.toTypedArray())
            if (!config.optBoolean(
                    "active",
                    false
                )
            ) return@hookMethod chain.proceed(chain.args.toTypedArray())
            try {
                val dhcpClass =
                    XposedHelpers.findClass("android.net.DhcpInfo", classLoader)
                val dhcp = XposedHelpers.newInstance(dhcpClass)
                XposedHelpers.setIntField(dhcp, "ipAddress", 0x6401A8C0.toInt())
                XposedHelpers.setIntField(
                    dhcp,
                    "gateway",
                    0x0101A8C0
                )     // 192.168.1.1
                XposedHelpers.setIntField(
                    dhcp,
                    "netmask",
                    0x00FFFFFF
                )     // 255.255.255.0
                XposedHelpers.setIntField(
                    dhcp,
                    "dns1",
                    0x0101A8C0
                )        // 192.168.1.1
                XposedHelpers.setIntField(dhcp, "dns2", 0x08080808)        // 8.8.8.8
                XposedHelpers.setIntField(dhcp, "serverAddress", 0x0101A8C0)
                return@hookMethod dhcp
            } catch (e: Throwable) { /* 忽略 */
            }
            return@hookMethod chain.proceed(chain.args.toTypedArray())
        }
    } catch (e: Throwable) {
        XposedBridge.log(e)
    }

    // ── 4. NetworkInfo.getExtraInfo() ──
    try {
        XposedHelpers.hookMethod(
            "android.net.NetworkInfo", classLoader, "getExtraInfo"
        ) { chain, method ->
            val config = readConfig() ?: return@hookMethod chain.proceed(chain.args.toTypedArray())
            if (!config.optBoolean(
                    "active",
                    false
                )
            ) return@hookMethod chain.proceed(chain.args.toTypedArray())
            val mockWifi = config.optBoolean("mock_wifi", true)
            val wifiObj = if (mockWifi) config.optJSONObject("wifi_json") else null
            val isConnected = wifiObj?.optBoolean("isConnected", false) ?: false
            val connectedWifi =
                if (isConnected) wifiObj?.optJSONObject("connectedWifi") else null
            if (connectedWifi != null) {
                return@hookMethod "\"${connectedWifi.optString("ssid", "HOME_WIFI")}\""
            } else {
                return@hookMethod null
            }
        }
    } catch (e: Throwable) {
        XposedBridge.log(e)
    }

    // ── 5. WifiScanner Hook ──
    try {
        val wifiScannerClass =
            XposedHelpers.findClassIfExists("android.net.wifi.WifiScanner", classLoader)
        if (wifiScannerClass != null) {
            // startScan(ScanSettings, ScanListener) 和重载
            XposedHelpers.hookAllMethods(wifiScannerClass, "startScan") { chain, method ->
                val config =
                    readConfig() ?: return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                if (!config.optBoolean(
                        "active",
                        false
                    )
                ) return@hookAllMethods chain.proceed(chain.args.toTypedArray())

                val listener = chain.args.lastOrNull() ?: return@hookAllMethods null
                val mockWifi = config.optBoolean("mock_wifi", true)
                val wifiObj = if (mockWifi) config.optJSONObject("wifi_json") else null
                if (mockWifi) {
                    try {
                        val scanResultClass = XposedHelpers.findClass(
                            "android.net.wifi.ScanResult",
                            classLoader
                        )
                        val baseTimestamp = android.os.SystemClock.elapsedRealtimeNanos()
                        val fakeList = java.util.ArrayList<Any>()

                        fun addFakeScanResult(wifi: org.json.JSONObject) {
                            val fakeScanResult = XposedHelpers.newInstance(scanResultClass)
                            val ssidVal = wifi.optString("ssid", "")
                            val bssidVal = wifi.optString("bssid", "")
                            val finalSsid =
                                if (ssidVal.isEmpty() || ssidVal == "<unknown ssid>") {
                                    "WIFI_${bssidVal.takeLast(5).replace(":", "")}"
                                } else {
                                    ssidVal
                                }
                            XposedHelpers.setObjectField(fakeScanResult, "SSID", finalSsid)
                            XposedHelpers.setObjectField(fakeScanResult, "BSSID", bssidVal)
                            val level = wifi.optInt("level", -65)
                            XposedHelpers.setIntField(fakeScanResult, "level", level)
                            XposedHelpers.setIntField(
                                fakeScanResult,
                                "frequency",
                                wifi.optInt("frequency", 2412)
                            )
                            XposedHelpers.setObjectField(
                                fakeScanResult,
                                "capabilities",
                                wifi.optString("capabilities", "[WPA2-PSK-CCMP][ESS]")
                            )
                            try {
                                val offsetNanos = (rng.nextInt(200_000) * 1000L)
                                XposedHelpers.setLongField(
                                    fakeScanResult,
                                    "timestamp",
                                    (baseTimestamp - offsetNanos) / 1000
                                )
                            } catch (e: Throwable) {
                            }
                            fakeList.add(fakeScanResult)
                        }

                        if (wifiObj != null) {
                            val isConnected = wifiObj.optBoolean("isConnected", false)
                            val connectedWifi =
                                if (isConnected) wifiObj.optJSONObject("connectedWifi") else null
                            if (connectedWifi != null) {
                                addFakeScanResult(connectedWifi)
                            }

                            val nearbyArray = wifiObj.optJSONArray("nearbyWifi")
                            if (nearbyArray != null) {
                                for (i in 0 until nearbyArray.length()) {
                                    val wifi = nearbyArray.getJSONObject(i)
                                    addFakeScanResult(wifi)
                                }
                            }
                        }

                        if (fakeList.isEmpty()) {
                            val lat = config.optDouble("lat", 0.0)
                            val lng = config.optDouble("lng", 0.0)
                            val seed = ((lat * 100000).toLong() xor (lng * 100000).toLong())
                            val random = java.util.Random(seed)
                            for (i in 0 until 5) {
                                val fakeWifi = org.json.JSONObject()
                                fakeWifi.put("ssid", "WIFI_${random.nextInt(9000) + 1000}")
                                val bssid = String.format(
                                    "%02x:%02x:%02x:%02x:%02x:%02x",
                                    random.nextInt(256),
                                    random.nextInt(256),
                                    random.nextInt(256),
                                    random.nextInt(256),
                                    random.nextInt(256),
                                    random.nextInt(256)
                                )
                                fakeWifi.put("bssid", bssid)
                                fakeWifi.put("level", -40 - random.nextInt(50))
                                fakeWifi.put(
                                    "frequency",
                                    if (random.nextBoolean()) 2412 else 5180
                                )
                                fakeWifi.put("capabilities", "[WPA2-PSK-CCMP][ESS]")
                                addFakeScanResult(fakeWifi)
                            }
                        }

                        if (fakeList.isNotEmpty()) {
                            val scanResultArray = ReflectArray.newInstance(
                                scanResultClass,
                                fakeList.size
                            )
                            for (i in 0 until fakeList.size) {
                                ReflectArray.set(scanResultArray, i, fakeList[i])
                            }

                            // 构造 ScanData 对象（包含 ScanResult 数组）
                            val scanDataClass = XposedHelpers.findClass(
                                "android.net.wifi.WifiScanner\$ScanData",
                                classLoader
                            )
                            val fakeScanData = XposedHelpers.newInstance(
                                scanDataClass,
                                0,
                                0,
                                scanResultArray
                            )
                            val fakeScanDataArray =
                                ReflectArray.newInstance(scanDataClass, 1)
                            ReflectArray.set(fakeScanDataArray, 0, fakeScanData)

                            // 主动回调 Listener，把假数据塞回去
                            XposedHelpers.callMethod(
                                listener,
                                "onResults",
                                fakeScanDataArray
                            )
                        } else {
                            val scanDataClass = XposedHelpers.findClass(
                                "android.net.wifi.WifiScanner\$ScanData",
                                classLoader
                            )
                            val emptyScanData = XposedHelpers.newInstance(
                                scanDataClass,
                                0,
                                0,
                                ReflectArray.newInstance(scanResultClass, 0)
                            )
                            val fakeScanDataArray =
                                ReflectArray.newInstance(scanDataClass, 1)
                            ReflectArray.set(fakeScanDataArray, 0, emptyScanData)
                            XposedHelpers.callMethod(
                                listener,
                                "onResults",
                                fakeScanDataArray
                            )
                        }
                    } catch (e: Throwable) {
                        XposedBridge.log("[LocationSpoofer] WifiScanner 伪造失败: $e")
                    }
                } else {
                    try {
                        val scanDataClass = XposedHelpers.findClass(
                            "android.net.wifi.WifiScanner\$ScanData",
                            classLoader
                        )
                        val scanResultClass = XposedHelpers.findClass(
                            "android.net.wifi.ScanResult",
                            classLoader
                        )
                        val emptyScanData = XposedHelpers.newInstance(
                            scanDataClass,
                            0,
                            0,
                            ReflectArray.newInstance(scanResultClass, 0)
                        )
                        val fakeScanDataArray =
                            ReflectArray.newInstance(scanDataClass, 1)
                        ReflectArray.set(fakeScanDataArray, 0, emptyScanData)
                        XposedHelpers.callMethod(listener, "onResults", fakeScanDataArray)
                    } catch (e: Throwable) { /* 忽略 */
                    }
                }
                return@hookAllMethods null
            }

            // startScan(ScanSettings, ScanListener) 和重载

        }
    } catch (e: Throwable) {
        XposedBridge.log(e)
    }

    XposedBridge.log("[LocationSpoofer] Wi-Fi environment hooks installed")
}