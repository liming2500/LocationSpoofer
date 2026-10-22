package com.vincenthzr.locationspoofer.utils

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadingOrientationTest {

    private fun assertAngle(expected: Double, actual: Double, tolerance: Double = 0.5) {
        val diff = abs(((actual - expected) % 360.0 + 540.0) % 360.0 - 180.0)
        assertEquals("expected ${expected}° got ${actual}°", 0.0, diff, tolerance)
    }

    @Test
    fun `north heading produces a unit quaternion whose azimuth is north`() {
        val q = HeadingOrientation.rotationVector(0.0)
        assertEquals(1.0, q.map { it.toDouble() }.let { it[0] * it[0] + it[1] * it[1] + it[2] * it[2] + it[3] * it[3] }, 1e-5)
        assertAngle(0.0, HeadingOrientation.azimuthOf(q)!!)
    }

    @Test
    fun `azimuth survives a round trip through the quaternion`() {
        for (deg in listOf(0.0, 45.0, 90.0, 135.0, 180.0, 225.0, 270.0, 315.0, 359.0)) {
            val q = HeadingOrientation.rotationVector(deg)
            assertAngle(deg, HeadingOrientation.azimuthOf(q)!!)
        }
    }

    @Test
    fun `replacing the azimuth on a tilted device keeps the tilt exactly`() {
        // 模拟真实读数：低头 30°、侧倾 15° 的设备
        val tilted = HeadingOrientation.rotationVector(17.0, pitchDeg = -30.0, rollDeg = 15.0)
        val before = HeadingOrientation.pitchRollOf(tilted)
        val replaced = HeadingOrientation.withAzimuth(tilted, 90.0)
        assertAngle(90.0, HeadingOrientation.azimuthOf(replaced)!!)
        val after = HeadingOrientation.pitchRollOf(replaced)
        assertEquals(before.pitch, after.pitch, 0.5)
        assertEquals(before.roll, after.roll, 0.5)
    }

    @Test
    fun `replacing the azimuth is idempotent`() {
        val q = HeadingOrientation.rotationVector(123.0, pitchDeg = -10.0, rollDeg = 4.0)
        val once = HeadingOrientation.withAzimuth(q, 200.0)
        val twice = HeadingOrientation.withAzimuth(once, 200.0)
        for (i in 0 until 4) assertEquals(once[i], twice[i], 1e-4f)
    }

    @Test
    fun `replacing the azimuth keeps tilt`() {
        val original = HeadingOrientation.rotationVector(10.0, pitchDeg = -25.0, rollDeg = 8.0)
        val tilted = HeadingOrientation.pitchRollOf(original)
        val replaced = HeadingOrientation.withAzimuth(original, 200.0)
        assertAngle(200.0, HeadingOrientation.azimuthOf(replaced)!!)
        // 俯仰与横滚必须与替换前完全一致（低头、侧倾姿态不变）
        val after = HeadingOrientation.pitchRollOf(replaced)
        assertEquals(tilted.pitch, after.pitch, 0.5)
        assertEquals(tilted.roll, after.roll, 0.5)
    }

    @Test
    fun `orientation sensor normalizes the azimuth`() {
        val v = HeadingOrientation.orientation(-30.0)
        assertEquals(330f, v[0], 1e-3f)
        assertNotNull(v)
    }

    @Test
    fun `degenerate quaternion has no azimuth`() {
        assertEquals(null, HeadingOrientation.azimuthOf(floatArrayOf(0f, 0f, 0f, 0f)))
        assertTrue(HeadingOrientation.rotationVector(0.0).isNotEmpty())
    }
}
