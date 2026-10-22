package com.vincenthzr.locationspoofer.utils

import com.vincenthzr.locationspoofer.utils.CoordinateUtils.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePathTest {

    private val a = LatLng(30.0, 120.0)
    private val b = GeoMath.destination(a, 90.0, 1000.0) // 正东 1000 米

    @Test
    fun `destination and distance are consistent`() {
        assertEquals(1000.0, GeoMath.distance(a, b), 0.01)
        assertEquals(90.0, GeoMath.bearing(a, b).toDouble(), 0.01)
    }

    @Test
    fun `open route goes back and forth`() {
        val path = RoutePath(listOf(a, b))
        assertFalse(path.isClosedLoop)
        val out = path.positionAt(400.0, stopAtDestination = false)
        assertEquals(400.0, GeoMath.distance(a, LatLng(out.lat, out.lng)), 0.5)
        assertEquals(90.0, out.bearing.toDouble(), 0.5)
        val back = path.positionAt(1600.0, stopAtDestination = false) // 折返途中，距起点 400 米
        assertEquals(400.0, GeoMath.distance(a, LatLng(back.lat, back.lng)), 0.5)
        assertEquals(270.0, back.bearing.toDouble(), 0.5)
    }

    @Test
    fun `closed loop keeps going forward`() {
        val c = GeoMath.destination(b, 0.0, 1000.0)
        val path = RoutePath(listOf(a, b, c, a))
        assertTrue(path.isClosedLoop)
        val lap = path.positionAt(path.totalDistance + 300.0, stopAtDestination = false)
        assertEquals(300.0, GeoMath.distance(a, LatLng(lap.lat, lap.lng)), 0.5)
    }

    @Test
    fun `stops at the destination when requested`() {
        val path = RoutePath(listOf(a, b))
        val end = path.positionAt(5000.0, stopAtDestination = true)
        assertTrue(end.arrived)
        assertEquals(b.lat, end.lat, 1e-9)
        assertEquals(b.lng, end.lng, 1e-9)
    }

    @Test
    fun `bearing follows the direction of actual motion`() {
        val path = RoutePath(listOf(a, b))
        // 出发前等待：航向指向即将要走的方向，而不是 0（正北）
        assertBearing(90.0, path.positionAtFollowingMotion(0.0, stopAtDestination = false).bearing)
        // 去程
        assertBearing(90.0, path.positionAtFollowingMotion(400.0, stopAtDestination = false).bearing)
        // 终点瞬间折返：即使位置就在折返点，航向也已经转向返程
        assertBearing(270.0, path.positionAtFollowingMotion(1000.0, stopAtDestination = false).bearing)
        assertBearing(270.0, path.positionAtFollowingMotion(1200.0, stopAtDestination = false).bearing)
    }

    @Test
    fun `bearing keeps the last travel direction after stopping at the destination`() {
        val path = RoutePath(listOf(a, b))
        val end = path.positionAtFollowingMotion(5000.0, stopAtDestination = true)
        assertTrue(end.arrived)
        assertBearing(90.0, end.bearing)
    }

    @Test
    fun `bearing is still correct when about to stop at the destination`() {
        val path = RoutePath(listOf(a, b))
        // 距终点不到 2 米：下一步采样会越过终点，但仍应按去程方向
        val nearEnd = path.positionAtFollowingMotion(999.0, stopAtDestination = true)
        assertBearing(90.0, nearEnd.bearing)
    }

    @Test
    fun `bearing follows the turn on a polyline`() {
        val north = GeoMath.destination(a, 0.0, 1000.0)
        val east = GeoMath.destination(north, 90.0, 1000.0)
        val path = RoutePath(listOf(a, north, east))
        assertBearing(0.0, path.positionAtFollowingMotion(500.0, stopAtDestination = false).bearing)
        assertBearing(90.0, path.positionAtFollowingMotion(1500.0, stopAtDestination = false).bearing)
    }

    /** 角度等价比较：0° 与 359.999° 视为同一方向 */
    private fun assertBearing(expected: Double, actual: Float) {
        val diff = Math.abs(((actual - expected) % 360.0 + 540.0) % 360.0 - 180.0)
        assertEquals("expected $expected°, got $actual°", 0.0, diff, 0.5)
    }

    @Test
    fun `laps limit stops the runner at the end of the last lap`() {
        val path = RoutePath(listOf(a, b))
        assertEquals(2000.0, path.lapDistance, 1.0) // 开放路线：一次往返
        // 未跑满时与普通位置一致
        val midLap = path.positionAtWithLaps(500.0, loops = 2, stopAtDestination = false)
        assertFalse(midLap.arrived)
        // 跑满 2 圈后标记到达
        val done = path.positionAtWithLaps(4200.0, loops = 2, stopAtDestination = false)
        assertTrue(done.arrived)
        // loops = 0 表示不限
        assertFalse(path.positionAtWithLaps(99999.0, loops = 0, stopAtDestination = false).arrived)
    }

    @Test
    fun `closed loop lap distance equals perimeter`() {
        val c = GeoMath.destination(b, 0.0, 1000.0)
        val path = RoutePath(listOf(a, b, c, a))
        assertTrue(path.isClosedLoop)
        assertEquals(path.totalDistance, path.lapDistance, 1.0)
    }
}
