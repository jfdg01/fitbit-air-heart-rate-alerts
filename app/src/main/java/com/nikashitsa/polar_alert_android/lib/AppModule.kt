package com.nikashitsa.polar_alert_android.lib

import android.util.Log
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Qualifier
import javax.inject.Singleton

/** A coroutine scope that lives as long as the process. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Each alert leaves a line in logcat, so a test by adb can check it without the sound. */
    @Provides
    @Singleton
    fun provideAlerts(
        sound: SoundManager,
        vibration: VibrationManager,
        settings: SettingsRepository,
        @ApplicationScope scope: CoroutineScope,
    ): Alerts = Alerts(
        playSound = { type ->
            Log.d("Alerts", "sound $type")
            scope.launch(Dispatchers.Main.immediate) { sound.play(type, settings.volumeFlow.first()) }
        },
        vibrate = { type ->
            scope.launch(Dispatchers.Main.immediate) {
                if (!settings.vibrateFlow.first()) return@launch
                Log.d("Alerts", "vibration $type")
                vibration.vibrate(type)
            }
        },
    )
}
