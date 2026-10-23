package com.vincenthzr.locationspoofer.utils

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

/**
 * 运动真实度引擎：给速度、步频（步幅）、海拔、加速度计波形加上自然起伏。
 *
 * 速度、步频这些"慢变量"都是以模拟开始时间为种子的确定性函数——system_server、每个被 Hook 的
 * App 进程、以及 App 自己的路线进度都在各自独立计算位置和步数，必须对同一时刻算出同样的结果，
 * 否则不同进程之间的距离、步数会对不上。随机强度为 [Level.OFF]、速度浮动和步频浮动都为 0 时
 * 退化为匀速匀步频。
 *
 * 速度浮动与步频浮动是两项独立设置（同海拔变化范围一样在界面上用滑块调）：
 * 速度浮动改变配速，步频浮动改变计步传感器的步频，二者之差落在步幅上。步频只影响步数，
 * 不参与位置计算，所以调大它不会让路线位置跳变。
 */
object MotionRealism {

    enum class Level(
        val id: Int,
        /** 随机强度在用户设置的步频起伏之上额外附加的相对起伏 */
        val cadenceVariation: Double,
        /** 海拔随时间漂移的幅度（米），由 [AltitudeModel] 使用 */
        val altitudeAmplitudeM: Double,
        /** 每一步冲击力度的相对差异 */
        val stepAmplitudeVariation: Double,
        /** 加速度计白噪声标准差（m/s²） */
        val sensorNoise: Double
    ) {
        OFF(0, 0.0, 0.0, 0.0, 0.0),
        LOW(1, 0.015, 1.5, 0.05, 0.03),
        MEDIUM(2, 0.03, 3.0, 0.10, 0.06),
        HIGH(3, 0.05, 5.0, 0.18, 0.10);

        companion object {
            fun fromId(id: Int): Level = entries.firstOrNull { it.id == id } ?: MEDIUM
        }
    }

    const val DEFAULT_LEVEL_ID = 2
    const val DEFAULT_SPEED_FLUCTUATION_PCT = 10
    const val MAX_SPEED_FLUCTUATION_PCT = 30

    /** 步频相对基准值的起伏范围（±百分比），与速度起伏一样在会话内取快照 */
    const val DEFAULT_CADENCE_FLUCTUATION_PCT = 6
    const val MAX_CADENCE_FLUCTUATION_PCT = 30

    /** 起步/停车的加减速斜坡时长（秒） */
    const val DEFAULT_RAMP_SEC = 4.0

    /** 坡度对配速的敏感度：0 = 不受坡度影响，1 = 标准（见 [LEVEL_GRADE_SENSITIVITY]） */
    const val DEFAULT_GRADE_SENSITIVITY = 50
    const val MAX_GRADE_SENSITIVITY = 100

    /** 坡度每 1% 对应的相对配速变化（该数值乘以敏感度系数使用） */
    private const val GRADE_SENSITIVITY = 0.022

    /** 速度变化中由步频承担的比例，其余由步幅承担（步幅 = 速度 / 步频） */
    private const val CADENCE_SHARE_OF_SPEED = 0.4
    private const val RUN_SPEED_THRESHOLD = 2.2
    private const val GRAVITY = 9.80665f

    /** 计算加速度振幅时速度的上限：避免超高速度档位算出不符合生理的冲击力 */
    private const val MAX_AMPLITUDE_SPEED = 6.0

    private const val SPEED_SALT = 0x5EED5EEDL
    private const val CADENCE_SALT = 0x0CADE0CEL
    private const val STEP_SALT = 0x57E9057EL
    private const val BREATH_SALT = 0xB4EA7B4EADL

    /** 短周期配速波在总浮动额度中占的比例（其余为长周期慢漂） */
    private const val BREATH_SHARE_OF_FLUCTUATION = 0.35
    private const val MIN_PACE_FACTOR = 0.25
    private const val MAX_PACE_FACTOR = 2.5

    /** 平滑起步曲线：两端导数为 0，避免速度突变 */
    private fun smoothStep(x: Double): Double = x * x * (3 - 2 * x)

    /**
     * 若干个周期、相位随机的正弦叠加成的平滑噪声，取值范围 [-1, 1]。
     * 用正弦叠加而不是随机游走，是因为它的积分有解析解：任意时刻的累计距离 / 步数都能直接算出来，
     * 不依赖逐帧累加的中间状态，所以各个进程在任意时刻都能独立得到一致的结果。
     */
    class SmoothNoise(seed: Long, minPeriodSec: Double, maxPeriodSec: Double, components: Int = 4) {
        private val periods: DoubleArray
        private val phases: DoubleArray
        private val weights: DoubleArray

