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
    }

    val bypassEnabledFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_BYPASS_ENABLED] ?: false }
    val bypassMethodFlow: Flow<String> = context.dataStore.data.map { it[KEY_BYPASS_METHOD] ?: BypassMethod.SOFTWARE_SPOOF.name }
    val testModeFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_TEST_MODE] ?: false }

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
        logger.i(TAG, "TestMode=$enabled")
    }

    fun isTestMode(): Boolean = prefs.getBoolean("test_mode_prefs", false) // fallback, will read from datastore async elsewhere
    // Synchronous check for quick UI
    fun isBypassEnabledSync(): Boolean = prefs.getBoolean("bypass_enabled", false)
    fun getBypassMethodSync(): BypassMethod {
        val name = prefs.getString("bypass_method", BypassMethod.SOFTWARE_SPOOF.name)
        return try { BypassMethod.valueOf(name!!) } catch (_: Exception) { BypassMethod.SOFTWARE_SPOOF }
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

    private fun applySoftwareSpoof() {
        try {
            // MeeTime method: modify app-internal SharedPreferences and Locale
            // MeeTime stores region in SharedPreferences "com.huawei.meetime" and "huawei_id_region"
            val meetimePrefs = context.getSharedPreferences("com.huawei.meetime", Context.MODE_PRIVATE)
            meetimePrefs.edit()
                .putString("huawei_id_region", "CN")
                .putString("region", "CN")
                .putString("last_region", "CN")
                .putBoolean("region_spoofed", true)
                .apply()

            // Also spoof Locale to CN
            val locale = Locale("zh", "CN")
            Locale.setDefault(locale)
            val config = context.resources.configuration
            config.setLocale(locale)
            // For Android 13+ store original
            val orig = Locale.getDefault().toString()
            prefs.edit().putString("original_locale", orig).apply()

            // Store in our own prefs
            prefs.edit()
                .putString("spoofed_region", "CN")
                .putString("spoofed_locale", "zh_CN")
                .apply()

            logger.hms("Software spoof applied: CN")
        } catch (e: Exception) {
            logger.e(TAG, "Software spoof failed", e)
        }
    }

    private fun applyHmsReflection() {
        try {
            // MeeTime uses reflection to override HMS Core region
            // Attempt to find com.huawei.hms.framework.common.RegionManager or similar
            val possibleClasses = listOf(
                "com.huawei.hms.framework.common.RegionManager",
                "com.huawei.hms.api.ConnectionManager",
                "com.huawei.hms.core.aidl.AbstractService",
                "com.huawei.hms.utils.RegionUtil"
            )
            var success = false
            for (clsName in possibleClasses) {
                try {
                    val cls = Class.forName(clsName)
                    Log.d(TAG, "Found HMS class $clsName: $cls")
                    // Try to set region field via reflection
                    cls.declaredFields.forEach { field ->
                        if (field.name.contains("region", true) || field.name.contains("country", true)) {
                            try {
                                field.isAccessible = true
                                // If static, set to CN
                                if (java.lang.reflect.Modifier.isStatic(field.modifiers)) {
                                    field.set(null, "CN")
                                    success = true
                                    logger.hms("HMS reflection set $clsName.${field.name}=CN")
                                }
                            } catch (ignored: Exception) {}
                        }
                    }
                } catch (ignored: ClassNotFoundException) {}
            }
            if (!success) {
                logger.w(TAG, "HMS reflection no target found, falling back to software spoof")
                applySoftwareSpoof()
            }
        } catch (e: Exception) {
            logger.e(TAG, "HMS reflection failed", e)
            applySoftwareSpoof()
        }
    }

    private fun applyAdbSettings() {
        try {
            // MeeTime debug method: adb shell settings put global huawei_id_region CN
            // Requires WRITE_SECURE_SETTINGS (system) or root
            Runtime.getRuntime().exec(arrayOf("settings", "put", "global", "huawei_id_region", "CN"))
            Runtime.getRuntime().exec(arrayOf("settings", "put", "global", "hw_id_region", "CN"))
            Runtime.getRuntime().exec(arrayOf("settings", "put", "global", "com.huawei.hwvoipservice.region", "CN"))
            prefs.edit().putBoolean("adb_settings_applied", true).apply()
            logger.hms("ADB settings applied")
        } catch (e: Exception) {
            logger.e(TAG, "ADB settings failed", e)
        }
    }

    private fun applyMagisk() {
        // Placeholder for Magisk module - would need root to modify /system/etc/region.xml or props
        try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "setprop ro.huawei.region CN"))
            Runtime.getRuntime().exec(arrayOf("su", "-c", "setprop persist.sys.country CN"))
            logger.hms("Magisk props set")
        } catch (e: Exception) {
            logger.e(TAG, "Magisk failed", e)
        }
    }

    private fun applyTestMode() {
        prefs.edit().putBoolean("test_mode_prefs", true).apply()
        logger.i(TAG, "Test mode applied - simulating satellite")
    }

    private fun restoreRegion() {
        try {
            val orig = prefs.getString("original_locale", null)
            logger.i(TAG, "Restoring region, original=$orig")
            prefs.edit().remove("spoofed_region").remove("spoofed_locale").apply()
            // Clear meetime spoof
            val meetimePrefs = context.getSharedPreferences("com.huawei.meetime", Context.MODE_PRIVATE)
            meetimePrefs.edit().remove("huawei_id_region").remove("region").remove("region_spoofed").apply()
        } catch (e: Exception) {
            logger.e(TAG, "Restore failed", e)
        }
    }

    fun isSatelliteSupported(): Boolean {
        // If bypass enabled or test mode, report supported
        if (prefs.getBoolean("bypass_enabled", false)) return true
        if (prefs.getBoolean("test_mode_prefs", false)) return true
        // Check actual region - if CN, supported
        val region = getCurrentRegion()
        return region == "CN" || region == "cn"
    }

    fun getCurrentRegion(): String {
        return prefs.getString("spoofed_region", null)
            ?: Locale.getDefault().country
            ?: "UNKNOWN"
    }

    fun getBypassStatus(): String {
        return buildString {
            append("Enabled: ${isBypassEnabledSync()}\n")
            append("Method: ${getBypassMethodSync()}\n")
            append("Current Region: ${getCurrentRegion()}\n")
            append("Satellite Supported: ${isSatelliteSupported()}\n")
            append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("Android: ${Build.VERSION.RELEASE} SDK ${Build.VERSION.SDK_INT}\n")
        }
    }
}
