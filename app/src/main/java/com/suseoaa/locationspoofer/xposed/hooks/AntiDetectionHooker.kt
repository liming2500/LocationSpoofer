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
import io.github.libxposed.api.*

/**
 * 反检测模块 (Anti-Detection Hooker)
 *
 * 上下文:
 * 部分应用 (如企业微信、钉钉、打卡应用) 会尝试通过系统 API 检测是否存在 "模拟位置" (Mock Location)。
 *
 * 作用:
 * 本模块在系统 API 层级拦截对模拟位置开关与 AppOps 权限的探测。
 *
 * 设计原则:
 * 1. 绝不 Hook java.lang.ClassLoader.loadClass / java.lang.Class.forName / Throwable.getStackTrace，
 *    避免在 ART (Android 14/15/16) 运行时引发类加载器锁死锁、ANR 卡死和崩溃。
 * 2. 仅拦截系统公开的模拟位置相关服务 (AppOpsManager 与 Settings.Secure)。
 */
internal fun LocationHooker.hookAntiDetection(classLoader: ClassLoader) {

    // 1. 拦截 AppOpsManager 的 OP_MOCK_LOCATION (58)
    try {
        val appOpsClass = XposedHelpers.findClassIfExists("android.app.AppOpsManager", classLoader)
        if (appOpsClass != null) {
            try {
                XposedHelpers.hookAllMethods(appOpsClass, "checkOp") { chain, method ->
                    val config = readConfig()
                    if (config == null || !config.optBoolean("active", false)) {
                        return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                    }
                    val opArg = chain.args.getOrNull(0)
                    val isMockOp = if (opArg is Int) opArg == 58 else if (opArg is String) opArg == "android:mock_location" else false
                    if (isMockOp) return@hookAllMethods 1 // MODE_IGNORED
                    return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                }
            } catch (_: Throwable) {}

            try {
                XposedHelpers.hookAllMethods(appOpsClass, "checkOpNoThrow") { chain, method ->
                    val config = readConfig()
                    if (config == null || !config.optBoolean("active", false)) {
                        return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                    }
                    val opArg = chain.args.getOrNull(0)
                    val isMockOp = if (opArg is Int) opArg == 58 else if (opArg is String) opArg == "android:mock_location" else false
                    if (isMockOp) return@hookAllMethods 1 // MODE_IGNORED
                    return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                }
            } catch (_: Throwable) {}

            try {
                XposedHelpers.hookAllMethods(appOpsClass, "noteOp") { chain, method ->
                    val config = readConfig()
                    if (config == null || !config.optBoolean("active", false)) {
                        return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                    }
                    val opArg = chain.args.getOrNull(0)
                    val isMockOp = if (opArg is Int) opArg == 58 else if (opArg is String) opArg == "android:mock_location" else false
                    if (isMockOp) return@hookAllMethods 1 // MODE_IGNORED
                    return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                }
            } catch (_: Throwable) {}

            try {
                XposedHelpers.hookAllMethods(appOpsClass, "noteOpNoThrow") { chain, method ->
                    val config = readConfig()
                    if (config == null || !config.optBoolean("active", false)) {
                        return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                    }
                    val opArg = chain.args.getOrNull(0)
                    val isMockOp = if (opArg is Int) opArg == 58 else if (opArg is String) opArg == "android:mock_location" else false
                    if (isMockOp) return@hookAllMethods 1 // MODE_IGNORED
                    return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                }
            } catch (_: Throwable) {}
        }
    } catch (e: Throwable) {
        XposedBridge.log(e)
    }

    // 2. 拦截 Settings.Secure 的 mock_location 开关查询
    try {
        try {
            XposedHelpers.hookMethod(
                "android.provider.Settings\$Secure", classLoader, "getInt",
                android.content.ContentResolver::class.java,
                String::class.java
            ) { chain, method ->
                val config = readConfig()
                if (config == null || !config.optBoolean("active", false)) {
                    return@hookMethod chain.proceed(chain.args.toTypedArray())
                }
                val name = chain.args.getOrNull(1) as? String
                if (name == "mock_location") {
                    return@hookMethod 0
                }
                return@hookMethod chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}

        try {
            XposedHelpers.hookMethod(
                "android.provider.Settings\$Secure", classLoader, "getInt",
                android.content.ContentResolver::class.java,
                String::class.java,
                Int::class.javaPrimitiveType!!
            ) { chain, method ->
                val config = readConfig()
                if (config == null || !config.optBoolean("active", false)) {
                    return@hookMethod chain.proceed(chain.args.toTypedArray())
                }
                val name = chain.args.getOrNull(1) as? String
                if (name == "mock_location") {
                    return@hookMethod 0
                }
                return@hookMethod chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}

        try {
            XposedHelpers.hookMethod(
                "android.provider.Settings\$Secure", classLoader, "getString",
                android.content.ContentResolver::class.java,
                String::class.java
            ) { chain, method ->
                val config = readConfig()
                if (config == null || !config.optBoolean("active", false)) {
                    return@hookMethod chain.proceed(chain.args.toTypedArray())
                }
                val name = chain.args.getOrNull(1) as? String
                if (name == "mock_location") {
                    return@hookMethod "0"
                }
                return@hookMethod chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}
    } catch (e: Throwable) {
        XposedBridge.log(e)
    }

    // 3. 拦截权限自检 Context / ContextWrapper / ContextImpl
    try {
        val permissionList = setOf(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION",
            "android.permission.BLUETOOTH",
            "android.permission.BLUETOOTH_ADMIN",
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.BLUETOOTH_ADVERTISE"
        )
        val contextClasses = listOfNotNull(
            XposedHelpers.findClassIfExists("android.content.Context", classLoader),
            XposedHelpers.findClassIfExists("android.content.ContextWrapper", classLoader),
            XposedHelpers.findClassIfExists("android.app.ContextImpl", classLoader)
        )
        val permMethods = arrayOf("checkPermission", "checkSelfPermission", "checkCallingOrSelfPermission", "checkCallingPermission")
        for (ctxClazz in contextClasses) {
            for (mName in permMethods) {
                try {
                    XposedHelpers.hookAllMethods(ctxClazz, mName) { chain, _ ->
                        val config = readConfig()
                        if (config != null && config.optBoolean("active", false)) {
                            val perm = chain.args.firstOrNull { it is String } as? String
                            if (perm != null && permissionList.contains(perm)) {
                                return@hookAllMethods 0 // PackageManager.PERMISSION_GRANTED
                            }
                        }
                        return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                    }
                } catch (_: Throwable) {}
            }
        }
    } catch (_: Throwable) {}

    // 4. 拦截 AppOpsManager 操作权限自检
    try {
        val appOpsClass = XposedHelpers.findClassIfExists("android.app.AppOpsManager", classLoader)
        if (appOpsClass != null) {
            val opMethods = arrayOf("checkOp", "checkOpNoThrow", "noteOp", "noteOpNoThrow", "noteProxyOp", "noteProxyOpNoThrow")
            for (mName in opMethods) {
                try {
                    XposedHelpers.hookAllMethods(appOpsClass, mName) { chain, _ ->
                        val config = readConfig()
                        if (config != null && config.optBoolean("active", false)) {
                            return@hookAllMethods 0 // AppOpsManager.MODE_ALLOWED
                        }
                        return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                    }
                } catch (_: Throwable) {}
            }
        }
    } catch (_: Throwable) {}

    // 5. 拦截 PackageManager.hasSystemFeature (确保蓝牙LE与定位硬件特性恒支持)
    try {
        val appPmClass = XposedHelpers.findClassIfExists("android.app.ApplicationPackageManager", classLoader)
        val pmClass = XposedHelpers.findClassIfExists("android.content.pm.PackageManager", classLoader)
        for (clazz in listOfNotNull(appPmClass, pmClass)) {
            try {
                XposedHelpers.hookAllMethods(clazz, "hasSystemFeature") { chain, _ ->
                    val feat = chain.args.firstOrNull { it is String } as? String
                    if (feat == "android.hardware.bluetooth_le" || feat == "android.hardware.bluetooth" ||
                        feat == "android.hardware.location.gps" || feat == "android.hardware.location.network") {
                        val config = readConfig()
                        if (config != null && config.optBoolean("active", false)) {
                            return@hookAllMethods true
                        }
                    }
                    return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                }
            } catch (_: Throwable) {}
        }
    } catch (_: Throwable) {}

    XposedBridge.log("[LocationSpoofer] Anti-detection hooks installed")
}

