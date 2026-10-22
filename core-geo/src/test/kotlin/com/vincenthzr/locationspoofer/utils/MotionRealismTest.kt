package com.vincenthzr.locationspoofer.utils

import java.util.Random
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionRealismTest {

    private val start = 1_789_398_037_217L

    @Test
    fun `level off with no speed fluctuation keeps the legacy uniform motion`() {
        val s = MotionRealism.Session(start, MotionRealism.Level.OFF, 0)
        assertEquals(3.0 * 125.0, s.distance(3.0, 125.0), 1e-9)
        assertEquals(3.0, s.speed(3.0, 125.0), 1e-12)
        assertEquals(165.0 / 60.0 * 125.0, s.steps(165.0, 125.0), 1e-9)
    }

    @Test
    fun `sessions with the same start timestamp agree across processes`() {
        val a = MotionRealism.Session(start, MotionRealism.Level.HIGH, 20)
        val b = MotionRealism.Session(start, MotionRealism.Level.HIGH, 20)
        val c = MotionRealism.Session(start + 1, MotionRealism.Level.HIGH, 20)
        assertEquals(a.distance(3.0, 777.0), b.distance(3.0, 777.0), 0.0)
        assertEquals(a.steps(160.0, 777.0), b.steps(160.0, 777.0), 0.0)
        assertNotEquals(a.distance(3.0, 777.0), c.distance(3.0, 777.0), 1e-6)
    }

    @Test
    fun `speed stays within the configured fluctuation and matches the distance derivative`() {
        val s = MotionRealism.Session(start, MotionRealism.Level.MEDIUM, 20)
        var sawVariation = false
        for (t in 0..3600 step 7) {
            val v = s.speed(3.0, t.toDouble())
            assertTrue("speed $v at $t", v in 3.0 * 0.8 - 1e-9..3.0 * 1.2 + 1e-9)
            if (abs(v - 3.0) > 0.1) sawVariation = true
            val h = 1e-3
            val derivative = (s.distance(3.0, t + h) - s.distance(3.0, t.toDouble())) / h
            assertEquals(v, derivative, 1e-3)
        }
        assertTrue(sawVariation)
    }

    @Test
    fun `step count keeps increasing and cadence varies around the base`() {
        val s = MotionRealism.Session(start, MotionRealism.Level.HIGH, 15)
        var last = -1.0
        var minCadence = Double.MAX_VALUE
        var maxCadence = 0.0
        for (t in 0..1800) {
            val steps = s.steps(170.0, t.toDouble())
            assertTrue(steps > last)
            last = steps
            val c = s.cadence(170.0, t.toDouble())
            minCadence = minOf(minCadence, c)
            maxCadence = maxOf(maxCadence, c)
        }
        assertTrue(maxCadence - minCadence > 5)
        assertTrue(minCadence > 170 * 0.85 && maxCadence < 170 * 1.15)
    }

    @Test
    fun `procedural gait averages to gravity over whole cycles`() {
        for (speed in listOf(1.3, 3.2)) {
            val s = MotionRealism.Session(start, MotionRealism.Level.OFF, 0)
            val rnd = Random(1)
            val n = 20_000
            var sx = 0.0
            var sy = 0.0
            var sz = 0.0
            for (i in 0 until n) {
                val stepsFloat = i * 40.0 / n // 恰好 40 步 = 20 个完整跨步
                val a = s.accelerometer(stepsFloat, speed, null, rnd)
                sx += a[0]; sy += a[1]; sz += a[2]
            }
            assertEquals(0.0, sx / n, 0.05)
            assertEquals(0.0, sy / n, 0.05)
            assertEquals(9.80665, sz / n, 0.05)
        }
    }

    @Test
    fun `step impact strength differs between steps when randomness is on`() {
        val s = MotionRealism.Session(start, MotionRealism.Level.MEDIUM, 0)
        val quiet = Random(0)
        // 关闭噪声的对照：同一相位、不同步序号，冲击峰值应当不同
        val peaks = (0 until 6).map { step -> s.accelerometer(step.toDouble(), 1.4, null, quiet)[2] }
        assertTrue(peaks.toSet().size > 1)
    }

    @Test
    fun `cadence fluctuation widens the cadence swing without touching distance`() {
        val quiet = MotionRealism.Session(start, MotionRealism.Level.OFF, 0, 0)
        val lively = MotionRealism.Session(start, MotionRealism.Level.OFF, 0, 15)
        var quietSwing = 0.0
        var livelySwing = 0.0
        var quietMin = Double.MAX_VALUE
        var quietMax = 0.0
        var livelyMin = Double.MAX_VALUE
        var livelyMax = 0.0
        for (t in 0..1800) {
            val q = quiet.cadence(170.0, t.toDouble())
            val l = lively.cadence(170.0, t.toDouble())
            quietMin = minOf(quietMin, q); quietMax = maxOf(quietMax, q)
            livelyMin = minOf(livelyMin, l); livelyMax = maxOf(livelyMax, l)
            // 步频起伏不影响位置：同一时刻的累计距离完全相同
            assertEquals(quiet.distance(3.0, t.toDouble()), lively.distance(3.0, t.toDouble()), 0.0)
        }
        quietSwing = quietMax - quietMin
        livelySwing = livelyMax - livelyMin
        assertTrue("quiet=$quietSwing lively=$livelySwing", livelySwing > quietSwing * 3)
        // 15% 的设置下步频不会超出 ±15% 的范围
        assertTrue(livelyMin >= 170 * 0.85 - 1e-9 && livelyMax <= 170 * 1.15 + 1e-9)
    }

    @Test
    fun `cadence fluctuation adds to the randomness level instead of replacing it`() {
        val a = MotionRealism.Session(start, MotionRealism.Level.OFF, 0, 10)
        val b = MotionRealism.Session(start, MotionRealism.Level.HIGH, 0, 10)
        var minA = Double.MAX_VALUE
        var maxA = 0.0
        var minB = Double.MAX_VALUE
        var maxB = 0.0
        for (t in 0..1800) {
            val ca = a.cadence(170.0, t.toDouble())
            val cb = b.cadence(170.0, t.toDouble())
            minA = minOf(minA, ca); maxA = maxOf(maxA, ca)
            minB = minOf(minB, cb); maxB = maxOf(maxB, cb)
        }
        assertTrue(maxB - minB > maxA - minA)
    }

    @Test
    fun `session cache returns the same instance for the same parameters`() {
        val a = MotionRealism.session(start, 2, 10, 5)
        val b = MotionRealism.session(start, 2, 10, 5)
        val c = MotionRealism.session(start, 2, 20, 5)
        val d = MotionRealism.session(start, 2, 10, 6)
        assertTrue(a === b)
        assertTrue(a !== c)
        assertTrue(a !== d)
    }
}
