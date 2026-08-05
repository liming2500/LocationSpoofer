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

package com.suseoaa.locationspoofer.xposed.utils

import com.suseoaa.locationspoofer.xposed.LocationHooker
import com.suseoaa.locationspoofer.xposed.utils.*
import com.suseoaa.locationspoofer.xposed.hooks.*
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.lang.reflect.*
import kotlin.math.*
import io.github.libxposed.api.*

/**
 * Xposed 反射辅助工具类 (Xposed Helpers)
 * 
 * 上下文:
 * LSPosed 等 Xposed 框架提供了底层的 Hook 能力，但在实际开发中，直接操作 Java 反射 (Reflection) 
 * 查找类、方法、修改私有字段的过程极其繁琐，且容易抛出异常。
 * 
 * 作用:
 * 提供一层封装，简化对类、方法、字段的查找和修改操作。
 * 关键部分解释:
 * 1. findAndHookMethod / hookAllMethods: 最核心的 Hook 方法，通过它我们可以拦截目标 App 甚至 Android 系统的任意函数调用。
 * 2. getObjectField / setObjectField: 绕过 Java 的 private 权限控制，强行读取或修改任意对象的私有变量。
 *    这在清除系统原生的 `mIsFromMockProvider` 标志位时起到了决定性作用。
 */


object XposedHelpers {
    lateinit var module: XposedModule

    fun findClass(className: String, classLoader: ClassLoader?): Class<*> {
        return Class.forName(className, false, classLoader ?: ClassLoader.getSystemClassLoader())
    }

    fun findClassIfExists(className: String, classLoader: ClassLoader?): Class<*>? {
        return try {
            findClass(className, classLoader)
        } catch (e: Throwable) {
            null
        }
    }

    fun getObjectField(obj: Any, fieldName: String): Any? {
        var clazz: Class<*>? = obj.javaClass
        while (clazz != null) {
            try {
                val f = clazz.getDeclaredField(fieldName)
                f.isAccessible = true
                return f.get(obj)
            } catch (e: NoSuchFieldException) {
                clazz = clazz.superclass
            }
        }
        throw NoSuchFieldException(fieldName)
    }

    fun setObjectField(obj: Any, fieldName: String, value: Any?) {
        var clazz: Class<*>? = obj.javaClass
        while (clazz != null) {
            try {
                val f = clazz.getDeclaredField(fieldName)
                f.isAccessible = true
                f.set(obj, value)
                return
            } catch (e: NoSuchFieldException) {
                clazz = clazz.superclass
            }
        }
        throw NoSuchFieldException(fieldName)
    }

    fun setIntField(obj: Any, fieldName: String, value: Int) {
        setObjectField(obj, fieldName, value)
    }

    fun setDoubleField(obj: Any, fieldName: String, value: Double) {
        setObjectField(obj, fieldName, value)
    }

    fun setBooleanField(obj: Any, fieldName: String, value: Boolean) {
        setObjectField(obj, fieldName, value)
    }

    fun setLongField(obj: Any, fieldName: String, value: Long) {
        setObjectField(obj, fieldName, value)
    }

    fun callMethod(obj: Any, methodName: String, vararg args: Any?): Any? {
        val argTypes = args.map { it?.javaClass ?: Any::class.java }
        var clazz: Class<*>? = obj.javaClass
        while (clazz != null) {
            for (m in clazz.declaredMethods) {
                if (m.name == methodName && m.parameterCount == args.size) {
                    m.isAccessible = true
                    return m.invoke(obj, *args)
                }
            }
            clazz = clazz.superclass
        }
        throw NoSuchMethodException(methodName)
    }

    fun callStaticMethod(clazz: Class<*>, methodName: String, vararg args: Any?): Any? {
        var c: Class<*>? = clazz
        while (c != null) {
            for (m in c.declaredMethods) {
                if (m.name == methodName && m.parameterCount == args.size && Modifier.isStatic(
                        m.modifiers
                    )
                ) {
                    m.isAccessible = true
                    return m.invoke(null, *args)
                }
            }
            c = c.superclass
        }
        throw NoSuchMethodException(methodName)
    }

    fun findMethodExact(
        clazz: Class<*>,
        methodName: String,
        vararg parameterTypes: Class<*>
    ): Method {
        var c: Class<*>? = clazz
        while (c != null) {
            try {
                val m = c.getDeclaredMethod(methodName, *parameterTypes)
                m.isAccessible = true
                return m
            } catch (e: NoSuchMethodException) {
                c = c.superclass
            }
        }
        throw NoSuchMethodException(methodName)
    }

    fun newInstance(clazz: Class<*>, vararg args: Any?): Any {
        for (c in clazz.declaredConstructors) {
            if (c.parameterCount == args.size) {
                c.isAccessible = true
                return c.newInstance(*args)
            }
        }
        throw NoSuchMethodException("Constructor for " + clazz.name + " not found")
    }


    /**
     * 现代 Kotlin DSL 语法的 Hook 封装
     */
    inline fun hookMethod(
        className: String,
        classLoader: ClassLoader?,
        methodName: String,
        vararg paramTypes: Any,
        crossinline interceptor: (io.github.libxposed.api.XposedInterface.Chain, Executable) -> Any?
    ) {
        try {
            val clazz = findClass(className, classLoader)
            val paramTypesClass = paramTypes.map {
                when (it) {
                    is Class<*> -> it
                    is String -> findClass(it, clazz.classLoader)
                    else -> throw IllegalArgumentException("Invalid argument type")
                }
            }.toTypedArray()

            val method = findMethodExact(clazz, methodName, *paramTypesClass)
            module.hook(method).intercept(object : io.github.libxposed.api.XposedInterface.Hooker {
                override fun intercept(chain: io.github.libxposed.api.XposedInterface.Chain): Any? {
                    return interceptor(chain, method)
                }
            })
        } catch (e: Throwable) {
            // 忽略
        }
    }

    /**
     * 现代 Kotlin DSL 语法的 hookAllMethods 封装
     */
    inline fun hookAllMethods(
        clazz: Class<*>,
        methodName: String,
        crossinline interceptor: (io.github.libxposed.api.XposedInterface.Chain, Executable) -> Any?
    ) {
        for (method in clazz.declaredMethods) {
            if (method.name == methodName) {
                try {
                    module.hook(method)
                        .intercept(object : io.github.libxposed.api.XposedInterface.Hooker {
                            override fun intercept(chain: io.github.libxposed.api.XposedInterface.Chain): Any? {
                                return interceptor(chain, method)
                            }
                        })
                } catch (e: Throwable) {
                    // 忽略
                }
            }
        }
    }
}

object XposedBridge {
    private val openCellLogLastTimes = ConcurrentHashMap<String, Long>()

    fun log(msg: String) {}
    fun logOpenCellId(msg: String) {}
    fun logOpenCellId(msg: String, t: Throwable) {}
    fun logOpenCellIdEvery(key: String, msg: String, intervalMs: Long = 10_000L) {}
    fun log(t: Throwable) {}
}

