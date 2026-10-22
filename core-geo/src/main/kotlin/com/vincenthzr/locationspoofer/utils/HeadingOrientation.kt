package com.vincenthzr.locationspoofer.utils

import kotlin.math.cos
import kotlin.math.sin

/**
 * 把运动航向换算成设备坐标系下的方向传感器读数。
 *
 * Android 的 [android.hardware.SensorEvent] 使用**设备坐标系**：X 轴指向屏幕右侧、Y 轴指向屏幕上方、
 * Z 轴垂直屏幕向外。旋转向量（ROTATION_VECTOR / GAME_ROTATION_VECTOR）描述的是设备姿态，
 * 方向传感器的方位角（azimuth）则是设备 Y 轴投影到水平面后相对正北的夹角。
 *
 * 方位角与俯仰/横滚统一按 Android `SensorManager.getOrientation` 的定义：
 * - azimuth = atan2(-R[1][0], R[0][0])，顺时针为正，0~360；
 * - pitch   = asin(-R[2][1])，-90~90；
 * - roll    = atan2(R[2][0], R[2][2])，-180~180。
 *
 * 关键点：**倾斜状态下的方位角不能靠"拆欧拉角再重组"得到**（倾斜会让欧拉角分解产生几度偏差）。
 * 替换朝向时在**世界坐标系**里绕天顶轴（世界 Z）整体旋转一个偏航角增量，
 * 这样设备的俯仰/横滚姿态（低头、侧倾）完全保持不变，只有水平朝向被改写。
 */
object HeadingOrientation {

    private const val DEG = Math.PI / 180.0
    private const val RAD = 180.0 / Math.PI

    /** 90° 的余弦保护误差，避免 cos(pitch)=0 时除零 */
    private const val EPS = 1e-9

    /**
     * 由方位角 / 俯仰角 / 横滚角生成旋转向量（设备端正立、屏幕朝上时为方位角指向正北）。
     *
     * @param azimuthDeg 方位角（度，正北 0，顺时针）
     * @param pitchDeg   俯仰角（度，设备顶部 leans toward/away）
     * @param rollDeg    横滚角（度）
     * @return 长度为 4 的 [x, y, z, w] 单位四元数，可直接写入 ROTATION_VECTOR 的 values
     */
    fun rotationVector(azimuthDeg: Double, pitchDeg: Double = 0.0, rollDeg: Double = 0.0): FloatArray {
        // Android 的方位角是顺时针为正，而旋转矩阵采用右手系（逆时针为正），因此这里取反
        val h = -azimuthDeg * DEG
        val p = pitchDeg * DEG
        val r = rollDeg * DEG

        val ch = cos(h); val sh = sin(h)
        val cp = cos(p); val sp = sin(p)
        val cr = cos(r); val sr = sin(r)

        // 按 Android getOrientation 的逆运算直接构造旋转矩阵（设备 → 世界，行主序）
        //   R[2][1] = -sin(p), R[2][0] = sin(r)cos(p), R[2][2] = cos(r)cos(p)
        //   R[0][0] = cos(h),  R[1][0] = -sin(h)
        // 其余元素由正交性（第三列 = 前两列叉乘）补齐。
        val m = doubleArrayOf(
            ch * cr, ch * sr * sp - sh * cp, ch * sr * cp + sh * sp,
            sh * cr, sh * sr * sp + ch * cp, sh * sr * cp - ch * sp,
            -sr, cr * sp, cr * cp
        )
        return matrixToQuaternion(m)
    }

    /**
     * 读取旋转向量的方位角（度，0~360）。
     * @param values [x, y, z, w]，退化四元数返回 null
     */
    fun azimuthOf(values: FloatArray): Double? {
        if (values.size < 4) return null
        val x = values[0].toDouble(); val y = values[1].toDouble()
        val z = values[2].toDouble(); val w = values[3].toDouble()
        if (x * x + y * y + z * z + w * w < 1e-9) return null
        val r = rotationMatrix(x, y, z, w)
        return normalizeDegrees(Math.atan2(-r[3], r[0]) * RAD)
    }

    /**
     * 把旋转向量的方位角替换为 [azimuthDeg]，**保留设备原本的俯仰与横滚**。
     *
     * 做法：计算当前方位与目标方位的差值，在**世界坐标系**里绕天顶轴左乘一个该增量对应的四元数。
     * 由于旋转轴是世界的竖直方向，设备的倾斜姿态不受影响，水平朝向恰好转到目标值。
     *
     * @return 新的 [x, y, z, w]
     */
    fun withAzimuth(values: FloatArray, azimuthDeg: Double): FloatArray {
        val current = azimuthOf(values)
            ?: return rotationVector(azimuthDeg)
        if (values.size < 4) return rotationVector(azimuthDeg)

        var delta = normalizeDegrees(azimuthDeg - current)
        if (delta > 180.0) delta -= 360.0
        if (kotlin.math.abs(delta) < 1e-6) return values.copyOf(4)

        val turn = quaternionAxisAngle(0.0, 0.0, 1.0, -delta * DEG)
        val original = doubleArrayOf(
            values[0].toDouble(), values[1].toDouble(),
            values[2].toDouble(), values[3].toDouble()
        )
        val q = multiply(turn, original)
        return floatArrayOf(q[0].toFloat(), q[1].toFloat(), q[2].toFloat(), q[3].toFloat())
    }