        init {
            val rnd = Random(seed)
            periods = DoubleArray(components) { minPeriodSec * (maxPeriodSec / minPeriodSec).pow(rnd.nextDouble()) }
            phases = DoubleArray(components) { rnd.nextDouble() * 2 * PI }
            val raw = DoubleArray(components) { 0.5 + rnd.nextDouble() }
            val sum = raw.sum()
            weights = DoubleArray(components) { raw[it] / sum }
        }

        fun value(tSec: Double): Double {
            var v = 0.0
            for (i in periods.indices) v += weights[i] * sin(2 * PI * tSec / periods[i] + phases[i])
            return v
        }

        /** ∫₀ᵗ value(τ) dτ */
        fun integral(tSec: Double): Double {
            var v = 0.0
            for (i in periods.indices) {
                val w = 2 * PI / periods[i]
                v += weights[i] / w * (cos(phases[i]) - cos(w * tSec + phases[i]))
            }
            return v
        }
    }

    class Session(
        val startTimestamp: Long,
        val level: Level,
        speedFluctuationPct: Int,
        cadenceFluctuationPct: Int = 0,
        /** 起步/停车斜坡时长（秒），0 表示瞬间到达目标速度（旧行为） */
        rampSec: Double = 0.0,
        /** 坡度敏感度，0~100，0 表示配速不受坡度影响 */
        gradeSensitivity: Int = 0
    ) {
        val speedFluctuationPct = speedFluctuationPct.coerceIn(0, MAX_SPEED_FLUCTUATION_PCT)
        val cadenceFluctuationPct = cadenceFluctuationPct.coerceIn(0, MAX_CADENCE_FLUCTUATION_PCT)
        val rampSec = rampSec.coerceIn(0.0, 20.0)
        val gradeSensitivity = gradeSensitivity.coerceIn(0, MAX_GRADE_SENSITIVITY)
        private val fluctuation = this.speedFluctuationPct / 100.0
        private val cadenceFluctuation = this.cadenceFluctuationPct / 100.0
        private val speedNoise = SmoothNoise(startTimestamp xor SPEED_SALT, 20.0, 600.0)
        // 短周期配速波：30~180 秒，模拟呼吸与发力节奏（方案 C）
        private val breathNoise = SmoothNoise(startTimestamp xor BREATH_SALT, 30.0, 180.0, components = 3)
        private val cadenceNoise = SmoothNoise(startTimestamp xor CADENCE_SALT, 3.0, 60.0)

        /** 坡度每 1% 对应的配速变化系数（已乘敏感度） */
        private val gradeGain: Double get() = GRADE_SENSITIVITY * (gradeSensitivity / 100.0)

        /**
         * 相对配速系数（不含起步斜坡）。
         *
         * @param grade 当前位置沿行进方向的坡度（米/米），null 表示不考虑坡度
         */
        /**
         * 短周期波占总浮动的比例：用户设置的是"配速起伏总幅度"，
         * 长周期慢漂与短周期呼吸波分配这个额度，而不是各自独立叠加（否则总幅度会超出设置值）。
         */
        private val breathShare: Double get() = BREATH_SHARE_OF_FLUCTUATION

        fun paceFactor(elapsedSec: Double, grade: Double? = null): Double {
            // 浮动为 0 时两项都为 0，退化为恒定配速
            val longGain = fluctuation * (1 - breathShare)
            val breathGain = fluctuation * breathShare
            val slope = if (grade == null) 0.0 else -gradeGain * grade * 100.0
            val v = 1 +
                longGain * speedNoise.value(elapsedSec) +
                breathGain * breathNoise.value(elapsedSec) +
                slope
            return v.coerceIn(MIN_PACE_FACTOR, MAX_PACE_FACTOR)
        }

        /**
         * 起步 / 停车斜坡系数。
         *
         * @param elapsedSec 本次起步后经过的时间
         * @param moving      当前是否处于运动状态（到达终点后为 false，逐渐减速到 0）
         */
        fun rampFactor(elapsedSec: Double, moving: Boolean): Double {
            if (rampSec <= 0.0) return if (moving) 1.0 else 0.0
            if (moving) return smoothStep((elapsedSec / rampSec).coerceIn(0.0, 1.0))
            // 停车：从最后一次运动时刻开始减速
            return 0.0
        }

        fun speed(baseSpeed: Double, elapsedSec: Double): Double =
            baseSpeed * paceFactor(elapsedSec)

        /**
         * 从开始到 elapsedSec 的累计行进距离（米），与其他方法保持同一速度模型（含短周期波），
         * 不含起步斜坡与坡度——那两项由 [RouteProgress] 负责。
         *
         * 短周期波与其乘积项按一阶近似积分：∫(1+a·n1)(1+b·n2)dt ≈ t + a∫n1 + b∫n2
         * （两个噪声相互独立，交叉项期望为 0，误差远小于 GPS 抖动）。
         */
        fun distance(baseSpeed: Double, elapsedSec: Double): Double {
            val longGain = fluctuation * (1 - breathShare)
            val breathGain = fluctuation * breathShare
            return baseSpeed * (
                elapsedSec +
                    longGain * speedNoise.integral(elapsedSec) +
                    breathGain * breathNoise.integral(elapsedSec)
                )
        }

        /** 步频由用户设置的步频起伏与随机强度的附加起伏叠加而成 */
        private val cadenceGain: Double get() = cadenceFluctuation + level.cadenceVariation

        fun cadence(baseCadenceSpm: Double, elapsedSec: Double): Double = baseCadenceSpm * (
            1 + CADENCE_SHARE_OF_SPEED * fluctuation * speedNoise.value(elapsedSec) +
                cadenceGain * cadenceNoise.value(elapsedSec)
            )

        /** 从开始到 elapsedSec 的累计步数（连续值，整数部分是步数，小数部分是当前这一步的相位） */
        fun steps(baseCadenceSpm: Double, elapsedSec: Double): Double = baseCadenceSpm / 60.0 * (
            elapsedSec + CADENCE_SHARE_OF_SPEED * fluctuation * speedNoise.integral(elapsedSec) +
                cadenceGain * cadenceNoise.integral(elapsedSec)
            )

        /**
         * 加速度计读数（含重力，m/s²），坐标约定：Z 竖直、Y 前后、X 左右。
         * 有录制的步态模板时按模板回放，否则用内置的步行 / 跑步波形；两者都会叠加逐步的力度差异和传感器噪声。
         */
        fun accelerometer(
            stepsFloat: Double,
            speed: Double,
            template: GaitTemplate?,
            noise: Random
        ): FloatArray {
            val stepIndex = floor(stepsFloat).toLong()
            val stepPhase = stepsFloat - stepIndex
            val strength = 1 + level.stepAmplitudeVariation * hashUnit(startTimestamp xor STEP_SALT, stepIndex)
            val out = if (template != null) {
                template.sample(stepsFloat / 2.0, strength)
            } else {
                proceduralGait(stepPhase, stepsFloat, stepIndex, speed, strength)
            }
            if (level.sensorNoise > 0) {
                for (i in 0..2) out[i] += (noise.nextGaussian() * level.sensorNoise).toFloat()
            }
            return out
        }
    }

