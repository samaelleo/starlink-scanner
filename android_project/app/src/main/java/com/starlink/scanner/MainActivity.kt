package com.starlink.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import com.starlink.scanner.data.local.AppDatabase
import com.starlink.scanner.service.ScanForegroundService
import com.starlink.scanner.ui.screens.HistoryScreen
import com.starlink.scanner.ui.screens.ProximityScreen
import com.starlink.scanner.ui.screens.ScanScreen
import com.starlink.scanner.ui.screens.SettingsScreen
import com.starlink.scanner.ui.theme.StarlinkDarkBg
import com.starlink.scanner.ui.theme.StarlinkScannerTheme
import kotlinx.coroutines.launch

sealed class Screen(val title: String, val icon: ImageVector) {
    object Scanner : Screen("اسکنر", Icons.Default.Wifi)
    object Radar : Screen("رادار", Icons.Default.Radar)
    object History : Screen("تاریخچه", Icons.Default.History)
    object Settings : Screen("تنظیمات", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkAndRequestPermissions()

        val db = AppDatabase.getDatabase(this)

        setContent {
            StarlinkScannerTheme {
                val coroutineScope = rememberCoroutineScope()
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Scanner) }

                val networks by ScanForegroundService.networks.collectAsState()
                val isScanning by ScanForegroundService.isRunning.collectAsState()
                val scanCount by ScanForegroundService.scanCount.collectAsState()
                val savedHistory by db.savedStarlinkDao().getAllStarlinks().collectAsState(initial = emptyList())

                Scaffold(
                    bottomBar = {
                        NavigationBar(containerColor = StarlinkDarkBg) {
                            val items = listOf(Screen.Scanner, Screen.Radar, Screen.History, Screen.Settings)
                            items.forEach { screen ->
                                NavigationBarItem(
                                    icon = { Icon(screen.icon, contentDescription = screen.title) },
                                    label = { Text(screen.title) },
                                    selected = currentScreen == screen,
                                    onClick = { currentScreen = screen }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (currentScreen) {
                            Screen.Scanner -> ScanScreen(
                                networks = networks,
                                isScanning = isScanning,
                                scanCount = scanCount,
                                onStartScan = { ScanForegroundService.start(this@MainActivity) },
                                onStopScan = { ScanForegroundService.stop(this@MainActivity) },
                                onPinNetwork = { net ->
                                    coroutineScope.launch {
                                        // Pin network action
                                    }
                                }
                            )
                            Screen.Radar -> ProximityScreen(networks = networks)
                            Screen.History -> HistoryScreen(
                                savedList = savedHistory,
                                onDelete = { id ->
                                    coroutineScope.launch {
                                        db.savedStarlinkDao().deleteById(id)
                                    }
                                },
                                onClearAll = {
                                    coroutineScope.launch {
                                        db.savedStarlinkDao().clearAll()
                                    }
                                }
                            )
                            Screen.Settings -> SettingsScreen()
                        }
                    }
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
}
