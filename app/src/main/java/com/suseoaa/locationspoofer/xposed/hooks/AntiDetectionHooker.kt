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

    XposedBridge.log("[LocationSpoofer] Anti-detection hooks installed")
}

