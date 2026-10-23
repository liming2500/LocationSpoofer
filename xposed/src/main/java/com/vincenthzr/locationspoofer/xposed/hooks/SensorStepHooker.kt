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

package com.vincenthzr.locationspoofer.xposed.hooks

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.vincenthzr.locationspoofer.utils.GaitTemplate
import com.vincenthzr.locationspoofer.utils.HeadingOrientation
import com.vincenthzr.locationspoofer.utils.StepClock
import com.vincenthzr.locationspoofer.xposed.LocationHooker
import com.vincenthzr.locationspoofer.xposed.utils.RouteEngine
import com.vincenthzr.locationspoofer.xposed.utils.XposedHelpers
import org.json.JSONObject
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object SensorStepHooker {

    data class CapturedSensorListener(
        val listener: Any,
        val sensor: Sensor?,
        val handler: Handler?
    )

    val capturedListeners = CopyOnWriteArrayList<CapturedSensorListener>()
    private val hookedListenerClasses = ConcurrentHashMap<Class<*>, Boolean>()

    // 真实计步器自开机以来单调递增：换会话 / 摇杆每次写配置导致 start_timestamp 变化时，
    // 把已累计的步数接续为新的起点，而不是回到初始值。
    // 换锚 / 单调护栏的细节都在 StepClock 里（core-geo，有单测覆盖）。
    private const val INITIAL_BOOT_STEPS = 2350.0
    private val stepClock = StepClock(INITIAL_BOOT_STEPS)

    /** 虚拟步数检测器单次补发事件的上限，避免主线程被一次性灌入过多事件 */
    private const val MAX_DETECTOR_BURST = 5

    /** 最近一次运动航向，静止时沿用，避免方向传感器跳回手机物理朝向 */
    @Volatile private var lastMotionHeading = 0.0

    private val noiseRng = java.util.Random()
    @Volatile private var cachedTemplateSource: String? = null
    @Volatile private var cachedTemplate: GaitTemplate? = null

    // 缓存虚拟 Sensor 实例
    private var mockStepCounterSensor: Sensor? = null
    private var mockStepDetectorSensor: Sensor? = null
    private var mockAccelerometerSensor: Sensor? = null

    // 跟踪 handle 到 sensor type 的映射
    private val handleToTypeMap = ConcurrentHashMap<Int, Int>()

    /** 需要按运动航向改写的朝向类传感器 */
    private val ORIENTATION_TYPES = setOf(
        Sensor.TYPE_ROTATION_VECTOR,
        Sensor.TYPE_GAME_ROTATION_VECTOR,
        Sensor.TYPE_ORIENTATION,
        Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR
    )

    fun hookSensorStepSimulation(classLoader: ClassLoader) {
        val targetClasses = listOf(
            "android.hardware.SensorManager",
            "android.hardware.SystemSensorManager"
        )

        for (className in targetClasses) {
            val managerClass = XposedHelpers.findClassIfExists(className, classLoader) ?: continue

            // 1. Hook getDefaultSensor: 当设备无物理计步传感器时注入虚拟 Sensor
            try {
                XposedHelpers.hookAllMethods(managerClass, "getDefaultSensor") { chain, _ ->
                    val result = chain.proceed(chain.args.toTypedArray())
                    val type = (chain.args.firstOrNull() as? Int) ?: return@hookAllMethods result
                    if (result == null && (type == Sensor.TYPE_STEP_COUNTER || type == Sensor.TYPE_STEP_DETECTOR)) {
                        return@hookAllMethods getOrCreateMockSensor(type, classLoader)
                    }
                    return@hookAllMethods result
                }
            } catch (_: Throwable) {}

            // 2. Hook getSensorList
            try {
                XposedHelpers.hookAllMethods(managerClass, "getSensorList") { chain, _ ->
                    val result = chain.proceed(chain.args.toTypedArray())
                    val type = (chain.args.firstOrNull() as? Int) ?: return@hookAllMethods result
                    if (type == Sensor.TYPE_STEP_COUNTER || type == Sensor.TYPE_STEP_DETECTOR) {
                        if (result is List<*> && result.isEmpty()) {
                            val mock = getOrCreateMockSensor(type, classLoader)
                            if (mock != null) return@hookAllMethods listOf(mock)
                        }
                    }
                    return@hookAllMethods result
                }
            } catch (_: Throwable) {}

            // 3. Hook registerListener / registerListenerImpl
            val regMethods = listOf("registerListener", "registerListenerImpl")
            for (methodName in regMethods) {
                try {
                    XposedHelpers.hookAllMethods(managerClass, methodName) { chain, _ ->
                        val args = chain.args
                        var listener: Any? = null
                        var sensor: Sensor? = null
                        var handler: Handler? = null

                        for (arg in args) {
                            if (arg != null) {
                                if (arg is SensorEventListener || LocationHooker.hasTypeByName(arg.javaClass, "android.hardware.SensorEventListener")) {
                                    listener = arg
                                } else if (arg is Sensor) {
                                    sensor = arg
                                } else if (arg is Handler) {
                                    handler = arg
                                }
                            }
                        }

                        if (listener != null) {
                            val targetSensor = sensor ?: getOrCreateMockSensor(Sensor.TYPE_STEP_COUNTER, classLoader)
                            val tracked = targetSensor != null && (
                                targetSensor.type == Sensor.TYPE_STEP_COUNTER ||
                                    targetSensor.type == Sensor.TYPE_STEP_DETECTOR ||
                                    targetSensor.type == Sensor.TYPE_ACCELEROMETER ||
                                    targetSensor.type in ORIENTATION_TYPES
                                )
                            if (tracked) {
                                val entry = CapturedSensorListener(listener, targetSensor, handler)
                                if (!capturedListeners.any { it.listener === listener && it.sensor?.type == targetSensor.type }) {
                                    capturedListeners.add(entry)
                                }

                                val handle = getSensorHandle(targetSensor)
                                if (handle != null) {
                                    handleToTypeMap[handle] = targetSensor.type
                                }

                                // 动态 Hook 该 Listener 的具体实现类中的 onSensorChanged 方法
                                hookConcreteListenerClass(listener.javaClass, classLoader)
                            }
                        }

                        return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                    }
                } catch (_: Throwable) {}
            }

            // 4. Hook unregisterListener / unregisterListenerImpl
            val unregMethods = listOf("unregisterListener", "unregisterListenerImpl")
            for (methodName in unregMethods) {
                try {
                    XposedHelpers.hookAllMethods(managerClass, methodName) { chain, _ ->
                        val args = chain.args
                        val listener = args.firstOrNull { it != null && (it is SensorEventListener || LocationHooker.hasTypeByName(it.javaClass, "android.hardware.SensorEventListener")) }
                        if (listener != null) {
                            capturedListeners.removeAll { it.listener === listener }
                        }
                        return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                    }
                } catch (_: Throwable) {}
            }
        }

        // 5. Hook SystemSensorManager$SensorEventQueue.dispatchSensorEvent 底层原生分发接口
        hookSensorEventQueue(classLoader)

        // 6. Hook SensorManager 的旋转矩阵 / 姿态静态方法：
        //    有些 App 不关心旋转向量的原始值，而是直接调用 getRotationMatrixFromVector + getOrientation
        //    自己算朝向。事件里的四元数已经被改过，这两个静态方法会基于改写后的值计算，结果自然跟着变，
        //    所以这里只需保证它们能拿到改写后的事件（不需要额外改写返回值）。
        hookSensorStaticHelpers(classLoader)
    }

    private fun hookSensorEventQueue(classLoader: ClassLoader) {
        try {
            val eventQueueClass = XposedHelpers.findClassIfExists("android.hardware.SystemSensorManager\$SensorEventQueue", classLoader)
                ?: XposedHelpers.findClassIfExists("android.hardware.SystemSensorManager\$BaseEventQueue", classLoader)
                ?: return

            XposedHelpers.hookAllMethods(eventQueueClass, "dispatchSensorEvent") { chain, _ ->
                val config = (XposedHelpers.module as? LocationHooker)?.readConfig()
                if (config != null && config.optBoolean("active", false)) {
                    val handle = chain.args.firstOrNull() as? Int
                    val values = chain.args.getOrNull(1) as? FloatArray

                    if (handle != null && values != null) {
                        val sensorType = handleToTypeMap[handle]
                        val speed = config.optDouble("speed_m_s", 0.0)

                        // 朝向属于定位的一部分，不受"步数与加速度模拟"开关影响
                        if (sensorType != null && sensorType in ORIENTATION_TYPES) {
                            applyMotionHeading(values, config, speed)
                        } else if (config.optBoolean("enable_step_simulation", true)) {
                            if (sensorType == Sensor.TYPE_STEP_COUNTER) {
                                // 仅对明确判定的计步器传感器进行步数计算，严禁误判光感/距离传感器
                                val curSteps = calculateCurrentSteps(config)
                                values[0] = curSteps.toFloat()
                            } else if (sensorType == Sensor.TYPE_STEP_DETECTOR) {
                                if (speed > 0.1) {
                                    values[0] = 1.0f
                                }
                            } else if (sensorType == Sensor.TYPE_SIGNIFICANT_MOTION) {
                                // 单值事件传感器：值为 1 表示刚检测到一次显著运动。
                                // 部分校园跑步 App 用它判断"真的在跑"，保持 1 即可，绝不能把步数写进去
                                if (speed > 0.1) {
                                    values[0] = 1.0f
                                }
                            } else if (sensorType == Sensor.TYPE_ACCELEROMETER && values.size >= 3) {
                                // 加速度计传感器：仅在运动中(speed > 0.1)叠加生理跑步/步行震动特征，静态时绝不篡改
                                if (speed > 0.1) {
                                    applySyntheticVibration(values, config, speed)
                                }
                            }
                        }
                    }
                }
                return@hookAllMethods chain.proceed(chain.args.toTypedArray())
            }
        } catch (_: Throwable) {}
    }

    internal fun saveReloadState(): Array<Any?> = arrayOf(
        capturedListeners.map { arrayOf<Any?>(it.listener, it.sensor, it.handler) }.toTypedArray(),
        stepClock.base, stepClock.steps, stepClock.anchorTs,
        mockStepCounterSensor, mockStepDetectorSensor, mockAccelerometerSensor,
        handleToTypeMap.map { arrayOf(it.key, it.value) }.toTypedArray()
    )

    internal fun clearReloadState() { capturedListeners.clear(); hookedListenerClasses.clear() }

    internal fun restoreReloadState(value: Any?) {
        val state = value as? Array<*> ?: return
        (state.getOrNull(0) as? Array<*>)?.filterIsInstance<Array<*>>()?.forEach {
            capturedListeners.add(CapturedSensorListener(it[0]!!, it[1] as? Sensor, it[2] as? Handler))
            hookConcreteListenerClass(it[0]!!.javaClass, it[0]!!.javaClass.classLoader ?: ClassLoader.getSystemClassLoader())
        }
        stepClock.restore(state[1] as Double, state[2] as Double, state[3] as Long)
        mockStepCounterSensor = state[4] as? Sensor
        mockStepDetectorSensor = state[5] as? Sensor
        mockAccelerometerSensor = state[6] as? Sensor
        (state[7] as? Array<*>)?.filterIsInstance<Array<*>>()?.forEach {
            handleToTypeMap[it[0] as Int] = it[1] as Int
        }
    }

    /**
     * 动态 Hook 具体的 Listener 实现类（如 Keep 的 StepListener）
     */
    private fun hookConcreteListenerClass(clazz: Class<*>, classLoader: ClassLoader) {
        if (hookedListenerClasses.putIfAbsent(clazz, true) == null) {
            try {
                XposedHelpers.hookAllMethods(clazz, "onSensorChanged") { chain, _ ->
                    val event = chain.args.firstOrNull() as? SensorEvent
                    if (event != null && event.sensor != null) {
                        val config = (XposedHelpers.module as? LocationHooker)?.readConfig()
                        if (config != null && config.optBoolean("active", false)) {
                            val speed = config.optDouble("speed_m_s", 0.0)
                            // 朝向属于定位的一部分，不受"步数与加速度模拟"开关影响；
                            // 即使静止也改写，避免停在原地时又回到手机物理朝向
                            if (event.sensor.type in ORIENTATION_TYPES) {
                                applyMotionHeading(event.values, config, speed)
                                event.timestamp = SystemClock.elapsedRealtimeNanos()
                            } else if (config.optBoolean("enable_step_simulation", true)) {
                                if (event.sensor.type == Sensor.TYPE_STEP_COUNTER && event.values.isNotEmpty()) {
                                    val curSteps = calculateCurrentSteps(config)
                                    event.values[0] = curSteps.toFloat()
                                    event.timestamp = SystemClock.elapsedRealtimeNanos()
                                } else if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR && event.values.isNotEmpty()) {
                                    if (speed > 0.1) {
                                        event.values[0] = 1.0f
                                        event.timestamp = SystemClock.elapsedRealtimeNanos()
                                    }
                                } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER && event.values.size >= 3 && speed > 0.1) {
                                    applySyntheticVibration(event.values, config, speed)
                                    event.timestamp = SystemClock.elapsedRealtimeNanos()
                                }
                            }
                        }
                    }
                    return@hookAllMethods chain.proceed(chain.args.toTypedArray())
                }
            } catch (_: Throwable) {}
        }
    }

    /**
     * 兜底：部分 App / 定位 SDK 直接用加速度计 + 磁力计，或在拿到旋转向量后自行调用
     * [android.hardware.SensorManager.getOrientation] 计算朝向，不经过上面两个 Hook 点。
     * 这里直接 Hook `getOrientation(R, values)`，把结算出的方位角改成运动航向，
     * 俯仰与横滚保持不变。旋转矩阵本身无法直接反推目标四元数，只改输出即可。
     */
    private fun hookSensorStaticHelpers(classLoader: ClassLoader) {
        try {
            val managerClass = XposedHelpers.findClassIfExists("android.hardware.SensorManager", classLoader)
                ?: return
            XposedHelpers.hookAllMethods(managerClass, "getOrientation") { chain, _ ->
                val result = chain.proceed(chain.args.toTypedArray())
                val config = (XposedHelpers.module as? LocationHooker)?.readConfig()
                val out = chain.args.lastOrNull() as? FloatArray
                if (config != null && config.optBoolean("active", false) && out != null && out.size >= 1) {
                    val motion = RouteEngine.calculateCurrentPosition(config)
                    if (motion.speed > 0.05f || lastMotionHeading != 0.0) {
                        if (motion.speed > 0.05f) lastMotionHeading = motion.bearing.toDouble()
                        out[0] = HeadingOrientation.normalizeDegrees(lastMotionHeading).toFloat()
                    }
                }
                return@hookAllMethods result
            }
        } catch (_: Throwable) {}
    }

    /**
     * 把方向类传感器的读数改成"朝向 = 运动航向"。
     *
     * - ROTATION_VECTOR / GAME_ROTATION_VECTOR / GEOMAGNETIC_ROTATION_VECTOR：[x, y, z, w]
     * - ORIENTATION：[azimuth, pitch, roll]
     *
     * 只替换水平朝向，保留设备真实的俯仰/横滚。静止时沿用上次的航向，避免停下后跳回手机物理朝向。
     */
    private fun applyMotionHeading(values: FloatArray, config: JSONObject, speed: Double) {
        val motion = RouteEngine.calculateCurrentPosition(config)
        if (motion.speed > 0.05f || speed > 0.05) {
            lastMotionHeading = motion.bearing.toDouble()
        }
        val heading = lastMotionHeading

        if (values.size >= 3 && values.size < 4) {
            // TYPE_ORIENTATION：直接改写方位角
            values[0] = HeadingOrientation.normalizeDegrees(heading).toFloat()
        } else if (values.size >= 4) {
            val q = HeadingOrientation.withAzimuth(values, heading)
            values[0] = q[0]; values[1] = q[1]; values[2] = q[2]; values[3] = q[3]
        }
    }

    private fun applySyntheticVibration(values: FloatArray, config: JSONObject, speed: Double) {
        val now = System.currentTimeMillis()
        val stepsFloat = calculateCurrentStepsFloat(config, now)
        val motionSpeed = RouteEngine.calculateCurrentPosition(config, now).speed.toDouble().takeIf { it > 0.0 } ?: speed
        val synthetic = RouteEngine.realismSession(config).accelerometer(stepsFloat, motionSpeed, gaitTemplate(config), noiseRng)
        values[0] = synthetic[0]
        values[1] = synthetic[1]
        values[2] = synthetic[2]
    }

    /** 用户录制的步态模板（配置里的编码字符串），按字符串内容缓存解码结果 */
    private fun gaitTemplate(config: JSONObject): GaitTemplate? {
        val source = config.optString("gait_template", "")
        if (source != cachedTemplateSource) {
            cachedTemplate = GaitTemplate.decode(source)
            cachedTemplateSource = source
        }
        return cachedTemplate
    }

    /**
     * 计算当前仿真总步数
     */
    fun calculateCurrentSteps(config: JSONObject, now: Long = System.currentTimeMillis()): Long =
        calculateCurrentStepsFloat(config, now).toLong()

    /**
     * 连续的累计步数：整数部分是步数，小数部分是当前这一步的相位，供加速度波形对齐。
     *
     * 累计值必须**单调不减**。配置每秒被重写，`start_timestamp` 可能来回切换（路线配置用固定的
     * routeStartTs、就位等待和摇杆用"现在"），如果每次都照配置里的时间戳重新计时，
     * 切回较早的时间戳时会把这段时间重复算一遍，步数越涨越快，App 算出的步频就翻倍。
     * 换锚规则与单调护栏集中在 [StepClock] 里，由 core-geo 的单测覆盖。
     */
    private fun calculateCurrentStepsFloat(config: JSONObject, now: Long): Double {
        val speed = config.optDouble("speed_m_s", 0.0)
        // 静止时不计步，同时把锚点推进到当前时刻：否则就位等待几分钟后一开跑，
        // 等待的那段会被一次性折算成几百步补进来
        if (speed <= 0.05) {
            stepClock.pause(now)
            return stepClock.steps
        }

        val isAutoCadence = config.optBoolean("is_auto_cadence", true)
        val baseCadence = if (isAutoCadence) {
            calculateAutoCadence(speed)
        } else {
            config.optInt("step_cadence_spm", 165).coerceIn(60, 240)
        }.toDouble()

        val startTime = config.optLong("start_timestamp", now)
        return stepClock.advance(startTime, now, baseCadence) { anchor ->
            RouteEngine.realismSession(config, anchor)
        }
    }

    /**
     * 智能根据速度自动计算匹配的生理步频 (SPM, 步/分钟)
     */
    fun calculateAutoCadence(speedMs: Double): Int {
        return when {
            speedMs <= 0.8 -> 95 // 慢走
            speedMs <= 1.5 -> (100 + (speedMs - 0.8) / 0.7 * 25).toInt() // 快走 100~125 SPM
            speedMs <= 2.8 -> (135 + (speedMs - 1.5) / 1.3 * 25).toInt() // 慢跑 135~160 SPM
            speedMs <= 4.0 -> (160 + (speedMs - 2.8) / 1.2 * 20).toInt() // 匀速跑 160~180 SPM
            speedMs <= 5.5 -> (180 + (speedMs - 4.0) / 1.5 * 15).toInt() // 快跑 180~195 SPM
            else -> 200 // 冲刺 200 SPM
        }
    }

    /**
     * 由 ConfigPoller 每秒主动向监听器推送计步事件（仅在运动路线模拟中生效）。
     *
     * 只对**虚拟传感器**补推。真实传感器的事件流已经在 dispatchSensorEvent / onSensorChanged
     * 里被改写过了，这里再补推一份会与真实事件流交错：补推事件在 handler 里排队，落地时
     * 携带的步态相位可能比已经送达的真实事件更旧，计步算法看到"相位倒退"会当成又出现一个峰，
     * 步数就被翻倍（实机表现为 3 m/s 显示 300+ 步频）。
     */
    fun dispatchStepEvents(config: JSONObject, classLoader: ClassLoader) {
        if (!config.optBoolean("active", false)) return
        val enableStep = config.optBoolean("enable_step_simulation", true)
        if (!enableStep) return

        val speed = config.optDouble("speed_m_s", 0.0)
        // 静态定位时绝不主动推送步频和加速度事件，保证主线程和人脸识别传感器纯净
        if (speed <= 0.05) return

        val now = System.currentTimeMillis()
        val listeners = capturedListeners.toList()
        if (listeners.isEmpty()) return

        // 步数增量每次分发只取一次：若有多个监听器挂同一个虚拟检测器，
        // 每个监听器各取一次会把同一批步数重复算进来，这里共用同一份
        val detectorDue = pendingDetectorSteps(config, now).coerceAtMost(MAX_DETECTOR_BURST)

        for (entry in listeners) {
            val listener = entry.listener
            val sensor = entry.sensor ?: continue
            val targetHandler = entry.handler ?: Handler(Looper.getMainLooper())

            // 真实传感器已有原生事件流，改写即可，不能再补推
            val isMockSensor = sensor === mockStepCounterSensor ||
                sensor === mockStepDetectorSensor ||
                sensor === mockAccelerometerSensor
            if (!isMockSensor) continue

            when (sensor.type) {
                Sensor.TYPE_STEP_COUNTER -> {
                    // 累计值本身是绝对量，重复送达不会多计
                    val totalSteps = calculateCurrentSteps(config, now)
                    val event = createSensorEvent(sensor, floatArrayOf(totalSteps.toFloat()))
                    if (event != null) {
                        targetHandler.post {
                            try {
                                if (listener is SensorEventListener) {
                                    listener.onSensorChanged(event)
                                } else {
                                    XposedHelpers.callMethod(listener, "onSensorChanged", event)
                                }
                            } catch (_: Throwable) {}
                        }
                    }
                }
                Sensor.TYPE_STEP_DETECTOR -> {
                    // 每步一个事件：按真实步频补上这段时间应该发生的步数，而不是固定每秒一个
                    repeat(detectorDue) {
                        val event = createSensorEvent(sensor, floatArrayOf(1.0f))
                        if (event != null) {
                            targetHandler.post {
                                try {
                                    if (listener is SensorEventListener) {
                                        listener.onSensorChanged(event)
                                    } else {
                                        XposedHelpers.callMethod(listener, "onSensorChanged", event)
                                    }
                                } catch (_: Throwable) {}
                            }
                        }
                    }
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    // 虚拟加速度计没有原生事件流，需要按当前步态相位补一帧
                    val rawValues = floatArrayOf(0f, 0f, 9.8f)
                    applySyntheticVibration(rawValues, config, speed)
                    val event = createSensorEvent(sensor, rawValues)
                    if (event != null) {
                        targetHandler.post {
                            try {
                                if (listener is SensorEventListener) {
                                    listener.onSensorChanged(event)
                                } else {
                                    XposedHelpers.callMethod(listener, "onSensorChanged", event)
                                }
                            } catch (_: Throwable) {}
                        }
                    }
                }
            }
        }
    }

    // 虚拟步数检测器：上次补发到的步数，用来补齐这段时间应发生的步数
    @Volatile private var lastDetectorSteps: Double = 0.0
    @Volatile private var lastDetectorTime: Long = 0L

    /** 自上次补发以来累计步数新增的整数部分，用于逐个补发步数检测器事件 */
    private fun pendingDetectorSteps(config: JSONObject, now: Long): Int {
        val current = calculateCurrentStepsFloat(config, now)
        if (lastDetectorTime == 0L || current < lastDetectorSteps) {
            lastDetectorSteps = current
            lastDetectorTime = now
            return 0
        }
        val due = (current - lastDetectorSteps).toInt()
        if (due > 0) {
            lastDetectorSteps += due
            lastDetectorTime = now
        }
        return due
    }

    private fun getSensorHandle(sensor: Sensor): Int? {
        return try {
            val handleField = Sensor::class.java.getDeclaredField("mHandle")
            handleField.isAccessible = true
            handleField.getInt(sensor)
        } catch (_: Throwable) {
            null
        }
    }

    private fun getOrCreateMockSensor(type: Int, classLoader: ClassLoader): Sensor? {
        if (type == Sensor.TYPE_STEP_COUNTER && mockStepCounterSensor != null) return mockStepCounterSensor
        if (type == Sensor.TYPE_STEP_DETECTOR && mockStepDetectorSensor != null) return mockStepDetectorSensor
        if (type == Sensor.TYPE_ACCELEROMETER && mockAccelerometerSensor != null) return mockAccelerometerSensor

        try {
            val sensorClass = Class.forName("android.hardware.Sensor", false, classLoader)
            val constructor: Constructor<*> = sensorClass.getDeclaredConstructor()
            constructor.isAccessible = true
            val sensor = constructor.newInstance() as Sensor

            setSensorField(sensor, "mType", type)
            setSensorField(sensor, "mName", when(type) {
                Sensor.TYPE_STEP_COUNTER -> "Step Counter Sensor"
                Sensor.TYPE_STEP_DETECTOR -> "Step Detector Sensor"
                else -> "Accelerometer Sensor"
            })
            setSensorField(sensor, "mVendor", "Android")
            setSensorField(sensor, "mVersion", 1)
            setSensorField(sensor, "mResolution", 1.0f)
            setSensorField(sensor, "mPower", 0.05f)

            when (type) {
                Sensor.TYPE_STEP_COUNTER -> mockStepCounterSensor = sensor
                Sensor.TYPE_STEP_DETECTOR -> mockStepDetectorSensor = sensor
                Sensor.TYPE_ACCELEROMETER -> mockAccelerometerSensor = sensor
            }
            return sensor
        } catch (_: Throwable) {
            return null
        }
    }

    private fun setSensorField(sensor: Sensor, fieldName: String, value: Any) {
        try {
            val field: Field = Sensor::class.java.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(sensor, value)
        } catch (_: Throwable) {}
    }

    private fun createSensorEvent(sensor: Sensor, values: FloatArray): SensorEvent? {
        try {
            val eventConstructor = SensorEvent::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType)
            eventConstructor.isAccessible = true
            val event = eventConstructor.newInstance(values.size) as SensorEvent
            System.arraycopy(values, 0, event.values, 0, values.size)

            val sensorField = SensorEvent::class.java.getDeclaredField("sensor")
            sensorField.isAccessible = true
            sensorField.set(event, sensor)

            val timestampField = SensorEvent::class.java.getDeclaredField("timestamp")
            timestampField.isAccessible = true
            timestampField.setLong(event, SystemClock.elapsedRealtimeNanos())

            val accuracyField = SensorEvent::class.java.getDeclaredField("accuracy")
            accuracyField.isAccessible = true
            accuracyField.setInt(event, 3) // SENSOR_STATUS_ACCURACY_HIGH

            return event
        } catch (_: Throwable) {
            try {
                // Fallback 1: search any constructors
                val constructors = SensorEvent::class.java.declaredConstructors
                for (ctor in constructors) {
                    ctor.isAccessible = true
                    val paramTypes = ctor.parameterTypes
                    val args = arrayOfNulls<Any>(paramTypes.size)
                    for (i in args.indices) {
                        if (paramTypes[i] == Int::class.javaPrimitiveType) args[i] = values.size
                    }
                    val event = ctor.newInstance(*args) as SensorEvent
                    System.arraycopy(values, 0, event.values, 0, values.size)
                    try {
                        val sf = SensorEvent::class.java.getDeclaredField("sensor")
                        sf.isAccessible = true
                        sf.set(event, sensor)
                        val tf = SensorEvent::class.java.getDeclaredField("timestamp")
                        tf.isAccessible = true
                        tf.setLong(event, SystemClock.elapsedRealtimeNanos())
                        val af = SensorEvent::class.java.getDeclaredField("accuracy")
                        af.isAccessible = true
                        af.setInt(event, 3)
                    } catch (_: Throwable) {}
                    return event
                }
            } catch (_: Throwable) {}

            try {
                // Fallback 2: sun.misc.Unsafe.allocateInstance
                val unsafeClass = Class.forName("sun.misc.Unsafe")
                val theUnsafeField = unsafeClass.getDeclaredField("theUnsafe")
                theUnsafeField.isAccessible = true
                val unsafe = theUnsafeField.get(null)
                val allocateMethod = unsafeClass.getMethod("allocateInstance", Class::class.java)
                val event = allocateMethod.invoke(unsafe, SensorEvent::class.java) as SensorEvent

                val valuesField = SensorEvent::class.java.getDeclaredField("values")
                valuesField.isAccessible = true
                val arr = FloatArray(values.size)
                System.arraycopy(values, 0, arr, 0, values.size)
                valuesField.set(event, arr)

                val sf = SensorEvent::class.java.getDeclaredField("sensor")
                sf.isAccessible = true
                sf.set(event, sensor)

                val tf = SensorEvent::class.java.getDeclaredField("timestamp")
                tf.isAccessible = true
                tf.setLong(event, SystemClock.elapsedRealtimeNanos())

                val af = SensorEvent::class.java.getDeclaredField("accuracy")
                af.isAccessible = true
                af.setInt(event, 3)
                return event
            } catch (_: Throwable) {}

            return null
        }
    }
}
