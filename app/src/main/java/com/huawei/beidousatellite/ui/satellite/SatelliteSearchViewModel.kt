package com.huawei.beidousatellite.ui.satellite

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.ViewModel
import com.huawei.beidousatellite.data.hms.HmsSmcManager
import com.huawei.beidousatellite.util.SatelliteLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SatelliteSearchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val hmsManager: HmsSmcManager,
    private val logger: SatelliteLogger
) : ViewModel(), SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private var gravity: FloatArray? = null
    private var geomagnetic: FloatArray? = null

    private val _deviceAzimuth = MutableStateFlow(0f)
    val deviceAzimuth: StateFlow<Float> = _deviceAzimuth

    private val _calibrationStatus = MutableStateFlow("Not calibrated")
    val calibrationStatus: StateFlow<String> = _calibrationStatus

    val signalInfo = hmsManager.signalInfo
    val searchStatus = hmsManager.searchStatus

    fun start() {
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI)
        hmsManager.startSatelliteSearch()
        logger.i("SearchVM", "Started search + sensors")
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        hmsManager.stopSatelliteSearch()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            when (it.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> gravity = it.values.clone()
                Sensor.TYPE_MAGNETIC_FIELD -> geomagnetic = it.values.clone()
            }
            if (gravity != null && geomagnetic != null) {
                val R = FloatArray(9)
                val I = FloatArray(9)
                val success = SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)
                if (success) {
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(R, orientation)
                    var azimuthRad = orientation[0]
                    var azimuthDeg = Math.toDegrees(azimuthRad.toDouble()).toFloat()
                    azimuthDeg = (azimuthDeg + 360) % 360
                    _deviceAzimuth.value = azimuthDeg

                    // Check calibration quality via accuracy
                    // Simplified
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        val status = when (accuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "High accuracy"
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Medium accuracy"
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Low - need figure-8 calibration"
            else -> "Unreliable - calibrate with figure-8 motion"
        }
        _calibrationStatus.value = status
        logger.sensor("Sensor ${sensor?.name} accuracy $accuracy $status")
    }

    override fun onCleared() {
        super.onCleared()
        sensorManager.unregisterListener(this)
    }
}
