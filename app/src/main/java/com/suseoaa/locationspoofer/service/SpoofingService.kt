package com.suseoaa.locationspoofer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class SpoofingService : Service() {

    private lateinit var locationManager: LocationManager

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_LAT = "EXTRA_LAT"
        const val EXTRA_LNG = "EXTRA_LNG"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "SpoofingServiceChannel"

        var isRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val lat = intent.getDoubleExtra(EXTRA_LAT, 0.0)
                val lng = intent.getDoubleExtra(EXTRA_LNG, 0.0)
                startSpoofing(lat, lng)
            }

            ACTION_STOP -> stopSpoofing()
        }
        return START_STICKY
    }

    private fun startSpoofing(lat: Double, lng: Double) {
        if (isRunning) return

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(com.suseoaa.locationspoofer.R.string.spoofing_service_title))
            .setContentText(
                getString(
                    com.suseoaa.locationspoofer.R.string.spoofing_service_content,
                    lat.toString(),
                    lng.toString()
                )
            )
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // 忽略异常，在没有前台状态的情况下继续运行
        }
        isRunning = true
    }

    private fun stopSpoofing() {
        isRunning = false
        cleanupLegacyTestProviders()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanupLegacyTestProviders() {
        val providers = mutableListOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            providers.add(LocationManager.FUSED_PROVIDER)
        }
        for (provider in providers) {
            try {
                locationManager.setTestProviderEnabled(provider, false)
            } catch (e: Throwable) {
            }
            try {
                locationManager.removeTestProvider(provider)
            } catch (e: Throwable) {
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(com.suseoaa.locationspoofer.R.string.spoofing_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        cleanupLegacyTestProviders()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
