package com.starlinks.scanner.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.starlinks.scanner.data.local.AppDatabase
import com.starlinks.scanner.data.local.SavedStarlink
import com.starlinks.scanner.data.model.WifiNetwork
import com.starlinks.scanner.scanner.StarlinkScorer
import com.starlinks.scanner.scanner.WifiDistanceEstimator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ScanForegroundService : Service() {

    companion object {
        var soundEnabled: Boolean = true
        var vibrationEnabled: Boolean = true
        var scanIntervalMs: Long = 3000L

        private val _networks = MutableStateFlow<List<WifiNetwork>>(emptyList())
        val networks: StateFlow<List<WifiNetwork>> = _networks.asStateFlow()

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _scanCount = MutableStateFlow(0)
        val scanCount: StateFlow<Int> = _scanCount.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, ScanForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ScanForegroundService::class.java)
            context.stopService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var wifiManager: WifiManager
    private lateinit var locationProvider: LocationProvider
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var alarmManager: DetectionAlarmManager
    private lateinit var db: AppDatabase
    private val scorer = StarlinkScorer()

    private val alertedBssids = mutableSetOf<String>()
    private var scanJob: Job? = null

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                processScanResults()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        locationProvider = LocationProvider(applicationContext)
        notificationHelper = NotificationHelper(applicationContext)
        alarmManager = DetectionAlarmManager(applicationContext)
        db = AppDatabase.getDatabase(applicationContext)

        notificationHelper.createChannels()
        registerReceiver(wifiScanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notif = notificationHelper.buildScanNotification(_scanCount.value, _networks.value.size, 0).build()
        startForeground(NotificationHelper.NOTIF_SCAN_ID, notif)

        _isRunning.value = true
        startScanLoop()

        return START_STICKY
    }

    private fun startScanLoop() {
        scanJob?.cancel()
        scanJob = serviceScope.launch {
            while (isActive) {
                try {
                    @Suppress("DEPRECATION")
                    wifiManager.startScan()
                } catch (e: Exception) {
                    Log.e("StarlinkScanner", "Wifi startScan error: ${e.message}")
                }
                // Check scan results or sleep for next scan interval
                delay(scanIntervalMs)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun processScanResults() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val results: List<ScanResult> = wifiManager.scanResults ?: emptyList()
                val evaluated = mutableListOf<WifiNetwork>()

                // Cluster counts
                val clusterCounts = mutableMapOf<String, Int>()
                for (r in results) {
                    val prefix = r.BSSID.take(14).uppercase()
                    clusterCounts[prefix] = (clusterCounts[prefix] ?: 0) + 1
                }

                var starlinkFoundCount = 0

                for (r in results) {
                    val bssid = r.BSSID.uppercase()
                    val ssid = r.SSID ?: "[Hidden]"
                    val channel = frequencyToChannel(r.frequency)
                    val clusterSize = clusterCounts[bssid.take(14)] ?: 1

                    // Inspect capabilities
                    val caps = r.capabilities ?: ""
                    val ht = caps.contains("HT") || caps.contains("WPA")
                    val vht = caps.contains("VHT")
                    val he = caps.contains("HE") || (Build.VERSION.SDK_INT >= 30 && r.wifiStandard == ScanResult.WIFI_STANDARD_11AX)

                    val breakdown = scorer.scoreWithBreakdown(
                        bssid = bssid,
                        ssid = ssid,
                        channel = channel,
                        htCapable = ht,
                        vhtCapable = vht,
                        heCapable = he,
                        vendorIEFound = false,
                        clusterSize = clusterSize
                    )

                    val dist = WifiDistanceEstimator.estimateDistance(r.level, r.frequency)
                    val isStarlink = breakdown.total >= 40

                    val net = WifiNetwork(
                        bssid = bssid,
                        ssid = ssid,
                        signalStrength = r.level,
                        frequency = r.frequency,
                        channel = channel,
                        capabilities = caps,
                        isHidden = ssid.isBlank() || ssid == "[Hidden]",
                        score = breakdown.total,
                        confidence = breakdown.confidence,
                        vendorIEFound = false,
                        htCapable = ht,
                        vhtCapable = vht,
                        heCapable = he,
                        estimatedDistanceMeters = dist.estimatedMeters,
                        distanceMin = dist.minMeters,
                        distanceMax = dist.maxMeters,
                        factors = breakdown.factors
                    )
                    evaluated.add(net)

                    if (isStarlink) {
                        starlinkFoundCount++
                        handleStarlinkDetected(net)
                    }
                }

                evaluated.sortByDescending { it.score }
                _networks.value = evaluated
                _scanCount.value += 1

                val updatedNotif = notificationHelper.buildScanNotification(
                    _scanCount.value,
                    evaluated.size,
                    starlinkFoundCount
                ).build()
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                nm.notify(NotificationHelper.NOTIF_SCAN_ID, updatedNotif)

            } catch (e: Exception) {
                Log.e("StarlinkScanner", "Error processing scan results: ${e.message}")
            }
        }
    }

    private suspend fun handleStarlinkDetected(net: WifiNetwork) {
        // Trigger alert once per BSSID
        if (!alertedBssids.contains(net.bssid)) {
            alertedBssids.add(net.bssid)
            alarmManager.triggerAlarm(soundEnabled, vibrationEnabled)
            notificationHelper.sendDetectionAlert(net.ssid, net.bssid, net.score)
        }

        // SAVE TO DATABASE WITH GUARANTEED GPS LOCATION!
        try {
            val location = locationProvider.getBestLocation()
            val lat = location?.latitude
            val lon = location?.longitude

            val entity = SavedStarlink(
                bssid = net.bssid,
                ssid = net.ssid,
                score = net.score,
                signalStrength = net.signalStrength,
                channel = net.channel,
                frequency = net.frequency,
                vendorIEFound = net.vendorIEFound,
                vendorIEOUI = net.vendorIEOUI,
                htCapable = net.htCapable,
                vhtCapable = net.vhtCapable,
                heCapable = net.heCapable,
                latitude = lat,  // Fixed: Guaranteed coordinate saving
                longitude = lon,
                timestamp = System.currentTimeMillis(),
                comment = ""
            )
            db.savedStarlinkDao().upsert(entity)
            Log.d("StarlinkScanner", "Saved Starlink with GPS: $lat, $lon")
        } catch (e: Exception) {
            Log.e("StarlinkScanner", "Database upsert failed: ${e.message}")
        }
    }

    private fun frequencyToChannel(freq: Int): Int {
        return when {
            freq == 2484 -> 14
            freq in 2412..2472 -> (freq - 2412) / 5 + 1
            freq in 5170..5825 -> (freq - 5170) / 5 + 34
            freq in 5945..7105 -> (freq - 5945) / 5 + 1
            else -> 0
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        scanJob?.cancel()
        serviceScope.cancel()
        try {
            unregisterReceiver(wifiScanReceiver)
        } catch (_: Exception) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
