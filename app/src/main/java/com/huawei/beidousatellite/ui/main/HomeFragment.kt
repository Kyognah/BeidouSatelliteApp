package com.huawei.beidousatellite.ui.main

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.huawei.beidousatellite.R
import com.huawei.beidousatellite.data.region.BypassMethod
import com.huawei.beidousatellite.ui.emergency.EmergencySosActivity
import com.huawei.beidousatellite.ui.message.ComposeMessageActivity
import com.huawei.beidousatellite.ui.message.MessageHistoryActivity
import com.huawei.beidousatellite.ui.satellite.SatelliteSearchActivity
import com.huawei.beidousatellite.ui.settings.LogViewerActivity
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
    private lateinit var sendMessageButton: MaterialButton
    private lateinit var autoDetectButton: MaterialButton
    private lateinit var openOfficialButton: MaterialButton

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
        sendMessageButton = view.findViewById(R.id.sendMessageButton)
        autoDetectButton = view.findViewById(R.id.autoDetectButton)
        openOfficialButton = view.findViewById(R.id.openOfficialButton)

        requestPerms()

        val isPura70 = android.os.Build.MODEL.contains("Pura 70", true) || android.os.Build.MODEL.contains("P70", true) || android.os.Build.MODEL.contains("Pura", true)
        if (isPura70) {
            openOfficialButton.visibility = View.VISIBLE
        }

        openOfficialButton.setOnClickListener { tryOpenOfficialSatelliteMessaging() }

        searchButton.setOnClickListener { startActivity(Intent(requireContext(), SatelliteSearchActivity::class.java)) }
        sosButton.setOnClickListener { startActivity(Intent(requireContext(), EmergencySosActivity::class.java)) }
        historyButton.setOnClickListener { startActivity(Intent(requireContext(), MessageHistoryActivity::class.java)) }
        settingsButton.setOnClickListener {
            val options = arrayOf("Settings - Bypass", "View Logs", "Pura 70 Guide - Real Satellite")
            AlertDialog.Builder(requireContext()).setTitle("Settings").setItems(options) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(requireContext(), SettingsActivity::class.java))
                    1 -> startActivity(Intent(requireContext(), LogViewerActivity::class.java))
                    2 -> showPura70Guide()
                }
            }.show()
        }
        sendMessageButton.setOnClickListener { startActivity(Intent(requireContext(), ComposeMessageActivity::class.java)) }

        autoDetectButton.setOnClickListener {
            autoDetectButton.isEnabled = false
            autoDetectButton.text = "Detecting..."
            lifecycleScope.launch {
                val results = viewModel.autoDetect()
                val message = results.joinToString("\n\n") { "Method: ${it.method}\nSuccess: ${it.success}\nInfo: ${it.message}" }
                AlertDialog.Builder(requireContext()).setTitle("Auto-Detect Results").setMessage(message + "\n\n" + viewModel.getStatusText()).setPositiveButton("OK") { _, _ -> }.show()
                autoDetectButton.isEnabled = true
                autoDetectButton.text = "🔍 Auto-Detect Best Bypass Method"
                updateStatus()
            }
        }

        bypassSwitch.setOnCheckedChangeListener { _, isChecked -> viewModel.setBypass(isChecked, BypassMethod.SOFTWARE_SPOOF) }
        testModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            requireContext().getSharedPreferences("beidou_region", 0).edit().putBoolean("test_mode_prefs", isChecked).apply()
            viewModel.saveTestModeSync(requireContext(), isChecked)
            updateStatus()
        }

        lifecycleScope.launch {
            viewModel.signalInfo.collect { info ->
                if (info != null) {
                    signalText.text = "📡 PRN ${info.satelliteId} SNR %.1f dB El %.1f° Az %.1f° Q=%s".format(info.snrDb, info.elevationDeg, info.azimuthDeg, info.signalQuality)
                } else {
                    signalText.text = "No signal - برای Pura 70: تست مود خاموش + بایپس روشن"
                }
            }
        }
        lifecycleScope.launch { viewModel.searchStatus.collect { updateStatus() } }
        lifecycleScope.launch { viewModel.bypassEnabled.collect { if (bypassSwitch.isChecked != it) bypassSwitch.isChecked = it } }
        lifecycleScope.launch { viewModel.testMode.collect { if (testModeSwitch.isChecked != it) testModeSwitch.isChecked = it } }

        updateStatus()
    }

    private fun tryOpenOfficialSatelliteMessaging() {
        val intents = listOf(
            Intent().apply { setClassName("com.huawei.meetime", "com.huawei.meetime.feature.satellite.SatelliteActivity") },
            Intent().apply { setClassName("com.huawei.meetime", "com.huawei.meetime.ui.satellite.SatelliteMessageActivity") },
            Intent().apply { setClassName("com.huawei.changlian", "com.huawei.changlian.ui.satellite.SatelliteActivity") },
            Intent("com.huawei.action.SATELLITE_MESSAGING"),
            Intent("com.huawei.meetime.action.SATELLITE_MESSAGE"),
            Intent().apply { setClassName("com.huawei.settings", "com.huawei.settings.satellite.SatelliteNetworkSettings") }
        )
        var opened = false
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                opened = true
                Toast.makeText(requireContext(), "Opened: ${intent.component ?: intent.action}", Toast.LENGTH_SHORT).show()
                break
            } catch (_: Exception) {}
        }
        if (!opened) showPura70Guide()
    }

    private fun showPura70Guide() {
        val guide = """
🛰️ Pura 70 راهنمای ارسال واقعی:

1. تو همین اپ: Bypass روشن + Test Mode خاموش + Auto-Detect

2. برو تنظیمات گوشی:
   Settings → Satellite network → Beidou satellite SMS
   یا Satellite network → MeeTime Beidou satellite messages

3. تو Changlian (MeeTime) اپ نسخه 2.1.42.664+:
   Messages → Beidou satellite message service account
   مخاطب + متن آزاد (Pura 70 تا 140 کاراکتر آزاد)
   ترافیک/WiFi خاموش، فضای باز بدون مانع
   جستجوی ماهواره 6-7 ثانیه، ارسال 10 ثانیه

4. گیرنده حتی غیر هواوی SMS با لینک لوکیشن میگیره. اگر Changlian داشته باشه تصویر هم میگیره.

5. ماهی 30 پیام رایگان، بعد بسته China Mobile KTBD به 10086

⚠️ چرا اپ ما مستقیم ماهواره نمیفرسته؟
- سرویس com.huawei.hwvoipservice فقط signatureOrSystem
- فقط اپ سیستمی MeeTime میتونه
- اپ ما فقط منطقه رو CN میکنه تا MeeTime خارج چین کار کنه (Reddit Pura 70 Ultra آمریکا)

برای تست بدون ماهواره واقعی: ✉️ ارسال پیام → شماره دوست (نه خودت) → Satellite Search → TRACKING → Send → SMS fallback با [BeiDou Test] میره
        """.trimIndent()
        AlertDialog.Builder(requireContext()).setTitle("Pura 70 Guide").setMessage(guide).setPositiveButton("فهمیدم") { _, _ -> }.setNeutralButton("باز کردن تنظیمات") { _, _ ->
            try {
                val intent = Intent().apply { setClassName("com.huawei.settings", "com.huawei.settings.satellite.SatelliteNetworkSettings") }
                startActivity(intent)
            } catch (_: Exception) {
                startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
            }
        }.show()
    }

    private fun updateStatus() {
        val isPura70 = android.os.Build.MODEL.contains("Pura 70", true) || android.os.Build.MODEL.contains("P70", true)
        val extra = if (isPura70) "\n\n📱 Pura 70 detected - Use 'Open Official' for real satellite. Our app SMS fallback is for testing." else ""
        statusText.text = viewModel.getStatusText() + extra
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
