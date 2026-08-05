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

package com.suseoaa.locationspoofer.xposed.hooks

import com.suseoaa.locationspoofer.xposed.LocationHooker
import com.suseoaa.locationspoofer.xposed.utils.*
import com.suseoaa.locationspoofer.xposed.hooks.*
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.lang.reflect.*
import kotlin.math.*
import io.github.libxposed.api.*

/**
 * 基础定位框架拦截模块 (Base Location Hooker)
 * 
 * 上下文:
 * Android 系统的原生定位服务由 `android.location.LocationManager` 提供，包含 GPS 卫星状态 (GnssStatus)、
 * NMEA 报文 (底层 GPS 硬件输出的数据格式) 以及基础坐标。
 * 
 * 作用:
 * 本模块是位置伪造的核心，直接 Hook Android 原生系统的底层接口，从而对所有的 App 生效。
 * 关键部分解释:
 * 1. hookLocationAPIs: 拦截 LocationManager 的 requestLocationUpdates 等方法，不仅伪造坐标，
 *    还会使用 Ornstein-Uhlenbeck 随机过程增加自然的坐标抖动，并清除 Location 对象的 `isFromMockProvider` 标志，
 *    这是绕过绝大多数检测的关键。
 * 2. hookGnssStatus / createSpoofedGpsSatellites: 伪造卫星数据。如果只伪造坐标但不伪造天上的卫星，
 *    高德/百度SDK会轻易发现异常（坐标在变但搜不到卫星）。这里动态生成了一套信噪比(SNR)和仰角逼真的卫星阵列。
 * 3. NMEA 拦截: NMEA-0183 报文是底层 GPS 芯片吐出的串口数据，高级的地图 SDK 会直接解析 NMEA 而非 Location 对象，
 *    所以我们必须在此处把伪造的经纬度按照 NMEA 格式 (GPGGA, GPRMC 等) 重新编码并注入。
 */


