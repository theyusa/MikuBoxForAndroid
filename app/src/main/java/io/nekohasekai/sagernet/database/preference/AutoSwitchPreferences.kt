package io.nekohasekai.sagernet.database.preference

import io.nekohasekai.sagernet.database.SagerDatabase

object AutoSwitchPreferences {
    
    private val kvPairDao get() = SagerDatabase.kvPairDao
    
    var autoSwitchEnabled: Boolean
        get() = kvPairDao.get("auto_switch_enabled")?.toBoolean() ?: false
        set(value) {
            kvPairDao.put("auto_switch_enabled", value.toString())
        }
    
    var autoSwitchCheckInterval: Int
        get() {
            val value = kvPairDao.get("auto_switch_check_interval")?.toIntOrNull() ?: 30
            return if (value < 10) 30 else value // Minimum 10 saniye
        }
        set(value) {
            val safeValue = if (value < 10) 30 else value
            kvPairDao.put("auto_switch_check_interval", safeValue.toString())
        }
    
    var autoSwitchMaxFailures: Int
        get() {
            val value = kvPairDao.get("auto_switch_max_failures")?.toIntOrNull() ?: 3
            return if (value < 1 || value > 10) 3 else value // 1-10 arası
        }
        set(value) {
            val safeValue = when {
                value < 1 -> 3
                value > 10 -> 10
                else -> value
            }
            kvPairDao.put("auto_switch_max_failures", safeValue.toString())
        }
    
    var autoSwitchCheckUrl: String
        get() = kvPairDao.get("auto_switch_check_url") 
            ?: "https://www.gstatic.com/generate_204"
        set(value) {
            kvPairDao.put("auto_switch_check_url", value)
        }
}
