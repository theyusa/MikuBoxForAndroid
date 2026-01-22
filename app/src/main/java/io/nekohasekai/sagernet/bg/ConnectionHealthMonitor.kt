package io.nekohasekai.sagernet.bg

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.nekohasekai.sagernet.database.preference.AutoSwitchPreferences
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

class ConnectionHealthMonitor(
    private val context: Context,
    private val onSwitchRequest: () -> Unit
) {
    private var monitorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var failureCount = 0

    fun startMonitoring() {
        if (!AutoSwitchPreferences.enabled) return
        
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (isActive) {
                delay(AutoSwitchPreferences.checkInterval * 1000L)
                
                val isHealthy = performCheck()
                
                if (isHealthy) {
                    failureCount = 0
                } else {
                    failureCount++
                    if (failureCount >= AutoSwitchPreferences.maxFailures) {
                        failureCount = 0
                        withContext(Dispatchers.Main) {
                            onSwitchRequest()
                        }
                    }
                }
            }
        }
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
    }

    private fun performCheck(): Boolean {
        // 1. Fiziksel Ağ Kontrolü
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false

        // 2. TCP Ping Kontrolü (Daha hızlı ve güvenilir)
        return try {
            val socket = Socket()
            val host = URL(AutoSwitchPreferences.checkUrl).host
            socket.connect(InetSocketAddress(host, 80), 5000)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }
}
