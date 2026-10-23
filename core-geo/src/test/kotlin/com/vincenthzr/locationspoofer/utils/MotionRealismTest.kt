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

    /**
     * 一步之内只能有一个局部极大值。
     *
     * 目标 App 的计步器普遍是"去均值 → 波峰计数 → 加不应期"，一步里出现两个等高的峰会被数成两步，
     * 于是 3 m/s（自动步频 163 spm）在 App 里显示成 300+ spm。这里按 500 Hz 采样整个步态周期，
     * 逐个判断局部极大值，确保步频不会被翻倍。
     */
    @Test
    fun `synthetic gait has exactly one peak per step`() {
        val s = MotionRealism.Session(start, MotionRealism.Level.OFF, 0)
        val rnd = Random(0)
        for (speed in listOf(1.0, 1.4, 2.0, 3.0, 4.0, 6.0)) {
            val samplesPerStep = 500
            val z = (0 until samplesPerStep).map { i ->
                val stepsFloat = i.toDouble() / samplesPerStep
                s.accelerometer(stepsFloat, speed, null, rnd)[2].toDouble() - 9.80665
            }
            // 环形求局部极大值：首尾相接，落在相位 0 附近的峰也能被数到一次
            val peaks = (0 until samplesPerStep).count { i ->
                val prev = z[(i - 1 + samplesPerStep) % samplesPerStep]
                val cur = z[i]
                val next = z[(i + 1) % samplesPerStep]
                cur > prev && cur >= next
            }
            assertEquals("speed $speed", 1, peaks)
        }
    }

    /**
     * 低采样率下也必须是"一步一个峰"。
     *
     * 目标 App 拿到的加速度计事件可能只有 10~20 Hz（SENSOR_DELAY_NORMAL / 游戏模式下的省电策略），
     * 尖峰太窄会被漏采，侧向与竖直峰错相位会让模长出现"有的步两个峰、有的步一个峰"，
     * 两种情况都会让波峰计数的结果偏离真实步频。
     */
    @Test
    fun `gait still counts one peak per step at low sample rates`() {
        val s = MotionRealism.Session(start, MotionRealism.Level.OFF, 0)
        val rnd = Random(0)
        for (speed in listOf(1.4, 3.0, 5.0)) {
            for (hz in listOf(10.0, 13.0, 16.0, 25.0)) {
                val seconds = 60.0
                val cadence = 170.0
                val z = ArrayList<Double>()
                val mag = ArrayList<Double>()
                var k = 0
                while (k / hz < seconds) {
                    val stepsFloat = k / hz * cadence / 60.0
                    val a = s.accelerometer(stepsFloat, speed, null, rnd)
                    z += a[2].toDouble()
                    mag += kotlin.math.sqrt((a[0] * a[0] + a[1] * a[1] + a[2] * a[2]).toDouble())
                    k++
                }
                assertEquals("speed $speed hz $hz z", cadence, countPeaks(z, hz), cadence * 0.06)
                assertEquals("speed $speed hz $hz magnitude", cadence, countPeaks(mag, hz), cadence * 0.06)
            }
        }
    }

    /**
     * 模拟器场景：没有硬件计步器时，App 会自己用加速度计算步。
     *
     * 运动世界的做法是逐轴取峰（任一轴出峰算一步）。三个轴的冲击必须同相位，否则一步之内
     * y 峰、z 峰、x 峰依次出现，会被数成两三步——实测 165 spm 记成 354、步幅只剩 51 cm。
     * 这里对单轴、三轴取并集、以及模长分别校验，保证每种算法都是一步一峰。
     */
    @Test
    fun `every axis has one peak per step so app-side pedometers cannot double count`() {
        val s = MotionRealism.Session(start, MotionRealism.Level.OFF, 0)
        val rnd = Random(0)
        // 10~16 Hz 下 165~200 spm 的一步只有 4~6 帧，相邻两帧可能同为峰（平台），
        // 这种量化误差任何波形都避免不了；App 侧计步器实际按 25~50 Hz 采样，
        // 所以这里从 25 Hz 起校验
        for (speed in listOf(1.4, 3.0, 5.0)) {
            for (hz in listOf(25.0, 50.0)) {
                for (cadence in listOf(120.0, 165.0, 180.0, 200.0)) {
                    val axis = Array(3) { ArrayList<Double>() }
                    val magnitude = ArrayList<Double>()
                    var k = 0
                    while (k / hz < 60.0) {
                        val a = s.accelerometer(k / hz * cadence / 60.0, speed, null, rnd)
                        for (i in 0..2) axis[i] += a[i].toDouble()
                        magnitude += kotlin.math.sqrt((a[0] * a[0] + a[1] * a[1] + a[2] * a[2]).toDouble())
                        k++
                    }
                    val perAxis = axis.map { peakIndices(it, hz) }
                    // 逐轴取峰后再合并：同一只脚的冲击落在相邻两帧时，三轴可能各报一个索引，
                    // 真实计步器会用不应期把它们算成一次，这里同样按 0.3 秒不应期合并
                    val merged = ArrayList<Int>()
                    // 不应期按步长的一半取：一步一个峰时相邻峰至少间隔半个步周期，
                    // 同一只脚落在相邻两帧时三轴报出的索引相差不超过 2 帧
                    val refractory = minOf(0.3, 30.0 / cadence)
                    for (i in perAxis.flatten().sorted()) {
                        if (merged.isEmpty() || (i - merged.last()) / hz > refractory) merged += i
                    }
                    assertEquals("speed $speed hz $hz cadence $cadence union", cadence, merged.size / 60.0 * 60.0, cadence * 0.06)
                    assertEquals("speed $speed hz $hz cadence $cadence magnitude", cadence, countPeaks(magnitude, hz), cadence * 0.06)
                }
            }
        }
    }

    private fun peakIndices(values: List<Double>, hz: Double): List<Int> {
        val mean = values.average()
        val centered = values.map { it - mean }
        val range = centered.max() - centered.min()
        val out = ArrayList<Int>()
        for (i in 1 until centered.size - 1) {
            val cur = centered[i]
            val isPeak = cur > centered[i - 1] && cur >= centered[i + 1] && cur - centered.min() > 0.2 * range
            if (isPeak && (out.isEmpty() || (i - out.last()) / hz > 0.05)) out += i
        }
        return out
    }

    /** 去均值后数局部极大值（阈值 20%、不应期 0.2 秒），与常见的计步算法一致 */
    private fun countPeaks(values: List<Double>, hz: Double): Double {
        val mean = values.average()
        val centered = values.map { it - mean }
        val range = centered.max() - centered.min()
        var count = 0
        var lastIndex = -1
        for (i in 1 until centered.size - 1) {
            val cur = centered[i]
            val isPeak = cur > centered[i - 1] && cur >= centered[i + 1] && cur - centered.min() > 0.2 * range
            if (isPeak && (lastIndex < 0 || (i - lastIndex) / hz > 0.2)) {
                count++
                lastIndex = i
            }
        }
        return count / (values.size / hz) * 60.0
    }

    /** 加速度振幅不能随速度无限膨胀：10 m/s 时旧的线性公式给到 21 m/s²，属于不可能出现的读数 */
    @Test
    fun `accelerometer amplitude stays physiological at high speed`() {
        val s = MotionRealism.Session(start, MotionRealism.Level.OFF, 0)
        val rnd = Random(0)
        for (speed in listOf(3.0, 6.0, 12.0)) {
            val z = (0 until 200).maxOf { i ->
                s.accelerometer(i * 0.05, speed, null, rnd)[2]
            }
            assertTrue("speed $speed peak $z", z < 30f)
        }
    }
}
