package com.vincenthzr.locationspoofer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 计步时钟的单调性与"不重复累计"。
 *
 * 目标 App 的步频 = 步骤增量 / 时间。只要累计步数在某个时刻跳变（重复累计了一段已走过的时间），
 * App 就会显示成两倍的步频。这里按真实配置的写法（每秒可能来回切换时间戳）驱动时钟，
 * 逐秒检查增量。
 */
class StepClockTest {

    private val start = 1_789_398_037_217L
    private val session = MotionRealism.Session(start, MotionRealism.Level.OFF, 0)

    private fun cadenceSpm(speed: Double): Double = when {
        speed <= 2.8 -> 150.0
        else -> 165.0
    }

    @Test
    fun `elapsed time is counted once when the timestamp goes backwards`() {
        val clock = StepClock()
        // 模拟：路线配置用固定 routeStartTs，就位等待 / 摇杆每秒把时间戳改成"现在"，
        // 之后又切回那个较早的 routeStartTs。
        var now = start
        clock.advance(start, now, cadenceSpm(3.0)) { session }
        var last = clock.steps
        for (i in 0 until 60) {
            now += 1_000L
            // 偶数秒读到"现在"，奇数秒读到更早的固定时间戳
            val ts = if (i % 2 == 0) now else start
            val steps = clock.advance(ts, now, cadenceSpm(3.0)) { session }
            val perSecond = steps - last
            assertTrue("t=$i steps jumped by $perSecond", perSecond < 165.0 / 60.0 * 1.2)
            assertTrue(steps >= last)
            last = steps
        }
        // 60 秒 165 spm 应该正好约 165 步，而不是 300+
        assertEquals(165.0, clock.steps, 1.0)
    }

    @Test
    fun `crossing into a newer session keeps the step count continuous`() {
        val clock = StepClock()
        var now = start
        repeat(30) {
            now += 1_000L
            clock.advance(start, now, cadenceSpm(3.0)) { session }
        }
        val before = clock.steps
        // 新的路线开始：时间戳推进到"现在"，已走步数不能清零，也不能重复累计
        val resume = now
        val after = clock.advance(resume, now, cadenceSpm(3.0)) { MotionRealism.Session(resume, MotionRealism.Level.OFF, 0) }
        assertEquals(before, after, 1e-6)
        assertEquals(82.5, after, 1.0)
    }

    @Test
    fun `advance is idempotent for the same instant`() {
        val clock = StepClock()
        val now = start + 45_000L
        val a = clock.advance(start, now, 165.0) { session }
        val b = clock.advance(start, now, 165.0) { session }
        assertEquals(a, b, 0.0)
        assertEquals(165.0 / 60.0 * 45, a, 1e-6)
    }

    @Test
    fun `standing still does not bank steps to release later`() {
        val clock = StepClock()
        val waitStart = start
        // 就位等待 5 分钟，期间每秒调用一次
        var now = waitStart
        repeat(300) {
            now += 1_000L
            clock.pause(now)
        }
        assertEquals(0.0, clock.steps, 0.0)
        // 开始跑步后 10 秒只该有 10 秒的步数，而不是把这 5 分钟补进来
        clock.advance(now, now + 10_000L, 165.0) { MotionRealism.Session(it, MotionRealism.Level.OFF, 0) }
        assertEquals(27.5, clock.steps, 1.0)
    }

    @Test
    fun `hot reload state restores the running count`() {
        val clock = StepClock(2350.0)
        val now = start + 10_000L
        val steps = clock.advance(start, now, 165.0) { session }
        val reloaded = StepClock()
        reloaded.restore(clock.base, steps, clock.anchorTs)
        assertEquals(steps, reloaded.advance(start, now, 165.0) { session }, 0.0)
    }
}
