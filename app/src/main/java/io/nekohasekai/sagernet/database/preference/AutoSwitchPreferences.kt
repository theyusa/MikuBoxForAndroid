package io.nekohasekai.sagernet.database.preference

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import io.nekohasekai.sagernet.SagerNet

object AutoSwitchPreferences {
    
    private val prefs: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(SagerNet.application)
    }
    
    val autoSwitchEnabled: Boolean
        get() = prefs.getBoolean("auto_switch_enabled", false)
    
    val autoSwitchCheckInterval: Int
        get() {
            val value = try {
                prefs.getString("auto_switch_check_interval", "30")?.toIntOrNull() ?: 30
            } catch (e: Exception) {
                30
            }
            return if (value < 10) 30 else value
        }
    
    val autoSwitchMaxFailures: Int
        get() {
            val value = try {
                prefs.getString("auto_switch_max_failures", "3")?.toIntOrNull() ?: 3
            } catch (e: Exception) {
                3
            }
            return when {
                value < 1 -> 3
                value > 10 -> 10
                else -> value
            }
        }
    
    val autoSwitchCheckUrl: String
        get() = prefs.getString("auto_switch_check_url", "https://www.gstatic.com/generate_204")
            ?: "https://www.gstatic.com/generate_204"
}
