package io.nekohasekai.sagernet.bg

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.annotation.MainThread
import io.nekohasekai.sagernet.Action
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.aidl.ISagerNetService
import io.nekohasekai.sagernet.aidl.ISagerNetServiceCallback
import io.nekohasekai.sagernet.aidl.TrafficStats
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.ktx.*
import io.nekohasekai.sagernet.utils.DefaultNetworkListener
import io.nekohasekai.sagernet.database.preference.AutoSwitchPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import libcore.Libcore
import java.io.File
import java.net.UnknownHostException

abstract class BaseService : VpnService(), LocalDnsService.Interface {

    companion object {
        private const val TAG = "BaseService"

        var serviceClass: Class<out BaseService>? = null

        @MainThread
        fun startService() {
            if (serviceClass == null) {
                Logs.e("BaseService.startService() called before initialization")
                return
            }
            SagerNet.application.startService(Intent(SagerNet.application, serviceClass))
        }

        @MainThread
        fun stopService() {
            if (serviceClass == null) {
                Logs.e("BaseService.stopService() called before initialization")
                return
            }
            SagerNet.application.stopService(Intent(SagerNet.application, serviceClass))
        }
    }

    enum class State(val canStop: Boolean = false) {
        /**
         * Idle state is only used by UI and will never be returned by BaseService.
         */
        Idle,
        Connecting(true),
        Connected(true),
        Stopping,
        Stopped,
    }

    interface ExpectedException

    class Data internal constructor(val proxy: ProxyEntity) {
        val profile = proxy
    }

    lateinit var data: ProxyInstance
    var state = State.Idle
    
    // Health monitor for automatic server switching
    private var healthMonitor: ConnectionHealthMonitor? = null

    interface TrafficListener {
        fun onTrafficUpdated(profileId: Long, stats: TrafficStats)
    }

    private val callbacks = mutableSetOf<ISagerNetServiceCallback>()
    private val bandwidthListeners = mutableSetOf<IBinder>() // the binder is the real identifier

    private lateinit var connectivity: DefaultNetworkListener

    override fun onBind(intent: Intent): IBinder? = when (intent.action) {
        Action.SERVICE -> binder
        else -> super.onBind(intent)
    }

    private val binder = object : ISagerNetService.Stub() {

        override fun getState(): Int = this@BaseService.state.ordinal

        override fun getTrafficStats(): TrafficStats {
            return trafficStats
        }

        override fun registerCallback(callback: ISagerNetServiceCallback) {
            if (callbacks.add(callback)) {
                if (state != State.Idle) {
                    callback.stateChanged(state.ordinal, state.name, null)
                }
            }
        }

        override fun unregisterCallback(callback: ISagerNetServiceCallback) {
            callbacks.remove(callback)
        }

        override fun urlTest(): Int {
            if (state != State.Connected) {
                error("not connected")
            }
            return Libcore.urlTestTimeout(Libcore.urlTestAsync(), 10 * 1000).toInt()
        }
    }

    var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sagernet:bgService")
        wakeLock?.acquire()

        connectivity = DefaultNetworkListener(this, true)

        // Health monitor'ı başlat
        try {
            healthMonitor = ConnectionHealthMonitor(this)
            Logs.d("$TAG: Health monitor initialized")
        } catch (e: Exception) {
            Logs.e("$TAG: Failed to initialize health monitor: ${e.message}", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action != Action.SERVICE) {
            stopRunner()
            return START_NOT_STICKY
        }

        when (state) {
            State.Idle -> {
                val profile = SagerDatabase.proxyDao.getById(DataStore.selectedProxy)
                if (profile == null) {
                    stopRunner()
                    return START_NOT_STICKY
                }
                val data = Data(profile)
                this.data = ProxyInstance(profile)
                startRunner(data)
            }
            State.Stopped -> {
                stopRunner()
            }
            else -> Logs.w("Start from unexpected state: $state")
        }

        return START_STICKY
    }

