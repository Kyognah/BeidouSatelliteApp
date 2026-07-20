package com.huawei.beidousatellite.data.region

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.huawei.beidousatellite.util.SatelliteLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "region_bypass")

enum class BypassMethod {
    SOFTWARE_SPOOF,
    HMS_REFLECTION,
    ADB_SETTINGS,
    MAGISK,
    TEST_MODE
}

data class BypassAttempt(
    val method: BypassMethod,
    val success: Boolean,
    val message: String
)

@Singleton
class RegionBypassManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: SatelliteLogger
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("beidou_region", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "RegionBypass"
        val KEY_BYPASS_ENABLED = booleanPreferencesKey("bypass_enabled")
        val KEY_BYPASS_METHOD = stringPreferencesKey("bypass_method")
        val KEY_TEST_MODE = booleanPreferencesKey("test_mode")
        val KEY_ORIGINAL_REGION = stringPreferencesKey("original_region")
        val KEY_LAST_SUCCESS_METHOD = stringPreferencesKey("last_success_method")
        val KEY_AUTO_DETECTED = booleanPreferencesKey("auto_detected")
    }

    val bypassEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_BYPASS_ENABLED] ?: false }
    val bypassMethodFlow: Flow<String> = context.dataStore.data.map { it[KEY_BYPASS_METHOD] ?: BypassMethod.SOFTWARE_SPOOF.name }
    val testModeFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_TEST_MODE] ?: false }

    val autoDetectedFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_DETECTED] ?: false }

    suspend fun setBypassEnabled(enabled: Boolean, method: BypassMethod = BypassMethod.SOFTWARE_SPOOF) {
        context.dataStore.edit { pref ->
            pref[KEY_BYPASS_ENABLED] = enabled
            pref[KEY_BYPASS_METHOD] = method.name
        }
        prefs.edit().putBoolean("bypass_enabled", enabled).putString("bypass_method", method.name).apply()
        if (enabled) applyBypass(method) else restoreRegion()
        logger.i(TAG, "Bypass enabled=$enabled method=$method")
    }

    suspend fun setTestMode(enabled: Boolean) {
        context.dataStore.edit { it[KEY_TEST_MODE] = enabled }
        prefs.edit().putBoolean("test_mode_prefs", enabled).apply()
        logger.i(TAG, "TestMode=$enabled")
    }

    // Auto-detect best method
    suspend fun autoDetectAndApply(): List<BypassAttempt> {
        val results = mutableListOf<BypassAttempt>()
        logger.i(TAG, "Auto-detecting bypass method...")

        // Try order: Software Spoof (always works), HMS Reflection, ADB, Magisk, Test Mode
        val methods = listOf(
            BypassMethod.SOFTWARE_SPOOF,
            BypassMethod.HMS_REFLECTION,
            BypassMethod.ADB_SETTINGS,
            BypassMethod.MAGISK,
            BypassMethod.TEST_MODE
        )

        for (method in methods) {
            val attempt = tryMethod(method)
            results.add(attempt)
            if (attempt.success) {
                // Apply this successful method
                setBypassEnabled(true, method)
                context.dataStore.edit { 
                    it[KEY_LAST_SUCCESS_METHOD] = method.name
                    it[KEY_AUTO_DETECTED] = true
                }
                prefs.edit().putString("last_success_method", method.name)
                    .putBoolean("auto_detected", true).apply()
                logger.i(TAG, "Auto-detect success with $method")
                break
            }
        }

        // If none worked except test mode, enable test mode as fallback
        if (results.none { it.success && it.method != BypassMethod.TEST_MODE }) {
            val testAttempt = tryMethod(BypassMethod.TEST_MODE)
            results.add(testAttempt)
            if (testAttempt.success) {
                setBypassEnabled(true, BypassMethod.TEST_MODE)
                setTestMode(true)
            }
        }

        return results
    }

    private fun tryMethod(method: BypassMethod): BypassAttempt {
        return try {
            when (method) {
                BypassMethod.SOFTWARE_SPOOF -> {
                    applySoftwareSpoof()
                    val ok = prefs.getString("spoofed_region", null) == "CN"
                    BypassAttempt(method, ok, if (ok) "Software spoof applied - Locale+Prefs modified" else "Failed to set prefs")
                }
                BypassMethod.HMS_REFLECTION -> {
                    val success = applyHmsReflectionWithResult()
                    BypassAttempt(method, success, if (success) "HMS reflection found and patched ${getLastHmsClass()}" else "No HMS class found or not rooted")
                }
                BypassMethod.ADB_SETTINGS -> {
                    applyAdbSettings()
                    val ok = prefs.getBoolean("adb_settings_applied", false)
                    BypassAttempt(method, ok, if (ok) "ADB settings executed" else "ADB exec failed - need WRITE_SECURE_SETTINGS or root")
                }
                BypassMethod.MAGISK -> {
                    val ok = applyMagiskWithResult()
                    BypassAttempt(method, ok, if (ok) "Magisk props set via su" else "Root not available")
                }
                BypassMethod.TEST_MODE -> {
                    applyTestMode()
                    BypassAttempt(method, true, "Test mode enabled - satellite will be simulated")
                }
            }
        } catch (e: Exception) {
            BypassAttempt(method, false, "Exception: ${e.message}")
        }
    }

    private var lastHmsClass: String = ""
    private fun getLastHmsClass() = lastHmsClass

    private fun applySoftwareSpoof() {
        try {
            val meetimePrefs = context.getSharedPreferences("com.huawei.meetime", Context.MODE_PRIVATE)
            meetimePrefs.edit()
                .putString("huawei_id_region", "CN")
                .putString("region", "CN")
                .putString("last_region", "CN")
                .putBoolean("region_spoofed", true)
                .apply()

            val locale = Locale("zh", "CN")
            Locale.setDefault(locale)
            prefs.edit()
                .putString("spoofed_region", "CN")
                .putString("spoofed_locale", "zh_CN")
                .putString("original_locale", Locale.getDefault().toString())
                .apply()

            logger.hms("Software spoof applied: CN")
        } catch (e: Exception) {
            logger.e(TAG, "Software spoof failed", e)
        }
    }

    private fun applyHmsReflection(): Boolean = applyHmsReflectionWithResult()

    private fun applyHmsReflectionWithResult(): Boolean {
        var success = false
        try {
            val possibleClasses = listOf(
                "com.huawei.hms.framework.common.RegionManager",
                "com.huawei.hms.api.ConnectionManager",
                "com.huawei.hms.core.aidl.AbstractService",
                "com.huawei.hms.utils.RegionUtil",
                "com.huawei.hms.common.internal.HmsClient",
                "com.huawei.hms.api.HuaweiApiClient"
            )
            for (clsName in possibleClasses) {
                try {
                    val cls = Class.forName(clsName)
                    lastHmsClass = clsName
                    Log.d(TAG, "Found HMS class $clsName")
                    cls.declaredFields.forEach { field ->
                        if (field.name.contains("region", true) || field.name.contains("country", true)) {
                            try {
                                field.isAccessible = true
                                if (java.lang.reflect.Modifier.isStatic(field.modifiers)) {
                                    field.set(null, "CN")
                                    success = true
                                    logger.hms("HMS reflection set $clsName.${field.name}=CN")
                                }
                            } catch (ignored: Exception) {}
                        }
                    }
                    if (success) break
                } catch (ignored: ClassNotFoundException) {}
            }
            if (!success) {
                logger.w(TAG, "HMS reflection no target found")
            }
        } catch (e: Exception) {
            logger.e(TAG, "HMS reflection failed", e)
        }
        return success
    }

    private fun applyAdbSettings() {
        try {
            Runtime.getRuntime().exec(arrayOf("settings", "put", "global", "huawei_id_region", "CN")).waitFor()
            Runtime.getRuntime().exec(arrayOf("settings", "put", "global", "hw_id_region", "CN")).waitFor()
            prefs.edit().putBoolean("adb_settings_applied", true).apply()
            logger.hms("ADB settings applied")
        } catch (e: Exception) {
            logger.e(TAG, "ADB settings failed", e)
        }
    }

    private fun applyMagisk(): Boolean = applyMagiskWithResult()

    private fun applyMagiskWithResult(): Boolean {
        return try {
            val p1 = Runtime.getRuntime().exec(arrayOf("su", "-c", "setprop ro.huawei.region CN"))
            p1.waitFor()
            val p2 = Runtime.getRuntime().exec(arrayOf("su", "-c", "setprop persist.sys.country CN"))
            p2.waitFor()
            prefs.edit().putBoolean("magisk_applied", true).apply()
            logger.hms("Magisk props set, exit codes ${p1.exitValue()}, ${p2.exitValue()}")
            p1.exitValue() == 0 || p2.exitValue() == 0
        } catch (e: Exception) {
            logger.e(TAG, "Magisk failed", e)
            false
        }
    }

    private fun applyTestMode() {
        prefs.edit().putBoolean("test_mode_prefs", true).apply()
        logger.i(TAG, "Test mode applied - simulating satellite")
    }

    private fun applyBypass(method: BypassMethod) {
        when (method) {
            BypassMethod.SOFTWARE_SPOOF -> applySoftwareSpoof()
            BypassMethod.HMS_REFLECTION -> applyHmsReflection()
            BypassMethod.ADB_SETTINGS -> applyAdbSettings()
            BypassMethod.MAGISK -> applyMagisk()
            BypassMethod.TEST_MODE -> applyTestMode()
        }
    }

    private fun restoreRegion() {
        try {
            prefs.edit().remove("spoofed_region").remove("spoofed_locale").remove("test_mode_prefs").apply()
            val meetimePrefs = context.getSharedPreferences("com.huawei.meetime", Context.MODE_PRIVATE)
            meetimePrefs.edit().remove("huawei_id_region").remove("region").remove("region_spoofed").apply()
            logger.i(TAG, "Restored region")
        } catch (e: Exception) {
            logger.e(TAG, "Restore failed", e)
        }
    }

    fun isBypassEnabledSync(): Boolean = prefs.getBoolean("bypass_enabled", false)
    fun getBypassMethodSync(): BypassMethod {
        val name = prefs.getString("bypass_method", BypassMethod.SOFTWARE_SPOOF.name)
        return try { BypassMethod.valueOf(name!!) } catch (_: Exception) { BypassMethod.SOFTWARE_SPOOF }
    }

    fun isTestModeSync(): Boolean = prefs.getBoolean("test_mode_prefs", false)

    fun isSatelliteSupported(): Boolean {
        if (prefs.getBoolean("bypass_enabled", false)) return true
        if (prefs.getBoolean("test_mode_prefs", false)) return true
        val region = getCurrentRegion()
        return region == "CN" || region.equals("cn", true)
    }

    fun getCurrentRegion(): String {
        return prefs.getString("spoofed_region", null)
            ?: Locale.getDefault().country
            ?: "UNKNOWN"
    }

    fun getBypassStatus(): String {
        val lastMethod = prefs.getString("last_success_method", "none")
        val autoDetected = prefs.getBoolean("auto_detected", false)
        return buildString {
            append("🛰️ Satellite Supported: ${isSatelliteSupported()}\n")
            append("✅ Bypass Enabled: ${isBypassEnabledSync()}\n")
            append("🔧 Method: ${getBypassMethodSync()} (last success: $lastMethod)\n")
            append("🧪 Test Mode: ${isTestModeSync()}\n")
            append("🌍 Current Region: ${getCurrentRegion()}\n")
            append("📱 Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("🤖 Android: ${Build.VERSION.RELEASE} SDK ${Build.VERSION.SDK_INT}\n")
            append("🔍 Auto-Detected: $autoDetected\n")
            append("\nMethods from MeeTime reverse engineering:\n")
            append("- Software Spoof: modifies SharedPreferences + Locale\n")
            append("- HMS Reflection: patches HMS RegionManager via reflection (needs root)\n")
            append("- ADB Settings: settings put global (needs WRITE_SECURE_SETTINGS)\n")
            append("- Magisk: setprop via su\n")
            append("- Test Mode: simulates satellite PRN 1-63")
        }
    }

    fun getDetailedStatus(): String {
        val attempts = prefs.getString("last_attempts", "Run auto-detect to see")
        return getBypassStatus() + "\n\nLast attempts:\n$attempts"
    }
}
