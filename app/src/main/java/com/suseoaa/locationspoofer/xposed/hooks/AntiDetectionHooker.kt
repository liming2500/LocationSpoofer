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
import java.lang.reflect.Member
import kotlin.math.*
import io.github.libxposed.api.*

/**
 * 反检测模块 (Anti-Detection Hooker)
 * 
 * 上下文:
 * 许多 App (如企业微信、钉钉、各种打卡应用) 会尝试检测当前环境是否存在 "模拟位置" (Mock Location)
 * 或者 Xposed 环境。一旦检测到，App 会拒绝打卡甚至封号。
 * 
 * 作用:
 * 本模块负责在系统层级拦截各种常见的反作弊检测手段。
 * 关键部分解释:
 * 1. 堆栈帧过滤: 拦截 getStackTrace，过滤掉 Xposed 框架相关的类名，防止反作弊 SDK 通过堆栈检测到 Hook 环境。
 * 2. 类加载器拦截: 拦截 Class.forName 和 ClassLoader.loadClass，阻止宿主 App 探测已知的 Xposed 类。
 * 3. AppOpsManager 权限拦截: 拦截 OP_MOCK_LOCATION (58)，欺骗 App 认为我们没有模拟位置的权限。
 * 4. 模拟位置开关拦截: 拦截 Settings.Secure 的 mock_location 查询，强行返回关闭状态。
 */


