@file:Suppress(
    "UNUSED_PARAMETER",
    "UNUSED_VARIABLE",
    "UNNECESSARY_NOT_NULL_ASSERTION",
    "DEPRECATION",
    "NAME_SHADOWING",
    "FunctionName",
    "PrivatePropertyName",
    "SpellCheckingInspection",
    "RedundantUnitReturnType",
    "RemoveRedundantQualifierName",
    "OPT_IN_USAGE",
    "unused",
    "UnusedImport"
)

package com.suseoaa.locationspoofer.xposed.utils

import com.suseoaa.locationspoofer.xposed.LocationHooker
import com.suseoaa.locationspoofer.xposed.utils.*
import com.suseoaa.locationspoofer.xposed.hooks.*
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.lang.reflect.Member
import kotlin.math.*
import io.github.libxposed.api.*

/**
 * 坐标系转换工具类 (Coordinate Converter)
 * 
 * 上下文:
 * - WGS-84: 国际标准 GPS 坐标。
 * - GCJ-02 (火星坐标系): 中国国家测绘局制定的强制加密坐标，中国大陆的所有地图(高德、腾讯)均使用此坐标。
 * - BD-09: 百度在 GCJ-02 的基础上再次加密的坐标系。
 * 
 * 作用:
 * 提供各个坐标系之间的精确数学转换。
 * 关键部分解释:
 * 1. WGS-84 和 GCJ-02 之间存在 300~500 米的非线性偏移。通过一套复杂的克拉索夫斯基椭球体参数进行转换。
 * 2. Ornstein-Uhlenbeck (OU) 随机过程: 真实人在走路时，GPS 坐标会有自然的抖动和漂移，绝对不可能静止不动。
 *    这里的 `getJitteredLocation` 函数就是用来生成极其逼真、服从物理学运动规律的随机微小偏移，对抗后台的大数据防作弊分析。
 */


internal fun LocationHooker.gcj02ToWgs84(gcjLat: Double, gcjLng: Double): Pair<Double, Double> {
    if (gcjLng < 72.004 || gcjLng > 137.8347 || gcjLat < 0.8293 || gcjLat > 55.8271)
        return Pair(gcjLat, gcjLng)
    val dLat = gcjTransformLat(gcjLng - 105.0, gcjLat - 35.0)
    val dLng = gcjTransformLng(gcjLng - 105.0, gcjLat - 35.0)
    val radLat = gcjLat / 180.0 * Math.PI
    var magic = sin(radLat)
    magic = 1 - GCJ_EE * magic * magic
    val sqrtMagic = sqrt(magic)
    val mLat = (dLat * 180.0) / ((GCJ_A * (1 - GCJ_EE)) / (magic * sqrtMagic) * Math.PI)
    val mLng = (dLng * 180.0) / (GCJ_A / sqrtMagic * cos(radLat) * Math.PI)
    return Pair(gcjLat - mLat, gcjLng - mLng)
}

internal fun LocationHooker.gcjTransformLat(x: Double, y: Double): Double {
    var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
    ret += (20.0 * sin(6.0 * x * Math.PI) + 20.0 * sin(2.0 * x * Math.PI)) * 2.0 / 3.0
    ret += (20.0 * sin(y * Math.PI) + 40.0 * sin(y / 3.0 * Math.PI)) * 2.0 / 3.0
    ret += (160.0 * sin(y / 12.0 * Math.PI) + 320.0 * sin(y * Math.PI / 30.0)) * 2.0 / 3.0
    return ret
}

internal fun LocationHooker.gcjTransformLng(x: Double, y: Double): Double {
    var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
    ret += (20.0 * sin(6.0 * x * Math.PI) + 20.0 * sin(2.0 * x * Math.PI)) * 2.0 / 3.0
    ret += (20.0 * sin(x * Math.PI) + 40.0 * sin(x / 3.0 * Math.PI)) * 2.0 / 3.0
    ret += (150.0 * sin(x / 12.0 * Math.PI) + 300.0 * sin(x / 30.0 * Math.PI)) * 2.0 / 3.0
    return ret
}