    /** 从旋转向量取出俯仰角与横滚角（度），按 Android getOrientation 的定义 */
    fun pitchRollOf(values: FloatArray): PitchRoll {
        if (values.size < 4) return PitchRoll(0.0, 0.0)
        val r = rotationMatrix(
            values[0].toDouble(), values[1].toDouble(),
            values[2].toDouble(), values[3].toDouble()
        )
        val pitch = Math.asin((-r[7]).coerceIn(-1.0, 1.0)) * RAD
        val roll = Math.atan2(r[6], r[8]) * RAD
        return PitchRoll(pitch, roll)
    }

    data class PitchRoll(val pitch: Double, val roll: Double)

    /** 方向传感器（TYPE_ORIENTATION）读数：[azimuth, pitch, roll]，单位度 */
    fun orientation(azimuthDeg: Double, pitchDeg: Double = 0.0, rollDeg: Double = 0.0): FloatArray =
        floatArrayOf(
            normalizeDegrees(azimuthDeg).toFloat(),
            pitchDeg.toFloat(),
            rollDeg.toFloat()
        )

    /** 归一化到 [0, 360) */
    fun normalizeDegrees(deg: Double): Double {
        val v = deg % 360.0
        return if (v < 0) v + 360.0 else v
    }

    /** 四元数 [x, y, z, w] → 3×3 旋转矩阵（行主序，设备坐标 → 世界坐标） */
    private fun rotationMatrix(x: Double, y: Double, z: Double, w: Double): DoubleArray = doubleArrayOf(
        1 - 2 * (y * y + z * z), 2 * (x * y - w * z), 2 * (x * z + w * y),
        2 * (x * y + w * z), 1 - 2 * (x * x + z * z), 2 * (y * z - w * x),
        2 * (x * z - w * y), 2 * (y * z + w * x), 1 - 2 * (x * x + y * y)
    )

    /** 3×3 旋转矩阵（行主序）→ 单位四元数 [x, y, z, w] */
    private fun matrixToQuaternion(m: DoubleArray): FloatArray {
        val trace = m[0] + m[4] + m[8]
        val x: Double; val y: Double; val z: Double; val w: Double
        if (trace > 0.0) {
            val s = kotlin.math.sqrt(trace + 1.0) * 2.0
            w = 0.25 * s
            x = (m[7] - m[5]) / s
            y = (m[2] - m[6]) / s
            z = (m[3] - m[1]) / s
        } else if (m[0] > m[4] && m[0] > m[8]) {
            val s = kotlin.math.sqrt(1.0 + m[0] - m[4] - m[8]) * 2.0
            w = (m[7] - m[5]) / s
            x = 0.25 * s
            y = (m[1] + m[3]) / s
            z = (m[2] + m[6]) / s
        } else if (m[4] > m[8]) {
            val s = kotlin.math.sqrt(1.0 + m[4] - m[0] - m[8]) * 2.0
            w = (m[2] - m[6]) / s
            x = (m[1] + m[3]) / s
            y = 0.25 * s
            z = (m[5] + m[7]) / s
        } else {
            val s = kotlin.math.sqrt(1.0 + m[8] - m[0] - m[4]) * 2.0
            w = (m[3] - m[1]) / s
            x = (m[2] + m[6]) / s
            y = (m[5] + m[7]) / s
            z = 0.25 * s
        }
        return floatArrayOf(x.toFloat(), y.toFloat(), z.toFloat(), w.toFloat())
    }

    private fun quaternionAxisAngle(ax: Double, ay: Double, az: Double, angle: Double): DoubleArray {
        val half = angle / 2.0
        val s = sin(half)
        return doubleArrayOf(ax * s, ay * s, az * s, cos(half))
    }

    /** 四元数乘法 [x, y, z, w] */
    private fun multiply(a: DoubleArray, b: DoubleArray): DoubleArray {
        val x1 = a[0]; val y1 = a[1]; val z1 = a[2]; val w1 = a[3]
        val x2 = b[0]; val y2 = b[1]; val z2 = b[2]; val w2 = b[3]
        return doubleArrayOf(
            w1 * x2 + x1 * w2 + y1 * z2 - z1 * y2,
            w1 * y2 - x1 * z2 + y1 * w2 + z1 * x2,
            w1 * z2 + x1 * y2 - y1 * x2 + z1 * w2,
            w1 * w2 - x1 * x2 - y1 * y2 - z1 * z2
        )
    }
}
