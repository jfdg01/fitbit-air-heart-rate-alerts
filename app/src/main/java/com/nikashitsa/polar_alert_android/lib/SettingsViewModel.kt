package com.nikashitsa.polar_alert_android.lib

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository
): ViewModel() {

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

    init {
        observe(repository.volumeFlow, _volume)
        observe(repository.hrMinFlow, _hrMin)
        observe(repository.hrMaxFlow, _hrMax)
        observe(repository.vibrateFlow, _vibrate)
        observe(repository.alertIntervalFlow, _alertInterval)
        observe(repository.outOfRangeForFlow, _outOfRangeFor)
        observe(repository.initialDelayFlow, _initialDelay)
        observe(repository.languageFlow.map(AppLanguage::resolve), _language)
    }

    fun setVolume(value: Int) = update { repository.setVolume(value) }
    // Each limit pushes the other one, so 120-140 can become 150-170 in any order.
    fun setHrMin(value: Int) = update {
        repository.setHrMin(value)
        if (value > _hrMax.value) repository.setHrMax(value)
    }
    fun setHrMax(value: Int) = update {
        repository.setHrMax(value)
        if (value < _hrMin.value) repository.setHrMin(value)
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