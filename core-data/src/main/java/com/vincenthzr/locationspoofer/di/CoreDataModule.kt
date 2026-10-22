package com.vincenthzr.locationspoofer.di

import com.vincenthzr.locationspoofer.data.db.AppDatabase
import com.vincenthzr.locationspoofer.data.motion.ConfigMotionSink
import com.vincenthzr.locationspoofer.data.motion.MotionController
import com.vincenthzr.locationspoofer.data.state.SpoofingState
import com.vincenthzr.locationspoofer.data.repository.LocationRepository
import com.vincenthzr.locationspoofer.data.repository.SettingsRepository
import com.vincenthzr.locationspoofer.data.repository.WifiRepository
import com.vincenthzr.locationspoofer.utils.ConfigManager
import com.vincenthzr.locationspoofer.utils.EnvironmentScanner
import com.vincenthzr.locationspoofer.utils.LSPosedManager
import com.vincenthzr.locationspoofer.utils.OpenCellIdClient
import com.vincenthzr.locationspoofer.utils.RootManager
import com.vincenthzr.locationspoofer.utils.MotionRealism
import com.vincenthzr.locationspoofer.utils.SettingsManager
import com.vincenthzr.locationspoofer.utils.WigleClient
import com.vincenthzr.locationspoofer.data.repository.HookStatusRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val coreDataModule = module {
    single { RootManager() }
    single { ConfigManager(androidContext(), get()) }
    single { LSPosedManager() }
    single { SettingsManager(androidContext()) }
    single { EnvironmentScanner(androidContext()) }

    single { WigleClient() }
    single { OpenCellIdClient() }
    single { WifiRepository(get()) }

    single { LocationRepository(get(), get(), get(), get(), get(), get()) }
    single { SettingsRepository(get()) }
    single { HookStatusRepository(get()) }
    single {
        val settings = get<SettingsManager>()
        MotionController(
            sink = ConfigMotionSink(get(), get()),
            realismParams = {
                val enabled = SpoofingState.motionFluctuationEnabled
                MotionController.RealismParams(
                    levelId = SpoofingState.realismLevel.takeIf { it >= 0 } ?: settings.realismLevel,
                    // 总开关关闭时把起伏比例清零，速度与步频都保持基准值
                    speedPct = if (enabled) SpoofingState.speedFluctuationPct.takeIf { it >= 0 } ?: settings.speedFluctuationPct else 0,
                    cadencePct = if (enabled) SpoofingState.cadenceFluctuationPct.takeIf { it >= 0 } ?: settings.cadenceFluctuationPct else 0,
                    rampSec = SpoofingState.speedRampSec.takeIf { it >= 0 } ?: settings.speedRampSec.toDouble(),
                    gradeSensitivity = SpoofingState.gradeSensitivity.takeIf { it >= 0 } ?: settings.gradeSensitivity,
                    altitudeVariationM = settings.altitudeVariationM,
                    altitudeDriftM = MotionRealism.Level.fromId(
                        SpoofingState.realismLevel.takeIf { it >= 0 } ?: settings.realismLevel
                    ).altitudeAmplitudeM
                )
            }
        )
    }

    single { AppDatabase.getDatabase(androidContext()) }
    single { get<AppDatabase>().environmentDao() }
    single { get<AppDatabase>().savedRouteDao() }
}
