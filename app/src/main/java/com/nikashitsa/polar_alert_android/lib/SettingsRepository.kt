package com.nikashitsa.polar_alert_android.lib

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    produceMigrations = { context ->
        listOf(
            SharedPreferencesMigration(context, "settings"),
        )
    }
)

object SettingsDefaults {
    const val VOLUME = 90
    const val HR_MIN = 120
    const val HR_MAX = 140
    const val VIBRATE = false
    const val ALERT_INTERVAL = 1
    const val OUT_OF_RANGE_FOR = 0
    const val INITIAL_DELAY = 0
}

object SettingsOptions {
    // initial delay never times out, it only ends once HR first reaches the range
    const val UNTIL_IN_RANGE = -1

    const val HR_LOWEST = 30
    const val HR_HIGHEST = 240
    val AGE = 10..100
    val RESTING_HR = 25..120
    val PEAK_HR = 100..HR_HIGHEST

    val ALERT_INTERVAL = listOf(1, 3, 5, 10)
    val OUT_OF_RANGE_FOR = listOf(0, 5, 10, 30, 60, 300, 600)
    val INITIAL_DELAY = listOf(0, UNTIL_IN_RANGE, 60, 300, 600, 900)
}

object SettingsKeys {
    val volume = intPreferencesKey("volume")
    val hrMin = intPreferencesKey("hrMin")
    val hrMax = intPreferencesKey("hrMax")
    val vibrate = booleanPreferencesKey("vibrate")
    val alertInterval = intPreferencesKey("alertInterval")
    val outOfRangeFor = intPreferencesKey("outOfRangeFor")
    val initialDelay = intPreferencesKey("initialDelay")
    val language = stringPreferencesKey("language")
    val zone = intPreferencesKey("zone")
    val age = intPreferencesKey("age")
    val restingHr = intPreferencesKey("restingHr")
    val healthRestingHr = intPreferencesKey("healthRestingHr")
    val peakHr = intPreferencesKey("peakHr")
    val measuredPeak = intPreferencesKey("measuredPeak")
}

@Singleton
class SettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val dataStore: DataStore<Preferences> = context.dataStore

    val volumeFlow: Flow<Int> = get(SettingsKeys.volume, SettingsDefaults.VOLUME)
    suspend fun setVolume(value: Int) = set(SettingsKeys.volume, value.coerceIn(0, 100))

    val hrMinFlow: Flow<Int> = get(SettingsKeys.hrMin, SettingsDefaults.HR_MIN)
    suspend fun setHrMin(value: Int) = set(SettingsKeys.hrMin, value)

    val hrMaxFlow: Flow<Int> = get(SettingsKeys.hrMax, SettingsDefaults.HR_MAX)
    suspend fun setHrMax(value: Int) = set(SettingsKeys.hrMax, value)

    val vibrateFlow: Flow<Boolean> = get(SettingsKeys.vibrate, SettingsDefaults.VIBRATE)
    suspend fun setVibrate(value: Boolean) = set(SettingsKeys.vibrate, value)

    val alertIntervalFlow: Flow<Int> = get(SettingsKeys.alertInterval, SettingsDefaults.ALERT_INTERVAL)
    suspend fun setAlertInterval(value: Int) = set(SettingsKeys.alertInterval, value)

    val outOfRangeForFlow: Flow<Int> = get(SettingsKeys.outOfRangeFor, SettingsDefaults.OUT_OF_RANGE_FOR)
    suspend fun setOutOfRangeFor(value: Int) = set(SettingsKeys.outOfRangeFor, value)

    val initialDelayFlow: Flow<Int> = get(SettingsKeys.initialDelay, SettingsDefaults.INITIAL_DELAY)
    suspend fun setInitialDelay(value: Int) = set(SettingsKeys.initialDelay, value)

    /** The zone that sets Min and Max, or 0 when the user typed them. */
    val zoneFlow: Flow<Int> = get(SettingsKeys.zone, 0)
    suspend fun setZone(value: Int) = set(SettingsKeys.zone, value)

    val ageFlow: Flow<Int?> = dataStore.data.map { prefs -> prefs[SettingsKeys.age] }
    suspend fun setAge(value: Int?) = setOrRemove(SettingsKeys.age, value)

    /** A typed value. It always wins over the value from Health Connect. */
    val restingHrFlow: Flow<Int?> = dataStore.data.map { prefs -> prefs[SettingsKeys.restingHr] }
    suspend fun setRestingHr(value: Int?) = setOrRemove(SettingsKeys.restingHr, value)

    val healthRestingHrFlow: Flow<Int?> = dataStore.data.map { prefs -> prefs[SettingsKeys.healthRestingHr] }
    suspend fun setHealthRestingHr(value: Int) = set(SettingsKeys.healthRestingHr, value)

    /** A typed value. It always wins over the estimate from the age. */
    val peakHrFlow: Flow<Int?> = dataStore.data.map { prefs -> prefs[SettingsKeys.peakHr] }
    suspend fun setPeakHr(value: Int?) = setOrRemove(SettingsKeys.peakHr, value)

    val measuredPeakFlow: Flow<Int?> = dataStore.data.map { prefs -> prefs[SettingsKeys.measuredPeak] }
    suspend fun setMeasuredPeak(value: Int) = set(SettingsKeys.measuredPeak, value)

    /**
     * The language tag the user picked (see [AppLanguage]), or null to follow the device.
     * Nothing is stored until the user picks one, so a device language change still applies.
     */
    val languageFlow: Flow<String?> = dataStore.data.map { prefs -> prefs[SettingsKeys.language] }
    suspend fun setLanguage(value: AppLanguage) = set(SettingsKeys.language, value.tag)

    /**
     * Entitled, whether bought or granted for being an existing user. Only ever set to true:
     * a grandfathered user has no Play purchase, so "no purchase found" must never revoke it.
     */
    private fun <T> get(key: Preferences.Key<T>, default: T): Flow<T> =
        dataStore.data.map { prefs -> prefs[key] ?: default }

    private suspend fun setOrRemove(key: Preferences.Key<Int>, value: Int?) {
        dataStore.edit { prefs -> if (value == null) prefs.remove(key) else prefs[key] = value }
    }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        dataStore.edit { prefs -> prefs[key] = value }
    }
}
