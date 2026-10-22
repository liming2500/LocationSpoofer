package com.vincenthzr.locationspoofer.utils

import com.vincenthzr.locationspoofer.utils.CoordinateUtils.LatLng
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object GeoMath {
    const val EARTH_RADIUS_M = 6378137.0

    fun distance(a: LatLng, b: LatLng): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(1 - h))
    }

    /** 从 from 指向 to 的方位角（度，正北为 0，顺时针） */
    fun bearing(from: LatLng, to: LatLng): Float {
        val lat1 = Math.toRadians(from.lat)
        val lat2 = Math.toRadians(to.lat)
        val dLng = Math.toRadians(to.lng - from.lng)
        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
    }

    /** 从 start 沿 bearing 方向前进 meters 米后的位置 */
    fun destination(start: LatLng, bearingDeg: Double, meters: Double): LatLng {
        val angular = meters / EARTH_RADIUS_M
        val bearingRad = Math.toRadians(bearingDeg)
        val lat1 = Math.toRadians(start.lat)
        val lng1 = Math.toRadians(start.lng)
        val lat2 = asin(sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearingRad))
        val lng2 = lng1 + atan2(sin(bearingRad) * sin(angular) * cos(lat1), cos(angular) - sin(lat1) * sin(lat2))
        return LatLng(Math.toDegrees(lat2), Math.toDegrees(lng2))
    }
}

/**
 * 一条路线上"已行进距离 → 位置"的映射，App 和 Xposed 两端共用，保证两边对同一距离算出同一位置。
 *
 * 规则：首尾相距 5 米以内视为闭环，走到终点后从起点继续；否则走到终点后原路折返，来回往复。
 * stopAtDestination 为真时走到终点就停下。
 */
class RoutePath(val points: List<LatLng>) {

    data class Position(val lat: Double, val lng: Double, val bearing: Float, val arrived: Boolean)

    private val cumulative: DoubleArray = DoubleArray(points.size).also { cum ->
        for (i in 1 until points.size) cum[i] = cum[i - 1] + GeoMath.distance(points[i - 1], points[i])
    }

    val totalDistance: Double = if (points.isEmpty()) 0.0 else cumulative.last()
    val isClosedLoop: Boolean = points.size >= 2 && GeoMath.distance(points.first(), points.last()) <= 5.0
    val isValid: Boolean get() = points.size >= 2 && totalDistance > 0.0

    fun positionAt(distanceTraveled: Double, stopAtDestination: Boolean): Position {
        val traveled = distanceTraveled.coerceAtLeast(0.0)
        return position(traveled, stopAtDestination)
    }

    /**
     * 与 [positionAt] 相同，但航向由前后两个位置的实测位移算出，始终指向真正在走的方向。
     * 折返点、终点停车（到达后保持最后行进方向）、起点等待（按即将出发的方向）都能正确给出朝向。
     */
    fun positionAtFollowingMotion(distanceTraveled: Double, stopAtDestination: Boolean, sampleMeters: Double = 2.0): Position {
        val traveled = distanceTraveled.coerceAtLeast(0.0)
        val here = position(traveled, stopAtDestination)
        // 终点停车：位置不变，航向保持最后一段的行进方向
        if (here.arrived) return here

        // 先看"下一步往哪走"：起点等待、折返点、闭环回到起点都能得到正确朝向。
        // 采样点一律按不停车计算，否则临近终点（stop_at_destination）时采样会落在到达状态上、拿不到方向；
        // 只有正停着（前后都没位移）时才回退到"刚从哪来"
        val after = position(traveled + sampleMeters, stopAtDestination = false)
        if (GeoMath.distance(here.asLatLng(), after.asLatLng()) > STOP_SAMPLE_M) {
            return here.copy(bearing = normalizeBearing(GeoMath.bearing(here.asLatLng(), after.asLatLng())))
        }
        val before = position((traveled - sampleMeters).coerceAtLeast(0.0), stopAtDestination = false)
        if (GeoMath.distance(before.asLatLng(), here.asLatLng()) > STOP_SAMPLE_M) {
            return here.copy(bearing = normalizeBearing(GeoMath.bearing(before.asLatLng(), here.asLatLng())))
        }
        return here.copy(bearing = 0f)
    }

    /** 浮点误差会让正北产出 359.99999，统一归一到 [0, 360) */
    private fun normalizeBearing(bearing: Float): Float {
        val b = bearing % 360f
        return if (b < 0f) b + 360f else if (b >= 359.999f) 0f else b
    }

    private fun position(traveled: Double, stopAtDestination: Boolean): Position {
        require(isValid) { "route needs at least two distinct points" }

        if (stopAtDestination && traveled >= totalDistance) {
            val last = points.last()
            return Position(last.lat, last.lng, GeoMath.bearing(points[points.size - 2], last), arrived = true)
        }

        // 非闭环路线来回往复：折返瞬间即使位置恰好落在端点，航向也已经指向返程方向
        val cycle = totalDistance * 2
        val inCycle = if (isClosedLoop) traveled % totalDistance else traveled % cycle
        if (!isClosedLoop && inCycle > totalDistance) {
            // 返程段按"从终点往回走"的镜像距离插值，位置和航向都取返程方向
            return mirroredPosition(cycle - inCycle)
        }

        return forwardPosition(inCycle, arrived = false)
    }

    /** 返程段：along 是从终点往回走的距离，位置按正向插值，航向取反指向起点 */
    private fun mirroredPosition(along: Double): Position {
        val forward = forwardPosition(along, arrived = false)
        return forward.copy(bearing = (forward.bearing + 180f) % 360f)
    }

    private fun forwardPosition(along: Double, arrived: Boolean): Position {
        var seg = 0
        while (seg < points.size - 2 && cumulative[seg + 1] < along) seg++
        val from = points[seg]
        val to = points[seg + 1]
        val segLen = (cumulative[seg + 1] - cumulative[seg]).coerceAtLeast(0.0001)
        val ratio = ((along - cumulative[seg]) / segLen).coerceIn(0.0, 1.0)
        return Position(
            from.lat + (to.lat - from.lat) * ratio,
            from.lng + (to.lng - from.lng) * ratio,
            GeoMath.bearing(from, to),
            arrived
        )
    }

    private fun Position.asLatLng() = LatLng(lat, lng)

    private companion object {
        /** 两个采样位置差小于该值（米）视为没动 */
        const val STOP_SAMPLE_M = 0.05
    }
}