    private fun startRunner(data: Data) {
        state = State.Connecting
        Logs.i("Starting runner")
        runOnDefaultDispatcher {
            try {
                Logs.i("$TAG runner started")

                // bind
                onBind()

                // start traffic stats
                trafficStats = TrafficStats()

                // change state
                changeState(State.Connected)

            } catch (e: Throwable) {
                if (e is ExpectedException) {
                    Logs.d("Start runner: ${e.readableMessage}")
                } else {
                    Logs.w("Start runner error: ${e.readableMessage}", e)
                }

                changeState(State.Stopped, e.readableMessage)
            }
        }
    }

    private fun stopRunner(restart: Boolean = false) {
        if (state == State.Stopping || state == State.Idle) return

        Logs.i("Stopping runner")

        // change state
        changeState(State.Stopping)

        runOnDefaultDispatcher {
            try {
                // cleanup
                onUnbind()
            } catch (e: Throwable) {
                Logs.w(e)
            }

            // change state
            changeState(State.Stopped)

            // restart if needed
            if (restart) {
                startRunner(Data(SagerDatabase.proxyDao.getById(DataStore.selectedProxy)!!))
            }
        }
    }

    private var trafficStats = TrafficStats()
    private var trafficUpdated = 0L

    fun onTrafficUpdated(stats: TrafficStats) {
        trafficStats = stats

        val now = System.currentTimeMillis()
        if (now - trafficUpdated > 500) {
            trafficUpdated = now

            if (bandwidthListeners.isNotEmpty()) {
                val profileId = DataStore.selectedProxy
                for (binder in bandwidthListeners) {
                    try {
                        callbacks.firstOrNull { it.asBinder() == binder }
                            ?.onTrafficUpdated(profileId, stats)
                    } catch (e: Exception) {
                        Logs.w(e)
                    }
                }
            }
        }
    }

    fun changeState(s: State, msg: String? = null) {
        if (state == s && msg == null) return

        if (state == State.Connected) {
            // Health monitoring'i durdur
            try {
                healthMonitor?.stopMonitoring()
                Logs.d("$TAG: Stopped health monitoring")
            } catch (e: Exception) {
                Logs.e("$TAG: Failed to stop health monitoring: ${e.message}", e)
            }
        }

        state = s
        Logs.i("State changed to: $s ${msg ?: ""}")

        if (callbacks.isEmpty()) return
        for (callback in callbacks) {
            try {
                callback.stateChanged(s.ordinal, s.name, msg)
            } catch (e: Exception) {
                Logs.w(e)
            }
        }

        if (s == State.Connected) {
            // Health monitoring'i başlat
            try {
                val selectedProxy = DataStore.selectedProxy
                if (selectedProxy > 0 && AutoSwitchPreferences.autoSwitchEnabled) {
                    healthMonitor?.startMonitoring(selectedProxy)
                    Logs.d("$TAG: Started health monitoring for profile $selectedProxy")
                }
            } catch (e: Exception) {
                Logs.e("$TAG: Failed to start health monitoring: ${e.message}", e)
            }
        }
    }

    protected abstract fun onBind()

    protected abstract fun onUnbind()

    override fun onRevoke() {
        stopRunner()
    }

    override fun onDestroy() {
        // Health monitoring'i temizle
        try {
            healthMonitor?.stopMonitoring()
            healthMonitor = null
            Logs.d("$TAG: Health monitor destroyed")
        } catch (e: Exception) {
            Logs.e("$TAG: Failed to destroy health monitor: ${e.message}", e)
        }

        connectivity.stop()

        wakeLock?.release()
        wakeLock = null

        super.onDestroy()
    }

    inner class CoroutineService : CoroutineScope {
        override val coroutineContext = Dispatchers.Default
    }

    val service = CoroutineService()

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (DataStore.serviceMode == Key.MODE_VPN && DataStore.stopOnRemoveTask) {
            stopService()
        }
        super.onTaskRemoved(rootIntent)
    }

}
