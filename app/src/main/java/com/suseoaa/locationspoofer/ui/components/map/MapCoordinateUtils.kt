package com.suseoaa.locationspoofer.ui.components.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.suseoaa.locationspoofer.ui.components.MarkerType
import kotlin.math.*

// 坐标系转换工具
// config 中统一存储 GCJ-02；各地图 SDK 的原生坐标在此处统一转换

private const val A = 6378245.0
private const val EE = 0.00669342162296594323
private const val X_PI = Math.PI * 3000.0 / 180.0

fun wgs84ToGcj02(wgsLat: Double, wgsLng: Double): Pair<Double, Double> {
    if (wgsLng < 72.004 || wgsLng > 137.8347 || wgsLat < 0.8293 || wgsLat > 55.8271)
        return Pair(wgsLat, wgsLng)
    val dLat = transformLat(wgsLng - 105.0, wgsLat - 35.0)
    val dLon = transformLon(wgsLng - 105.0, wgsLat - 35.0)
    val radLat = wgsLat / 180.0 * Math.PI
    var magic = sin(radLat)
    magic = 1 - EE * magic * magic
    val sqrtM = sqrt(magic)
    val mLat = dLat * 180.0 / (A * (1 - EE) / (magic * sqrtM) * Math.PI)
    val mLon = dLon * 180.0 / (A / sqrtM * cos(radLat) * Math.PI)
    return Pair(wgsLat + mLat, wgsLng + mLon)
}

/**
 * GCJ-02 → WGS-84（采用3轮反向逼近迭代算法，误差小于0.5毫米）
 */
fun gcj02ToWgs84(gcjLat: Double, gcjLng: Double): Pair<Double, Double> {
    if (gcjLng < 72.004 || gcjLng > 137.8347 || gcjLat < 0.8293 || gcjLat > 55.8271)
        return Pair(gcjLat, gcjLng)
    var wgsLat = gcjLat
    var wgsLng = gcjLng
    for (i in 0 until 3) {
        val (currGcjLat, currGcjLng) = wgs84ToGcj02(wgsLat, wgsLng)
        wgsLat -= (currGcjLat - gcjLat)
        wgsLng -= (currGcjLng - gcjLng)
    }
    return Pair(wgsLat, wgsLng)
}

fun bd09ToGcj02(bdLat: Double, bdLng: Double): Pair<Double, Double> {
    val x = bdLng - 0.0065
    val y = bdLat - 0.006
    val z = sqrt(x * x + y * y) - 0.00002 * sin(y * X_PI)
    val theta = atan2(y, x) - 0.000003 * cos(x * X_PI)
    val gcjLng = z * cos(theta)
    val gcjLat = z * sin(theta)
    return Pair(gcjLat, gcjLng)
}

fun gcj02ToBd09(gcjLat: Double, gcjLng: Double): Pair<Double, Double> {
    val x = gcjLng
    val y = gcjLat
    val z = sqrt(x * x + y * y) + 0.00002 * sin(y * X_PI)
    val theta = atan2(y, x) + 0.000003 * cos(x * X_PI)
    val bdLng = z * cos(theta) + 0.0065
    val bdLat = z * sin(theta) + 0.006
    return Pair(bdLat, bdLng)
}

private fun transformLat(x: Double, y: Double): Double {
    var r = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
    r += (20.0 * sin(6.0 * x * Math.PI) + 20.0 * sin(2.0 * x * Math.PI)) * 2.0 / 3.0
    r += (20.0 * sin(y * Math.PI) + 40.0 * sin(y / 3.0 * Math.PI)) * 2.0 / 3.0
    r += (160.0 * sin(y / 12.0 * Math.PI) + 320.0 * sin(y * Math.PI / 30.0)) * 2.0 / 3.0
    return r
}

private fun transformLon(x: Double, y: Double): Double {
    var r = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
    r += (20.0 * sin(6.0 * x * Math.PI) + 20.0 * sin(2.0 * x * Math.PI)) * 2.0 / 3.0
    r += (20.0 * sin(x * Math.PI) + 40.0 * sin(x / 3.0 * Math.PI)) * 2.0 / 3.0
    r += (150.0 * sin(x / 12.0 * Math.PI) + 300.0 * sin(x / 30.0 * Math.PI)) * 2.0 / 3.0
    return r
}

object GaodeMarkerHelper {
    private val bitmapCache = mutableMapOf<String, Bitmap>()

    fun getMarkerBitmap(
        context: Context,
        text: String,
        type: MarkerType
    ): Bitmap {
        val cacheKey = "${type.name}_$text"
        bitmapCache[cacheKey]?.let {
            if (!it.isRecycled) return it
        }

        val density = context.resources.displayMetrics.density
        val width = (32 * density).toInt().coerceAtLeast(1)
        val height = (44 * density).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val mainColor = when (type) {
            MarkerType.GREEN -> android.graphics.Color.parseColor("#00B578")  // 高德起点绿
            MarkerType.RED -> android.graphics.Color.parseColor("#FA5151")    // 高德终点红
            MarkerType.ORANGE -> android.graphics.Color.parseColor("#FF7A00") // 高德导航橙
            MarkerType.DEFAULT -> android.graphics.Color.parseColor("#388BFD")// 高德经典蓝
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mainColor
            style = Paint.Style.FILL
            isDither = true
        }

        // 绘制阴影
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#33000000")
            style = Paint.Style.FILL
        }
        canvas.drawOval(
            RectF(
                width * 0.2f,
                height * 0.88f,
                width * 0.8f,
                height * 0.98f
            ),
            shadowPaint
        )

        // 绘制外层图钉气滴路径
        val path = Path()
        val radius = width / 2f
        path.moveTo(radius, height * 0.88f)
        path.cubicTo(
            width * 0.95f, height * 0.55f,
            width.toFloat(), height * 0.35f,
            width.toFloat(), radius
        )
        path.arcTo(RectF(0f, 0f, width.toFloat(), width.toFloat()), 0f, -180f, false)
        path.cubicTo(
            0f, height * 0.35f,
            width * 0.05f, height * 0.55f,
            radius, height * 0.88f
        )
        path.close()
        canvas.drawPath(path, paint)

        // 绘制白色同心圆
        val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(radius, radius, radius * 0.65f, whitePaint)

        // 绘制居中文本/数字/徽标
        if (text.isNotBlank()) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = mainColor
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = if (text.length > 2) radius * 0.65f else radius * 0.85f
            }
            val fontMetrics = textPaint.fontMetrics
            val baseline = radius - (fontMetrics.ascent + fontMetrics.descent) / 2f
            canvas.drawText(text, radius, baseline, textPaint)
        }

        bitmapCache[cacheKey] = bitmap
        return bitmap
    }
}
