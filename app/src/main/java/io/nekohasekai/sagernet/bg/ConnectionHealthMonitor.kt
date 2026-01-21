package io.nekohasekai.sagernet.bg

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.preference.AutoSwitchPreferences
import io.nekohasekai.sagernet.ktx.Logs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class ConnectionHealthMonitor(private val context: Context) {
    
    private val handler = Handler(Looper.getMainLooper())
    private var isMonitoring = false
    private var failureCount = 0
    private var currentProfileId = 0L
    
    companion object {
        private const val TAG = "ConnectionHealthMonitor"
        private const val TIMEOUT = 5000 // 5 saniye timeout
    }
    
    fun startMonitoring(profileId: Long) {
        if (!AutoSwitchPreferences.autoSwitchEnabled) {
            Logs.d("$TAG: Auto switch disabled, not starting monitor")
            return
        }
        
        Logs.d("$TAG: Starting connection monitoring for profile $profileId")
        currentProfileId = profileId
        isMonitoring = true
        failureCount = 0
        scheduleNextCheck()
    }
    
    fun stopMonitoring() {
        Logs.d("$TAG: Stopping connection monitoring")
        isMonitoring = false
        handler.removeCallbacksAndMessages(null)
        failureCount = 0
    }
    
    private fun scheduleNextCheck() {
        if (!isMonitoring) return
        
        val intervalSeconds = AutoSwitchPreferences.autoSwitchCheckInterval
        val intervalMillis = (intervalSeconds * 1000).toLong()
        
        handler.postDelayed({
            checkConnection()
        }, intervalMillis)
    }
    
    private fun checkConnection() {
        if (!isMonitoring) return
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val checkUrl = AutoSwitchPreferences.autoSwitchCheckUrl
                val isHealthy = pingServer(checkUrl)
                
                if (isHealthy) {
                    Logs.d("$TAG: Connection check passed")
                    failureCount = 0
                } else {
                    failureCount++
                    val maxFailures = AutoSwitchPreferences.autoSwitchMaxFailures
                    Logs.w("$TAG: Connection check failed ($failureCount/$maxFailures)")
                    
                    if (failureCount >= maxFailures) {
                        Logs.w("$TAG: Max failures reached, switching to next profile")
                        switchToNextProfile()
                    }
                }
            } catch (e: Exception) {
                failureCount++
                val maxFailures = AutoSwitchPreferences.autoSwitchMaxFailures
                Logs.e("$TAG: Connection check error: ${e.message}", e)
                
                if (failureCount >= maxFailures) {
                    Logs.w("$TAG: Max failures reached due to exception, switching to next profile")
                    switchToNextProfile()
                }
            } finally {
                scheduleNextCheck()
            }
        }
    }
    
    private suspend fun pingServer(urlString: String): Boolean {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            
            connection.connectTimeout = TIMEOUT
            connection.readTimeout = TIMEOUT
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "MikuBox/1.0")
            
            connection.connect()
            val responseCode = connection.responseCode
            connection.disconnect()
            
            // 204 (No Content) veya 200 (OK) başarılı sayılır
            val success = responseCode == 204 || responseCode == 200
            Logs.d("$TAG: Ping response code: $responseCode, success: $success")
            success
            
        } catch (e: Exception) {
            Logs.e("$TAG: Ping failed: ${e.message}")
            false
        }
    }
    
    private suspend fun switchToNextProfile() {
        withContext(Dispatchers.Main) {
            try {
                // Mevcut profili getir
                val currentProfile = SagerDatabase.proxyDao.getById(currentProfileId)
                if (currentProfile == null) {
                    Logs.e("$TAG: Current profile not found")
                    return@withContext
                }
                
                // Aynı gruptaki tüm profilleri al
                val groupId = currentProfile.groupId
                val profiles = SagerDatabase.proxyDao.getByGroup(groupId)
                
                if (profiles.isEmpty()) {
                    Logs.e("$TAG: No profiles found in group")
                    return@withContext
                }
                
                if (profiles.size == 1) {
                    Logs.w("$TAG: Only one profile in group, cannot switch")
                    showToast("Başka sunucu yok, değiştirilemiyor")
                    return@withContext
                }
                
                // Mevcut profil indexini bul
                val currentIndex = profiles.indexOfFirst { it.id == currentProfileId }
                
                if (currentIndex == -1) {
                    Logs.e("$TAG: Current profile not found in list")
                    return@withContext
                }
                
                // Sonraki profili seç (circular: son profildeyse başa dön)
                val nextIndex = (currentIndex + 1) % profiles.size
                val nextProfile = profiles[nextIndex]
                
                Logs.i("$TAG: Switching from profile ${currentProfileId} to ${nextProfile.id} (${nextProfile.name})")
                
                // Servisi durdur
                BaseService.stopService()
                delay(1500) // 1.5 saniye bekle
                
                // Yeni profili seç
                SagerDatabase.selectedProxy = nextProfile.id
                currentProfileId = nextProfile.id
                
                // Servisi yeniden başlat
                BaseService.startService()
                delay(2000) // 2 saniye bekle servis başlasın
                
                // Hata sayacını sıfırla
                failureCount = 0
                
                // Bildirim göster
                showToast("Sunucu değiştirildi: ${nextProfile.name}")
                
            } catch (e: Exception) {
                Logs.e("$TAG: Error switching profile: ${e.message}", e)
                showToast("Sunucu değiştirme hatası: ${e.message}")
            }
        }
    }
    
    private fun showToast(message: String) {
        handler.post {
            try {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Logs.e("$TAG: Failed to show toast: ${e.message}")
            }
        }
    }
}
