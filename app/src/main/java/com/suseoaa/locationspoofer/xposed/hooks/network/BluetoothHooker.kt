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

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.suseoaa.locationspoofer.xposed.LocationHooker
import com.suseoaa.locationspoofer.xposed.utils.*
import org.json.JSONObject
import kotlin.random.Random
import io.github.libxposed.api.*

/**
 * 蓝牙环境伪造模块 (Bluetooth LE Hooker)
 *
 * 上下文:
 * 钉钉、企业微信、飞书等考勤打卡系统广泛采用低功耗蓝牙信标 (BLE Beacon) 进行高精度室内位置验证。
 *
 * 核心优化点:
 * 1. 精确反射实例化 BluetoothDevice 与 ScanResult，避免参数类型不匹配导致构造失败。
 * 2. 所有扫描回调 (onScanResult/onBatchScanResults/onLeScan) 均通过 MainLooper 异步派发，严格遵循 Android Framework 线程契约。
 * 3. 拦截 BluetoothLeScanner.startScan 与 BluetoothAdapter.startLeScan 的全部重载，确保无论使用何种 SDK 均能稳定拦截。
 * 4. 周期性持续心跳分发 (600ms) 并引入真实物理信号抖动 (±2 dBm)。
 * 5. 全量模拟 BluetoothAdapter 状态 (isEnabled, isLeEnabled, getState, getLeState, 各类 BLE 特性支持)。
 * 6. 拦截 BluetoothDevice.getName() / getAlias() / getType()。
 */

private fun hexStringToByteArray(s: String): ByteArray {
    val len = s.length
    val data = ByteArray(len / 2)
    var i = 0
    while (i < len) {
        data[i / 2] =
            ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
        i += 2
    }
    return data
}

