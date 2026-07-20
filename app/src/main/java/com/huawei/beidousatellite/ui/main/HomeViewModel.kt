package com.huawei.beidousatellite.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.huawei.beidousatellite.data.hms.HmsSmcManager
import com.huawei.beidousatellite.data.region.BypassAttempt
import com.huawei.beidousatellite.data.region.BypassMethod
import com.huawei.beidousatellite.data.region.RegionBypassManager
import com.huawei.beidousatellite.data.repository.SatelliteRepository
import com.huawei.beidousatellite.util.SatelliteLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val regionManager: RegionBypassManager,
    private val hmsManager: HmsSmcManager,
    private val repository: SatelliteRepository,
    private val logger: SatelliteLogger
) : ViewModel() {

    val bypassEnabled = regionManager.bypassEnabledFlow
    val bypassMethod = regionManager.bypassMethodFlow
    val testMode = regionManager.testModeFlow
    val connectionState = hmsManager.connectionState
    val capability = hmsManager.capability
    val signalInfo = hmsManager.signalInfo
    val searchStatus = hmsManager.searchStatus

    private val _messageCount = MutableStateFlow(0)
    val messageCount: StateFlow<Int> = _messageCount

    init {
        viewModelScope.launch {
            repository.getAllMessagesFlow().collect { list ->
                _messageCount.value = list.size
            }
        }
        hmsManager.connect()
    }

    fun setBypass(enabled: Boolean, method: BypassMethod = BypassMethod.SOFTWARE_SPOOF) {
        viewModelScope.launch {
            regionManager.setBypassEnabled(enabled, method)
            if (enabled) hmsManager.connect() else hmsManager.disconnect()
        }
    }

    fun setTestMode(enabled: Boolean) {
        viewModelScope.launch {
            regionManager.setTestMode(enabled)
            logger.i("HomeVM", "TestMode $enabled")
            if (enabled) hmsManager.connect() else hmsManager.disconnect()
        }
    }

    fun saveTestModeSync(context: Context, enabled: Boolean) {
        context.getSharedPreferences("beidou_region", Context.MODE_PRIVATE)
            .edit().putBoolean("test_mode_prefs", enabled).apply()
        setTestMode(enabled)
    }

    suspend fun autoDetect(): List<BypassAttempt> {
        return regionManager.autoDetectAndApply()
    }

    fun getStatusText(): String = regionManager.getBypassStatus()

    override fun onCleared() {
        super.onCleared()
        hmsManager.disconnect()
    }
}