// ── GCJ-02 → BD-09 转换(百度坐标系) ──
//
// BD-09是百度在GCJ-02基础上施加的二次偏移坐标系。百度地图/百度定位SDK(BDLocation)
// 内部期望接收BD-09坐标,若直接传入GCJ-02会产生约100-500米的固定偏移。
//
// 算法原理:
// 1. 将GCJ-02坐标解释为以(0,0)为中心的直角坐标(x=lng, y=lat)
// 2. 施加百度公开的偏移常量(x偏移0.0065度, y偏移0.006度)
// 3. 将偏移后的直角坐标转为极坐标(r, theta),其中r=sqrt(x^2+y^2), theta=atan2(y,x)
// 4. 对极角theta叠加一个与r相关的微小旋转量: theta += BD_PI * sin(r * BD_PI) * 0.000003
//    BD_PI = pi * 3000/180 ≈ 52.3598..., 这是百度定义的旋转频率系数
// 5. 对极径r叠加微小伸缩: r += BD_PI * cos(r * BD_PI) * 0.00002
// 6. 将修正后的极坐标转回直角坐标,即为BD-09经纬度
//
// 为何不能省略此转换:
// BDLocation.getLatitude()被Hook后如果返回GCJ-02坐标,百度SDK内部不会再做转换,
// 直接将该值作为BD-09渲染到地图上,导致显示位置相对真实位置偏移数百米。

/** 百度坐标系旋转频率常量: pi * 3000 / 180 */
internal fun LocationHooker.gcj02ToBd09(gcjLat: Double, gcjLng: Double): Pair<Double, Double> {
    val x = gcjLng
    val y = gcjLat
    val z = sqrt(x * x + y * y) + 0.00002 * sin(y * BD_PI)
    val theta = Math.atan2(y, x) + 0.000003 * cos(x * BD_PI)
    val bdLng = z * cos(theta) + 0.0065
    val bdLat = z * sin(theta) + 0.006
    return Pair(bdLat, bdLng)
}

/**
 * 高斯随机游走状态(Xposed进程内独立维护)
 * 使用Ornstein-Uhlenbeck过程: X(t+dt) = X(t) + sigma*sqrt(dt)*N(0,1) - alpha*X(t)*dt
 * 产生白噪声频谱,FFT检测无法发现单频峰
 */
internal fun LocationHooker.bd09ToGcj02(bdLat: Double, bdLng: Double): Pair<Double, Double> {
    val x = bdLng - 0.0065
    val y = bdLat - 0.006
    val z = Math.sqrt(x * x + y * y) - 0.00002 * Math.sin(y * Math.PI)
    val theta = Math.atan2(y, x) - 0.000003 * Math.cos(x * Math.PI)
    val gcjLng = z * Math.cos(theta)
    val gcjLat = z * Math.sin(theta)
    return Pair(gcjLat, gcjLng)
}

internal fun LocationHooker.getJitteredLocation(
    baseLat: Double,
    baseLng: Double
): Pair<Double, Double> {
    val enableJitter = lastConfig?.optBoolean("enable_jitter", true) ?: true
    if (!enableJitter) return Pair(baseLat, baseLng)

    val now = System.currentTimeMillis()
    val dt = if (hookLastCallTime > 0) {
        ((now - hookLastCallTime) / 1000.0).coerceIn(0.01, 5.0)
    } else 1.0
    hookLastCallTime = now

    // sigma=0.000002度(约0.2米步长), alpha=0.05(均值回归)
    val sigma = 0.000002
    val alpha = 0.05

    // 使用 Ornstein-Uhlenbeck 过程生成自然偏移，并硬性限制在 4 米以内 (约 0.00004 度)
    hookDriftLat =
        (hookDriftLat + sigma * sqrt(dt) * rng.nextGaussian() - alpha * hookDriftLat * dt)
            .coerceIn(-0.00004, 0.00004)
    hookDriftLng =
        (hookDriftLng + sigma * sqrt(dt) * rng.nextGaussian() - alpha * hookDriftLng * dt)
            .coerceIn(-0.00004, 0.00004)

    return Pair(baseLat + hookDriftLat, baseLng + hookDriftLng)
}

internal fun LocationHooker.getJitteredAccuracy(): Float {
    // 精度值在基准20m附近做高斯漂移,模拟GDOP变化
    hookAccuracyDrift += 0.5 * rng.nextGaussian() - 0.03 * hookAccuracyDrift
    return (20.0 + hookAccuracyDrift).coerceIn(3.0, 45.0).toFloat()
}


