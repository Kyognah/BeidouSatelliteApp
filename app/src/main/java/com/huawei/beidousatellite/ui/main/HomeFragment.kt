package com.huawei.beidousatellite.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.huawei.beidousatellite.R
import com.huawei.beidousatellite.data.region.BypassMethod
import com.huawei.beidousatellite.ui.emergency.EmergencySosActivity
import com.huawei.beidousatellite.ui.message.MessageHistoryActivity
import com.huawei.beidousatellite.ui.satellite.SatelliteSearchActivity
import com.huawei.beidousatellite.ui.settings.SettingsActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textview.MaterialTextView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var statusText: MaterialTextView
    private lateinit var signalText: MaterialTextView
    private lateinit var bypassSwitch: SwitchMaterial
    private lateinit var testModeSwitch: SwitchMaterial
    private lateinit var searchButton: MaterialButton
    private lateinit var sosButton: MaterialButton
    private lateinit var historyButton: MaterialButton
    private lateinit var settingsButton: MaterialButton

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        statusText = view.findViewById(R.id.statusText)
        signalText = view.findViewById(R.id.signalText)
        bypassSwitch = view.findViewById(R.id.bypassSwitch)
        testModeSwitch = view.findViewById(R.id.testModeSwitch)
        searchButton = view.findViewById(R.id.searchButton)
        sosButton = view.findViewById(R.id.sosButton)
        historyButton = view.findViewById(R.id.historyButton)
        settingsButton = view.findViewById(R.id.settingsButton)

        // Permissions
        requestPerms()

        searchButton.setOnClickListener {
            startActivity(Intent(requireContext(), SatelliteSearchActivity::class.java))
        }
        sosButton.setOnClickListener {
            startActivity(Intent(requireContext(), EmergencySosActivity::class.java))
        }
        historyButton.setOnClickListener {
            startActivity(Intent(requireContext(), MessageHistoryActivity::class.java))
        }
        settingsButton.setOnClickListener {
            startActivity(Intent(requireContext(), SettingsActivity::class.java))
        }

        bypassSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setBypass(isChecked, BypassMethod.SOFTWARE_SPOOF)
        }

        testModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            requireContext().getSharedPreferences("beidou_region", 0).edit().putBoolean("test_mode_prefs", isChecked).apply()
            viewModel.saveTestModeSync(requireContext(), isChecked)
            updateStatus()
        }

        lifecycleScope.launch {
            viewModel.signalInfo.collect { info ->
                if (info != null) {
                    signalText.text = "PRN ${info.satelliteId} SNR %.1f dB El %.1f Az %.1f Q=%s".format(info.snrDb, info.elevationDeg, info.azimuthDeg, info.signalQuality)
                }
            }
        }
        lifecycleScope.launch {
            viewModel.searchStatus.collect { status ->
                statusText.text = viewModel.getStatusText() + "\nSearch: $status\nConn: ${viewModel.connectionState.value} Cap: mode=${viewModel.capability.value.searchMode}"
            }
        }
        lifecycleScope.launch {
            viewModel.bypassEnabled.collect { enabled ->
                if (bypassSwitch.isChecked != enabled) bypassSwitch.isChecked = enabled
            }
        }
        lifecycleScope.launch {
            viewModel.testMode.collect { enabled ->
                if (testModeSwitch.isChecked != enabled) testModeSwitch.isChecked = enabled
            }
        }

        updateStatus()
    }

    private fun updateStatus() {
        val bypass = view?.findViewById<SwitchMaterial>(R.id.bypassSwitch)?.isChecked ?: false
        statusText.text = viewModel.getStatusText()
    }

    private fun requestPerms() {
        val perms = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_PHONE_STATE
        )
        if (perms.any { ActivityCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(requireActivity(), perms, 1001)
        }
    }
}
