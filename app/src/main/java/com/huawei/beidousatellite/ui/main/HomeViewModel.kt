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
        logger.i("HomeVM", "=== HomeViewModel init started ===")
        logger.i("HomeVM", "Bypass enabled sync=${regionManager.isBypassEnabledSync()} method=${regionManager.getBypassMethodSync()} testMode=${regionManager.isTestModeSync()} supported=${regionManager.isSatelliteSupported()}")
        viewModelScope.launch {
            try {
                repository.getAllMessagesFlow().collect { list ->
                    _messageCount.value = list.size
                    logger.d("HomeVM", "Message count: ${list.size}")
                }
            } catch (e: Throwable) {
                logger.e("HomeVM", "Collect messages failed", e)
            }
        }
        viewModelScope.launch {
            try {
                logger.i("HomeVM", "Calling hmsManager.connect() from init")
                hmsManager.connect()
                logger.i("HomeVM", "hmsManager.connect() finished, connectionState=${hmsManager.connectionState.value}")
            } catch (e: Throwable) {
                logger.e("HomeVM", "connect() crashed in init - should never happen now", e)
            }
        }
        logger.i("HomeVM", "=== init finished ===")
    }

    fun setBypass(enabled: Boolean, method: BypassMethod = BypassMethod.SOFTWARE_SPOOF) {
        viewModelScope.launch {
            try {
                logger.i("HomeVM", "setBypass enabled=$enabled method=$method")
                regionManager.setBypassEnabled(enabled, method)
                if (enabled) {
                    logger.i("HomeVM", "Bypass enabled, connecting HMS")
                    hmsManager.connect()
                } else {
                    logger.i("HomeVM", "Bypass disabled, disconnecting")
                    hmsManager.disconnect()
                }
            } catch (e: Throwable) {
                logger.e("HomeVM", "setBypass failed", e)
            }
        }
    }

    fun setTestMode(enabled: Boolean) {
        viewModelScope.launch {
            try {
                logger.i("HomeVM", "setTestMode $enabled")
                regionManager.setTestMode(enabled)
                if (enabled) {
                    hmsManager.connect()
                } else {
                    hmsManager.disconnect()
                }
            } catch (e: Throwable) {
                logger.e("HomeVM", "setTestMode failed", e)
            }
        }
    }

    fun saveTestModeSync(context: Context, enabled: Boolean) {
        try {
            logger.i("HomeVM", "saveTestModeSync $enabled")
            context.getSharedPreferences("beidou_region", Context.MODE_PRIVATE)
                .edit().putBoolean("test_mode_prefs", enabled).apply()
            setTestMode(enabled)
        } catch (e: Throwable) {
            logger.e("HomeVM", "saveTestModeSync failed", e)
        }
    }

    suspend fun autoDetect(): List<BypassAttempt> {
        return try {
            logger.i("HomeVM", "autoDetect called")
            val res = regionManager.autoDetectAndApply()
            logger.i("HomeVM", "autoDetect results: ${res.size}")
            res
        } catch (e: Throwable) {
            logger.e("HomeVM", "autoDetect failed", e)
            emptyList()
        }
    }

    fun getStatusText(): String {
        return try {
            regionManager.getBypassStatus()
        } catch (e: Throwable) {
            logger.e("HomeVM", "getStatusText failed", e)
            "Error getting status: ${e.message}"
        }
    }

    override fun onCleared() {
        try {
            logger.i("HomeVM", "onCleared, disconnecting")
            hmsManager.disconnect()
        } catch (e: Throwable) {
            logger.e("HomeVM", "onCleared disconnect failed", e)
        }
        super.onCleared()
    }
}
