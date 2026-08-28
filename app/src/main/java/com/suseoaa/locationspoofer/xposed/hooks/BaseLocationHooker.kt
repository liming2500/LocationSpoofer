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


internal fun LocationHooker.getCurrentSpoofedMotion(defaultSystem: String = "WGS-84"): SpoofedMotion? {
    val config = readConfig() ?: return null
    if (!config.optBoolean("active", false)) return null

    val rawMotion = RouteEngine.calculateCurrentPosition(config)
    val finalCoords = getAppTargetCoordinate(rawMotion.lat, rawMotion.lng, config, defaultSystem)
    val jittered = getJitteredLocation(finalCoords.first, finalCoords.second)
    return SpoofedMotion(jittered.first, jittered.second, rawMotion.bearing, rawMotion.speed)
}

internal fun LocationHooker.hookLocationAPIs(classLoader: ClassLoader, currentPkg: String) {
    try {
        // android.location.Location 标准接口: GPS 默认规范输出 WGS-84 坐标
        // 当高德、腾讯、百度或各应用内部 SDK 获取底层 Location 时，会按 WGS-84 进行内部转换加偏。
        // 若应用直接读取则也可在「自定义坐标算法」中强制指定其目标坐标系。
        XposedHelpers.hookMethod(
            "android.location.Location",
            classLoader,
            "getLatitude"
        ) { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())

            if (currentPkg.substringBefore(":") == "com.suseoaa.locationspoofer") return@hookMethod result
            val thisObj = chain.thisObject
            val defaultSys = when {
                thisObj != null && (thisObj.javaClass.name.contains("amap", ignoreCase = true) || thisObj.javaClass.name.contains("autonavi", ignoreCase = true)) -> "GCJ-02"
                thisObj != null && thisObj.javaClass.name.contains("baidu", ignoreCase = true) -> "BD-09"
                thisObj != null && thisObj.javaClass.name.contains("tencent", ignoreCase = true) -> "GCJ-02"
                else -> {
                    val provider = try { (thisObj as? android.location.Location)?.provider } catch (_: Throwable) { null }
                    when (provider?.lowercase()) {
                        "network", "amap", "lbs", "tencent" -> "GCJ-02"
                        "baidu" -> "BD-09"
                        else -> "WGS-84"
                    }
                }
            }
            val motion = getCurrentSpoofedMotion(defaultSys)
            if (motion != null) {
                result = motion.lat
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
            val thisObj = chain.thisObject
            val defaultSys = when {
                thisObj != null && (thisObj.javaClass.name.contains("amap", ignoreCase = true) || thisObj.javaClass.name.contains("autonavi", ignoreCase = true)) -> "GCJ-02"
                thisObj != null && thisObj.javaClass.name.contains("baidu", ignoreCase = true) -> "BD-09"
                thisObj != null && thisObj.javaClass.name.contains("tencent", ignoreCase = true) -> "GCJ-02"
                else -> {
                    val provider = try { (thisObj as? android.location.Location)?.provider } catch (_: Throwable) { null }
                    when (provider?.lowercase()) {
                        "network", "amap", "lbs", "tencent" -> "GCJ-02"
                        "baidu" -> "BD-09"
                        else -> "WGS-84"
                    }
                }
            }
            val motion = getCurrentSpoofedMotion(defaultSys)
            if (motion != null) {
                result = motion.lng
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
            "hasAccuracy"
        ) { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())
            val config = readConfig()
            if (config != null && config.optBoolean("active", false)) {
                result = true
            }
            return@hookMethod result
        }

        XposedHelpers.hookMethod(
            "android.location.Location",
            classLoader,
            "getSpeed"
        ) { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())

            if (currentPkg.substringBefore(":") == "com.suseoaa.locationspoofer") return@hookMethod result
            val motion = getCurrentSpoofedMotion()
            if (motion != null) {
                result = motion.speed
            }

            return@hookMethod result
        }

        XposedHelpers.hookMethod(
            "android.location.Location",
            classLoader,
            "hasSpeed"
        ) { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())
            val config = readConfig()
            if (config != null && config.optBoolean("active", false)) {
                result = true
            }
            return@hookMethod result
        }

        XposedHelpers.hookMethod(
            "android.location.Location",
            classLoader,
            "getBearing"
        ) { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())

            if (currentPkg.substringBefore(":") == "com.suseoaa.locationspoofer") return@hookMethod result
            val motion = getCurrentSpoofedMotion()
            if (motion != null) {
                result = motion.bearing
            }

            return@hookMethod result
        }

        XposedHelpers.hookMethod(
            "android.location.Location",
            classLoader,
            "hasBearing"
        ) { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())
            val config = readConfig()
            if (config != null && config.optBoolean("active", false)) {
                result = true
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
                val baseAlt = config.optDouble("altitude", 25.0)
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

        XposedHelpers.hookMethod(
            "android.location.Location",
            classLoader,
            "hasAltitude"
        ) { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())
            val config = readConfig()
            if (config != null && config.optBoolean("active", false)) {
                result = true
            }
            return@hookMethod result
        }

        // Android 8.0+ 速度、航向、垂直精度扩展接口
        try {
            XposedHelpers.hookMethod("android.location.Location", classLoader, "getSpeedAccuracyMetersPerSecond") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) 0.1f else chain.proceed(chain.args.toTypedArray())
            }
            XposedHelpers.hookMethod("android.location.Location", classLoader, "hasSpeedAccuracy") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) true else chain.proceed(chain.args.toTypedArray())
            }
            XposedHelpers.hookMethod("android.location.Location", classLoader, "getBearingAccuracyDegrees") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) 1.5f else chain.proceed(chain.args.toTypedArray())
            }
            XposedHelpers.hookMethod("android.location.Location", classLoader, "hasBearingAccuracy") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) true else chain.proceed(chain.args.toTypedArray())
            }
            XposedHelpers.hookMethod("android.location.Location", classLoader, "getVerticalAccuracyMeters") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) 1.2f else chain.proceed(chain.args.toTypedArray())
            }
            XposedHelpers.hookMethod("android.location.Location", classLoader, "hasVerticalAccuracy") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) true else chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}

        // 动态注入最新时间戳，防止运动软件（Keep/悦跑圈/高德等）因时间戳陈旧判定为丢弃点
        XposedHelpers.hookMethod(
            "android.location.Location",
            classLoader,
            "getTime"
        ) { chain, method ->
            var result = chain.proceed(chain.args.toTypedArray())
            val config = readConfig()
            if (config != null && config.optBoolean("active", false) && currentPkg.substringBefore(":") != "com.suseoaa.locationspoofer") {
                result = System.currentTimeMillis()
            }
            return@hookMethod result
        }

        try {
            XposedHelpers.hookMethod(
                "android.location.Location",
                classLoader,
                "getElapsedRealtimeNanos"
            ) { chain, method ->
                var result = chain.proceed(chain.args.toTypedArray())
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && currentPkg.substringBefore(":") != "com.suseoaa.locationspoofer") {
                    result = android.os.SystemClock.elapsedRealtimeNanos()
                }
                return@hookMethod result
            }
        } catch (_: Throwable) {}

        try {
            XposedHelpers.hookMethod(
                "android.location.Location",
                classLoader,
                "getElapsedRealtimeMillis"
            ) { chain, method ->
                var result = chain.proceed(chain.args.toTypedArray())
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && currentPkg.substringBefore(":") != "com.suseoaa.locationspoofer") {
                    result = android.os.SystemClock.elapsedRealtime()
                }
                return@hookMethod result
            }
        } catch (_: Throwable) {}

        try {
            XposedHelpers.hookMethod(
                "android.location.Location",
                classLoader,
                "getElapsedRealtimeAgeMillis"
            ) { chain, method ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && currentPkg.substringBefore(":") != "com.suseoaa.locationspoofer") 0L else chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}

        // ★ 拦截 getExtras：注入真实的卫星与定位元数据，防止百度/高德因卫星数为0判定为无定位信号丢弃坐标
        try {
            XposedHelpers.hookMethod(
                "android.location.Location",
                classLoader,
                "getExtras"
            ) { chain, method ->
                var result = chain.proceed(chain.args.toTypedArray())
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && currentPkg.substringBefore(":") != "com.suseoaa.locationspoofer") {
                    val bundle = (result as? android.os.Bundle) ?: android.os.Bundle()
                    val satCount = config.optInt("satellite_count", 20)
                    bundle.putInt("satellites", satCount)
                    bundle.putInt("satellites_in_view", satCount)
                    bundle.putInt("satellites_used_in_fix", satCount.coerceAtLeast(12))
                    bundle.putInt("satellites_visible", satCount)
                    bundle.putBoolean("mockLocation", false)
                    result = bundle
                }
                return@hookMethod result
            }
        } catch (_: Throwable) {}

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

        // ★ 捕获 LocationListener 和 Consumer 以便主动注入模拟位置，并直接Hook其回调方法
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
                    if (className == "java.lang.String" || className == "android.os.Looper" || className == "android.location.Criteria" || className == "android.location.LocationRequest") continue

                    try {
                        val isExcluded = LocationHooker.hasTypeByName(arg.javaClass, "com.amap.api.location.AMapLocationListener") ||
                                LocationHooker.hasTypeByName(arg.javaClass, "com.baidu.location.BDLocationListener") ||
                                LocationHooker.hasTypeByName(arg.javaClass, "com.baidu.location.BDAbstractLocationListener") ||
                                LocationHooker.hasTypeByName(arg.javaClass, "com.tencent.map.geolocation.TencentLocationListener")

                        val isListener = !isExcluded && (LocationHooker.hasTypeByName(
                            arg.javaClass,
                            "android.location.LocationListener"
                        ) || LocationHooker.hasTypeByName(
                            arg.javaClass,
                            "java.util.function.Consumer"
                        ) || LocationHooker.hasTypeByName(
                            arg.javaClass,
                            "androidx.core.util.Consumer"
                        ))

                        if (isListener) {
                            capturedLocationListeners.addIfAbsent(arg)

                            val listenerClazz = arg.javaClass
                            if (hookedCallbackClasses.putIfAbsent(listenerClazz, true) == null) {
                                try {
                                    XposedHelpers.hookAllMethods(listenerClazz, "onLocationChanged") { lChain, _ ->
                                        val config = readConfig()
                                        if (config != null && config.optBoolean("active", false) && currentPkg.substringBefore(":") != "com.suseoaa.locationspoofer") {
                                            if (lChain.args.isNotEmpty()) {
                                                val firstArg = lChain.args[0]
                                                if (firstArg != null) {
                                                    if (firstArg is android.location.Location) {
                                                        val defaultSys = when {
                                                            firstArg.javaClass.name.contains("amap", ignoreCase = true) || firstArg.javaClass.name.contains("autonavi", ignoreCase = true) -> "GCJ-02"
                                                            firstArg.javaClass.name.contains("baidu", ignoreCase = true) -> "BD-09"
                                                            firstArg.javaClass.name.contains("tencent", ignoreCase = true) -> "GCJ-02"
                                                            else -> {
                                                                val provider = try { firstArg.provider } catch (_: Throwable) { null }
                                                                when (provider?.lowercase()) {
                                                                    "network", "amap", "lbs", "tencent" -> "GCJ-02"
                                                                    "baidu" -> "BD-09"
                                                                    else -> "WGS-84"
                                                                }
                                                            }
                                                        }
                                                        val motion = getCurrentSpoofedMotion(defaultSys)
                                                        if (motion != null) {
                                                            firstArg.latitude = motion.lat
                                                            firstArg.longitude = motion.lng
                                                            firstArg.accuracy = getJitteredAccuracy()
                                                            firstArg.speed = motion.speed
                                                            firstArg.bearing = motion.bearing
                                                            firstArg.altitude = config.optDouble("altitude", 25.0)
                                                            firstArg.time = System.currentTimeMillis()
                                                            firstArg.elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
                                                            try {
                                                                val extras = firstArg.extras ?: android.os.Bundle()
                                                                val satCount = config.optInt("satellite_count", 20)
                                                                extras.putInt("satellites", satCount)
                                                                extras.putInt("satellites_in_view", satCount)
                                                                extras.putInt("satellites_used_in_fix", satCount.coerceAtLeast(12))
                                                                extras.putInt("satellites_visible", satCount)
                                                                extras.putBoolean("mockLocation", false)
                                                                firstArg.extras = extras
                                                            } catch (_: Throwable) {}
                                                        }
                                                    } else if (firstArg is List<*>) {
                                                        for (loc in firstArg) {
                                                            if (loc is android.location.Location) {
                                                                val defaultSys = when {
                                                                    loc.javaClass.name.contains("amap", ignoreCase = true) || loc.javaClass.name.contains("autonavi", ignoreCase = true) -> "GCJ-02"
                                                                    loc.javaClass.name.contains("baidu", ignoreCase = true) -> "BD-09"
                                                                    loc.javaClass.name.contains("tencent", ignoreCase = true) -> "GCJ-02"
                                                                    else -> {
                                                                        val provider = try { loc.provider } catch (_: Throwable) { null }
                                                                        when (provider?.lowercase()) {
                                                                            "network", "amap", "lbs", "tencent" -> "GCJ-02"
                                                                            "baidu" -> "BD-09"
                                                                            else -> "WGS-84"
                                                                        }
                                                                    }
                                                                }
                                                                val motion = getCurrentSpoofedMotion(defaultSys)
                                                                if (motion != null) {
                                                                    loc.latitude = motion.lat
                                                                    loc.longitude = motion.lng
                                                                    loc.accuracy = getJitteredAccuracy()
                                                                    loc.speed = motion.speed
                                                                    loc.bearing = motion.bearing
                                                                    loc.altitude = config.optDouble("altitude", 25.0)
                                                                    loc.time = System.currentTimeMillis()
                                                                    loc.elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
                                                                    try {
                                                                        val extras = loc.extras ?: android.os.Bundle()
                                                                        val satCount = config.optInt("satellite_count", 20)
                                                                        extras.putInt("satellites", satCount)
                                                                        extras.putInt("satellites_in_view", satCount)
                                                                        extras.putInt("satellites_used_in_fix", satCount.coerceAtLeast(12))
                                                                        extras.putInt("satellites_visible", satCount)
                                                                        extras.putBoolean("mockLocation", false)
                                                                        loc.extras = extras
                                                                    } catch (_: Throwable) {}
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        return@hookAllMethods lChain.proceed(lChain.args.toTypedArray())
                                    }
                                } catch (_: Throwable) {}
                            }
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
                        capturedLocationListeners.remove(arg)
                    } catch (e: Throwable) {
                    }
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }

            // ★ Hook LocationManager.getCurrentLocation (Android 11+)
            XposedHelpers.hookAllMethods(
                locationManagerClazz,
                "getCurrentLocation"
            ) { chain, method ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && currentPkg.substringBefore(":") != "com.suseoaa.locationspoofer") {
                    val provider = chain.args.getOrNull(0) as? String ?: "gps"
                    val defaultSys = when (provider.lowercase()) {
                        "network", "amap", "lbs", "tencent" -> "GCJ-02"
                        "baidu" -> "BD-09"
                        else -> "WGS-84"
                    }
                    val motion = getCurrentSpoofedMotion(defaultSys)
                    if (motion != null) {
                        var consumerArg: Any? = null
                        var executorArg: java.util.concurrent.Executor? = null
                        for (arg in chain.args) {
                            if (arg is java.util.concurrent.Executor) {
                                executorArg = arg
                            } else if (arg != null && (arg.javaClass.name.contains("Consumer") || LocationHooker.hasTypeByName(arg.javaClass, "java.util.function.Consumer") || LocationHooker.hasTypeByName(arg.javaClass, "androidx.core.util.Consumer"))) {
                                consumerArg = arg
                            }
                        }
                        if (consumerArg != null) {
                            val timeNow = System.currentTimeMillis()
                            val locClass = Class.forName("android.location.Location", false, classLoader)
                            val fakeLoc = locClass.getConstructor(String::class.java).newInstance(if (provider.isNotBlank()) provider else android.location.LocationManager.GPS_PROVIDER)
                            XposedHelpers.callMethod(fakeLoc, "setLatitude", motion.lat)
                            XposedHelpers.callMethod(fakeLoc, "setLongitude", motion.lng)
                            XposedHelpers.callMethod(fakeLoc, "setAccuracy", getJitteredAccuracy())
                            XposedHelpers.callMethod(fakeLoc, "setSpeed", motion.speed)
                            XposedHelpers.callMethod(fakeLoc, "setBearing", motion.bearing)
                            XposedHelpers.callMethod(fakeLoc, "setAltitude", config.optDouble("altitude", 25.0))
                            XposedHelpers.callMethod(fakeLoc, "setTime", timeNow)
                            XposedHelpers.callMethod(fakeLoc, "setElapsedRealtimeNanos", android.os.SystemClock.elapsedRealtimeNanos())
                            try {
                                val extras = android.os.Bundle().apply {
                                    val satCount = config.optInt("satellite_count", 20)
                                    putInt("satellites", satCount)
                                    putInt("satellites_in_view", satCount)
                                    putInt("satellites_used_in_fix", satCount.coerceAtLeast(12))
                                    putInt("satellites_visible", satCount)
                                    putBoolean("mockLocation", false)
                                }
                                XposedHelpers.callMethod(fakeLoc, "setExtras", extras)
                            } catch (_: Throwable) {}
                            try { XposedHelpers.callMethod(fakeLoc, "setIsFromMockProvider", false) } catch (_: Throwable) {}

                            val runDispatch = Runnable {
                                try {
                                    XposedHelpers.callMethod(consumerArg, "accept", fakeLoc)
                                } catch (_: Throwable) {}
                            }
                            if (executorArg != null) {
                                executorArg.execute(runDispatch)
                            } else {
                                android.os.Handler(android.os.Looper.getMainLooper()).post(runDispatch)
                            }
                            return@hookAllMethods null
                        }
                    }
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (e: Throwable) {
            XposedBridge.log("[LocationSpoofer] Failed to hook requestLocationUpdates/getCurrentLocation: $e")
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
                val provider = chain.args.getOrNull(0) as? String ?: "gps"
                val defaultSys = when (provider.lowercase()) {
                    "network", "amap", "lbs", "tencent" -> "GCJ-02"
                    "baidu" -> "BD-09"
                    else -> "WGS-84"
                }
                val motion = getCurrentSpoofedMotion(defaultSys) ?: return@hookAllMethods result
                try {
                    val locClass = Class.forName("android.location.Location", false, classLoader)
                    val fakeLoc = locClass.getConstructor(String::class.java)
                        .newInstance(if (provider.isNotBlank()) provider else android.location.LocationManager.GPS_PROVIDER)
                    XposedHelpers.callMethod(fakeLoc, "setLatitude", motion.lat)
                    XposedHelpers.callMethod(fakeLoc, "setLongitude", motion.lng)
                    XposedHelpers.callMethod(fakeLoc, "setAccuracy", getJitteredAccuracy())
                    XposedHelpers.callMethod(fakeLoc, "setSpeed", motion.speed)
                    XposedHelpers.callMethod(fakeLoc, "setBearing", motion.bearing)
                    XposedHelpers.callMethod(fakeLoc, "setAltitude", config.optDouble("altitude", 25.0))
                    XposedHelpers.callMethod(fakeLoc, "setTime", System.currentTimeMillis())
                    XposedHelpers.callMethod(
                        fakeLoc, "setElapsedRealtimeNanos",
                        android.os.SystemClock.elapsedRealtimeNanos()
                    )
                    try {
                        val extras = android.os.Bundle().apply {
                            val satCount = config.optInt("satellite_count", 20)
                            putInt("satellites", satCount)
                            putInt("satellites_in_view", satCount)
                            putInt("satellites_used_in_fix", satCount.coerceAtLeast(12))
                            putInt("satellites_visible", satCount)
                            putBoolean("mockLocation", false)
                        }
                        XposedHelpers.callMethod(fakeLoc, "setExtras", extras)
                    } catch (_: Throwable) {}
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

        // Hook isLocationEnabled & isProviderEnabled (确保应用自检定位开启状态时恒为 true)
        try {
            val lmClazz = XposedHelpers.findClass("android.location.LocationManager", classLoader)
            XposedHelpers.hookAllMethods(lmClazz, "isLocationEnabled") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && currentPkg.substringBefore(":") != "com.suseoaa.locationspoofer") {
                    return@hookAllMethods true
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }

            XposedHelpers.hookAllMethods(lmClazz, "isProviderEnabled") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && currentPkg.substringBefore(":") != "com.suseoaa.locationspoofer") {
                    return@hookAllMethods true
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }

            XposedHelpers.hookAllMethods(lmClazz, "getAllProviders") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && currentPkg.substringBefore(":") != "com.suseoaa.locationspoofer") {
                    return@hookAllMethods listOf("gps", "network", "passive")
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }

            XposedHelpers.hookAllMethods(lmClazz, "getProviders") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && currentPkg.substringBefore(":") != "com.suseoaa.locationspoofer") {
                    return@hookAllMethods listOf("gps", "network", "passive")
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}

    } catch (e: Throwable) {
        XposedBridge.log(e)
    }

    // 第三方地图SDK深度Hook(高德/腾讯/百度)
    hookAllMapSdks(classLoader)
}

/**
 * GNSS 卫星状态与 NMEA 消息拦截模块
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
                            XposedHelpers.hookAllMethods(
                                clazz,
                                "onGpsStatusChanged"
                            ) { innerChain, innerMethod ->
                                return@hookAllMethods innerChain.proceed(innerChain.args.toTypedArray())
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (e: Throwable) {
            XposedBridge.log(e)
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
                            XposedHelpers.hookAllMethods(
                                clazz,
                                "onSatelliteStatusChanged"
                            ) { innerChain, innerMethod ->
                                val statusObj = innerChain.args[0]
                                val config = readConfig()
                                if (config != null && config.optBoolean("active", false)) {
                                    val count = config.optInt("satellite_count", 20)
                                    val enableJitter = config.optBoolean("enable_jitter", true)
                                    val spoofedStatus = GnssFastMockEngine.getOrCreateSpoofedGnssStatus(
                                        classLoader = clazz.classLoader ?: classLoader,
                                        targetCount = count,
                                        enableJitter = enableJitter,
                                        fallbackOriginalObj = statusObj
                                    )
                                    if (spoofedStatus != null) {
                                        innerChain.args[0] = spoofedStatus
                                    }
                                }
                                return@hookAllMethods innerChain.proceed(innerChain.args.toTypedArray())
                            }
                        } catch (_: Throwable) {
                        }
                    }
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (e: Throwable) {
            XposedBridge.log(e)
        }

        // Hook LocationManager.getGpsStatus 以适配通过 LocationManager 直接拉取卫星的旧版 SDK
        try {
            XposedHelpers.hookAllMethods(
                locationManagerClazz,
                "getGpsStatus"
            ) { chain, method ->
                val result = chain.proceed(chain.args.toTypedArray())
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    val count = config.optInt("satellite_count", 20)
                    val statusObj = result ?: chain.args.firstOrNull()
                    if (statusObj != null) {
                        try {
                            val satellites = GnssFastMockEngine.getOrCreateSpoofedGpsSatellites(classLoader, count)
                            XposedHelpers.setObjectField(statusObj, "mSatellites", satellites)
                        } catch (_: Throwable) {}
                    }
                }
                return@hookAllMethods result
            }
        } catch (_: Throwable) {
        }

        // Hook GpsStatus.getSatellites() 以适配像 DevCheck 这样的旧应用
        try {
            XposedHelpers.hookMethod(
                "android.location.GpsStatus",
                classLoader,
                "getSatellites"
            ) { chain, method ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    val count = config.optInt("satellite_count", 20)
                    return@hookMethod GnssFastMockEngine.getOrCreateSpoofedGpsSatellites(classLoader, count)
                }
                return@hookMethod chain.proceed(chain.args.toTypedArray())
            }
        } catch (e: Throwable) {
            XposedBridge.log(e)
        }

        XposedBridge.log("[LocationSpoofer] GnssStatus hooks installed (High-Performance Engine)")
    } catch (e: Throwable) {
        XposedBridge.log("[LocationSpoofer] GnssStatus hook failed: $e")
    }

    readConfig()
}

internal fun LocationHooker.createSpoofedGpsSatellites(classLoader: ClassLoader): Iterable<Any> {
    val config = readConfig()
    val count = config?.optInt("satellite_count", 20) ?: 20
    return GnssFastMockEngine.getOrCreateSpoofedGpsSatellites(classLoader, count)
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

        val motion = RouteEngine.calculateCurrentPosition(config)
        val targetCoords = getAppTargetCoordinate(motion.lat, motion.lng, config, "WGS-84")
        val targetLat = targetCoords.first
        val targetLng = targetCoords.second

        val parts = sentence.split("*")
        val mainPart = parts[0]
        val fields = mainPart.split(",").toMutableList()
        if (fields.isEmpty()) return sentence

        val type = fields[0]
        var modified = false

        if (type.endsWith("RMC") && fields.size >= 7) {
            val (latStr, latDir) = convertToNmeaLatitude(targetLat)
            val (lngStr, lngDir) = convertToNmeaLongitude(targetLng)
            fields[2] = "A" // Status Active / Valid
            fields[3] = latStr
            fields[4] = latDir
            fields[5] = lngStr
            fields[6] = lngDir
            if (fields.size > 7) {
                val knots = motion.speed * 1.943844f
                fields[7] = String.format(java.util.Locale.US, "%.2f", knots)
            }
            if (fields.size > 8) {
                fields[8] = String.format(java.util.Locale.US, "%.1f", motion.bearing)
            }
            modified = true
        } else if (type.endsWith("GGA") && fields.size >= 6) {
            val (latStr, latDir) = convertToNmeaLatitude(targetLat)
            val (lngStr, lngDir) = convertToNmeaLongitude(targetLng)
            fields[2] = latStr
            fields[3] = latDir
            fields[4] = lngStr
            fields[5] = lngDir
            if (fields.size > 6) fields[6] = "1" // GPS Fix (Quality indicator)
            if (fields.size > 7) fields[7] = config.optInt("satellite_count", 18).toString() // Number of satellites
            if (fields.size > 8) fields[8] = "0.8" // HDOP (High accuracy)
            if (fields.size > 9) fields[9] = String.format(java.util.Locale.US, "%.1f", config.optDouble("altitude", 25.0))
            modified = true
        } else if (type.endsWith("GLL") && fields.size >= 5) {
            val (latStr, latDir) = convertToNmeaLatitude(targetLat)
            val (lngStr, lngDir) = convertToNmeaLongitude(targetLng)
            fields[1] = latStr
            fields[2] = latDir
            fields[3] = lngStr
            fields[4] = lngDir
            if (fields.size > 6) fields[6] = "A"
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