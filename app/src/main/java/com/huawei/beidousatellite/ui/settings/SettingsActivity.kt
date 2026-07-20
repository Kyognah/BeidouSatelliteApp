package com.huawei.beidousatellite.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.huawei.beidousatellite.R
import com.huawei.beidousatellite.data.region.BypassMethod
import com.huawei.beidousatellite.data.region.RegionBypassManager
import com.huawei.beidousatellite.util.SatelliteLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    @Inject lateinit var regionManager: RegionBypassManager
    @Inject lateinit var logger: SatelliteLogger

    private lateinit var bypassSwitch: Switch
    private lateinit var testModeSwitch: Switch
    private lateinit var methodGroup: RadioGroup
    private lateinit var statusText: TextView
    private lateinit var applyButton: Button
    private lateinit var clearLogsButton: Button
    private lateinit var viewLogsButton: Button
    private lateinit var autoDetectButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        bypassSwitch = findViewById(R.id.bypassSwitch)
        testModeSwitch = findViewById(R.id.testModeSwitch)
        methodGroup = findViewById(R.id.methodGroup)
        statusText = findViewById(R.id.statusText)
        applyButton = findViewById(R.id.applyButton)
        clearLogsButton = findViewById(R.id.clearLogsButton)
        viewLogsButton = findViewById(R.id.viewLogsButton)
        autoDetectButton = findViewById(R.id.autoDetectButton)

        lifecycleScope.launch {
            regionManager.bypassEnabledFlow.collect { enabled ->
                bypassSwitch.isChecked = enabled
                updateStatus()
            }
        }
        lifecycleScope.launch {
            regionManager.testModeFlow.collect { enabled ->
                testModeSwitch.isChecked = enabled
                updateStatus()
            }
        }
        lifecycleScope.launch {
            regionManager.bypassMethodFlow.collect { methodName ->
                val method = try { BypassMethod.valueOf(methodName) } catch (_: Exception) { BypassMethod.SOFTWARE_SPOOF }
                when (method) {
                    BypassMethod.SOFTWARE_SPOOF -> methodGroup.check(R.id.radioSoftware)
                    BypassMethod.HMS_REFLECTION -> methodGroup.check(R.id.radioReflection)
                    BypassMethod.ADB_SETTINGS -> methodGroup.check(R.id.radioAdb)
                    BypassMethod.MAGISK -> methodGroup.check(R.id.radioMagisk)
                    BypassMethod.TEST_MODE -> methodGroup.check(R.id.radioTest)
                }
            }
        }

        applyButton.setOnClickListener {
            val method = when (methodGroup.checkedRadioButtonId) {
                R.id.radioReflection -> BypassMethod.HMS_REFLECTION
                R.id.radioAdb -> BypassMethod.ADB_SETTINGS
                R.id.radioMagisk -> BypassMethod.MAGISK
                R.id.radioTest -> BypassMethod.TEST_MODE
                else -> BypassMethod.SOFTWARE_SPOOF
            }
            lifecycleScope.launch {
                regionManager.setBypassEnabled(bypassSwitch.isChecked, method)
                regionManager.setTestMode(testModeSwitch.isChecked)
                getSharedPreferences("beidou_region", Context.MODE_PRIVATE).edit()
                    .putBoolean("test_mode_prefs", testModeSwitch.isChecked)
                    .putBoolean("bypass_enabled", bypassSwitch.isChecked)
                    .putString("bypass_method", method.name)
                    .apply()
                updateStatus()
                logger.i("Settings", "Applied bypass=${bypassSwitch.isChecked} method=$method test=${testModeSwitch.isChecked}")
            }
        }

        autoDetectButton.setOnClickListener {
            autoDetectButton.isEnabled = false
            autoDetectButton.text = "Detecting..."
            lifecycleScope.launch {
                val results = regionManager.autoDetectAndApply()
                val msg = results.joinToString("\n\n") { "Method: ${it.method}\nSuccess: ${it.success}\nInfo: ${it.message}" }
                statusText.text = "Auto-Detect Results:\n\n$msg\n\n" + regionManager.getBypassStatus()
                autoDetectButton.isEnabled = true
                autoDetectButton.text = "🔍 Auto-Detect Best Method"
            }
        }

        viewLogsButton.setOnClickListener {
            startActivity(Intent(this, LogViewerActivity::class.java))
        }

        clearLogsButton.setOnClickListener {
            statusText.text = "Log path: ${logger.getLogPath()}\n\nLogs are in app-specific external storage. Use Files app to browse."
        }

        updateStatus()
    }

    private fun updateStatus() {
        statusText.text = regionManager.getBypassStatus() + "\n\nLog path:\n${logger.getLogPath()}\n\nTap View Logs to see inside app."
    }
}
