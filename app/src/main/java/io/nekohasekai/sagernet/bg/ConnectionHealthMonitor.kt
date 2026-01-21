package io.nekohasekai.sagernet.bg

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
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
    private var allAvailableProfiles = listOf<ProxyEntity>()
    private var currentProfileIndex = 0
    
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
        
        // Tüm kullanılabilir profilleri yükle
        loadAllAvailableProfiles()
        
        scheduleNextCheck()
    }
    
    fun stopMonitoring() {
        Logs.d("$TAG: Stopping connection monitoring")
        isMonitoring = false
        handler.removeCallbacksAndMessages(null)
        failureCount = 0
        allAvailableProfiles = emptyList()
    }
    
    private fun loadAllAvailableProfiles() {
        try {
            // TÜM profilleri al (tüm gruplardan/aboneliklerden)
            allAvailableProfiles = SagerDatabase.proxyDao.all().filter { profile ->
                // Sadece aktif ve kullanılabilir profilleri filtrele
                try {
                    // Boş veya geçersiz profilleri filtrele
                    profile.id > 0 && 
                    !profile.name.isNullOrBlank() &&
                    profile.type != null
                } catch (e: Exception) {
                    Logs.e("$TAG: Error filtering profile ${profile.id}: ${e.message}")
                    false
                }
            }
            
            // Mevcut profilin indexini bul
            currentProfileIndex = allAvailableProfiles.indexOfFirst { it.id == currentProfileId }
            if (currentProfileIndex == -1) currentProfileIndex = 0
            
            Logs.i("$TAG: Loaded ${allAvailableProfiles.size} available profiles")
            
        } catch (e: Exception) {
            Logs.e("$TAG: Error loading profiles: ${e.message}", e)
            allAvailableProfiles = emptyList()
        }
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
                // Profil listesini yeniden yükle (güncel olmayabilir)
                loadAllAvailableProfiles()
                
                if (allAvailableProfiles.isEmpty()) {
                    Logs.e("$TAG: No profiles available for switching")
                    showToast("Kullanılabilir başka sunucu yok")
                    return@withContext
                }
                
                if (allAvailableProfiles.size == 1) {
                    Logs.w("$TAG: Only one profile available, cannot switch")
                    showToast("Başka sunucu yok, değiştirilemiyor")
                    return@withContext
                }
                
                // Sonraki profili seç (circular rotation)
                currentProfileIndex = (currentProfileIndex + 1) % allAvailableProfiles.size
                val nextProfile = allAvailableProfiles[currentProfileIndex]
                
                // Güvenli isim alma
                val profileName = getProfileDisplayName(nextProfile)
                val currentProfileName = allAvailableProfiles.find { it.id == currentProfileId }?.let {
                    getProfileDisplayName(it)
                } ?: "Unknown"
                
                Logs.i("$TAG: Switching from '$currentProfileName' (${currentProfileId}) to '$profileName' (${nextProfile.id})")
                
                // Servisi durdur
                try {
                    BaseService.stopService()
                } catch (e: Exception) {
                    Logs.e("$TAG: Error stopping service: ${e.message}")
                }
                
                delay(1500) // 1.5 saniye bekle
                
                // Yeni profili seç
                try {
                    DataStore.selectedProxy = nextProfile.id
                    currentProfileId = nextProfile.id
                } catch (e: Exception) {
                    Logs.e("$TAG: Error setting selected proxy: ${e.message}")
                    // Alternatif yöntem
                    SagerDatabase.selectedProxy = nextProfile.id
                    currentProfileId = nextProfile.id
                }
                
                // Servisi yeniden başlat
                try {
                    BaseService.startService()
                } catch (e: Exception) {
                    Logs.e("$TAG: Error starting service: ${e.message}")
                }
                
                delay(2000) // 2 saniye bekle servis başlasın
                
                // Hata sayacını sıfırla
                failureCount = 0
                
                // Bildirim göster
                showToast("Sunucu değiştirildi: $profileName")
                
            } catch (e: Exception) {
                Logs.e("$TAG: Error switching profile: ${e.message}", e)
                showToast("Sunucu değiştirme hatası")
                
                // Hata olsa bile listeyi yeniden yükle
                try {
                    loadAllAvailableProfiles()
                } catch (e2: Exception) {
                    Logs.e("$TAG: Error reloading profiles: ${e2.message}")
                }
            }
        }
    }
    
    /**
     * Profil için güvenli görünen isim döndürür
     * Öncelik sırası: name -> displayName() -> remarks -> "Server #id"
     */
    private fun getProfileDisplayName(profile: ProxyEntity): String {
        return try {
            when {
                // Önce name alanını kontrol et
                !profile.name.isNullOrBlank() -> profile.name!!
                
                // displayName() fonksiyonu varsa kullan
                else -> {
                    try {
                        val displayName = profile.displayName()
                        if (!displayName.isNullOrBlank()) displayName else "Server #${profile.id}"
                    } catch (e: Exception) {
                        // displayName() yoksa veya hata verirse remarks'i dene
                        try {
                            if (!profile.remarks.isNullOrBlank()) {
                                profile.remarks!!
                            } else {
                                "Server #${profile.id}"
                            }
                        } catch (e2: Exception) {
                            "Server #${profile.id}"
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Logs.e("$TAG: Error getting profile display name: ${e.message}")
            "Server #${profile.id}"
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
