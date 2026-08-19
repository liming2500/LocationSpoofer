@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package com.suseoaa.locationspoofer.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Embedded
import androidx.room.Relation
import com.suseoaa.locationspoofer.data.model.SavedLocation
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "location_records",
    indices = [
        Index(value = ["lat", "lng"]),
        Index(value = ["timestamp"])
    ]
)
data class LocationRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val lat: Double,
    val lng: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val placeName: String = "",
    val remark: String = "",
    val selectedWifiBssid: String? = null,
    val selectedBluetoothAddress: String? = null,
    val selectedCellKey: String? = null
)

@Serializable
@Entity(tableName = "wifi_devices")
data class WifiDevice(
    @PrimaryKey val bssid: String,
    val ssid: String = "",
    val frequency: Int = 2412,
    val capabilities: String = "",
    val vendor: String = ""
)

@Serializable
@Entity(
    tableName = "location_connected_wifi",
    foreignKeys = [
        ForeignKey(
            entity = LocationRecord::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class LocationConnectedWifi(
    @PrimaryKey val locationId: Long = 0,
    val bssid: String,
    val ssid: String = "",
    val vendor: String = "",
    val macAddress: String = "",
    val frequency: Int = 2412,
    val linkSpeed: Int = 0,
    val level: Int = -50,
    val capabilities: String = "",
    val networkId: Int = 0,
    val wifiStandard: Int = 0
)

@Entity(
    tableName = "location_wifi",
    primaryKeys = ["locationId", "bssid"],
    foreignKeys = [
        ForeignKey(
            entity = LocationRecord::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = WifiDevice::class,
            parentColumns = ["bssid"],
            childColumns = ["bssid"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bssid")]
)
@Serializable
data class LocationWifi(
    val locationId: Long = 0,
    val bssid: String,
    val level: Int = -60
)

@Serializable
@Entity(tableName = "bluetooth_devices")
data class BluetoothDevice(
    @PrimaryKey val address: String,
    val name: String = "",
    val scanRecordHex: String = ""
)

@Entity(
    tableName = "location_bluetooth",
    primaryKeys = ["locationId", "address"],
    foreignKeys = [
        ForeignKey(
            entity = LocationRecord::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = BluetoothDevice::class,
            parentColumns = ["address"],
            childColumns = ["address"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("address")]
)
@Serializable
data class LocationBluetooth(
    val locationId: Long = 0,
    val address: String,
    val rssi: Int = -70
)

@Serializable
@Entity(tableName = "cell_devices")
data class CellDevice(
    @PrimaryKey val cellKey: String,
    val type: String = "LTE",
    val mcc: Int = 460,
    val mnc: Int = 0,
    // LTE
    val tac: Int = 0,
    val ci: Int = 0,
    val pci: Int = 0,
    // GSM/WCDMA
    val lac: Int = 0,
    val cid: Int = 0,
    // WCDMA
    val psc: Int = 0,
    // NR
    val nci: Long = 0,
    // CDMA
    val networkId: Int = 0,
    val systemId: Int = 0,
    val basestationId: Int = 0
)

@Entity(
    tableName = "location_cells",
    primaryKeys = ["locationId", "cellKey"],
    foreignKeys = [
        ForeignKey(
            entity = LocationRecord::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CellDevice::class,
            parentColumns = ["cellKey"],
            childColumns = ["cellKey"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("cellKey")]
)
@Serializable
data class LocationCell(
    val locationId: Long = 0,
    val cellKey: String,
    val dbm: Int = -80,
    val isRegistered: Boolean = true
)

@Serializable
data class LocationWithWifi(
    @Embedded val locationWifi: LocationWifi,
    @Relation(parentColumn = "bssid", entityColumn = "bssid")
    val device: WifiDevice
)

@Serializable
data class LocationWithBluetooth(
    @Embedded val locationBluetooth: LocationBluetooth,
    @Relation(parentColumn = "address", entityColumn = "address")
    val device: BluetoothDevice
)

@Serializable
data class LocationWithCell(
    @Embedded val locationCell: LocationCell,
    @Relation(parentColumn = "cellKey", entityColumn = "cellKey")
    val device: CellDevice
)

@Serializable
data class CompleteLocation(
    @Embedded val location: LocationRecord,
    @Relation(parentColumn = "id", entityColumn = "locationId")
    val connectedWifi: LocationConnectedWifi? = null,
    @Relation(entity = LocationWifi::class, parentColumn = "id", entityColumn = "locationId")
    val wifis: List<LocationWithWifi> = emptyList(),
    @Relation(entity = LocationBluetooth::class, parentColumn = "id", entityColumn = "locationId")
    val bluetooths: List<LocationWithBluetooth> = emptyList(),
    @Relation(entity = LocationCell::class, parentColumn = "id", entityColumn = "locationId")
    val cells: List<LocationWithCell> = emptyList()
)

@Serializable
@Entity(tableName = "saved_routes")
data class SavedRouteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val pointsJson: String = "[]",
    val timestamp: Long = System.currentTimeMillis()
)

// 综合导出与导入数据包（支持全量多版本互通）
@Serializable
data class LocationSpooferDataPackage(
    val version: Int = 2,
    val exportTimestamp: Long = System.currentTimeMillis(),
    val appVersion: String = "2.0.0",
    val locations: List<CompleteLocation> = emptyList(),
    val savedLocations: List<SavedLocation> = emptyList(),
    val savedRoutes: List<SavedRouteEntity> = emptyList(),
    val appCoordinateSystems: Map<String, String> = emptyMap()
)
