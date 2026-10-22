package com.vincenthzr.locationspoofer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 速度模型积分：起步斜坡、坡度联动、跨进程一致性 */
class RouteProgressTest {

    private val start = 1_789_398_037_217L
    private val base = 3.0

    private fun progress(
        ramp: Double = 0.0,
        grade: Double? = null,
        pct: Int = 0
    ) = RouteProgress(
        // 坡度不为 null 时把敏感度拉满，让坡度效应可观测
        MotionRealism.Session(
            start, MotionRealism.Level.OFF, pct, 0, ramp,
            if (grade != null) MotionRealism.MAX_GRADE_SENSITIVITY else 0
        ),
        base,
        ramp,
        grade?.let { g -> { _: Double -> g } }
    )

    @Test
    fun `zero ramp and zero grade matches uniform motion exactly`() {
        val p = progress()
        assertEquals(base * 600.0, p.distanceAt(600.0), 1e-6)
    }

    @Test
    fun `ramp delays the start without losing distance afterwards`() {
        val p = progress(ramp = 4.0)
        // 起步 4 秒内走不满
        assertTrue(p.distanceAt(2.0) < base * 2.0)
        // 10 秒：前 4 秒按 smoothStep 积分（面积恰为 ramp 的一半），之后按全速
        val expected = base * (4.0 / 2.0 + 6.0)
        assertEquals(expected, p.distanceAt(10.0), 1.0)
    }

    @Test
    fun `uphill slows the pace and downhill speeds it up`() {
        val flat = progress()
        val up = progress(grade = 0.05)
        val down = progress(grade = -0.05)
        val t = 600.0
        assertTrue("uphill should be slower", up.distanceAt(t) < flat.distanceAt(t))
        assertTrue("downhill should be faster", down.distanceAt(t) > flat.distanceAt(t))
    }

    @Test
    fun `two processes compute the same distance for the same inputs`() {
        val a = progress(ramp = 3.0, grade = 0.03, pct = 12)
        val b = progress(ramp = 3.0, grade = 0.03, pct = 12)
        for (t in listOf(0.0, 1.0, 7.5, 60.0, 333.3, 1800.0)) {
            assertEquals(a.distanceAt(t), b.distanceAt(t), 0.0)
        }
    }

    @Test
    fun `distance is monotonic in time`() {
        val p = progress(ramp = 2.0, grade = 0.02, pct = 10)
        var last = -1.0
        var t = 0.0
        while (t <= 900.0) {
            val d = p.distanceAt(t)
            assertTrue("distance went backwards at $t", d >= last - 1e-9)
            last = d
            t += 0.5
        }
    }

    @Test
    fun `coast distance is bounded by half the ramp`() {
        val p = progress(ramp = 4.0)
        val v = 3.0
        assertEquals(0.0, p.coastDistance(v, 0.0), 1e-9)
        // 完全停下后总滑行 = v * ramp / 2
        assertEquals(v * 4.0 / 2.0, p.coastDistance(v, 10.0), 0.5)
        // 中途单调递增
        assertTrue(p.coastDistance(v, 2.0) < p.coastDistance(v, 3.0))
    }

    @Test
    fun `query order does not affect the result`() {
        // 同一组参数、不同查询顺序，任意时刻的距离必须完全相同
        val forward = progress(ramp = 3.0, grade = 0.03, pct = 12)
        val backward = progress(ramp = 3.0, grade = 0.03, pct = 12)
        val times = listOf(0.5, 30.0, 777.0, 5.0, 3600.0, 12.0, 7199.0)
        for (t in times) { forward.distanceAt(t) }
        for (t in times.reversed()) { backward.distanceAt(t) }
        for (t in times) {
            assertEquals(forward.distanceAt(t), backward.distanceAt(t), 0.0)
        }
    }

    @Test
    fun `distance beyond the precomputed window stays consistent`() {
        val a = progress(ramp = 2.0, grade = 0.02, pct = 8)
        val b = progress(ramp = 2.0, grade = 0.02, pct = 8)
        // 超出 7200 秒预计算窗口后按末段速度外推，两端仍一致且单调
        val t = 9000.0
        assertEquals(a.distanceAt(t), b.distanceAt(t), 0.0)
        assertTrue(a.distanceAt(t) > a.distanceAt(7200.0))
    }

    @Test
    fun `construction stays fast enough for per-second reuse`() {
        val gradeAt: (Double) -> Double = { d -> Math.sin(d / 300.0) * 0.05 }
        val s = MotionRealism.Session(start, MotionRealism.Level.MEDIUM, 10, 6, 4.0, 50)
        val t0 = System.nanoTime()
        repeat(10) { RouteProgress(s, 3.0, 4.0, gradeAt).distanceAt(600.0) }
        val perBuildMs = (System.nanoTime() - t0) / 1e6 / 10
        // 每次重建不应超过 50 毫秒，否则高频调用会拖慢 Hook
        assertTrue("build took ${perBuildMs}ms", perBuildMs < 50.0)
    }
}
