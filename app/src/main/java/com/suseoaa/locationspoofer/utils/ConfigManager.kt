package com.suseoaa.locationspoofer.utils

import android.content.Context
import android.location.Geocoder
import com.suseoaa.locationspoofer.data.model.RoutePoint
import com.suseoaa.locationspoofer.utils.CoordinateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class ConfigManager(private val context: Context, private val rootManager: RootManager) {


    private var lastGeocodedLat = -999.0
    private var lastGeocodedLng = -999.0
    private var cachedProvince = ""
    private var cachedCity = ""
    private var cachedDistrict = ""
    private var cachedStreet = ""
    private var cachedStreetNum = ""
    private var cachedAddressText = ""
    private var cachedCountry = ""
    private var cachedPoiName = ""

    suspend fun saveConfig(

        lat: Double,
        lng: Double,
        active: Boolean,
        simMode: String = "STILL",
        simBearing: Float = 0f,
        startTimestamp: Long = System.currentTimeMillis(),
        routePoints: List<RoutePoint> = emptyList(),
        isRouteMode: Boolean = false,
        wifiJson: String = "[]",
        appCoordinateSystems: Map<String, String> = emptyMap(),
        cellJson: String = "[]",
        bluetoothJson: String = "[]",
        mockWifi: Boolean = true,
        mockCell: Boolean = true,
        mockBluetooth: Boolean = true,
        enableJitter: Boolean = true,
        altitude: Double = 0.0,
        satelliteCount: Int = 20
    ) = withContext(Dispatchers.IO) {
        val routeArray = JSONArray()
        routePoints.forEach { p ->
            val obj = JSONObject()
            obj.put("lat", p.lat)
            obj.put("lng", p.lng)
            routeArray.put(obj)
        }


        val dist = FloatArray(1)
        if (lastGeocodedLat != -999.0) {
            android.location.Location.distanceBetween(
                lastGeocodedLat,
                lastGeocodedLng,
                lat,
                lng,
                dist
            )
        }

        if (lastGeocodedLat == -999.0 || dist[0] > 500f) {
            lastGeocodedLat = lat
            lastGeocodedLng = lng
            try {
                // 原坐标是 GCJ-02，Geocoder 是调用 Android 系统的原生服务，严格要求 WGS-84 坐标
                // 因此在此处做一次逆向偏移，防止解析出的街道文本发生几百米的偏移误差
                val wgs84 = CoordinateUtils.gcj02ToWgs84(lat, lng)
                val geocoder = Geocoder(context)
                val addresses = geocoder.getFromLocation(wgs84.lat, wgs84.lng, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    cachedCountry = addr.countryName ?: ""
                    cachedProvince = addr.adminArea ?: ""
                    cachedCity = addr.locality ?: addr.subAdminArea ?: ""
                    cachedDistrict = addr.subLocality ?: ""
                    cachedStreet = addr.thoroughfare ?: ""
                    cachedStreetNum = addr.subThoroughfare ?: ""
                    cachedAddressText = addr.getAddressLine(0)
                        ?: "${cachedProvince}${cachedCity}${cachedDistrict}${cachedStreet}${cachedStreetNum}"
                    cachedPoiName = addr.featureName ?: ""
                }
            } catch (e: Exception) {
                // Ignore network/geocoder errors
            }
        }

        val json = JSONObject().apply {
            put("province", cachedProvince)
            put("city", cachedCity)
            put("district", cachedDistrict)
            put("street", cachedStreet)
            put("streetNum", cachedStreetNum)
            put("address", cachedAddressText)
            put("country", cachedCountry)
            put("poiName", cachedPoiName)

            put("lat", lat)
            put("lng", lng)
            put("active", active)
            put("sim_mode", simMode)
            put("sim_bearing", simBearing.toDouble())
            put("start_timestamp", startTimestamp)
            put("route_points", routeArray)
            put("is_route_mode", isRouteMode)
            val wifiObj = try {
                JSONObject(wifiJson)
            } catch (e: Exception) {
                JSONObject().apply {
                    put("isConnected", false)
                    put("connectedWifi", JSONObject.NULL)
                    put("nearbyWifi", JSONArray())
                }
            }
            put("wifi_json", wifiObj)
            put("cell_json", JSONArray(cellJson))
            put("bluetooth_json", JSONArray(bluetoothJson))
            put("mock_wifi", mockWifi)
            put("mock_cell", mockCell)
            put("mock_bluetooth", mockBluetooth)
            put("enable_jitter", enableJitter)
            put("altitude", altitude)
            put("satellite_count", satelliteCount)

            val coordSysObj = JSONObject()
            appCoordinateSystems.forEach { (pkg, sys) -> coordSysObj.put(pkg, sys) }
            put("app_coordinate_systems", coordSysObj)
        }
        val cellCount = json.optJSONArray("cell_json")?.length() ?: 0

        // 使用 stdin 写入，避免命令行过长 (ARG_MAX) 导致 su 执行失败，实现实时更新
        val jsonText = json.toString()
        val command = """
            cat > /data/local/tmp/locationspoofer_config_tmp.json
            chmod 666 /data/local/tmp/locationspoofer_config_tmp.json
            chcon u:object_r:shell_data_file:s0 /data/local/tmp/locationspoofer_config_tmp.json 2>/dev/null || true
            cp /data/local/tmp/locationspoofer_config_tmp.json /data/system/locationspoofer_config_tmp.json
            chown system:system /data/system/locationspoofer_config_tmp.json 2>/dev/null || true
            chmod 644 /data/system/locationspoofer_config_tmp.json
            chcon u:object_r:system_data_file:s0 /data/system/locationspoofer_config_tmp.json 2>/dev/null || true
            mv /data/local/tmp/locationspoofer_config_tmp.json /data/local/tmp/locationspoofer_config.json
            mv /data/system/locationspoofer_config_tmp.json /data/system/locationspoofer_config.json
        """.trimIndent()

        val result = rootManager.executeCommandWithInput(command, jsonText)
    }
}