internal fun LocationHooker.hookAntiDetection(classLoader: ClassLoader) {

    // ── 1. 堆栈帧过滤 ──
    // 反作弊SDK通过getStackTrace()检查调用链,发现Xposed帧即判定为Hook环境
    // 只过滤精确匹配的Xposed类名,不影响正常堆栈
    val xposedClassNames = setOf(
        "de.robv.android.xposed.XposedBridge",
        "de.robv.android.xposed.XC_MethodHook",
        "de.robv.android.xposed.XC_MethodReplacement",
        "de.robv.android.xposed.XposedHelpers",
        "de.robv.android.xposed.XC_MethodHook\$MethodHookParam",
        "io.github.libxposed.api.XposedModule",
        "io.github.libxposed.api.XposedInterface",
        "io.github.libxposed.api.XposedModuleInterface",
        "org.lsposed.manager.MainApplication",
        "io.github.lsposed.manager.App"
    )

    try {
        XposedHelpers.hookMethod(
            "java.lang.Throwable", classLoader, "getStackTrace"
        ) { chain, method ->
            val result = chain.proceed(chain.args.toTypedArray())

            @Suppress("UNCHECKED_CAST")
            val stackTrace = result as? Array<StackTraceElement> ?: return@hookMethod result
            val filtered = stackTrace.filter { elem ->
                elem.className !in xposedClassNames
            }.toTypedArray()
            if (filtered.size != stackTrace.size) {
                return@hookMethod filtered
            }
            return@hookMethod result
        }
    } catch (_: Throwable) {
    }

    try {
        XposedHelpers.hookMethod(
            "java.lang.Thread", classLoader, "getStackTrace"
        ) { chain, method ->
            val result = chain.proceed(chain.args.toTypedArray())

            @Suppress("UNCHECKED_CAST")
            val stackTrace = result as? Array<StackTraceElement> ?: return@hookMethod result
            val filtered = stackTrace.filter { elem ->
                elem.className !in xposedClassNames
            }.toTypedArray()
            if (filtered.size != stackTrace.size) {
                return@hookMethod filtered
            }
            return@hookMethod result
        }
    } catch (_: Throwable) {
    }

    // ── 2. Class.forName 精确匹配 ──
    // 反作弊SDK通过Class.forName()尝试加载Xposed类,成功则判定为Hook环境
    // 使用精确匹配(不是contains),只拦截已知Xposed类名
    try {
        XposedHelpers.hookMethod(
            "java.lang.Class", classLoader, "forName",
            String::class.java,
            Boolean::class.javaPrimitiveType!!,
            ClassLoader::class.java
        ) { chain, method ->
            val className = chain.args[0] as? String
                ?: return@hookMethod chain.proceed(chain.args.toTypedArray())
            if (className in xposedClassNames) {
                throw ClassNotFoundException()
            }
            return@hookMethod chain.proceed(chain.args.toTypedArray())
        }
    } catch (_: Throwable) {
        // 降级: 尝试2参数版本
        try {
            XposedHelpers.hookMethod(
                "java.lang.Class", classLoader, "forName",
                String::class.java
            ) { chain, method ->
                val className = chain.args[0] as? String
                    ?: return@hookMethod chain.proceed(chain.args.toTypedArray())
                if (className in xposedClassNames) {
                    throw ClassNotFoundException()
                }
                return@hookMethod chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {
        }
    }

    // ── 3. ClassLoader.loadClass 精确匹配 ──
    // 同样使用精确匹配,只拦截已知Xposed类名
    // loadClass被调用频率很高,精确匹配确保零误杀
    try {
        XposedHelpers.hookMethod(
            "java.lang.ClassLoader", classLoader, "loadClass",
            String::class.java,
            Boolean::class.javaPrimitiveType!!
        ) { chain, method ->
            val className = chain.args[0] as? String
                ?: return@hookMethod chain.proceed(chain.args.toTypedArray())
            if (className in xposedClassNames) {
                throw ClassNotFoundException()
            }
            return@hookMethod chain.proceed(chain.args.toTypedArray())
        }
    } catch (_: Throwable) {
    }

// ── 4. 拦截 AppOpsManager 的 OP_MOCK_LOCATION (58) ──
    // 很多深度定制系统（如 MIUI）和硬核反作弊会检查 AppOps 权限
    try {
        val appOpsClass = XposedHelpers.findClass("android.app.AppOpsManager", classLoader)
        try {
            XposedHelpers.hookAllMethods(appOpsClass, "checkOp") { chain, method ->
                val config = readConfig()
                if (config == null || !config.optBoolean(
                        "active",
                        false
                    )
                ) return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                val opArg = chain.args[0]
                val isMockOp =
                    if (opArg is Int) opArg == 58 else if (opArg is String) opArg == "android:mock_location" else false
                if (isMockOp) return@hookAllMethods 1
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (e: Throwable) {
        }
        try {
            XposedHelpers.hookAllMethods(appOpsClass, "checkOpNoThrow") { chain, method ->
                val config = readConfig()
                if (config == null || !config.optBoolean(
                        "active",
                        false
                    )
                ) return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                val opArg = chain.args[0]
                val isMockOp =
                    if (opArg is Int) opArg == 58 else if (opArg is String) opArg == "android:mock_location" else false
                if (isMockOp) return@hookAllMethods 1
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (e: Throwable) {
        }
        try {
            XposedHelpers.hookAllMethods(appOpsClass, "noteOp") { chain, method ->
                val config = readConfig()
                if (config == null || !config.optBoolean(
                        "active",
                        false
                    )
                ) return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                val opArg = chain.args[0]
                val isMockOp =
                    if (opArg is Int) opArg == 58 else if (opArg is String) opArg == "android:mock_location" else false
                if (isMockOp) return@hookAllMethods 1
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (e: Throwable) {
        }
        try {
            XposedHelpers.hookAllMethods(appOpsClass, "noteOpNoThrow") { chain, method ->
                val config = readConfig()
                if (config == null || !config.optBoolean(
                        "active",
                        false
                    )
                ) return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                val opArg = chain.args[0]
                val isMockOp =
                    if (opArg is Int) opArg == 58 else if (opArg is String) opArg == "android:mock_location" else false
                if (isMockOp) return@hookAllMethods 1
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (e: Throwable) {
        }
    } catch (e: Throwable) {
        XposedBridge.log(e)
    }

    // ── 5. 拦截 Settings.Secure 的 mock_location 开关查询 ──
    try {
        try {
            XposedHelpers.hookMethod(
                "android.provider.Settings\$Secure", classLoader, "getInt",
                android.content.ContentResolver::class.java,
                String::class.java
            ) { chain, method ->
                val config = readConfig()
                if (config == null || !config.optBoolean(
                        "active",
                        false
                    )
                ) return@hookMethod chain.proceed(chain.args.toTypedArray())
                val name = chain.args[1] as? String
                if (name == "mock_location") {
                    if (method.name == "getInt") return@hookMethod 0
                    else if (method.name == "getString") return@hookMethod "0"
                }
                return@hookMethod chain.proceed(chain.args.toTypedArray())
            }
        } catch (e: Throwable) {
        }
        try {
            XposedHelpers.hookMethod(
                "android.provider.Settings\$Secure", classLoader, "getInt",
                android.content.ContentResolver::class.java,
                String::class.java,
                Int::class.javaPrimitiveType!!
            ) { chain, method ->
                val config = readConfig()
                if (config == null || !config.optBoolean(
                        "active",
                        false
                    )
                ) return@hookMethod chain.proceed(chain.args.toTypedArray())
                val name = chain.args[1] as? String
                if (name == "mock_location") {
                    if (method.name == "getInt") return@hookMethod 0
                    else if (method.name == "getString") return@hookMethod "0"
                }
                return@hookMethod chain.proceed(chain.args.toTypedArray())
            }
        } catch (e: Throwable) {
        }
        try {
            XposedHelpers.hookMethod(
                "android.provider.Settings\$Secure", classLoader, "getString",
                android.content.ContentResolver::class.java,
                String::class.java
            ) { chain, method ->
                val config = readConfig()
                if (config == null || !config.optBoolean(
                        "active",
                        false
                    )
                ) return@hookMethod chain.proceed(chain.args.toTypedArray())
                val name = chain.args[1] as? String
                if (name == "mock_location") {
                    if (method.name == "getInt") return@hookMethod 0
                    else if (method.name == "getString") return@hookMethod "0"
                }
                return@hookMethod chain.proceed(chain.args.toTypedArray())
            }
        } catch (e: Throwable) {
        }
    } catch (e: Throwable) {
        XposedBridge.log(e)
    }

    XposedBridge.log("[LocationSpoofer] Anti-detection hooks installed")
}

