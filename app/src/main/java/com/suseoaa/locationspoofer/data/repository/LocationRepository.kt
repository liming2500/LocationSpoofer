package com.suseoaa.locationspoofer.data.repository

import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import com.suseoaa.locationspoofer.data.db.SavedRouteDao
import com.suseoaa.locationspoofer.data.db.SavedRouteEntity
import com.suseoaa.locationspoofer.data.model.RoutePoint
import com.suseoaa.locationspoofer.data.state.SpoofingState
import com.suseoaa.locationspoofer.service.SpoofingService
import com.suseoaa.locationspoofer.utils.ConfigManager
import com.suseoaa.locationspoofer.utils.LSPosedManager
import com.suseoaa.locationspoofer.utils.RootManager
import com.suseoaa.locationspoofer.utils.SettingsManager
import org.json.JSONArray
import org.json.JSONObject

class LocationRepository(
    private val configManager: ConfigManager,
    private val rootManager: RootManager,
    private val lsposedManager: LSPosedManager,
    private val settingsManager: SettingsManager,
    private val savedRouteDao: SavedRouteDao
) {
    suspend fun checkRootAccess(): Boolean = rootManager.checkRootAccess()

    fun isModuleActive(): Boolean = lsposedManager.isModuleActive()

    suspend fun startSpoofing(
        context: Context,
        lat: Double,
        lng: Double,
        simMode: String,
        simBearing: Float,
        startTime: Long,
        routePoints: List<RoutePoint>,
        isRouteMode: Boolean,
        appCoordinateSystems: Map<String, String>,
        wifiJson: String = "[]",
        cellJson: String = "[]",
        bluetoothJson: String = "[]",
        mockWifi: Boolean = true,
        mockCell: Boolean = true,
        mockBluetooth: Boolean = true,
        enableJitter: Boolean = true,
        speedMs: Double = 0.0,
        stopAtDestination: Boolean = false,
        enableStepSimulation: Boolean = true,
        stepCadenceSpm: Int = 165,
        isAutoCadence: Boolean = true
    ) {
        SpoofingState.isActive = true
        SpoofingState.latitude = lat
        SpoofingState.longitude = lng
        SpoofingState.startTimestamp = startTime
        SpoofingState.simMode = simMode
        SpoofingState.simBearing = simBearing
        SpoofingState.wifiJson = wifiJson
        SpoofingState.cellJson = cellJson
        SpoofingState.bluetoothJson = bluetoothJson
        SpoofingState.routeJson = routePointsToJson(routePoints)
        SpoofingState.isRouteMode = isRouteMode
        SpoofingState.enableJitter = enableJitter

        val alt = settingsManager.altitude.toDoubleOrNull() ?: 0.0
        val satCount = settingsManager.satelliteCount.toIntOrNull() ?: 20
        configManager.saveConfig(
            lat,
            lng,
            true,
            simMode,
            simBearing,
            startTime,
            routePoints,
            isRouteMode,
            SpoofingState.wifiJson,
            appCoordinateSystems,
            SpoofingState.cellJson,
            SpoofingState.bluetoothJson,
            mockWifi,
            mockCell,
            mockBluetooth,
            enableJitter,
            alt,
            satCount,
            speedMs,
            stopAtDestination,
            enableStepSimulation,
            stepCadenceSpm,
            isAutoCadence
        )

        context.startForegroundService(
            Intent(context, SpoofingService::class.java).apply {
                action = SpoofingService.ACTION_START
                putExtra(SpoofingService.EXTRA_LAT, lat)
                putExtra(SpoofingService.EXTRA_LNG, lng)
            }
        )
    }

    suspend fun stopSpoofing(context: Context) {
        SpoofingState.isActive = false
        SpoofingState.latitude = 0.0
        SpoofingState.longitude = 0.0
        SpoofingState.wifiJson = "[]"
        SpoofingState.cellJson = "[]"
        SpoofingState.bluetoothJson = "[]"
        SpoofingState.routeJson = "[]"
        SpoofingState.isRouteMode = false
        configManager.saveConfig(0.0, 0.0, false)

        // 1. 同步在当前进程清理所有可能残留的 TestProvider
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm != null) {
            try {
                lm.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false)
            } catch (e: Throwable) {
            }
            try {
                lm.removeTestProvider(LocationManager.GPS_PROVIDER)
            } catch (e: Throwable) {
            }
            try {
                lm.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
            } catch (e: Throwable) {
            }
            try {
                lm.removeTestProvider(LocationManager.NETWORK_PROVIDER)
            } catch (e: Throwable) {
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    lm.setTestProviderEnabled(LocationManager.FUSED_PROVIDER, false)
                    lm.removeTestProvider(LocationManager.FUSED_PROVIDER)
                }
            } catch (e: Throwable) {
            }
        }

        // 2. 停止前台模拟服务
        try {
            context.stopService(Intent(context, SpoofingService::class.java))
        } catch (e: Throwable) {
        }
        try {
            context.startService(Intent(context, SpoofingService::class.java).apply {
                action = SpoofingService.ACTION_STOP
            })
        } catch (e: Throwable) {
        }

        // 3. 彻底重置系统的 mock_location 状态，防止被澎湃OS/系统安全中心记录为模拟中
        rootManager.revokeMockLocation()
    }

    suspend fun updateConfig(
        lat: Double,
        lng: Double,
        simMode: String,
        simBearing: Float,
        startTime: Long,
        routePoints: List<RoutePoint>,
        isRouteMode: Boolean,
        appCoordinateSystems: Map<String, String>,
        wifiJson: String = SpoofingState.wifiJson,
        cellJson: String = SpoofingState.cellJson,
        bluetoothJson: String = SpoofingState.bluetoothJson,
        mockWifi: Boolean = true,
        mockCell: Boolean = true,
        mockBluetooth: Boolean = true,
        enableJitter: Boolean = true,
        speedMs: Double = 0.0,
        stopAtDestination: Boolean = false,
        enableStepSimulation: Boolean = true,
        stepCadenceSpm: Int = 165,
        isAutoCadence: Boolean = true
    ) {
        SpoofingState.latitude = lat
        SpoofingState.longitude = lng
        SpoofingState.startTimestamp = startTime
        SpoofingState.simMode = simMode
        SpoofingState.simBearing = simBearing
        SpoofingState.routeJson = routePointsToJson(routePoints)
        SpoofingState.isRouteMode = isRouteMode
        SpoofingState.wifiJson = wifiJson
        SpoofingState.cellJson = cellJson
        SpoofingState.bluetoothJson = bluetoothJson
        SpoofingState.enableJitter = enableJitter
        val alt = settingsManager.altitude.toDoubleOrNull() ?: 0.0
        val satCount = settingsManager.satelliteCount.toIntOrNull() ?: 20
        configManager.saveConfig(
            lat,
            lng,
            true,
            simMode,
            simBearing,
            startTime,
            routePoints,
            isRouteMode,
            SpoofingState.wifiJson,
            appCoordinateSystems,
            SpoofingState.cellJson,
            SpoofingState.bluetoothJson,
            mockWifi,
            mockCell,
            mockBluetooth,
            enableJitter,
            alt,
            satCount,
            speedMs,
            stopAtDestination,
            enableStepSimulation,
            stepCadenceSpm,
            isAutoCadence
        )
    }

    suspend fun updateWifiJson(wifiJson: String, appCoordinateSystems: Map<String, String>) {
        SpoofingState.wifiJson = wifiJson
        // 同步写入配置文件,确保Xposed端能读取到WiFi数据
        configManager.saveConfig(
            SpoofingState.latitude,
            SpoofingState.longitude,
            SpoofingState.isActive,
            SpoofingState.simMode,
            SpoofingState.simBearing,
            startTimestamp = SpoofingState.startTimestamp,
            wifiJson = wifiJson,
            appCoordinateSystems = appCoordinateSystems,
            cellJson = SpoofingState.cellJson,
            bluetoothJson = SpoofingState.bluetoothJson,
            altitude = settingsManager.altitude.toDoubleOrNull() ?: 0.0,
            satelliteCount = settingsManager.satelliteCount.toIntOrNull() ?: 20
        )
    }

    private fun routePointsToJson(points: List<RoutePoint>): String {
        val arr = JSONArray()
        points.forEach { p ->
            arr.put(JSONObject().apply {
                put("lat", p.lat)
                put("lng", p.lng)
            })
        }
        return arr.toString()
    }

    fun getSavedRoutes(): kotlinx.coroutines.flow.Flow<List<SavedRouteEntity>> {
        return savedRouteDao.getAllSavedRoutes()
    }

    suspend fun getAllSavedRoutesList(): List<SavedRouteEntity> {
        return savedRouteDao.getAllSavedRoutesList()
    }

    suspend fun insertSavedRoute(name: String, points: List<RoutePoint>) {
        val pointsJson = routePointsToJson(points)
        savedRouteDao.insertSavedRoute(
            SavedRouteEntity(
                name = name,
                pointsJson = pointsJson
            )
        )
    }

    suspend fun insertSavedRouteEntity(route: SavedRouteEntity) {
        savedRouteDao.insertSavedRoute(route)
    }

    suspend fun deleteSavedRoute(route: SavedRouteEntity) {
        savedRouteDao.deleteSavedRoute(route)
    }
}