    @Volatile
    private var cached: Session? = null

    /** 同一次模拟会话内复用同一个 Session，避免在高频 Hook 里反复初始化噪声参数 */
    fun session(
        startTimestamp: Long,
        levelId: Int,
        speedFluctuationPct: Int,
        cadenceFluctuationPct: Int = 0,
        rampSec: Double = 0.0,
        gradeSensitivity: Int = 0
    ): Session {
        val level = Level.fromId(levelId)
        val speedPct = speedFluctuationPct.coerceIn(0, MAX_SPEED_FLUCTUATION_PCT)
        val cadencePct = cadenceFluctuationPct.coerceIn(0, MAX_CADENCE_FLUCTUATION_PCT)
        val ramp = rampSec.coerceIn(0.0, 20.0)
        val grade = gradeSensitivity.coerceIn(0, MAX_GRADE_SENSITIVITY)
        val c = cached
        if (c != null && c.startTimestamp == startTimestamp && c.level == level &&
            c.speedFluctuationPct == speedPct && c.cadenceFluctuationPct == cadencePct &&
            c.rampSec == ramp && c.gradeSensitivity == grade
        ) return c
        return Session(startTimestamp, level, speedPct, cadencePct, ramp, grade).also { cached = it }
    }

    // ---------------------------------------------------------------- 内置步态波形

    private const val SHAPE_SAMPLES = 256

    private fun bump(p: Double, center: Double, width: Double): Double {
        var d = abs(p - center)
        if (d > 0.5) d = 1 - d
        return exp(-0.5 * (d / width) * (d / width))
    }

    /**
     * 不对称的单峰冲击：落地瞬间上升很快、回落较慢，比对称高斯更像真实着地波形。
     * [rise] 用于 center 之前的宽度，[fall] 用于 center 之后，单位都是"步"（相位 0~1）。
     */
    private fun impulse(p: Double, center: Double, rise: Double, fall: Double): Double {
        var d = p - center
        if (d > 0.5) d -= 1.0
        if (d < -0.5) d += 1.0
        val w = if (d < 0) rise else fall
        return exp(-0.5 * (d / w) * (d / w))
    }