internal fun LocationHooker.hookBluetoothLE(
    classLoader: ClassLoader,
    isCoreSystemProcess: Boolean = false
) {
    if (isCoreSystemProcess) {
        XposedBridge.log("[LocationSpoofer] Skipping Bluetooth LE hooks in core system process")
        return
    }

    val mainHandler = Handler(Looper.getMainLooper())
    val scanRecordClass = XposedHelpers.findClassIfExists("android.bluetooth.le.ScanRecord", classLoader)

    // 辅助函数：MAC 地址标准化（补齐冒号以防底层抛出 IllegalArgumentException）
    fun normalizeMacAddress(mac: String): String {
        val clean = mac.replace(":", "").replace("-", "").trim().uppercase()
        if (clean.length == 12 && clean.all { it in "0123456789ABCDEF" }) {
            return clean.chunked(2).joinToString(":")
        }
        return mac.trim().uppercase()
    }

    // 检查字节数组是否为合法的 BLE 广播 AD 结构 (TLV 格式: [Length, Type, Value...])
    fun isValidAdStructure(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        var pos = 0
        var foundValidAd = false
        while (pos < bytes.size) {
            val len = bytes[pos].toInt() and 0xFF
            if (len == 0) {
                // BLE 广播包末尾填充 00 是标准行为
                return foundValidAd
            }
            if (pos + 1 + len > bytes.size) {
                return false
            }
            foundValidAd = true
            pos += 1 + len
        }
        return foundValidAd
    }

    // 检查字节数组是否已包含名称 AD (0x08 简称 或 0x09 全称)
    fun containsAdName(bytes: ByteArray): Boolean {
        var pos = 0
        while (pos < bytes.size) {
            val len = bytes[pos].toInt() and 0xFF
            if (len == 0 || pos + 1 + len > bytes.size) break
            val type = bytes[pos + 1].toInt() and 0xFF
            if (type == 0x08 || type == 0x09) return true
            pos += 1 + len
        }
        return false
    }

    // 辅助函数：智能构造并规范化 BLE 广播包字节数组
    fun buildScanRecordBytes(name: String, hexRecord: String, address: String = ""): ByteArray {
        val cleanHex = hexRecord.replace("0x", "", ignoreCase = true)
            .replace("0X", "")
            .replace(" ", "")
            .replace(":", "")
            .replace("-", "")
            .replace(",", "")
            .replace(";", "")
            .replace("\n", "")
            .replace("\r", "")
            .trim()
            .uppercase()

        var rawPayload: ByteArray? = null

        if (cleanHex.isNotEmpty() && cleanHex.length >= 2) {
            try {
                val validHex = if (cleanHex.length % 2 != 0) cleanHex.substring(0, cleanHex.length - 1) else cleanHex
                val inputBytes = hexStringToByteArray(validHex)

                if (isValidAdStructure(inputBytes)) {
                    // 格式 1: 完整的标准 TLV 结构广播包（例如从系统/抓包工具导出的 0201061AFF...）
                    rawPayload = inputBytes
                } else if (validHex.startsWith("1AFF4C00") || validHex.startsWith("1EFF4C00")) {
                    // 格式 2: 带有长度的厂商数据段，前面缺少 020106 Flags
                    val flags = byteArrayOf(0x02, 0x01, 0x06)
                    rawPayload = flags + inputBytes
                } else if (validHex.startsWith("FF4C00") || validHex.startsWith("FF")) {
                    // 格式 3: 带有 Type(0xFF) 但缺少 Length 的厂商数据段
                    val flags = byteArrayOf(0x02, 0x01, 0x06)
                    val lenByte = byteArrayOf(inputBytes.size.toByte())
                    rawPayload = flags + lenByte + inputBytes
                } else if (validHex.startsWith("4C000215") || validHex.startsWith("004C0215") || validHex.startsWith("4C00")) {
                    // 格式 4: 纯 Apple iBeacon 厂商数据段（从 nRF Connect 等工具复制的 Manufacturer Data）
                    val flags = byteArrayOf(0x02, 0x01, 0x06)
                    val mfrHeader = byteArrayOf((inputBytes.size + 1).toByte(), 0xFF.toByte())
                    rawPayload = flags + mfrHeader + inputBytes
                } else if (validHex.startsWith("0215") && validHex.length >= 46) {
                    // 格式 5: 缺少 Apple 厂商 ID 的 iBeacon 数据（0215 + UUID + Major + Minor + TxPower）
                    val flags = byteArrayOf(0x02, 0x01, 0x06)
                    val mfrHeader = byteArrayOf(0x1A, 0xFF.toByte(), 0x4C, 0x00)
                    rawPayload = flags + mfrHeader + inputBytes
                } else if (validHex.length == 32) {
                    // 格式 6: 纯 16 字节 UUID
                    val flags = byteArrayOf(0x02, 0x01, 0x06)
                    val mfrData = byteArrayOf(
                        0x1A, 0xFF.toByte(), 0x4C, 0x00, 0x02, 0x15
                    ) + inputBytes + byteArrayOf(0x00, 0x01, 0x00, 0x01, 0xC5.toByte())
                    rawPayload = flags + mfrData
                } else {
                    // 格式 7: 其他厂商自定义数据段，自动封装 Flags + 0xFF 结构
                    val flags = byteArrayOf(0x02, 0x01, 0x06)
                    val mfrHeader = byteArrayOf((inputBytes.size + 1).toByte(), 0xFF.toByte())
                    rawPayload = flags + mfrHeader + inputBytes
                }
            } catch (_: Throwable) {}
        }

        if (rawPayload != null && rawPayload.isNotEmpty()) {
            return rawPayload
        }

        // 若用户未填写，自动合成标准的 iBeacon 考勤广播包与名称结构
        val flags = byteArrayOf(0x02, 0x01, 0x06)
        val defaultIBeacon = byteArrayOf(
            0x1A, 0xFF.toByte(), 0x4C, 0x00, 0x02, 0x15,
            0xFD.toByte(), 0xA5.toByte(), 0x06, 0x93.toByte(),
            0xA4.toByte(), 0xE2.toByte(), 0x4F, 0xB1.toByte(),
            0xAF.toByte(), 0xCF.toByte(), 0xC6.toByte(), 0xEB.toByte(),
            0x07, 0x64, 0x78, 0x25,
            0x00, 0x01, 0x00, 0x01, 0xC5.toByte()
        )
        var synthetic = flags + defaultIBeacon
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        if (nameBytes.isNotEmpty() && synthetic.size + nameBytes.size + 2 <= 31) {
            val nameAd = byteArrayOf((nameBytes.size + 1).toByte(), 0x09) + nameBytes
            synthetic = synthetic + nameAd
        }

        return synthetic
    }

    // 辅助函数：安全创建 BluetoothDevice
    fun createBluetoothDevice(cl: ClassLoader, address: String): Any? {
        val cleanAddress = normalizeMacAddress(address)
        val devClass = XposedHelpers.findClassIfExists("android.bluetooth.BluetoothDevice", cl) ?: return null
        
        // 尝试 1: BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address)
        try {
            val adapterClass = XposedHelpers.findClassIfExists("android.bluetooth.BluetoothAdapter", cl)
            if (adapterClass != null) {
                val adapter = XposedHelpers.callStaticMethod(adapterClass, "getDefaultAdapter")
                if (adapter != null) {
                    val dev = XposedHelpers.callMethod(adapter, "getRemoteDevice", cleanAddress)
                    if (dev != null) return dev
                }
            }
        } catch (_: Throwable) {}

        // 尝试 2: 显式构造器 BluetoothDevice(String)
        try {
            val ctor = devClass.getDeclaredConstructor(String::class.java)
            ctor.isAccessible = true
            return ctor.newInstance(cleanAddress)
        } catch (_: Throwable) {}

        // 尝试 3: 反射所有构造器兜底
        for (c in devClass.declaredConstructors) {
            try {
                if (c.parameterTypes.size == 1 && c.parameterTypes[0] == String::class.java) {
                    c.isAccessible = true
                    return c.newInstance(cleanAddress)
                }
            } catch (_: Throwable) {}
        }
        return null
    }

    // 辅助函数：智能判断对象是否为 BLE 扫描回调 (兼容匿名内部类、多层继承与动态代理)
    fun isScanCallback(obj: Any?): Boolean {
        if (obj == null) return false
        if (obj is android.os.Parcelable || obj is String || obj is Number || obj is Boolean) return false
        val cls = obj.javaClass
        val name = cls.name
        if (name.contains("ScanFilter") || name.contains("ScanSettings") || name.contains("PendingIntent")) return false

        var c: Class<*>? = cls
        while (c != null && c != Any::class.java) {
            val cName = c.name
            if (cName == "android.bluetooth.le.ScanCallback" || cName.endsWith("ScanCallback")) {
                return true
            }
            for (iface in c.interfaces) {
                if (iface.name.contains("ScanCallback") || iface.name.contains("LeScanCallback")) {
                    return true
                }
            }
            c = c.superclass
        }
        // 兜底：通过方法名反射检测 (包含 onScanResult 或 onBatchScanResults)
        try {
            if (cls.methods.any { it.name == "onScanResult" || it.name == "onBatchScanResults" } ||
                cls.declaredMethods.any { it.name == "onScanResult" || it.name == "onBatchScanResults" }) {
                return true
            }
        } catch (_: Throwable) {}

        return obj is android.bluetooth.le.ScanCallback || obj is android.bluetooth.BluetoothAdapter.LeScanCallback
    }

    // 辅助函数：智能判断对象是否为旧版 LeScanCallback
    fun isLeScanCallback(obj: Any?): Boolean {
        if (obj == null) return false
        var cls: Class<*>? = obj.javaClass
        while (cls != null && cls != Any::class.java) {
            val name = cls.name
            val sName = cls.simpleName
            if (name == "android.bluetooth.BluetoothAdapter\$LeScanCallback" || sName == "LeScanCallback" || name.endsWith("\$LeScanCallback")) {
                return true
            }
            for (iface in cls.interfaces) {
                if (iface.name.contains("LeScanCallback")) {
                    return true
                }
            }
            cls = cls.superclass
        }
        return obj is android.bluetooth.BluetoothAdapter.LeScanCallback
    }

    // 辅助函数：安全创建 ScanResult
    fun createScanResult(
        cl: ClassLoader,
        device: Any,
        scanRecord: Any?,
        rssi: Int,
        timestampNanos: Long
    ): Any? {
        val scanResultClass = XposedHelpers.findClassIfExists("android.bluetooth.le.ScanResult", cl) ?: return null
        val devClass = XposedHelpers.findClassIfExists("android.bluetooth.BluetoothDevice", cl) ?: return null
        val recordClass = XposedHelpers.findClassIfExists("android.bluetooth.le.ScanRecord", cl)

        var result: Any? = null

        // 尝试 1: 10 参数扩展构造器 (Android 8.0 ~ Android 17 标准)
        // ScanResult(BluetoothDevice, int eventType, int primaryPhy, int secondaryPhy,
        //            int advertisingSid, int txPower, int rssi, int periodicAdvertisingInterval,
        //            ScanRecord, long timestampNanos)
        if (recordClass != null) {
            try {
                val ctor10 = scanResultClass.getDeclaredConstructor(
                    devClass,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    recordClass,
                    Long::class.javaPrimitiveType
                )
                result = ctor10.newInstance(
                    device,
                    0x001B /* DATA_COMPLETE | ET_CONNECTABLE | ET_SCANNABLE | ET_LEGACY_ADV */,
                    1 /* PHY_LE_1M */,
                    0 /* PHY_UNUSED */,
                    255 /* SID_NOT_PRESENT */,
                    127 /* TX_POWER_NOT_PRESENT */,
                    rssi,
                    0 /* periodicAdvertisingInterval */,
                    scanRecord,
                    timestampNanos
                )
            } catch (_: Throwable) {}
        }

        // 尝试 2: 4 参数标准构造器 (ScanResult(BluetoothDevice, ScanRecord, int, long))
        if (result == null && recordClass != null) {
            try {
                val ctor4 = scanResultClass.getDeclaredConstructor(
                    devClass, recordClass, Int::class.javaPrimitiveType, Long::class.javaPrimitiveType
                )
                ctor4.isAccessible = true
                result = ctor4.newInstance(device, scanRecord, rssi, timestampNanos)
            } catch (_: Throwable) {}
        }

        // 尝试 3: 遍历所有可用构造器进行自适应匹配
        if (result == null) {
            for (c in scanResultClass.declaredConstructors) {
                try {
                    c.isAccessible = true
                    val args = arrayOfNulls<Any>(c.parameterCount)
                    for (idx in 0 until c.parameterCount) {
                        val pType = c.parameterTypes[idx]
                        when {
                            devClass.isAssignableFrom(pType) -> args[idx] = device
                            recordClass != null && recordClass.isAssignableFrom(pType) -> args[idx] = scanRecord
                            pType == Int::class.javaPrimitiveType || pType == java.lang.Integer::class.java -> {
                                when (idx) {
                                    1 -> args[idx] = 0x001B
                                    2 -> args[idx] = if (c.parameterCount == 4) rssi else 1
                                    3 -> args[idx] = 0
                                    4 -> args[idx] = 255
                                    5 -> args[idx] = 127
                                    6 -> args[idx] = rssi
                                    else -> args[idx] = if (idx == c.parameterCount - 2) rssi else 0
                                }
                            }
                            pType == Long::class.javaPrimitiveType || pType == java.lang.Long::class.java -> args[idx] = timestampNanos
                            pType == Boolean::class.javaPrimitiveType || pType == java.lang.Boolean::class.java -> args[idx] = false
                            else -> args[idx] = null
                        }
                    }
                    result = c.newInstance(*args)
                    if (result != null) break
                } catch (_: Throwable) {}
            }
        }

        // 强制通过反射回填内部关键属性，确保跨各系统版本属性 100% 完整与正确
        if (result != null) {
            try { XposedHelpers.setObjectField(result, "mDevice", device) } catch (_: Throwable) {}
            try { XposedHelpers.setObjectField(result, "mScanRecord", scanRecord) } catch (_: Throwable) {}
            try { XposedHelpers.setIntField(result, "mRssi", rssi) } catch (_: Throwable) {}
            try { XposedHelpers.setLongField(result, "mTimestampNanos", timestampNanos) } catch (_: Throwable) {}
            try { XposedHelpers.setIntField(result, "mEventType", 0x001B) } catch (_: Throwable) {}
            try { XposedHelpers.setIntField(result, "mPrimaryPhy", 1) } catch (_: Throwable) {}
            try { XposedHelpers.setIntField(result, "mTxPower", 127) } catch (_: Throwable) {}
        }

        return result
    }

    // 辅助函数：安全反射调用 onScanResult
    fun dispatchScanResultToCallback(callback: Any, scanResultObj: Any) {
        var dispatched = false
        var c: Class<*>? = callback.javaClass
        while (c != null && c != Any::class.java) {
            for (m in c.declaredMethods) {
                if (m.name == "onScanResult" && m.parameterCount == 2) {
                    try {
                        m.isAccessible = true
                        m.invoke(callback, 1 /* CALLBACK_TYPE_ALL_MATCHES */, scanResultObj)
                        dispatched = true
                        break
                    } catch (e: Throwable) {
                        XposedBridge.log("[LocationSpoofer] dispatch onScanResult direct error: ${e.cause ?: e}")
                    }
                }
            }
            if (dispatched) break
            c = c.superclass
        }
        if (!dispatched) {
            try {
                XposedHelpers.callMethod(callback, "onScanResult", 1, scanResultObj)
                dispatched = true
            } catch (_: Throwable) {}
        }
        if (!dispatched) {
            try {
                val inner = XposedHelpers.getObjectField(callback, "mScanCallback") ?:
                            XposedHelpers.getObjectField(callback, "mCallback")
                if (inner != null) {
                    dispatchScanResultToCallback(inner, scanResultObj)
                }
            } catch (_: Throwable) {}
        }
    }

    // 辅助函数：安全反射调用 onBatchScanResults
    fun dispatchBatchResultsToCallback(callback: Any, results: List<Any>) {
        var dispatched = false
        var c: Class<*>? = callback.javaClass
        while (c != null && c != Any::class.java) {
            for (m in c.declaredMethods) {
                if (m.name == "onBatchScanResults" && m.parameterCount == 1) {
                    try {
                        m.isAccessible = true
                        m.invoke(callback, results)
                        dispatched = true
                        break
                    } catch (_: Throwable) {}
                }
            }
            if (dispatched) break
            c = c.superclass
        }
        if (!dispatched) {
            try {
                XposedHelpers.callMethod(callback, "onBatchScanResults", results)
            } catch (_: Throwable) {}
        }
    }

    // BLE 扫描结果伪造的核心分发逻辑
    fun deliverBleScanResults(config: JSONObject, callback: Any, cl: ClassLoader) {
        if (!config.optBoolean("mock_bluetooth", true)) return
        val bluetoothArray = config.optJSONArray("bluetooth_json") ?: return
        if (bluetoothArray.length() == 0) return

        val results = java.util.ArrayList<Any>()
        val targetScanRecordClass = XposedHelpers.findClassIfExists("android.bluetooth.le.ScanRecord", cl) ?: scanRecordClass
        val timestampNanos = SystemClock.elapsedRealtimeNanos()

        for (i in 0 until bluetoothArray.length()) {
            try {
                val obj = bluetoothArray.getJSONObject(i)
                val address = obj.optString("address", "00:11:22:33:44:55")
                val name = obj.optString("name", "")
                val baseRssi = obj.optInt("rssi", -60)
                val rssi = baseRssi + (if (config.optBoolean("enable_jitter", true)) Random.nextInt(-2, 3) else 0)
                val hexRecord = obj.optString("scanRecordHex", "")
                val rawBytes = buildScanRecordBytes(name, hexRecord, address)

                val device = createBluetoothDevice(cl, address)
                if (device == null) {
                    XposedBridge.log("[LocationSpoofer] 创建虚拟蓝牙设备失败 address=$address")
                    continue
                }

                var scanRecord: Any? = null
                if (targetScanRecordClass != null && rawBytes.isNotEmpty()) {
                    try {
                        scanRecord = XposedHelpers.callStaticMethod(targetScanRecordClass, "parseFromBytes", rawBytes)
                    } catch (e: Throwable) {
                        XposedBridge.log("[LocationSpoofer] ScanRecord.parseFromBytes 异常: $e")
                    }
                }

                val scanResultObj = createScanResult(cl, device, scanRecord, rssi, timestampNanos)
                if (scanResultObj != null) {
                    results.add(scanResultObj)
                    mainHandler.post {
                        try {
                            dispatchScanResultToCallback(callback, scanResultObj)
                            XposedBridge.log("[LocationSpoofer] 成功派发 onScanResult 给 ${callback.javaClass.name} | MAC=$address RSSI=$rssi 广播包长=${rawBytes.size}")
                        } catch (e: Throwable) {
                            XposedBridge.log("[LocationSpoofer] 派发 onScanResult 异常: $e")
                        }
                    }
                } else {
                    XposedBridge.log("[LocationSpoofer] 创建 ScanResult 失败 address=$address")
                }
            } catch (e: Throwable) {
                XposedBridge.log("[LocationSpoofer] 构造虚拟BLE失败: $e")
            }
        if (results.isNotEmpty()) {
            mainHandler.post {
                try {
                    dispatchBatchResultsToCallback(callback, results)
                } catch (_: Throwable) {}
            }
        }
    }

    // 启动周期性定时广播模拟真实信标心跳 (约每 200ms 发送一次)
    fun startBleTimer(callback: Any, cl: ClassLoader) {
        bleScanTimers.remove(callback)?.cancel()
        val timer = java.util.Timer("LocationSpoofer-BleTimer", true)
        bleScanTimers[callback] = timer

        // 立即执行第一次
        var config = readConfig()
        if (config == null) {
            config = loadConfigFromDisk("startBleTimer_init")
        }
        if (config != null && config.optBoolean("active", false) && config.optBoolean("mock_bluetooth", true)) {
            deliverBleScanResults(config, callback, cl)
        }

        timer.schedule(object : java.util.TimerTask() {
            override fun run() {
                val cfg = readConfig() ?: loadConfigFromDisk("bleTimer")
                if (cfg == null || !cfg.optBoolean("active", false) || !cfg.optBoolean("mock_bluetooth", true)) {
                    cancel()
                    bleScanTimers.remove(callback)
                    return
                }
                deliverBleScanResults(cfg, callback, cl)
            }
        }, 200L, 200L)
    }

    fun stopBleTimer(callback: Any) {
        bleScanTimers.remove(callback)?.cancel()
    }

    // 1. Hook BluetoothLeScanner 的所有 startScan / startScanFromSource 重载
    val leScannerClass = XposedHelpers.findClassIfExists("android.bluetooth.le.BluetoothLeScanner", classLoader)
    if (leScannerClass != null) {
        val scanMethodNames = arrayOf("startScan", "startScanFromSource")
        for (scanName in scanMethodNames) {
            try {
                XposedHelpers.hookAllMethods(leScannerClass, scanName) { chain, method ->
                    XposedBridge.log("[LocationSpoofer] 捕获到 BluetoothLeScanner.$scanName: args=${chain.args.map { it?.javaClass?.simpleName }}")
                    var config = readConfig()
                    if (config == null) {
                        config = loadConfigFromDisk("startScan_direct")
                    }
                    if (config == null || !config.optBoolean("active", false) || !config.optBoolean("mock_bluetooth", true)) {
                        XposedBridge.log("[LocationSpoofer] BluetoothLeScanner.$scanName 放行原生系统 (active/mock_bt 为 false)")
                        return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                    }
                    val callback = chain.args.firstOrNull { isScanCallback(it) }
                        ?: chain.args.lastOrNull {
                            it != null && it !is List<*> && it !is java.util.Collection<*> &&
                                    !it.javaClass.name.contains("ScanFilter") &&
                                    !it.javaClass.name.contains("ScanSettings") &&
                                    !it.javaClass.name.contains("PendingIntent") &&
                                    !it.javaClass.name.contains("WorkSource")
                        }
                    if (callback != null) {
                        XposedBridge.log("[LocationSpoofer] 成功拦截 $scanName, 目标回调=${callback.javaClass.name}, 启动心跳分发")
                        startBleTimer(callback, classLoader)
                    } else {
                        XposedBridge.log("[LocationSpoofer] $scanName 未找到匹配的 ScanCallback, args=${chain.args.map { it?.javaClass?.name }}")
                    }
                    if (method is java.lang.reflect.Method && method.returnType == Int::class.javaPrimitiveType) {
                        return@hookAllMethods 0
                    }
                    return@hookAllMethods null
                }
            } catch (e: Throwable) {
                XposedBridge.log("[LocationSpoofer] Hook BluetoothLeScanner.$scanName 异常: $e")
            }
        }

        // 2. Hook BluetoothLeScanner 的所有 stopScan 重载
        try {
            XposedHelpers.hookAllMethods(leScannerClass, "stopScan") { chain, _ ->
                val callback = chain.args.firstOrNull { isScanCallback(it) }
                    ?: chain.args.lastOrNull {
                        it != null && it !is List<*> && !it.javaClass.name.contains("PendingIntent")
                    }
                if (callback != null) {
                    stopBleTimer(callback)
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}
    }

    // 3. 旧版 startLeScan 分发逻辑
    fun deliverOldLeScanResults(config: JSONObject, callback: Any, cl: ClassLoader) {
        if (!config.optBoolean("mock_bluetooth", true)) return
        val bluetoothArray = config.optJSONArray("bluetooth_json") ?: return
        if (bluetoothArray.length() == 0) return

        for (i in 0 until bluetoothArray.length()) {
            try {
                val obj = bluetoothArray.getJSONObject(i)
                val address = obj.optString("address", "00:11:22:33:44:55")
                val name = obj.optString("name", "")
                val baseRssi = obj.optInt("rssi", -60)
                val rssi = baseRssi + (if (config.optBoolean("enable_jitter", true)) Random.nextInt(-2, 3) else 0)
                val hexRecord = obj.optString("scanRecordHex", "")
                val rawBytes = buildScanRecordBytes(name, hexRecord, address)

                val device = createBluetoothDevice(cl, address) ?: continue
                mainHandler.post {
                    try {
                        XposedHelpers.callMethod(callback, "onLeScan", device, rssi, rawBytes)
                    } catch (_: Throwable) {}
                }
            } catch (e: Throwable) {
                XposedBridge.log("[LocationSpoofer] 构造旧版LeScan失败: $e")
            }
        }
    }

    fun startOldLeScanTimer(callback: Any, cl: ClassLoader) {
        bleScanTimers.remove(callback)?.cancel()
        val timer = java.util.Timer("LocationSpoofer-OldBleTimer", true)
        bleScanTimers[callback] = timer

        var config = readConfig()
        if (config == null) {
            config = loadConfigFromDisk("startOldLeScanTimer")
        }
        if (config != null && config.optBoolean("active", false) && config.optBoolean("mock_bluetooth", true)) {
            deliverOldLeScanResults(config, callback, cl)
        }

        timer.schedule(object : java.util.TimerTask() {
            override fun run() {
                val cfg = readConfig() ?: loadConfigFromDisk("oldTimer")
                if (cfg == null || !cfg.optBoolean("active", false) || !cfg.optBoolean("mock_bluetooth", true)) {
                    cancel()
                    bleScanTimers.remove(callback)
                    return
                }
                deliverOldLeScanResults(cfg, callback, cl)
            }
        }, 200L, 200L)
    }

    // 4. Hook BluetoothAdapter 的所有 startLeScan / stopLeScan 重载
    val bluetoothAdapterClass = XposedHelpers.findClassIfExists("android.bluetooth.BluetoothAdapter", classLoader)
    if (bluetoothAdapterClass != null) {
        try {
            XposedHelpers.hookAllMethods(bluetoothAdapterClass, "startLeScan") { chain, _ ->
                XposedBridge.log("[LocationSpoofer] 捕获到 BluetoothAdapter.startLeScan: args=${chain.args.map { it?.javaClass?.simpleName }}")
                var config = readConfig()
                if (config == null) {
                    config = loadConfigFromDisk("startLeScan_direct")
                }
                if (config == null || !config.optBoolean("active", false) || !config.optBoolean("mock_bluetooth", true)) {
                    return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                }
                val callback = chain.args.firstOrNull { isLeScanCallback(it) }
                    ?: chain.args.lastOrNull { it != null && it !is Array<*> }
                if (callback != null) {
                    XposedBridge.log("[LocationSpoofer] 成功拦截 startLeScan, 目标回调=${callback.javaClass.name}")
                    startOldLeScanTimer(callback, classLoader)
                }
                return@hookAllMethods true
            }
        } catch (_: Throwable) {}

        try {
            XposedHelpers.hookAllMethods(bluetoothAdapterClass, "stopLeScan") { chain, _ ->
                val callback = chain.args.firstOrNull { isLeScanCallback(it) }
                    ?: chain.args.lastOrNull { it != null && it !is Array<*> }
                if (callback != null) {
                    stopBleTimer(callback)
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}

        try {
            XposedHelpers.hookAllMethods(bluetoothAdapterClass, "getRemoteDevice") { chain, _ ->
                val result = chain.proceed(chain.args.toTypedArray())
                if (result != null) return@hookAllMethods result
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && config.optBoolean("mock_bluetooth", true)) {
                    val mac = chain.args.firstOrNull { it is String } as? String
                    if (mac != null) {
                        val dev = createBluetoothDevice(classLoader, mac)
                        if (dev != null) return@hookAllMethods dev
                    }
                }
                return@hookAllMethods null
            }
        } catch (_: Throwable) {}

        // 5. Hook BluetoothAdapter 状态查询与特性支持
        val booleanTrueMethods = arrayOf(
            "isEnabled",
            "isLeEnabled",
            "getLeAccess",
            "isOffloadedFilteringSupported",
            "isOffloadedScanBatchingSupported",
            "isMultipleAdvertisementSupported",
            "isLe2MPhySupported",
            "isLeCodedPhySupported",
            "isLeExtendedAdvertisingSupported",
            "isLePeriodicAdvertisingSupported"
        )
        for (mName in booleanTrueMethods) {
            try {
                XposedHelpers.hookAllMethods(bluetoothAdapterClass, mName) { chain, _ ->
                    val config = readConfig() ?: loadConfigFromDisk(mName)
                    if (config != null && config.optBoolean("active", false) && config.optBoolean("mock_bluetooth", true)) {
                        return@hookAllMethods true
                    }
                    return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                }
            } catch (_: Throwable) {}
        }

        val stateMethods = arrayOf("getState", "getLeState")
        for (mName in stateMethods) {
            try {
                XposedHelpers.hookAllMethods(bluetoothAdapterClass, mName) { chain, _ ->
                    val config = readConfig() ?: loadConfigFromDisk(mName)
                    if (config != null && config.optBoolean("active", false) && config.optBoolean("mock_bluetooth", true)) {
                        return@hookAllMethods 12 // BluetoothAdapter.STATE_ON
                    }
                    return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                }
            } catch (_: Throwable) {}
        }

        // 6. Hook BluetoothAdapter.getBluetoothLeScanner
        try {
            XposedHelpers.hookAllMethods(bluetoothAdapterClass, "getBluetoothLeScanner") { chain, _ ->
                val result = chain.proceed(chain.args.toTypedArray())
                val config = readConfig()
                if (result == null && config != null && config.optBoolean("active", false) && config.optBoolean("mock_bluetooth", true)) {
                    val adapter = chain.thisObject
                    try {
                        var scanner = XposedHelpers.getObjectField(adapter, "mBluetoothLeScanner")
                        if (scanner == null && leScannerClass != null) {
                            for (c in leScannerClass.declaredConstructors) {
                                try {
                                    c.isAccessible = true
                                    val dummyArgs = arrayOfNulls<Any>(c.parameterCount)
                                    scanner = c.newInstance(*dummyArgs)
                                    if (scanner != null) break
                                } catch (_: Throwable) {}
                            }
                            if (scanner != null) {
                                try {
                                    XposedHelpers.setObjectField(adapter, "mBluetoothLeScanner", scanner)
                                } catch (_: Throwable) {}
                            }
                        }
                        if (scanner != null) return@hookAllMethods scanner
                    } catch (_: Throwable) {}
                }
                return@hookAllMethods result
            }
        } catch (_: Throwable) {}

        // 7. Hook BluetoothAdapter.getBondedDevices
        try {
            XposedHelpers.hookAllMethods(bluetoothAdapterClass, "getBondedDevices") { chain, _ ->
                val config = readConfig() ?: return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                if (!config.optBoolean("active", false) || !config.optBoolean("mock_bluetooth", true)) {
                    return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                }
                val bondedSet = java.util.HashSet<Any>()
                try {
                    val bluetoothArray = config.optJSONArray("bluetooth_json")
                    if (bluetoothArray != null && bluetoothArray.length() > 0) {
                        for (i in 0 until bluetoothArray.length()) {
                            val obj = bluetoothArray.getJSONObject(i)
                            if (obj.optBoolean("isConnected", false)) {
                                val address = obj.optString("address", "00:00:00:00:00:00")
                                val dev = createBluetoothDevice(classLoader, address)
                                if (dev != null) bondedSet.add(dev)
                            }
                        }
                    }
                } catch (_: Throwable) {}
                return@hookAllMethods bondedSet
            }
        } catch (_: Throwable) {}

        // 8. Hook BluetoothAdapter.startDiscovery 模拟经典蓝牙扫描并分发 ACTION_FOUND 广播
        try {
            XposedHelpers.hookAllMethods(bluetoothAdapterClass, "startDiscovery") { chain, _ ->
                val config = readConfig() ?: return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                if (!config.optBoolean("active", false) || !config.optBoolean("mock_bluetooth", true)) {
                    return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                }
                val bluetoothArray = config.optJSONArray("bluetooth_json")
                if (bluetoothArray != null && bluetoothArray.length() > 0) {
                    try {
                        val activityThreadClass = XposedHelpers.findClassIfExists("android.app.ActivityThread", classLoader)
                        val app = if (activityThreadClass != null) {
                            XposedHelpers.callStaticMethod(activityThreadClass, "currentApplication") as? android.content.Context
                        } else null

                        if (app != null) {
                            mainHandler.postDelayed({
                                try {
                                    app.sendBroadcast(android.content.Intent("android.bluetooth.adapter.action.DISCOVERY_STARTED"))
                                } catch (_: Throwable) {}
                            }, 50)
                            for (i in 0 until bluetoothArray.length()) {
                                val obj = bluetoothArray.getJSONObject(i)
                                val address = obj.optString("address", "00:11:22:33:44:55")
                                val name = obj.optString("name", "")
                                val rssi = obj.optInt("rssi", -60)
                                val dev = createBluetoothDevice(classLoader, address) ?: continue
                                mainHandler.postDelayed({
                                    try {
                                        val foundIntent = android.content.Intent("android.bluetooth.device.action.FOUND").apply {
                                            putExtra("android.bluetooth.device.extra.DEVICE", dev as android.os.Parcelable)
                                            putExtra("android.bluetooth.device.extra.NAME", name)
                                            putExtra("android.bluetooth.device.extra.RSSI", rssi.toShort())
                                        }
                                        app.sendBroadcast(foundIntent)
                                    } catch (_: Throwable) {}
                                }, (100 + i * 150).toLong())
                            }
                        }
                    } catch (_: Throwable) {}
                }
                return@hookAllMethods true
            }
        } catch (_: Throwable) {}
    }

    // 9. Hook BluetoothDevice.getName / getAlias / getType / getBondState / getUuids / getAddress
    val bluetoothDeviceClass = XposedHelpers.findClassIfExists("android.bluetooth.BluetoothDevice", classLoader)
    if (bluetoothDeviceClass != null) {
        try {
            XposedHelpers.hookAllMethods(bluetoothDeviceClass, "getAddress") { chain, _ ->
                val result = chain.proceed(chain.args.toTypedArray()) as? String
                if (!result.isNullOrEmpty() && result != "00:00:00:00:00:00") return@hookAllMethods result
                val config = readConfig() ?: loadConfigFromDisk("getAddress")
                if (config != null && config.optBoolean("active", false) && config.optBoolean("mock_bluetooth", true)) {
                    val bluetoothArray = config.optJSONArray("bluetooth_json")
                    if (bluetoothArray != null && bluetoothArray.length() > 0) {
                        val addr = bluetoothArray.getJSONObject(0).optString("address", "")
                        if (addr.isNotEmpty()) return@hookAllMethods normalizeMacAddress(addr)
                    }
                }
                return@hookAllMethods result
            }
        } catch (_: Throwable) {}

        try {
            XposedHelpers.hookAllMethods(bluetoothDeviceClass, "getName") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && config.optBoolean("mock_bluetooth", true)) {
                    val thisDevice = chain.thisObject
                    val address = (XposedHelpers.callMethod(thisDevice, "getAddress") as? String)?.uppercase()
                    val bluetoothArray = config.optJSONArray("bluetooth_json")
                    if (address != null && bluetoothArray != null) {
                        for (i in 0 until bluetoothArray.length()) {
                            val obj = bluetoothArray.getJSONObject(i)
                            if (obj.optString("address").uppercase() == address) {
                                val mockName = obj.optString("name")
                                if (mockName.isNotEmpty()) return@hookAllMethods mockName
                            }
                        }
                    }
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}

        try {
            XposedHelpers.hookAllMethods(bluetoothDeviceClass, "getAlias") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && config.optBoolean("mock_bluetooth", true)) {
                    val thisDevice = chain.thisObject
                    val address = (XposedHelpers.callMethod(thisDevice, "getAddress") as? String)?.uppercase()
                    val bluetoothArray = config.optJSONArray("bluetooth_json")
                    if (address != null && bluetoothArray != null) {
                        for (i in 0 until bluetoothArray.length()) {
                            val obj = bluetoothArray.getJSONObject(i)
                            if (obj.optString("address").uppercase() == address) {
                                val mockName = obj.optString("name")
                                if (mockName.isNotEmpty()) return@hookAllMethods mockName
                            }
                        }
                    }
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}

        try {
            XposedHelpers.hookAllMethods(bluetoothDeviceClass, "getType") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && config.optBoolean("mock_bluetooth", true)) {
                    return@hookAllMethods 2 // DEVICE_TYPE_LE
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}

        try {
            XposedHelpers.hookAllMethods(bluetoothDeviceClass, "getBondState") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && config.optBoolean("mock_bluetooth", true)) {
                    val thisDevice = chain.thisObject
                    val address = (XposedHelpers.callMethod(thisDevice, "getAddress") as? String)?.uppercase()
                    val bluetoothArray = config.optJSONArray("bluetooth_json")
                    if (address != null && bluetoothArray != null) {
                        for (i in 0 until bluetoothArray.length()) {
                            val obj = bluetoothArray.getJSONObject(i)
                            if (obj.optString("address").uppercase() == address) {
                                if (obj.optBoolean("isConnected", false)) {
                                    return@hookAllMethods 12 // BOND_BONDED
                                }
                            }
                        }
                    }
                    return@hookAllMethods 10 // BOND_NONE
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}

        try {
            XposedHelpers.hookAllMethods(bluetoothDeviceClass, "getUuids") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && config.optBoolean("mock_bluetooth", true)) {
                    val parcelUuidClass = XposedHelpers.findClassIfExists("android.os.ParcelUuid", classLoader)
                    if (parcelUuidClass != null) {
                        try {
                            val uuidObj = XposedHelpers.callStaticMethod(
                                parcelUuidClass,
                                "fromString",
                                "0000fe3c-0000-1000-8000-00805f9b34fb"
                            )
                            if (uuidObj != null) {
                                val array = java.lang.reflect.Array.newInstance(parcelUuidClass, 1)
                                java.lang.reflect.Array.set(array, 0, uuidObj)
                                return@hookAllMethods array
                            }
                        } catch (_: Throwable) {}
                    }
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}

        try {
            XposedHelpers.hookAllMethods(bluetoothDeviceClass, "connectGatt") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && config.optBoolean("mock_bluetooth", true)) {
                    val gattCallback = chain.args.firstOrNull { it != null && it.javaClass.name.contains("GattCallback") }
                    val result = chain.proceed(chain.args.toTypedArray())
                    if (gattCallback != null && result != null) {
                        mainHandler.postDelayed({
                            try {
                                XposedHelpers.callMethod(gattCallback, "onConnectionStateChange", result, 0, 2 /* STATE_CONNECTED */)
                            } catch (_: Throwable) {}
                        }, 200)
                    }
                    return@hookAllMethods result
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}
    }

    // 10. Hook ScanRecord (getDeviceName, getServiceUuids, getManufacturerSpecificData)
    if (scanRecordClass != null) {
        try {
            XposedHelpers.hookAllMethods(scanRecordClass, "getDeviceName") { chain, _ ->
                val result = chain.proceed(chain.args.toTypedArray())
                if (result != null && (result as? String)?.isNotEmpty() == true) {
                    return@hookAllMethods result
                }
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && config.optBoolean("mock_bluetooth", true)) {
                    val bluetoothArray = config.optJSONArray("bluetooth_json")
                    if (bluetoothArray != null && bluetoothArray.length() > 0) {
                        val name = bluetoothArray.getJSONObject(0).optString("name", "")
                        if (name.isNotEmpty()) return@hookAllMethods name
                    }
                }
                return@hookAllMethods result
            }
        } catch (_: Throwable) {}

        try {
            XposedHelpers.hookAllMethods(scanRecordClass, "getServiceUuids") { chain, _ ->
                val result = chain.proceed(chain.args.toTypedArray()) as? List<*>
                if (!result.isNullOrEmpty()) return@hookAllMethods result
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && config.optBoolean("mock_bluetooth", true)) {
                    val parcelUuidClass = XposedHelpers.findClassIfExists("android.os.ParcelUuid", classLoader)
                    if (parcelUuidClass != null) {
                        try {
                            val uuidObj = XposedHelpers.callStaticMethod(parcelUuidClass, "fromString", "0000fe3c-0000-1000-8000-00805f9b34fb")
                            if (uuidObj != null) return@hookAllMethods listOf(uuidObj)
                        } catch (_: Throwable) {}
                    }
                }
                return@hookAllMethods result
            }
        } catch (_: Throwable) {}

        try {
            XposedHelpers.hookAllMethods(scanRecordClass, "getManufacturerSpecificData") { chain, _ ->
                val config = readConfig() ?: loadConfigFromDisk("mfr_data")
                if (config != null && config.optBoolean("active", false) && config.optBoolean("mock_bluetooth", true)) {
                    val bluetoothArray = config.optJSONArray("bluetooth_json")
                    if (bluetoothArray != null && bluetoothArray.length() > 0) {
                        val hex = bluetoothArray.getJSONObject(0).optString("scanRecordHex", bluetoothArray.getJSONObject(0).optString("rawBytes", ""))
                        val rawBytes = if (hex.isNotEmpty()) hexStringToByteArray(hex) else ByteArray(0)
                        if (rawBytes.isNotEmpty()) {
                            if (chain.args.isEmpty()) {
                                val sparseArray = android.util.SparseArray<ByteArray>()
                                sparseArray.put(0x0100, rawBytes)
                                sparseArray.put(0x0001, rawBytes)
                                sparseArray.put(256, rawBytes)
                                sparseArray.put(1, rawBytes)
                                sparseArray.put(0, rawBytes)
                                return@hookAllMethods sparseArray
                            } else {
                                return@hookAllMethods rawBytes
                            }
                        }
                    }
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}

        try {
            XposedHelpers.hookAllMethods(scanRecordClass, "getBytes") { chain, _ ->
                val config = readConfig() ?: loadConfigFromDisk("getBytes")
                if (config != null && config.optBoolean("active", false) && config.optBoolean("mock_bluetooth", true)) {
                    val bluetoothArray = config.optJSONArray("bluetooth_json")
                    if (bluetoothArray != null && bluetoothArray.length() > 0) {
                        val obj = bluetoothArray.getJSONObject(0)
                        val hex = obj.optString("scanRecordHex", obj.optString("rawBytes", ""))
                        val name = obj.optString("name", "")
                        val address = obj.optString("address", "")
                        val rawBytes = buildScanRecordBytes(name, hex, address)
                        if (rawBytes.isNotEmpty()) {
                            return@hookAllMethods rawBytes
                        }
                    }
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}
    }

    // 11. Hook ScanFilter.matches (确保客户端过滤恒通过)
    val scanFilterClass = XposedHelpers.findClassIfExists("android.bluetooth.le.ScanFilter", classLoader)
    if (scanFilterClass != null) {
        try {
            XposedHelpers.hookAllMethods(scanFilterClass, "matches") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && config.optBoolean("mock_bluetooth", true)) {
                    return@hookAllMethods true
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}
    }

    // 12. Hook BluetoothManager.getConnectedDevices & getDevicesMatchingConnectionStates & getAdapter
    val bluetoothManagerClass = XposedHelpers.findClassIfExists("android.bluetooth.BluetoothManager", classLoader)
    if (bluetoothManagerClass != null) {
        try {
            XposedHelpers.hookAllMethods(bluetoothManagerClass, "getAdapter") { chain, _ ->
                val result = chain.proceed(chain.args.toTypedArray())
                if (result != null) return@hookAllMethods result
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && config.optBoolean("mock_bluetooth", true)) {
                    val adapterClass = XposedHelpers.findClassIfExists("android.bluetooth.BluetoothAdapter", classLoader)
                    if (adapterClass != null) {
                        return@hookAllMethods XposedHelpers.callStaticMethod(adapterClass, "getDefaultAdapter")
                    }
                }
                return@hookAllMethods null
            }
        } catch (_: Throwable) {}

        try {
            XposedHelpers.hookAllMethods(bluetoothManagerClass, "getConnectedDevices") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && config.optBoolean("mock_bluetooth", true)) {
                    val bluetoothArray = config.optJSONArray("bluetooth_json")
                    if (bluetoothArray != null && bluetoothArray.length() > 0) {
                        val list = java.util.ArrayList<Any>()
                        for (i in 0 until bluetoothArray.length()) {
                            val obj = bluetoothArray.getJSONObject(i)
                            if (obj.optBoolean("isConnected", false) || obj.optBoolean("isDesignated", false)) {
                                val address = obj.optString("address", "00:00:00:00:00:00")
                                val dev = createBluetoothDevice(classLoader, address)
                                if (dev != null) list.add(dev)
                            }
                        }
                        if (list.isNotEmpty()) return@hookAllMethods list
                    }
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}

        try {
            XposedHelpers.hookAllMethods(bluetoothManagerClass, "getDevicesMatchingConnectionStates") { chain, _ ->
                val config = readConfig()
                if (config != null && config.optBoolean("active", false) && config.optBoolean("mock_bluetooth", true)) {
                    val bluetoothArray = config.optJSONArray("bluetooth_json")
                    if (bluetoothArray != null && bluetoothArray.length() > 0) {
                        val list = java.util.ArrayList<Any>()
                        for (i in 0 until bluetoothArray.length()) {
                            val obj = bluetoothArray.getJSONObject(i)
                            if (obj.optBoolean("isConnected", false) || obj.optBoolean("isDesignated", false)) {
                                val address = obj.optString("address", "00:00:00:00:00:00")
                                val dev = createBluetoothDevice(classLoader, address)
                                if (dev != null) list.add(dev)
                            }
                        }
                        if (list.isNotEmpty()) return@hookAllMethods list
                    }
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}
    }

    XposedBridge.log("[LocationSpoofer] Bluetooth LE hooks installed")
}
}
