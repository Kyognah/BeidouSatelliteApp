package com.huawei.beidousatellite.ui.satellite

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.huawei.beidousatellite.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SatelliteSearchActivity : AppCompatActivity() {

    private val viewModel: SatelliteSearchViewModel by viewModels()
    private lateinit var compassView: CompassView
    private lateinit var statusText: TextView
    private lateinit var calibrationText: TextView
    private lateinit var signalText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_satellite_search)

        compassView = findViewById(R.id.compassView)
        statusText = findViewById(R.id.statusText)
        calibrationText = findViewById(R.id.calibrationText)
        signalText = findViewById(R.id.signalText)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)

        startButton.setOnClickListener { viewModel.start() }
        stopButton.setOnClickListener { viewModel.stop() }

        lifecycleScope.launch {
            viewModel.deviceAzimuth.collect { azimuth ->
                // Update compass will be done in signal collector
                statusText.text = "Device Azimuth: %.1f°\nSearch: ${viewModel.searchStatus.value}".format(azimuth)
            }
        }
        lifecycleScope.launch {
            viewModel.calibrationStatus.collect { cal ->
                calibrationText.text = "Calibration: $cal\nDo figure-8 motion to calibrate"
            }
        }
        lifecycleScope.launch {
            viewModel.signalInfo.collect { info ->
                info?.let {
                    signalText.text = "PRN ${it.satelliteId}\nSNR %.1f dB\nEl %.1f° Az %.1f°\nQuality: %s\nUsable: %s".format(it.snrDb, it.elevationDeg, it.azimuthDeg, it.signalQuality, it.isUsable)
                    compassView.updateCompass(
                        viewModel.deviceAzimuth.value,
                        it.azimuthDeg.toFloat(),
                        it.elevationDeg.toFloat(),
                        it.snrDb.toFloat(),
                        it.satelliteId
                    )
                }
            }
        }
        lifecycleScope.launch {
            viewModel.searchStatus.collect { status ->
                statusText.text = "Device Azimuth: %.1f°\nSearch: $status".format(viewModel.deviceAzimuth.value)
            }
        }

        viewModel.start()
    }

    override fun onPause() {
        super.onPause()
        viewModel.stop()
    }
}