internal fun LocationHooker.hookLocationAPIs(classLoader: ClassLoader, currentPkg: String) {
    try {
        // android.location.Location 标准接口: 返回GCJ-02坐标
        // 在中国大陆,系统GPS HAL层已内置GCJ-02强制加偏(国家测绘法规要求)。
        // 因此android.location.Location.getLatitude()在中国设备上实际返回的是GCJ-02坐标,
        // 它们从Location拿到坐标后不会再做WGS-84到GCJ-02的转换,而是直接使用。
        // 如果我们返回真正的WGS-84,App会把它当GCJ-02直接传给地图SDK渲染,
        // 由于WGS-84与GCJ-02之间存在约300-500米的非线性偏移,地图上会出现固定偏移。


        XposedHelpers.hookMethod(
            "android.location.Location",
            classLoader,
            "getLatitude"
        ) { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())

            // 宿主 App 需要从真实 Location 读取坐标（如"定位到当前位置"），不拦截
            if (currentPkg.substringBefore(":") == "com.suseoaa.locationspoofer") return@hookMethod result
            val config = readConfig()
            if (config != null && config.optBoolean("active", false)) {
                val appSystems = config.optJSONObject("app_coordinate_systems")
                val basePkg = currentPkg.substringBefore(":")
                // optString 在 key 不存在时返回 ""（空字符串），不是 null！
                // 必须用 has() 先检查，否则会错误匹配到空字符串分支
                val targetSys = if (appSystems?.has(basePkg) == true) {
                    appSystems.optString(basePkg, "GCJ-02")
                } else {
                    "GCJ-02"
                }
                val baseLat = when (targetSys) {
                    "WGS-84" -> config.optDouble("wgs84_lat", result as Double)
                    "BD-09" -> config.optDouble("bd09_lat", result as Double)
                    else -> config.optDouble("lat", result as Double)
                }
                val baseLng = when (targetSys) {
                    "WGS-84" -> config.optDouble("wgs84_lng", 0.0)
                    "BD-09" -> config.optDouble("bd09_lng", 0.0)
                    else -> config.optDouble("lng", 0.0)
                }
                result = getJitteredLocation(baseLat, baseLng).first
            }

            return@hookMethod result
        }
        XposedHelpers.hookMethod(
            "android.location.Location",
            classLoader,
            "getLongitude"
        ) { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())

            if (currentPkg.substringBefore(":") == "com.suseoaa.locationspoofer") return@hookMethod result
            val config = readConfig()
            if (config != null && config.optBoolean("active", false)) {
                val appSystems = config.optJSONObject("app_coordinate_systems")
                val basePkg = currentPkg.substringBefore(":")
                // optString 在 key 不存在时返回 ""（空字符串），不是 null！
                val targetSys = if (appSystems?.has(basePkg) == true) {
                    appSystems.optString(basePkg, "GCJ-02")
                } else {
                    "GCJ-02"
                }
                val baseLat = when (targetSys) {
                    "WGS-84" -> config.optDouble("wgs84_lat", 0.0)
                    "BD-09" -> config.optDouble("bd09_lat", 0.0)
                    else -> config.optDouble("lat", 0.0)
                }
                val baseLng = when (targetSys) {
                    "WGS-84" -> config.optDouble("wgs84_lng", result as Double)
                    "BD-09" -> config.optDouble("bd09_lng", result as Double)
                    else -> config.optDouble("lng", result as Double)
                }
                result = getJitteredLocation(baseLat, baseLng).second
            }

            return@hookMethod result
        }
        XposedHelpers.hookMethod(
            "android.location.Location",
            classLoader,
            "getAccuracy"
        ) { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())

            if (currentPkg.substringBefore(":") == "com.suseoaa.locationspoofer") return@hookMethod result
            val config = readConfig()
            if (config != null && config.optBoolean("active", false)) {
                result = getJitteredAccuracy()
            }

            return@hookMethod result
        }
        XposedHelpers.hookMethod(
            "android.location.Location",
            classLoader,
            "getAltitude"
        ) { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())

            val config = readConfig()
            if (config != null && config.optBoolean("active", false)) {
                val baseAlt = config.optDouble("altitude", 0.0)
                val enableJitter = config.optBoolean("enable_jitter", true)
                result = if (enableJitter && baseAlt > 0.0) {
                    // 稍微抖动海拔，真实气压计存在起伏，±0.5米
                    baseAlt + (rng.nextDouble() - 0.5)
                } else {
                    baseAlt
                }
            }

            return@hookMethod result
        }

        // 抹除 isFromMockProvider 标志位（strategy:100 的根本来源）

        // Android 6~11: isFromMockProvider()
        XposedHelpers.hookMethod(
            "android.location.Location",
            classLoader,
            "isFromMockProvider"
        ) { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())

            val config = readConfig()
            if (config != null && config.optBoolean("active", false)) {
                result = false
            }

            return@hookMethod result
        }
        // Android 12+: isMock()
        try {
            XposedHelpers.hookMethod(
                "android.location.Location",
                classLoader,
                "isMock"
            ) { chain, method ->
                var result = chain.proceed(chain.args.toTypedArray())

                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    result = false
                }

                return@hookMethod result
            }
        } catch (e: Throwable) { /* API < 31 的系统没有此方法 */
        }

        // ★ Android 13 专项：直接对 Location 对象的 mMock / mIsFromMockProvider 字段写 false
        // (Android 12+ 字段名改为 mMock，Android 6-11 为 mIsFromMockProvider)

        // 在 getLatitude/getLongitude/getAccuracy 时同步清字段，确保在实际读值前已抹除
        XposedHelpers.hookMethod(
            "android.location.Location",
            classLoader,
            "getLatitude"
        ) { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())

            val config = readConfig()
            if (config != null && config.optBoolean("active", false)) {
                val loc = chain.thisObject ?: return@hookMethod result
                try {
                    XposedHelpers.setBooleanField(loc, "mMock", false)
                } catch (e: Throwable) {
                }
                try {
                    XposedHelpers.setBooleanField(loc, "mIsFromMockProvider", false)
                } catch (e: Throwable) {
                }
                // 清理 extras bundle 中可能残留的 mock 标记
                try {
                    val extras =
                        XposedHelpers.callMethod(loc, "getExtras") as? android.os.Bundle
                    extras?.remove("mockLocation")
                    extras?.remove("isMock")
                    // ★ 将伪造的卫星数量添加到 bundle 中
                    val satCount = config.optInt("satellite_count", 10)
                    if (extras == null) {
                        val newBundle = android.os.Bundle()
                        newBundle.putInt("satellites", satCount)
                        XposedHelpers.setObjectField(loc, "mExtras", newBundle)
                    } else {
                        extras.putInt("satellites", satCount)
                    }
                } catch (e: Throwable) {
                }
            }

            return@hookMethod result
        }
        XposedHelpers.hookMethod(
            "android.location.Location",
            classLoader,
            "getLongitude"
        ) { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())

            val config = readConfig()
            if (config != null && config.optBoolean("active", false)) {
                val loc = chain.thisObject ?: return@hookMethod result
                try {
                    XposedHelpers.setBooleanField(loc, "mMock", false)
                } catch (e: Throwable) {
                }
                try {
                    XposedHelpers.setBooleanField(loc, "mIsFromMockProvider", false)
                } catch (e: Throwable) {
                }
                // 清理 extras bundle 中可能残留的 mock 标记
                try {
                    val extras =
                        XposedHelpers.callMethod(loc, "getExtras") as? android.os.Bundle
                    extras?.remove("mockLocation")
                    extras?.remove("isMock")
                    // ★ 将伪造的卫星数量添加到 bundle 中
                    val satCount = config.optInt("satellite_count", 10)
                    if (extras == null) {
                        val newBundle = android.os.Bundle()
                        newBundle.putInt("satellites", satCount)
                        XposedHelpers.setObjectField(loc, "mExtras", newBundle)
                    } else {
                        extras.putInt("satellites", satCount)
                    }
                } catch (e: Throwable) {
                }
            }

            return@hookMethod result
        }


        // ★ 拦截 getProvider：将 "mock" / "test" 提供者名隐藏，换成 "gps"
        XposedHelpers.hookMethod(
            "android.location.Location", classLoader, "getProvider"
        ) { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())

            val config = readConfig()
            if (config != null && config.optBoolean("active", false)) {
                val provider = result as? String ?: return@hookMethod result
                if (provider.contains("mock", ignoreCase = true) ||
                    provider.contains("test", ignoreCase = true) ||
                    provider.contains("fake", ignoreCase = true)
                ) {
                    result = android.location.LocationManager.GPS_PROVIDER
                }
            }
            return@hookMethod result
        }

        // ★ 拦截 LocationManager.getProviders() / getAllProviders()：移除 mock/test 提供者

        try {
            XposedHelpers.hookMethod(
                "android.location.LocationManager", classLoader, "getProviders",
                Boolean::class.javaPrimitiveType!!
            ) { chain, method ->
                var result = chain.proceed(chain.args.toTypedArray())

                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    @Suppress("UNCHECKED_CAST")
                    val list = result as? MutableList<String> ?: return@hookMethod result
                    val cleaned = list.filterNot {
                        it.contains("mock", ignoreCase = true) ||
                                it.contains("test", ignoreCase = true) ||
                                it.contains("fake", ignoreCase = true)
                    }.toMutableList()
                    if (!cleaned.contains(android.location.LocationManager.GPS_PROVIDER))
                        cleaned.add(android.location.LocationManager.GPS_PROVIDER)
                    result = cleaned
                }

                return@hookMethod result
            }
            XposedHelpers.hookMethod(
                "android.location.LocationManager", classLoader, "getAllProviders"
            ) { chain, method ->
                var result = chain.proceed(chain.args.toTypedArray())

                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    @Suppress("UNCHECKED_CAST")
                    val list = result as? MutableList<String> ?: return@hookMethod result
                    val cleaned = list.filterNot {
                        it.contains("mock", ignoreCase = true) ||
                                it.contains("test", ignoreCase = true) ||
                                it.contains("fake", ignoreCase = true)
                    }.toMutableList()
                    if (!cleaned.contains(android.location.LocationManager.GPS_PROVIDER))
                        cleaned.add(android.location.LocationManager.GPS_PROVIDER)
                    result = cleaned
                }

                return@hookMethod result
            }
        } catch (e: Throwable) {
            XposedBridge.log(e)
        }

        // ★ NMEA-0183 报文劫持
        try {

            val locationManagerClazz =
                XposedHelpers.findClass("android.location.LocationManager", classLoader)
            XposedHelpers.hookAllMethods(
                locationManagerClazz,
                "addNmeaListener"
            ) { chain, method ->

                // 强制启动代理注入，无论当前active是true还是false。
                // 真正的状态校验在代理注入器的Timer中进行。

                val args = chain.args
                for (i in args.indices) {
                    val arg = args[i] ?: continue

                    // 检查它是否实现了 OnNmeaMessageListener
                    val isOnNmea = try {
                        LocationHooker.hasTypeByName(
                            arg.javaClass,
                            "android.location.OnNmeaMessageListener"
                        )
                    } catch (e: Exception) {
                        false
                    }

                    // 检查它是否实现了 GpsStatus.NmeaListener
                    val isGpsNmea = try {
                        LocationHooker.hasTypeByName(
                            arg.javaClass,
                            "android.location.GpsStatus\$NmeaListener"
                        )
                    } catch (e: Exception) {
                        false
                    }

                    if (isOnNmea) {
                        XposedBridge.log("[GPS_Spoofer] Detected addNmeaListener(OnNmeaMessageListener)! Starting active injector.")
                        args[i] = createOnNmeaMessageListenerProxy(arg, classLoader)
                    } else if (isGpsNmea) {
                        XposedBridge.log("[GPS_Spoofer] Detected addNmeaListener(GpsStatus.NmeaListener)! Starting active injector.")
                        args[i] = createGpsStatusNmeaListenerProxy(arg, classLoader)
                    }
                }

                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }

            // Hook 取消注册 NmeaListener
            XposedHelpers.hookAllMethods(
                locationManagerClazz,
                "removeNmeaListener"
            ) { chain, method ->

                for (arg in chain.args) {
                    if (arg != null) {
                        nmeaTimers.remove(arg)?.cancel()
                        XposedBridge.log("[GPS_Spoofer] removeNmeaListener called, canceled timer.")
                    }
                }

                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (e: Throwable) {
            XposedBridge.log(e)
        }

        // ★ Capture LocationListeners for proactive mock location injection
        try {
            val locationManagerClazz =
                XposedHelpers.findClass("android.location.LocationManager", classLoader)
            XposedHelpers.hookAllMethods(
                locationManagerClazz,
                "requestLocationUpdates"
            ) { chain, method ->

                for (arg in chain.args) {
                    if (arg == null) continue
                    val className = arg.javaClass.name
                    if (className == "java.lang.String" || className == "android.os.Looper" || className == "android.location.Criteria") continue

                    try {
                        if (LocationHooker.hasTypeByName(
                                arg.javaClass,
                                "android.location.LocationListener"
                            )
                        ) {
                            capturedLocationListeners.addIfAbsent(arg)
                        }
                    } catch (e: Throwable) {
                    }
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }

            XposedHelpers.hookAllMethods(
                locationManagerClazz,
                "removeUpdates"
            ) { chain, method ->

                for (arg in chain.args) {
                    if (arg == null) continue
                    try {
                        if (LocationHooker.hasTypeByName(
                                arg.javaClass,
                                "android.location.LocationListener"
                            )
                        ) {
                            capturedLocationListeners.remove(arg)
                        }
                    } catch (e: Throwable) {
                    }
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (e: Throwable) {
            XposedBridge.log("[LocationSpoofer] Failed to hook requestLocationUpdates: $e")
        }

        // getLastKnownLocation: 立即返回伪造的位置，修复“需要多次尝试才能定位”的问题
        try {
            val locationManagerClazz2 =
                XposedHelpers.findClass("android.location.LocationManager", classLoader)
            XposedHelpers.hookAllMethods(
                locationManagerClazz2,
                "getLastKnownLocation"
            ) { chain, method ->
                var result = chain.proceed(chain.args.toTypedArray())

                // 宿主 App 自身需要获取真实位置（"定位到当前位置"功能）
                // 不拦截宿主 App 的 getLastKnownLocation，只拦截目标 App 的
                val hostPkg = currentPkg.substringBefore(":")
                if (hostPkg == "com.suseoaa.locationspoofer") return@hookAllMethods result

                val config = readConfig()
                if (config == null || !config.optBoolean(
                        "active",
                        false
                    )
                ) return@hookAllMethods result
                val appSystems = config.optJSONObject("app_coordinate_systems")
                val basePkg = currentPkg.substringBefore(":")
                val targetSys = if (appSystems?.has(basePkg) == true)
                    appSystems.optString(basePkg, "GCJ-02") else "GCJ-02"
                val lat = when (targetSys) {
                    "WGS-84" -> config.optDouble("wgs84_lat", 0.0)
                    "BD-09" -> config.optDouble("bd09_lat", 0.0)
                    else -> config.optDouble("lat", 0.0)
                }
                val lng = when (targetSys) {
                    "WGS-84" -> config.optDouble("wgs84_lng", 0.0)
                    "BD-09" -> config.optDouble("bd09_lng", 0.0)
                    else -> config.optDouble("lng", 0.0)
                }
                if (lat == 0.0 && lng == 0.0) return@hookAllMethods result
                try {
                    val locClass = Class.forName("android.location.Location", false, classLoader)
                    val fakeLoc = locClass.getConstructor(String::class.java)
                        .newInstance(android.location.LocationManager.GPS_PROVIDER)
                    XposedHelpers.callMethod(fakeLoc, "setLatitude", lat)
                    XposedHelpers.callMethod(fakeLoc, "setLongitude", lng)
                    XposedHelpers.callMethod(fakeLoc, "setAccuracy", 20.0f)
                    XposedHelpers.callMethod(fakeLoc, "setTime", System.currentTimeMillis())
                    XposedHelpers.callMethod(
                        fakeLoc, "setElapsedRealtimeNanos",
                        android.os.SystemClock.elapsedRealtimeNanos()
                    )
                    try {
                        XposedHelpers.callMethod(fakeLoc, "setIsFromMockProvider", false)
                    } catch (_: Throwable) {
                    }
                    result = fakeLoc
                } catch (e2: Throwable) {
                    XposedBridge.log("[LocationSpoofer] getLastKnownLocation build error: $e2")
                }
                return@hookAllMethods result
            }
        } catch (e: Throwable) {
            XposedBridge.log("[LocationSpoofer] Failed to hook getLastKnownLocation: $e")
        }

        // ── 高德SDK专属Hook(含抖动,与原生Location保持同步) ──
        // 使用findClassIfExists安全探测: 微信小程序子进程(:appbrand0等)不加载高德SDK,
        // 直接findAndHookMethod会抛出ClassNotFoundError,中断整个hookLocationAPIs执行流。
        // findClassIfExists在类不存在时返回null而非抛异常,可安全跳过。
        val amapLocClazz = XposedHelpers.findClassIfExists(
            "com.amap.api.location.AMapLocation", classLoader
        )

        if (amapLocClazz != null) {
            XposedBridge.log("[LocationSpoofer] AMapLocation class found, installing AMap hooks")
            val amapLocClass = "com.amap.api.location.AMapLocation"

            // 高德 SDK 专属 Hook（包含抖动，与原生 Location 保持同步）

            try {
                XposedHelpers.hookMethod(
                    amapLocClass,
                    classLoader,
                    "getLatitude"
                ) { chain, method ->
                    var result = chain.proceed(chain.args.toTypedArray())

                    // 宿主 App 使用 AMapLocationClient 获取真实位置，不拦截
                    if (currentPkg.substringBefore(":") == "com.suseoaa.locationspoofer") return@hookMethod result
                    val config = readConfig()
                    if (config != null && config.optBoolean("active", false)) {
                        val baseLat = config.optDouble("lat", 0.0)
                        val baseLng = config.optDouble("lng", 0.0)
                        val jittered = getJitteredLocation(baseLat, baseLng)
                        when (method.name) {
                            "getLatitude" -> result = jittered.first
                            "getLongitude" -> result = jittered.second
                        }
                    }

                    return@hookMethod result
                }
                XposedHelpers.hookMethod(
                    amapLocClass,
                    classLoader,
                    "getLongitude"
                ) { chain, method ->
                    var result = chain.proceed(chain.args.toTypedArray())

                    // 宿主 App 使用 AMapLocationClient 获取真实位置，不拦截
                    if (currentPkg.substringBefore(":") == "com.suseoaa.locationspoofer") return@hookMethod result
                    val config = readConfig()
                    if (config != null && config.optBoolean("active", false)) {
                        val baseLat = config.optDouble("lat", 0.0)
                        val baseLng = config.optDouble("lng", 0.0)
                        val jittered = getJitteredLocation(baseLat, baseLng)
                        when (method.name) {
                            "getLatitude" -> result = jittered.first
                            "getLongitude" -> result = jittered.second
                        }
                    }

                    return@hookMethod result
                }
            } catch (e: Throwable) { /* 如果高德 SDK 方法签名不匹配则跳过 */
            }

            // 注入伪造的地址字符串，防止云端解析失败时导致“Location fetch failed”空指针异常
            hookAddressFields(amapLocClazz!!, classLoader)

            // ★★★ 高德SDK深度反检测（strategy:500 的来源）
            // mockData JSON 就是 AMapLocation.getMockData() 的返回值，直接置空


            try {
                // 1. getMockData() -> null（直接砍掉mockData字段的数据来源）
                XposedHelpers.hookMethod(
                    amapLocClass,
                    classLoader,
                    "getMockData"
                ) { chain, method ->
                    var result = chain.proceed(chain.args.toTypedArray())

                    val config = readConfig()
                    if (config != null && config.optBoolean("active", false)) {
                        result = null
                    }

                    return@hookMethod result
                }
            } catch (e: Throwable) {
                XposedBridge.log(e)
            }
            // 2. getMockFlag() / getMockType() -> 0
            try {
                XposedHelpers.hookMethod(
                    amapLocClass,
                    classLoader,
                    "getMockFlag"
                ) { chain, method ->
                    var result = chain.proceed(chain.args.toTypedArray())

                    val config = readConfig()
                    if (config != null && config.optBoolean("active", false)) {
                        result = 0
                    }

                    return@hookMethod result
                }
            } catch (e: Throwable) {
            }
            try {
                XposedHelpers.hookMethod(
                    amapLocClass,
                    classLoader,
                    "getMockType"
                ) { chain, method ->
                    var result = chain.proceed(chain.args.toTypedArray())

                    val config = readConfig()
                    if (config != null && config.optBoolean("active", false)) {
                        result = 0
                    }

                    return@hookMethod result
                }
            } catch (e: Throwable) {
            }
            // 3. isMocked() -> false（AMap SDK 12.0+ 新接口）
            try {
                XposedHelpers.hookMethod(
                    amapLocClass,
                    classLoader,
                    "isMocked"
                ) { chain, method ->
                    var result = chain.proceed(chain.args.toTypedArray())

                    val config = readConfig()
                    if (config != null && config.optBoolean("active", false)) {
                        result = false
                    }

                    return@hookMethod result
                }
            } catch (e: Throwable) {
            }
            // 4. getErrorCode() -> 0（非0表示定位失败）
            XposedHelpers.hookMethod(
                amapLocClass,
                classLoader,
                "getErrorCode"
            ) { chain, method ->
                var result = chain.proceed(chain.args.toTypedArray())

                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    result = 0
                }

                return@hookMethod result
            }
            // 5. getLocationType() -> 动态保留网络定位类型，否则强制返回GPS类型（1）
            try {
                XposedHelpers.hookMethod(
                    amapLocClass, classLoader, "getLocationType"
                ) { chain, method ->
                    var result = chain.proceed(chain.args.toTypedArray())
                    val config = readConfig()
                    if (config != null && config.optBoolean("active", false)) {
                        val originalLocationType = result as? Int ?: 1
                        if (originalLocationType == 5 || originalLocationType == 6 || originalLocationType == 12) {
                            result = originalLocationType
                        } else {
                            result = 1
                        }
                    }
                    return@hookMethod result
                }
            } catch (e: Throwable) {
                XposedBridge.log(e)
            }

            // 6. getProvider() -> 动态保留网络提供者，否则强制返回"gps"
            try {
                XposedHelpers.hookMethod(
                    amapLocClass, classLoader, "getProvider"
                ) { chain, method ->
                    var result = chain.proceed(chain.args.toTypedArray())
                    val config = readConfig()
                    if (config != null && config.optBoolean("active", false)) {
                        val originalProvider = result as? String ?: "gps"
                        if (originalProvider == "network" || originalProvider.contains(
                                "wifi",
                                ignoreCase = true
                            )
                        ) {
                            result = originalProvider
                        } else {
                            result = "gps"
                        }
                    }
                    return@hookMethod result
                }
            } catch (e: Throwable) {
                XposedBridge.log(e)
            }

            // 7. 直接写底层 mock 相关字段（防反射读字段绕过 getter）
            try {
                XposedHelpers.hookMethod(
                    amapLocClass,
                    classLoader,
                    "getLatitude"
                ) { chain, method ->
                    var result = chain.proceed(chain.args.toTypedArray())

                    val config = readConfig()
                    if (config != null && config.optBoolean("active", false)) {
                        val obj = chain.thisObject ?: return@hookMethod result
                        try {
                            XposedHelpers.setObjectField(obj, "mockData", null)
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setIntField(obj, "mockFlag", 0)
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setIntField(obj, "mockType", 0)
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setBooleanField(obj, "isMocked", false)
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setBooleanField(obj, "mMock", false)
                        } catch (e: Throwable) {
                        }
                        try {
                            XposedHelpers.setIntField(obj, "errorCode", 0)
                        } catch (e: Throwable) {
                        }
                    }
                    return@hookMethod result
                }
            } catch (e: Throwable) {
                XposedBridge.log(e)
            }

            // 8. AMapLocationQualityReport 质量报告也要清零
            val qualityClazz = XposedHelpers.findClassIfExists(
                "com.amap.api.location.AMapLocationQualityReport", classLoader
            )
            if (qualityClazz != null) {
                try {
                    XposedHelpers.hookAllMethods(qualityClazz, "getMockInfo") { chain, method ->
                        var result = chain.proceed(chain.args.toTypedArray())

                        val config = readConfig()
                        if (config != null && config.optBoolean("active", false)) {
                            result = null
                        }

                        return@hookAllMethods result
                    }
                } catch (e: Throwable) {
                }
                try {
                    XposedHelpers.hookAllMethods(
                        qualityClazz,
                        "isMockLocation"
                    ) { chain, method ->
                        var result = chain.proceed(chain.args.toTypedArray())

                        val config = readConfig()
                        if (config != null && config.optBoolean("active", false)) {
                            result = false
                        }

                        return@hookAllMethods result
                    }
                } catch (e: Throwable) {
                }
            }

            // 9. setMockEnable(false) 让高德SDK禁用自身的 mock 校验流程
            val clientClazz = XposedHelpers.findClassIfExists(
                "com.amap.api.location.AMapLocationClient", classLoader
            )
            if (clientClazz != null) {
                try {
                    XposedHelpers.hookAllMethods(
                        clientClazz, "setMockEnable"
                    ) { chain, method ->

                        val config = readConfig()
                        if (config != null && config.optBoolean("active", false)) {
                            // 强制设为 true，让高德自己相信当前位置是真实的
                            chain.args[0] = true
                        }
                        return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                    }
                } catch (e: Throwable) {
                }

                try {
                    XposedHelpers.hookAllMethods(
                        clientClazz, "setLocationListener"
                    ) { chain, method ->

                        val listener = chain.args[0]
                        if (listener != null) {
                            try {
                                if (LocationHooker.hasTypeByName(
                                        listener.javaClass,
                                        "com.amap.api.location.AMapLocationListener"
                                    )
                                ) {
                                    capturedAMapListeners.addIfAbsent(listener)
                                }
                            } catch (e: Throwable) {
                            }
                        }
                        return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                    }
                    XposedHelpers.hookAllMethods(
                        clientClazz, "unRegisterLocationListener"
                    ) { chain, method ->

                        val listener = chain.args[0]
                        if (listener != null) {
                            capturedAMapListeners.remove(listener)
                        }

                        return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                    }
                } catch (e: Throwable) {
                }
            }
        } else {
            XposedBridge.log("[LocationSpoofer] AMapLocation class not found in ${classLoader}, skipping AMap hooks")
        }

    } catch (e: Throwable) {
        XposedBridge.log(e)
    }

    // ── 第三方地图SDK深度Hook(腾讯/百度) ──
    hookTencentSDK(classLoader)
    hookBaiduSDK(classLoader)
}

/**
 * 腾讯定位SDK深度Hook
 *
 * 架构分析:
 * TencentLocation在腾讯SDK中是一个**接口(interface)**,不是具体类。
 * 其方法签名为: public interface TencentLocation { double getLatitude(); ... }
 * Xposed的findAndHookMethod无法Hook接口方法(接口没有方法体),
 * 必须找到实现该接口的具体类并对其进行Hook。
 *
 * 腾讯SDK常见的实现类名(不同版本可能不同):
 * - com.tencent.map.geolocation.internal.TencentLocationImpl
 * - com.tencent.map.geolocation.TencentLocationImpl
 * - 部分版本使用ProGuard混淆后类名不固定
 *
 * 策略: 先尝试已知实现类名,若均不存在则降级为hookAllMethods扫描所有实现。
 *
 * 坐标系: GCJ-02(与高德相同)
 */
internal fun LocationHooker.hookGnssStatus(classLoader: ClassLoader) {
    try {
        val locationManagerClazz =
            XposedHelpers.findClass("android.location.LocationManager", classLoader)

        // Hook 注册 GpsStatusListener
        try {
            XposedHelpers.hookAllMethods(
                locationManagerClazz,
                "addGpsStatusListener"
            ) { chain, method ->

                val listener = chain.args[0]
                if (listener != null) {
                    val clazz = listener.javaClass
                    if (hookedCallbackClasses.putIfAbsent(clazz, true) == null) {
                        try {
                            var lastCallTime = 0L
                            XposedHelpers.hookAllMethods(
                                clazz,
                                "onGpsStatusChanged"
                            ) { innerChain, innerMethod ->
                                val event = innerChain.args[0] as? Int
                                if (event == 4) { // GPS 卫星状态事件
                                    val now = System.currentTimeMillis()
                                    if (now - lastCallTime < 1000) {
                                        return@hookAllMethods null // 限制频率 (防止卡顿)
                                    } else {
                                        lastCallTime = now
                                    }
                                }

                                return@hookAllMethods innerChain.proceed(innerChain.args.toTypedArray())
                            }
                        } catch (e: Throwable) {
                        }
                    }
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (e: Throwable) {
            XposedBridge.log(e)
        }

        // Hook 取消注册 GpsStatusListener
        try {
            XposedHelpers.hookAllMethods(
                locationManagerClazz,
                "removeGpsStatusListener"
            ) { chain, method ->

                // 不再需要

                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (e: Throwable) {
        }

        // Hook 注册 GnssStatusCallback
        try {
            XposedHelpers.hookAllMethods(
                locationManagerClazz,
                "registerGnssStatusCallback"
            ) { chain, method ->
                var callbackObj: Any? = null
                for (arg in chain.args) {
                    if (arg != null && LocationHooker.hasTypeByName(
                            arg.javaClass,
                            "android.location.GnssStatus\$Callback"
                        )
                    ) {
                        callbackObj = arg
                        break
                    }
                }
                if (callbackObj != null) {
                    val clazz = callbackObj.javaClass
                    if (hookedCallbackClasses.putIfAbsent(clazz, true) == null) {
                        try {
                            var lastCallTime = 0L
                            var cachedGnssStatus: Any? = null
                            var lastGnssStatusUpdate = 0L
                            XposedHelpers.hookAllMethods(
                                clazz,
                                "onSatelliteStatusChanged"
                            ) { innerChain, innerMethod ->
                                val now = System.currentTimeMillis()
                                if (now - lastCallTime < 1000) {
                                    return@hookAllMethods null // 限制频率 (防止卡顿)
                                }
                                lastCallTime = now
                                val statusObj =
                                    innerChain.args[0] ?: return@hookAllMethods innerChain.proceed(
                                        innerChain.args.toTypedArray()
                                    )
                                updateSatelliteCacheIfNeeded()
                                if (isSpoofingActiveCache && cachedSatellites != null) {
                                    val count = spoofingCountCache
                                    val sats = cachedSatellites!!
                                    // 1. Android 11+
                                    try {
                                        val builderClass = XposedHelpers.findClassIfExists(
                                            "android.location.GnssStatus\$Builder",
                                            clazz.classLoader
                                        )
                                        if (builderClass != null) {
                                            if (cachedGnssStatus == null || now - lastGnssStatusUpdate > 1000) {
                                                val builder = builderClass.getDeclaredConstructor()
                                                    .newInstance()
                                                val addMethod = builderClass.getDeclaredMethod(
                                                    "addSatellite",
                                                    Int::class.javaPrimitiveType,
                                                    Int::class.javaPrimitiveType,
                                                    Float::class.javaPrimitiveType,
                                                    Float::class.javaPrimitiveType,
                                                    Float::class.javaPrimitiveType,
                                                    Boolean::class.javaPrimitiveType,
                                                    Boolean::class.javaPrimitiveType,
                                                    Boolean::class.javaPrimitiveType,
                                                    Boolean::class.javaPrimitiveType,
                                                    Float::class.javaPrimitiveType,
                                                    Boolean::class.javaPrimitiveType,
                                                    Float::class.javaPrimitiveType
                                                )
                                                addMethod.isAccessible = true
                                                for (i in 0 until count) {
                                                    val sat = sats[i]
                                                    addMethod.invoke(
                                                        builder,
                                                        sat.type,
                                                        sat.svid,
                                                        sat.cn0,
                                                        sat.elevation,
                                                        sat.azimuth,
                                                        true,
                                                        true,
                                                        sat.usedInFix,
                                                        false,
                                                        0f,
                                                        false,
                                                        0f
                                                    )
                                                }
                                                val buildMethod =
                                                    builderClass.getDeclaredMethod("build")
                                                buildMethod.isAccessible = true
                                                cachedGnssStatus = buildMethod.invoke(builder)
                                                lastGnssStatusUpdate = now
                                            }
                                            innerChain.args[0] = cachedGnssStatus
                                            return@hookAllMethods innerChain.proceed(innerChain.args.toTypedArray())
                                        }
                                    } catch (e: Throwable) {
                                    }

                                    // 2. Android 10-
                                    try {
                                        val countField =
                                            statusObj::class.java.getDeclaredField("mSvCount")
                                        countField.isAccessible = true
                                        countField.setInt(statusObj, count)

                                        val cn0DbHzs = FloatArray(count)
                                        val elevations = FloatArray(count)
                                        val azimuths = FloatArray(count)
                                        val svidWithFlags = IntArray(count)

                                        for (i in 0 until count) {
                                            val sat = sats[i]
                                            cn0DbHzs[i] = sat.cn0
                                            elevations[i] = sat.elevation
                                            azimuths[i] = sat.azimuth
                                            var flags = 1 or 2
                                            if (sat.usedInFix) flags = flags or 4
                                            svidWithFlags[i] =
                                                (sat.svid shl 8) or (sat.type and 0xF) or (flags shl 4)
                                        }

                                        val setField = { name: String, value: Any ->
                                            try {
                                                val f = statusObj::class.java.getDeclaredField(name)
                                                f.isAccessible = true
                                                f.set(statusObj, value)
                                            } catch (e: Exception) {
                                            }
                                        }

                                        setField("mSvidWithFlags", svidWithFlags)
                                        setField("mCn0DbHz", cn0DbHzs)
                                        setField("mElevations", elevations)
                                        setField("mAzimuths", azimuths)
                                        setField("mCarrierFrequencies", FloatArray(count))
                                        setField("mBasebandCn0DbHzs", FloatArray(count))
                                    } catch (e: Exception) {
                                    }
                                }
                                return@hookAllMethods innerChain.proceed(innerChain.args.toTypedArray())
                            }
                        } catch (e: Throwable) {
                        }
                    }
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (e: Throwable) {
            XposedBridge.log(e)
        }

        // Hook 取消注册 GnssStatusCallback
        try {
            XposedHelpers.hookAllMethods(
                locationManagerClazz,
                "unregisterGnssStatusCallback"
            ) { chain, method ->

                // 不再需要

                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (e: Throwable) {
        }

        // Hook GpsStatus.getSatellites() 以适配像 DevCheck 这样的旧应用
        try {
            XposedHelpers.hookMethod(
                "android.location.GpsStatus",
                classLoader,
                "getSatellites"
            ) { chain, method ->
                var result = chain.proceed(chain.args.toTypedArray())
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    result = createSpoofedGpsSatellites(classLoader)
                }
                return@hookMethod result
            }
        } catch (e: Throwable) {
            XposedBridge.log(e)
        }


        XposedBridge.log("[LocationSpoofer] GnssStatus hooks installed")
    } catch (e: Throwable) {
        XposedBridge.log("[LocationSpoofer] GnssStatus hook failed: $e")
    }

    // 确保配置轮询守护进程在所有被 Hook 的应用中立即启动
    readConfig()
}


internal fun LocationHooker.createSpoofedGpsSatellites(classLoader: ClassLoader): Iterable<Any> {
    val now = System.currentTimeMillis()
    if (cachedGpsSatellitesList == null || now - lastGpsSatellitesUpdate > 1000) {
        val list = ArrayList<Any>()
        try {
            updateSatelliteCacheIfNeeded()
            if (!isSpoofingActiveCache || cachedSatellites == null) return list

            val satelliteClass = classLoader.loadClass("android.location.GpsSatellite")
            val constructor =
                satelliteClass.getDeclaredConstructor(Int::class.javaPrimitiveType)
            constructor.isAccessible = true

            for (i in 0 until spoofingCountCache) {
                val data = getCachedSatellite(i) ?: continue
                val prn = if (data.type == 3) data.svid + 64 else data.svid
                val sat = constructor.newInstance(prn)
                try {
                    XposedHelpers.setBooleanField(sat, "mValid", true)
                } catch (e: Throwable) {
                }
                try {
                    XposedHelpers.setBooleanField(sat, "mHasEphemeris", true)
                } catch (e: Throwable) {
                }
                try {
                    XposedHelpers.setBooleanField(sat, "mHasAlmanac", true)
                } catch (e: Throwable) {
                }
                try {
                    XposedHelpers.setBooleanField(sat, "mUsedInFix", data.usedInFix)
                } catch (e: Throwable) {
                }
                try {
                    XposedHelpers.setObjectField(sat, "mSnr", data.cn0)
                } catch (_: Throwable) {
                }
                try {
                    XposedHelpers.setObjectField(sat, "mElevation", data.elevation)
                } catch (_: Throwable) {
                }
                try {
                    XposedHelpers.setObjectField(sat, "mAzimuth", data.azimuth)
                } catch (_: Throwable) {
                }
                list.add(sat)
            }
            cachedGpsSatellitesList = list
            lastGpsSatellitesUpdate = now
        } catch (e: Throwable) {
            XposedBridge.log(e)
        }
    }
    return cachedGpsSatellitesList ?: ArrayList()
}

internal fun LocationHooker.createOnNmeaMessageListenerProxy(
    original: Any,
    classLoader: ClassLoader
): Any {
    val interfaceClass = classLoader.loadClass("android.location.OnNmeaMessageListener")
    val proxy = Proxy.newProxyInstance(
        classLoader,
        arrayOf(interfaceClass),
        object : InvocationHandler {
            override fun invoke(
                proxy: Any,
                method: Method,
                args: Array<out Any>?
            ): Any? {
                if (method.name == "onNmeaMessage" && args != null && args.size >= 1) {
                    val originalMsg = args[0] as? String
                    if (originalMsg != null) {
                        val spoofedMsg = spoofNmeaMessage(originalMsg)
                        if (spoofedMsg == null) return null // 允许丢弃消息
                        val newArgs = arrayOfNulls<Any>(args.size)
                        for (i in args.indices) {
                            newArgs[i] = if (i == 0) spoofedMsg else args[i]
                        }
                        return method.invoke(original, *newArgs)
                    }
                }
                val methodArgs =
                    if (args == null) emptyArray<Any>() else Array(args.size) { i -> args[i] }
                return method.invoke(original, *methodArgs)
            }
        }
    )
    // startNmeaGsvInjector(original, "onNmeaMessage", classLoader)
    return proxy
}

internal fun LocationHooker.createGpsStatusNmeaListenerProxy(
    original: Any,
    classLoader: ClassLoader
): Any {
    val interfaceClass = classLoader.loadClass("android.location.GpsStatus\$NmeaListener")
    val proxy = Proxy.newProxyInstance(
        classLoader,
        arrayOf(interfaceClass),
        object : InvocationHandler {
            override fun invoke(
                proxy: Any,
                method: Method,
                args: Array<out Any>?
            ): Any? {
                if (method.name == "onNmeaReceived" && args != null && args.size >= 2) {
                    val originalMsg = args[1] as? String
                    if (originalMsg != null) {
                        val spoofedMsg = spoofNmeaMessage(originalMsg)
                        if (spoofedMsg == null) return null // 允许丢弃消息
                        val newArgs = arrayOfNulls<Any>(args.size)
                        for (i in args.indices) {
                            newArgs[i] = if (i == 1) spoofedMsg else args[i]
                        }
                        return method.invoke(original, *newArgs)
                    }
                }
                val methodArgs =
                    if (args == null) emptyArray<Any>() else Array(args.size) { i -> args[i] }
                return method.invoke(original, *methodArgs)
            }
        }
    )
    // startNmeaGsvInjector(original, "onNmeaReceived", classLoader)
    return proxy
}

internal fun LocationHooker.spoofNmeaMessage(sentence: String): String? {
    try {
        val config = readConfig() ?: return sentence
        if (!config.optBoolean("active", false)) return sentence

        val targetLat = config.optDouble("wgs84_lat", 0.0)
        val targetLng = config.optDouble("wgs84_lng", 0.0)
        if (targetLat == 0.0 && targetLng == 0.0) return sentence

        val parts = sentence.split("*")
        val mainPart = parts[0]
        val fields = mainPart.split(",").toMutableList()
        if (fields.isEmpty()) return sentence

        val type = fields[0]
        var modified = false

        // 丢弃 GSV 语句以隐藏真实的硬件卫星。
        // （我们移除了伪造的 GSV 注入器，因此应用程序将根本看不到 GSV 语句，
        // 这比显示与我们伪造的 GnssStatus 冲突的真实硬件 GSV 语句更安全）。
        if (type.endsWith("GSV")) {
            return null
        }

        if (type.endsWith("RMC") && fields.size >= 7) {
            val (latStr, latDir) = convertToNmeaLatitude(targetLat)
            val (lngStr, lngDir) = convertToNmeaLongitude(targetLng)
            fields[3] = latStr
            fields[4] = latDir
            fields[5] = lngStr
            fields[6] = lngDir
            modified = true
        } else if (type.endsWith("GGA") && fields.size >= 6) {
            val (latStr, latDir) = convertToNmeaLatitude(targetLat)
            val (lngStr, lngDir) = convertToNmeaLongitude(targetLng)
            fields[2] = latStr
            fields[3] = latDir
            fields[4] = lngStr
            fields[5] = lngDir
            modified = true
        } else if (type.endsWith("GLL") && fields.size >= 5) {
            val (latStr, latDir) = convertToNmeaLatitude(targetLat)
            val (lngStr, lngDir) = convertToNmeaLongitude(targetLng)
            fields[1] = latStr
            fields[2] = latDir
            fields[3] = lngStr
            fields[4] = lngDir
            modified = true
        }

        if (!modified) return sentence

        val newMainPart = fields.joinToString(",")
        val newChecksum = calculateNmeaChecksum(newMainPart)

        val tail = if (parts.size > 1) {
            val rawTail = parts[1]
            val lineEnding = rawTail.substring(Math.min(2, rawTail.length))
            "*$newChecksum$lineEnding"
        } else {
            "*$newChecksum"
        }
        return newMainPart + tail
    } catch (e: Exception) {
        XposedBridge.log(e)
        return sentence
    }
}

internal fun LocationHooker.convertToNmeaLatitude(lat: Double): Pair<String, String> {
    val absLat = Math.abs(lat)
    val degrees = absLat.toInt()
    val minutes = (absLat - degrees) * 60.0
    val latStr = String.format(java.util.Locale.US, "%02d%08.5f", degrees, minutes)
    val dir = if (lat >= 0) "N" else "S"
    return Pair(latStr, dir)
}

internal fun LocationHooker.convertToNmeaLongitude(lng: Double): Pair<String, String> {
    val absLng = Math.abs(lng)
    val degrees = absLng.toInt()
    val minutes = (absLng - degrees) * 60.0
    val lngStr = String.format(java.util.Locale.US, "%03d%08.5f", degrees, minutes)
    val dir = if (lng >= 0) "E" else "W"
    return Pair(lngStr, dir)
}

internal fun LocationHooker.calculateNmeaChecksum(sentence: String): String {
    var checksum = 0
    val startIndex = if (sentence.startsWith("$")) 1 else 0
    val endIndex = sentence.indexOf('*')
    val limit = if (endIndex != -1) endIndex else sentence.length
    for (i in startIndex until limit) {
        checksum = checksum xor sentence[i].code
    }
    return String.format(java.util.Locale.US, "%02X", checksum)
}

data class SatelliteData(
    val svid: Int,
    val type: Int, // 1=GPS, 3=GLONASS, 5=BDS
    val elevation: Float,
    val azimuth: Float,
    val cn0: Float,
    val usedInFix: Boolean
)