    /** 采样成查表并去掉均值：真实加速度计在一个步态周期内的平均值必须等于重力，否则长时间积分会暴露 */
    private fun zeroMeanTable(shape: (Double) -> Double): DoubleArray {
        val table = DoubleArray(SHAPE_SAMPLES) { shape(it.toDouble() / SHAPE_SAMPLES) }
        val mean = table.average()
        for (i in table.indices) table[i] -= mean
        return table
    }

    // 步行：脚跟着地冲击 → 支撑期回落 → 蹬地后进入摆动期低谷
    //
    // 每个"步"（相位 0~1）里只允许出现**一个**极大值。计步传感器大多用"均值去偏 + 波峰计数 + 不应期"
    // 来数步，一步里若有两个高度接近的峰（旧的波形就是这样：0.00 处 1.00、0.16 处 0.73），
    // 会被数成两步，App 显示的平均步频就翻倍到 300+。所以第二峰只保留为不显峰的平台，
    // 其余动态量都放在谷（摆动期低谷）上，让单个周期的波峰数恒为 1。
    private val WALK_VERTICAL = zeroMeanTable { p ->
        1.00 * impulse(p, 0.02, 0.030, 0.070) - 0.45 * bump(p, 0.55, 0.17)
    }
    private val WALK_FORWARD = zeroMeanTable { p ->
        0.45 * impulse(p, 0.42, 0.070, 0.090) - 0.35 * bump(p, 0.88, 0.13)
    }

    // 跑步：尖锐的落地冲击 + 腾空期接近失重
    private val RUN_VERTICAL = zeroMeanTable { p ->
        1.00 * impulse(p, 0.02, 0.022, 0.055) - 0.55 * bump(p, 0.55, 0.17)
    }
    private val RUN_FORWARD = zeroMeanTable { p ->
        0.50 * impulse(p, 0.28, 0.060, 0.080) - 0.40 * bump(p, 0.80, 0.14)
    }

    /** 左右摆动：每一步一次侧向冲击，符号按奇偶步交替，同样保证一步只有一个峰 */
    private val LATERAL = zeroMeanTable { p ->
        impulse(p, 0.05, 0.035, 0.060)
    }

    private fun lookup(table: DoubleArray, phase: Double): Double {
        val pos = (phase - floor(phase)) * table.size
        val i = pos.toInt() % table.size
        val frac = pos - floor(pos)
        return table[i] * (1 - frac) + table[(i + 1) % table.size] * frac
    }

    private fun proceduralGait(
        stepPhase: Double,
        stepsFloat: Double,
        stepIndex: Long,
        speed: Double,
        strength: Double
    ): FloatArray {
        val running = speed >= RUN_SPEED_THRESHOLD
        // 振幅随速度增长，但上限压住：速度再快也不该出现几十 m/s² 的读数（10 m/s 时旧公式到 21 m/s²）
        val amplitude = if (running) 6.0 + 1.5 * speed.coerceAtMost(MAX_AMPLITUDE_SPEED)
        else 2.0 + 1.0 * speed.coerceAtMost(MAX_AMPLITUDE_SPEED)
        val vertical = lookup(if (running) RUN_VERTICAL else WALK_VERTICAL, stepPhase)
        val forward = lookup(if (running) RUN_FORWARD else WALK_FORWARD, stepPhase)
        // 左右方向：侧向冲击方向按左右脚交替，一步一次；摆动项以一个跨步（两步）为周期
        val side = if (stepIndex % 2 == 0L) 1.0 else -1.0
        val lateral = 0.55 * amplitude * side * lookup(LATERAL, stepPhase) +
            0.05 * amplitude * sin(PI * stepsFloat)

        val z = (GRAVITY + strength * amplitude * vertical).coerceAtLeast(0.0)
        return floatArrayOf(
            (strength * lateral).toFloat(),
            (strength * 0.35 * amplitude * forward).toFloat(),
            z.toFloat()
        )
    }

    /** 由 (seed, n) 确定的 [-1, 1] 伪随机数：同一步在不同进程里算出同样的力度 */
    private fun hashUnit(seed: Long, n: Long): Double {
        var x = seed xor (n * -0x61c8864680b583ebL)
        x = (x xor (x ushr 33)) * -0xae502812aa7333L
        x = (x xor (x ushr 33)) * -0x3b314601e57a13adL
        x = x xor (x ushr 33)
        return (x ushr 11).toDouble() / (1L shl 53).toDouble() * 2 - 1
    }
}
