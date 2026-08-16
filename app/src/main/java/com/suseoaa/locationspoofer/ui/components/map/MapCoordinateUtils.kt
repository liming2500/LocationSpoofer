package com.suseoaa.locationspoofer.ui.components.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.suseoaa.locationspoofer.ui.components.MarkerType

// 坐标系转换工具
// config 中统一存储 GCJ-02；各地图 SDK 的原生坐标在此处统一转换

fun wgs84ToGcj02(wgsLat: Double, wgsLng: Double): Pair<Double, Double> {
    val a = 6378245.0
    val ee = 0.00669342162296594323
    if (wgsLng < 72.004 || wgsLng > 137.8347 || wgsLat < 0.8293 || wgsLat > 55.8271)
        return Pair(wgsLat, wgsLng)
    var dLat = transformLat(wgsLng - 105.0, wgsLat - 35.0)
    var dLon = transformLon(wgsLng - 105.0, wgsLat - 35.0)
    val radLat = wgsLat / 180.0 * Math.PI
    var magic = Math.sin(radLat); magic = 1 - ee * magic * magic
    val sqrtM = Math.sqrt(magic)
    dLat = dLat * 180.0 / (a * (1 - ee) / (magic * sqrtM) * Math.PI)
    dLon = dLon * 180.0 / (a / sqrtM * Math.cos(radLat) * Math.PI)
    return Pair(wgsLat + dLat, wgsLng + dLon)
}

private val X_PI = Math.PI * 3000.0 / 180.0

fun bd09ToGcj02(bdLat: Double, bdLng: Double): Pair<Double, Double> {
    val x = bdLng - 0.0065
    val y = bdLat - 0.006
    val z = Math.sqrt(x * x + y * y) - 0.00002 * Math.sin(y * X_PI)
    val theta = Math.atan2(y, x) - 0.000003 * Math.cos(x * X_PI)
    return Pair(z * Math.sin(theta), z * Math.cos(theta))
}

fun gcj02ToBd09(gcjLat: Double, gcjLng: Double): Pair<Double, Double> {
    val z = Math.sqrt(gcjLng * gcjLng + gcjLat * gcjLat) + 0.00002 * Math.sin(gcjLat * X_PI)
    val theta = Math.atan2(gcjLat, gcjLng) + 0.000003 * Math.cos(gcjLng * X_PI)
    return Pair(z * Math.sin(theta) + 0.006, z * Math.cos(theta) + 0.0065)
}

fun gcj02ToWgs84(gcjLat: Double, gcjLng: Double): Pair<Double, Double> {
    val a = 6378245.0
    val ee = 0.00669342162296594323
    if (gcjLng < 72.004 || gcjLng > 137.8347 || gcjLat < 0.8293 || gcjLat > 55.8271)
        return Pair(gcjLat, gcjLng)
    var dLat = transformLat(gcjLng - 105.0, gcjLat - 35.0)
    var dLon = transformLon(gcjLng - 105.0, gcjLat - 35.0)
    val radLat = gcjLat / 180.0 * Math.PI
    var magic = Math.sin(radLat); magic = 1 - ee * magic * magic
    val sqrtM = Math.sqrt(magic)
    dLat = dLat * 180.0 / (a * (1 - ee) / (magic * sqrtM) * Math.PI)
    dLon = dLon * 180.0 / (a / sqrtM * Math.cos(radLat) * Math.PI)
    return Pair(gcjLat - dLat, gcjLng - dLon)
}

private fun transformLat(x: Double, y: Double): Double {
    var r = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x))
    r += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0
    r += (20.0 * Math.sin(y * Math.PI) + 40.0 * Math.sin(y / 3.0 * Math.PI)) * 2.0 / 3.0
    r += (160.0 * Math.sin(y / 12.0 * Math.PI) + 320 * Math.sin(y * Math.PI / 30.0)) * 2.0 / 3.0
    return r
}

private fun transformLon(x: Double, y: Double): Double {
    var r = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x))
    r += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0
    r += (20.0 * Math.sin(x * Math.PI) + 40.0 * Math.sin(x / 3.0 * Math.PI)) * 2.0 / 3.0
    r += (150.0 * Math.sin(x / 12.0 * Math.PI) + 300.0 * Math.sin(x / 30.0 * Math.PI)) * 2.0 / 3.0
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
            MarkerType.DEFAULT -> android.graphics.Color.parseColor("#0084FF")// 高德途经蓝
        }

        // 1. 底部微阴影
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#33000000")
            style = Paint.Style.FILL
        }
        val shadowRect = RectF(
            width * 0.22f,
            height - 5 * density,
            width * 0.78f,
            height - 1 * density
        )
        canvas.drawOval(shadowRect, shadowPaint)

        // 2. 高德经典水滴图钉主体路径
        val headRadius = width * 0.44f
        val headCenterX = width / 2f
        val headCenterY = headRadius + 2 * density
        val bottomTipY = height - 4.5f * density

        val path = Path().apply {
            val ovalRect = RectF(
                headCenterX - headRadius,
                headCenterY - headRadius,
                headCenterX + headRadius,
                headCenterY + headRadius
            )
            arcTo(ovalRect, 145f, 250f, false)
            lineTo(headCenterX, bottomTipY)
            close()
        }

        // 填充图钉主体
        val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mainColor
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, pinPaint)

        // 绘制图钉纯白外描边
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * density
        }
        canvas.drawPath(path, strokePaint)

        // 3. 中心白色圆形徽章
        val innerRadius = headRadius * 0.60f
        val innerCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(headCenterX, headCenterY, innerRadius, innerCirclePaint)

        // 4. 中心内容（文字或小圆点）
        if (type == MarkerType.ORANGE || text.isBlank()) {
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = mainColor
                style = Paint.Style.FILL
            }
            canvas.drawCircle(headCenterX, headCenterY, innerRadius * 0.52f, dotPaint)
        } else {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = mainColor
                textSize = if (text.length > 1) innerRadius * 1.05f else innerRadius * 1.28f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val fontMetrics = textPaint.fontMetrics
            val baseline = headCenterY - (fontMetrics.ascent + fontMetrics.descent) / 2f
            canvas.drawText(text, headCenterX, baseline, textPaint)
        }

        bitmapCache[cacheKey] = bitmap
        return bitmap
    }
}
