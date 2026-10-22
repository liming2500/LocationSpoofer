package com.vincenthzr.locationspoofer.utils

import kotlin.math.abs

/**
 * 路线上的"时间 → 累计行进距离"映射，把速度模型（配速起伏、起步斜坡、坡度）积分成距离。
 *
 * 为什么需要它：速度里一旦引入**坡度**，速度就依赖当前位置、位置又依赖速度的积分，形成反馈环，
 * 不再有闭式解。而 App 端、Xposed 端、system_server 各自独立计算同一时刻的位置，必须得到相同结果。
 *
 * 解法是把时间切成固定长度的小段（默认 5 秒），每段内：
 * 1. 取该段起点的位置算坡度，段内视为不变；
 * 2. 用配速系数在该段中点处的取值，把段内的平均速度当作常数；
 * 3. 累加得到距离。
 *
 * 由于分段、段长、种子都固定，任何进程都能从起点重新算到任意时刻，结果逐位一致。
 * 分段误差在 5 秒尺度上远小于 GPS 本身的抖动，可以忽略。
 */
class RouteProgress(
    private val body: MotionRealism.Session,
    private val baseSpeedMs: Double,
    private val rampSec: Double,
    /** 给定累计距离（米）返回该处的坡度（米/米）；null 表示不考虑坡度 */
    private val gradeAt: ((distanceM: Double) -> Double)?
) {

    private var starts = DoubleArray(INITIAL_SEGMENTS)
    private var ends = DoubleArray(INITIAL_SEGMENTS)
    /** 第 i 段结束时的累计距离，长度 = 段数 + 1 */
    private var cumulative = DoubleArray(INITIAL_SEGMENTS + 1)
    private var segmentCount = 0
    private var initialized = false

    /**
     * 返回从起步到 elapsedSec 时刻的累计行进距离（米），不含停车后的滑行。
     * 停车减速由调用方按 [stoppedSec] 单独叠加（见 [coastDistance]）。
     */
    fun distanceAt(elapsedSec: Double): Double = distanceMovingAt(elapsedSec)

    /**
     * 到达终点后经过 [stoppedSec] 秒的滑行距离：速度从停车时刻的 v 按 smoothStep 衰减到 0，
     * 总滑行距离 = v · rampSec / 2，与 [rampSec] 成正比。不依赖任何历史状态，任意进程算出的值一致。
     */
    fun coastDistance(v: Double, stoppedSec: Double): Double {
        if (rampSec <= 0.0 || stoppedSec <= 0.0) return 0.0
        val progress = (stoppedSec / rampSec).coerceIn(0.0, 1.0)
        // ∫ smoothStep 的补函数：面积占比 = 1 - (x³ - 0.5x⁴)... 直接用数值稳定的闭式解
        val area = progress - progress * progress * progress + 0.5 * progress * progress * progress * progress
        return (v * rampSec * area).coerceAtLeast(0.0)
    }

    /** 行进中 elapsedSec 时刻的距离 */
    private fun distanceMovingAt(elapsedSec: Double): Double {
        if (elapsedSec <= 0.0) return 0.0
        ensureUpTo(elapsedSec)
        val last = segmentCount - 1
        if (last < 0) return 0.0
        var lo = 0
        var hi = last
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (ends[mid] < elapsedSec) lo = mid + 1 else hi = mid
        }
        val seg = lo
        val frac = ((elapsedSec - starts[seg]) / SEGMENT_SEC).coerceIn(0.0, 1.0)
        val startDist = cumulative[seg]
        return startDist + (cumulative[seg + 1] - startDist) * frac
    }

    /** elapsedSec 时刻的瞬时速度（米/秒），含配速、坡度与斜坡 */
    fun speedAt(elapsedSec: Double): Double {
        ensureUpTo(elapsedSec)
        val grade = gradeAt?.invoke(distanceMovingAt(elapsedSec))
        return baseSpeedMs * body.paceFactor(elapsedSec, grade) * rampAt(elapsedSec)
    }

    private fun ensureUpTo(t: Double) {
        if (!initialized) {
            initialized = true
            segmentCount = 0
            cumulative[0] = 0.0
        }
        val needed = (t / SEGMENT_SEC).toInt() + 2
        while (segmentCount < needed && segmentCount < MAX_SEGMENTS) {
            appendSegment()
        }
    }

    private fun appendSegment() {
        if (segmentCount + 1 >= starts.size) {
            starts = starts.copyOf(starts.size * 2)
            ends = ends.copyOf(ends.size * 2)
            cumulative = cumulative.copyOf(cumulative.size * 2)
        }
        val startT = segmentCount * SEGMENT_SEC
        val midT = startT + SEGMENT_SEC / 2.0
        val startDist = cumulative[segmentCount]
        // 用段中点的位置取坡度，再据此算段中点速度，把段内速度当常数
        val midDist = startDist + baseSpeedMs * SEGMENT_SEC / 2.0
        val grade = gradeAt?.invoke(midDist)
        val speed = (baseSpeedMs * body.paceFactor(midT, grade) * rampAt(midT)).coerceAtLeast(0.0)

        starts[segmentCount] = startT
        ends[segmentCount] = startT + SEGMENT_SEC
        cumulative[segmentCount + 1] = startDist + speed * SEGMENT_SEC
        segmentCount++
    }

    /**
     * 起步斜坡的**瞬时速度系数**：smoothStep(t / rampSec)，从 0 平滑升到 1。
     * 因为每一段都按段中点取值，累积距离自然等于该曲线的积分，起步只是推迟、不会丢里程。
     */
    private fun rampAt(t: Double): Double {
        if (rampSec <= 0.0) return 1.0
        return smoothStep((t / rampSec).coerceIn(0.0, 1.0))
    }

    private fun smoothStep(x: Double): Double = x * x * (3 - 2 * x)

    companion object {
        /**
         * 分段长度（秒）。越短越精确，但每段要算几次三角函数。
         * 取 1 秒：起步斜坡只有几秒，5 秒分段会把 smoothStep 曲线台阶化、
         * 单段误差可达 1 米以上；1 秒分段把误差压到厘米级，代价是每秒 1 次计算。
         */
        private const val SEGMENT_SEC = 1.0
        private const val INITIAL_SEGMENTS = 256
        private const val MAX_SEGMENTS = 20_000

        /** 两个进程算出的距离差允许的浮点误差（米） */
        const val CONSISTENCY_TOLERANCE_M = 1e-6

        fun differs(a: Double, b: Double): Boolean = abs(a - b) > CONSISTENCY_TOLERANCE_M
    }
}
