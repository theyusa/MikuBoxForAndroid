package io.nekohasekai.sagernet.bg

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.RemoteException
import androidx.annotation.MainThread
import io.nekohasekai.sagernet.Action
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.aidl.ISagerNetService
import io.nekohasekai.sagernet.aidl.ISagerNetServiceCallback
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.database.preference.AutoSwitchPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel

abstract class BaseService : Service() {

    companion object {
        private const val TAG = "BaseService"

        var serviceClass: Class<out BaseService>? = null

        @MainThread
        fun startService() {
            SagerNet.startService()
        }

        @MainThread
        fun stopService() {
            SagerNet.stopService()
        }
    }

    enum class State(val canStop: Boolean = false) {
        Idle,
        Connecting(true),
        Connected(true),
        Stopping,
        Stopped,
    }

    var state = State.Idle
    private var healthMonitor: ConnectionHealthMonitor? = null

    interface TrafficListener {
        fun onTrafficUpdated(profileId: Long, stats: io.nekohasekai.sagernet.aidl.TrafficStats)
    }

    private val callbacks = android.os.RemoteCallbackList<ISagerNetServiceCallback>()

    override fun onBind(intent: Intent): IBinder? = when (intent.action) {
        Action.SERVICE -> binder
        else -> null
    }

    private val binder = object : ISagerNetService.Stub() {

        override fun getState(): Int = this@BaseService.state.ordinal

        override fun getTrafficStats(): io.nekohasekai.sagernet.aidl.TrafficStats {
            return trafficStats
        }

        override fun registerCallback(callback: ISagerNetServiceCallback) {
            callbacks.register(callback)
            if (state != State.Idle) {
                try {
                    callback.stateChanged(state.ordinal, state.name, null)
                } catch (e: RemoteException) {
                    Logs.w(e)
                }
            }
        }

        override fun unregisterCallback(callback: ISagerNetServiceCallback) {
            callbacks.unregister(callback)
        }

        override fun urlTest(): Int {
            if (state != State.Connected) {
                error("not connected")
            }
            return runUrlTest()
        }
    }

    var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sagernet:bgService")
        wakeLock?.acquire()

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
                startRunner()
            }
            State.Stopped -> {
                stopRunner()
            }
            else -> Logs.w("Start from unexpected state: $state")
        }

        return START_STICKY
    }

    private fun startRunner() {
        state = State.Connecting
        Logs.i("Starting runner")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Logs.i("$TAG runner started")

                onBind()

                trafficStats = io.nekohasekai.sagernet.aidl.TrafficStats()

                changeState(State.Connected)

            } catch (e: Throwable) {
                Logs.w("Start runner error: ${e.message}", e)
                changeState(State.Stopped, e.message)
            }
        }
    }

    private fun stopRunner(restart: Boolean = false) {
        if (state == State.Stopping || state == State.Idle) return

        Logs.i("Stopping runner")

        changeState(State.Stopping)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                onUnbind()
            } catch (e: Throwable) {
                Logs.w(e)
            }

            changeState(State.Stopped)

            if (restart) {
                startRunner()
            }
        }
    }

    private var trafficStats = io.nekohasekai.sagernet.aidl.TrafficStats()
    private var trafficUpdated = 0L

    fun onTrafficUpdated(stats: io.nekohasekai.sagernet.aidl.TrafficStats) {
        trafficStats = stats

        val now = System.currentTimeMillis()
        if (now - trafficUpdated > 500) {
            trafficUpdated = now

            val count = callbacks.beginBroadcast()
            for (i in 0 until count) {
                try {
                    callbacks.getBroadcastItem(i)?.onTrafficUpdated(DataStore.selectedProxy, stats)
                } catch (e: Exception) {
                    Logs.w(e)
                }
            }
            callbacks.finishBroadcast()
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

        val count = callbacks.beginBroadcast()
        for (i in 0 until count) {
            try {
                callbacks.getBroadcastItem(i)?.stateChanged(s.ordinal, s.name, msg)
            } catch (e: Exception) {
                Logs.w(e)
            }
        }
        callbacks.finishBroadcast()

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
        } else if (s == State.Stopping || s == State.Stopped) {
            // Health monitoring'i durdur
            try {
                healthMonitor?.stopMonitoring()
                Logs.d("$TAG: Stopped health monitoring")
            } catch (e: Exception) {
                Logs.e("$TAG: Failed to stop health monitoring: ${e.message}", e)
            }
        }
    }

    protected abstract fun onBind()

    protected abstract fun onUnbind()

    protected abstract fun runUrlTest(): Int

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

        wakeLock?.release()
        wakeLock = null

        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }
}
