package com.vincenthzr.locationspoofer.data.completion

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

/**
 * 路线跑完时的提醒：通知栏、提示音、震动三种方式，各自可开关。
 *
 * 只在路线跑到设定圈数时触发一次。触发时若应用在前台，同时弹一个 Toast 作为即时反馈。
 */
object CompletionNotifier {

    private const val CHANNEL_ID = "route_completed"
    private const val CHANNEL_NAME = "路线完成提醒"

    data class Options(
        val notify: Boolean = true,
        val sound: Boolean = true,
        val vibrate: Boolean = true
    )

    fun notifyCompleted(
        context: Context,
        title: String,
        message: String,
        options: Options
    ) {
        if (options.notify) postNotification(context, title, message, options.sound)
        if (options.sound) playSound(context)
        if (options.vibrate) vibrate(context)
        if (!options.notify) toastOnMain(context, "$title — $message")
    }

    private fun postNotification(context: Context, title: String, message: String, withSound: Boolean) {
        try {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            ensureChannel(context, manager, withSound)

            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val pending = launch?.let {
                PendingIntent.getActivity(
                    context, 0, it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setAutoCancel(true)
                .apply { if (pending != null) setContentIntent(pending) }
                .build()

            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: Throwable) {
        }
    }

    private fun ensureChannel(context: Context, manager: NotificationManager, withSound: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
            description = "路线跑到设定圈数时提醒"
            if (withSound) {
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            } else {
                setSound(null, null)
            }
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun playSound(context: Context) {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(context, uri)?.play()
        } catch (_: Throwable) {
        }
    }

    private fun vibrate(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            // 三次短震，比单次长震更容易被注意到
            val pattern = longArrayOf(0, 350, 180, 350, 180, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        } catch (_: Throwable) {
        }
    }

    private fun toastOnMain(context: Context, text: String) {
        Handler(Looper.getMainLooper()).post {
            try {
                android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_LONG).show()
            } catch (_: Throwable) {
            }
        }
    }

    private const val NOTIFICATION_ID = 0x5F00
}
