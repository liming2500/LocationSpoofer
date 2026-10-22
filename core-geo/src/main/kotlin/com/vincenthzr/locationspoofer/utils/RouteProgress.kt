package com.vincenthzr.locationspoofer.utils

import kotlin.math.abs

/**
 * 路线上的"时间 → 累计行进距离"映射，把速度模型（配速起伏、起步斜坡、坡度）积分成距离。
 *
 * 为什么需要它：速度里一旦引入**坡度**，速度就依赖当前位置、位置又依赖速度的积分，形成反馈环，
 * 不再有闭式解。而 App 端、Xposed 端、system_server 各自独立计算同一时刻的位置，必须得到相同结果。
 *
 * 解法是把时间切成固定长度的小段（[SEGMENT_SEC] 秒），每段内：
 * 1. 取该段**起点**的累计距离算坡度，段内视为不变；
 * 2. 用配速系数在该段中点处的取值，把段内的平均速度当作常数；
 * 3. 累加得到距离。
 *
 * **一致性契约（重要）**：分段表一旦构建，就一次性算满 [PRECOMPUTE_SEC] 秒，
 * 结果只由构造函数参数决定，与"第一次查询发生在第几秒"无关。
 * 任何进程在任意时刻查询，得到的分段表完全相同，因而距离逐位一致。
 * 超出预计算范围后按最后一段速度线性外推（长距离路线也不会卡）。
 */
class RouteProgress(
    private val body: MotionRealism.Session,
    private val baseSpeedMs: Double,
    private val rampSec: Double,
    /** 给定累计距离（米）返回该处的坡度（米/米）；null 表示不考虑坡度 */
    private val gradeAt: ((distanceM: Double) -> Double)?
) {

    private val segmentCount: Int = (PRECOMPUTE_SEC / SEGMENT_SEC).toInt()
    /** 第 i 段的起点时间（秒） */
    private val starts = DoubleArray(segmentCount)
    /** 第 i 段结束时的累计距离 */
    private val cumulative = DoubleArray(segmentCount + 1)

    init {
        cumulative[0] = 0.0
        for (i in 0 until segmentCount) {
            val startT = i * SEGMENT_SEC
            val midT = startT + SEGMENT_SEC / 2.0
            starts[i] = startT
            // 段起点位置已知且与历史无关；不要用"起点里程 + 半段距离"估算中点，
            // 否则结果依赖预计算起点，两个进程会算出不同距离。
            val grade = gradeAt?.invoke(cumulative[i])
            val speed = (baseSpeedMs * body.paceFactor(midT, grade) * rampAt(midT)).coerceAtLeast(0.0)
            cumulative[i + 1] = cumulative[i] + speed * SEGMENT_SEC
        }
    }

    /** 从起步到 elapsedSec 时刻的累计行进距离（米），不含停车后的滑行 */
    fun distanceAt(elapsedSec: Double): Double {
        if (elapsedSec <= 0.0) return 0.0
        val lastStart = (segmentCount - 1) * SEGMENT_SEC
        if (elapsedSec >= lastStart + SEGMENT_SEC) {
            // 超出预计算范围：按最后一段速度线性外推，结果仍与查询时刻无关
            val tailSpeed = (cumulative[segmentCount] - cumulative[segmentCount - 1]) / SEGMENT_SEC
            return cumulative[segmentCount] + tailSpeed * (elapsedSec - segmentCount * SEGMENT_SEC)
        }
        val seg = (elapsedSec / SEGMENT_SEC).toInt().coerceIn(0, segmentCount - 1)
        val frac = ((elapsedSec - starts[seg]) / SEGMENT_SEC).coerceIn(0.0, 1.0)
        return cumulative[seg] + (cumulative[seg + 1] - cumulative[seg]) * frac
    }

    /**
     * 到达终点后经过 [stoppedSec] 秒的滑行距离：速度从停车时刻的 v 按 smoothStep 衰减到 0，
     * 总滑行距离 = v · rampSec / 2。
     */
    fun coastDistance(v: Double, stoppedSec: Double): Double {
        if (rampSec <= 0.0 || stoppedSec <= 0.0) return 0.0
        val p = (stoppedSec / rampSec).coerceIn(0.0, 1.0)
        val area = p - p * p * p + 0.5 * p * p * p * p
        return (v * rampSec * area).coerceAtLeast(0.0)
    }

    /** elapsedSec 时刻的瞬时速度（米/秒），含配速、坡度与斜坡 */
    fun speedAt(elapsedSec: Double): Double {
        val grade = gradeAt?.invoke(distanceAt(elapsedSec))
        return (baseSpeedMs * body.paceFactor(elapsedSec, grade) * rampAt(elapsedSec)).coerceAtLeast(0.0)
    }

    /** 起步斜坡的瞬时速度系数：smoothStep(t / rampSec)，从 0 平滑升到 1 */
    private fun rampAt(t: Double): Double {
        if (rampSec <= 0.0) return 1.0
        return smoothStep((t / rampSec).coerceIn(0.0, 1.0))
    }

    private fun smoothStep(x: Double): Double = x * x * (3 - 2 * x)

    companion object {
        /**
         * 分段长度（秒）。越短越精确，但每段要算几次三角函数。
         * 取 1 秒：起步斜坡只有几秒，5 秒分段会把 smoothStep 曲线台阶化、
         * 单段误差可达 1 米以上；1 秒分段把误差压到厘米级。
         */
        private const val SEGMENT_SEC = 1.0

        /**
         * 预计算时长（秒）。分段表一次性算满，保证"结果与何时首次查询无关"。
         * 2 小时覆盖绝大多数校园跑场景；超出部分按末段速度外推。
         */
        private const val PRECOMPUTE_SEC = 7200.0

        /** 两个进程算出的距离差允许的浮点误差（米） */
        const val CONSISTENCY_TOLERANCE_M = 1e-6

        fun differs(a: Double, b: Double): Boolean = abs(a - b) > CONSISTENCY_TOLERANCE_M
    }
}
