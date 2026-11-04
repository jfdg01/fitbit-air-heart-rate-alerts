package com.nikashitsa.polar_alert_android.lib

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    @param:ApplicationContext private val context: Context,
): ViewModel() {

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name
    private val _volume = MutableStateFlow<Int>(SettingsDefaults.VOLUME)
    val volume: StateFlow<Int> = _volume
    private val _hrMin = MutableStateFlow<Int>(SettingsDefaults.HR_MIN)
    val hrMin: StateFlow<Int> = _hrMin
    private val _hrMax = MutableStateFlow<Int>(SettingsDefaults.HR_MAX)
    val hrMax: StateFlow<Int> = _hrMax
    private val _vibrate = MutableStateFlow<Boolean>(SettingsDefaults.VIBRATE)
    val vibrate: StateFlow<Boolean> = _vibrate
    private val _alertInterval = MutableStateFlow<Int>(SettingsDefaults.ALERT_INTERVAL)
    val alertInterval: StateFlow<Int> = _alertInterval
    private val _outOfRangeFor = MutableStateFlow<Int>(SettingsDefaults.OUT_OF_RANGE_FOR)
    val outOfRangeFor: StateFlow<Int> = _outOfRangeFor
    private val _initialDelay = MutableStateFlow<Int>(SettingsDefaults.INITIAL_DELAY)
    val initialDelay: StateFlow<Int> = _initialDelay
    private val _language = MutableStateFlow<AppLanguage>(AppLanguage.resolve(null))
    val language: StateFlow<AppLanguage> = _language

    private val _zone = MutableStateFlow(0)
    val zone: StateFlow<Int> = _zone
    private val _age = MutableStateFlow<Int?>(null)
    val age: StateFlow<Int?> = _age
    private val _restingHr = MutableStateFlow<Int?>(null)
    val restingHr: StateFlow<Int?> = _restingHr
    private val _measuredPeak = MutableStateFlow<Int?>(null)
    val measuredPeak: StateFlow<Int?> = _measuredPeak
    private val _healthStatus = MutableStateFlow(HealthStatus.UNAVAILABLE)
    val healthStatus: StateFlow<HealthStatus> = _healthStatus
    private val _healthRestingHr = MutableStateFlow<Int?>(null)
    val healthRestingHr: StateFlow<Int?> = _healthRestingHr
    private val _peakHr = MutableStateFlow<Int?>(null)
    val peakHr: StateFlow<Int?> = _peakHr

    init {
        observe(repository.zoneFlow, _zone)
        observe(repository.ageFlow, _age)
        observe(repository.restingHrFlow, _restingHr)
        observe(repository.measuredPeakFlow, _measuredPeak)
        observe(repository.healthRestingHrFlow, _healthRestingHr)
        observe(repository.peakHrFlow, _peakHr)
        // The chosen zone follows the data, so a new resting heart rate moves Min and Max.
        viewModelScope.launch {
            combine(
                repository.zoneFlow,
                repository.ageFlow,
                repository.restingHrFlow,
                repository.healthRestingHrFlow,
                repository.peakHrFlow,
            ) { zone, age, typedResting, healthResting, typedPeak ->
                val resting = typedResting ?: healthResting
                val peak = typedPeak ?: age?.let(HeartZones::peakFromAge)
                if (zone == 0 || resting == null || peak == null || peak <= resting) null
                else HeartZones.range(zone, resting, peak)
            }.collect { range ->
                if (range != null) {
                    repository.setHrMin(range.first)
                    repository.setHrMax(range.last)
                }
            }
        }
        observe(repository.nameFlow, _name)
        observe(repository.volumeFlow, _volume)
        observe(repository.hrMinFlow, _hrMin)
        observe(repository.hrMaxFlow, _hrMax)
        observe(repository.vibrateFlow, _vibrate)
        observe(repository.alertIntervalFlow, _alertInterval)
        observe(repository.outOfRangeForFlow, _outOfRangeFor)
        observe(repository.initialDelayFlow, _initialDelay)
        observe(repository.languageFlow.map(AppLanguage::resolve), _language)
    }

    fun setName(value: String) = update { repository.setName(value) }
    fun setVolume(value: Int) = update { repository.setVolume(value) }
    // Each limit pushes the other one, so 120-140 can become 150-170 in any order.
    fun setHrMin(value: Int) = update {
        repository.setZone(0)
        repository.setHrMin(value)
        if (value > _hrMax.value) repository.setHrMax(value)
    }
    fun setHrMax(value: Int) = update {
        repository.setZone(0)
        repository.setHrMax(value)
        if (value < _hrMin.value) repository.setHrMin(value)
    }
    fun setZone(value: Int) = update { repository.setZone(value) }
    fun setAge(value: Int?) = update { repository.setAge(value) }
    fun setPeakHr(value: Int?) = update { repository.setPeakHr(value) }
    fun setRestingHr(value: Int?) = update { repository.setRestingHr(value) }

    /** Runs each time the screen opens: Google Health adds a new resting heart rate each day. */
    fun refreshHealth() = update {
        try {
            _healthStatus.value = HealthData.status(context)
            if (_healthStatus.value == HealthStatus.READY) {
                val reading = HealthData.read(context)
                Log.d("SettingsViewModel", "Health Connect: $reading")
                reading.resting?.let { repository.setHealthRestingHr(it) }
                reading.measuredPeak?.let { repository.setMeasuredPeak(it) }
            }
        } catch (e: Exception) {
            // The user can still type the values.
            Log.e("SettingsViewModel", "Health Connect failed", e)
        }
    }

    fun setVibrate(value: Boolean) = update { repository.setVibrate(value) }
    fun setAlertInterval(value: Int) = update { repository.setAlertInterval(value) }
    fun setOutOfRangeFor(value: Int) = update { repository.setOutOfRangeFor(value) }
    fun setInitialDelay(value: Int) = update { repository.setInitialDelay(value) }
    fun setLanguage(value: AppLanguage) = update { repository.setLanguage(value) }

    private fun <T> observe(flow: Flow<T>, state: MutableStateFlow<T>) {
        viewModelScope.launch {
            flow.collect { state.value = it }
        }
    }
    private fun update(action: suspend () -> Unit) {
        viewModelScope.launch { action() }
    }
}