package com.starlinks.scanner.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.starlinks.scanner.service.AdbConnectionManager
import com.starlinks.scanner.service.AdbConnectionState
import com.starlinks.scanner.service.ShellStatusHelper
import com.starlinks.scanner.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun AdbConnectDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val connectionState by AdbConnectionManager.connectionState.collectAsState()
    val logs by AdbConnectionManager.logs.collectAsState()

    var hostInput by remember { mutableStateOf(AdbConnectionManager.lastHost) }
    var portInput by remember { mutableStateOf(AdbConnectionManager.lastPort.toString()) }
    var customCommand by remember { mutableStateOf("") }
    val detectedWifiIp = remember { ShellStatusHelper.getLocalWifiIpAddress() }

    val listState = rememberLazyListState()

    // Auto-scroll console to bottom when new logs arrive
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(20.dp),
            color = StarlinkDarkBg,
            border = BorderStroke(1.dp, StarlinkCyan.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = StarlinkCyan,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "اتصال به اشکال‌زدایی بی‌سیم (ADB)",
                            color = StarlinkTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "بستن", tint = StarlinkTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Connection State Indicator Banner
                when (val state = connectionState) {
                    is AdbConnectionState.Connected -> {
                        Surface(
                            color = StarlinkGreen.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, StarlinkGreen.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StarlinkGreen, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "متصل به ${state.host}:${state.port} (UID 2000)",
                                        color = StarlinkGreen,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                TextButton(
                                    onClick = { AdbConnectionManager.disconnect() },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("قطع اتصال", color = StarlinkRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    is AdbConnectionState.Connecting -> {
                        Surface(
                            color = StarlinkCyan.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, StarlinkCyan.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    color = StarlinkCyan,
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "در حال اتصال به ADB و احراز هویت...",
                                    color = StarlinkCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    is AdbConnectionState.Error -> {
                        Surface(
                            color = StarlinkRed.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, StarlinkRed.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = StarlinkRed, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = state.message,
                                    color = StarlinkRed,
                                    fontSize = 11.sp,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                    is AdbConnectionState.Disconnected -> {
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "برای اتصال خودکار، پورت صفحه Wireless Debugging را وارد کنید:",
                                color = StarlinkTextSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Guide Banner
                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, StarlinkCyan.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = StarlinkCyan, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "پورت را از کجا پیدا کنیم؟",
                                color = StarlinkCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "۱. پورت را از صفحه اصلی Wireless Debugging (عدد بعد از دو‌نقطه در بخش IP address & Port) وارد کنید.\n" +
                                   "۲. از باز کردن «Pair with device» خودداری کنید؛ پورت آن موقتی است.\n" +
                                   "۳. در گوشی شیائومی/پوکو: گزینه «Wireless debugging (Security settings)» را هم فعال کنید.",
                            color = StarlinkTextSecondary,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick IP Selector Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("انتخاب سریع IP:", color = StarlinkTextSecondary, fontSize = 10.sp)
                    FilterChip(
                        selected = hostInput == "127.0.0.1",
                        onClick = { hostInput = "127.0.0.1" },
                        label = { Text("127.0.0.1 (لوکال)", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = StarlinkCyan.copy(alpha = 0.25f),
                            selectedLabelColor = StarlinkCyan
                        )
                    )
                    if (detectedWifiIp != null && detectedWifiIp != "127.0.0.1") {
                        FilterChip(
                            selected = hostInput == detectedWifiIp,
                            onClick = { hostInput = detectedWifiIp },
                            label = { Text("$detectedWifiIp (وای‌فای)", fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = StarlinkCyan.copy(alpha = 0.25f),
                                selectedLabelColor = StarlinkCyan
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Input Fields (IP & Port)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = hostInput,
                        onValueChange = { hostInput = it },
                        label = { Text("آدرس IP", fontSize = 11.sp) },
                        modifier = Modifier.weight(1.3f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = StarlinkCyan,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = StarlinkTextPrimary,
                            unfocusedTextColor = StarlinkTextPrimary
                        )
                    )

                    OutlinedTextField(
                        value = portInput,
                        onValueChange = { portInput = it.filter { char -> char.isDigit() } },
                        label = { Text("پورت (Port)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = StarlinkCyan,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = StarlinkTextPrimary,
                            unfocusedTextColor = StarlinkTextPrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val port = portInput.toIntOrNull() ?: 5555
                            AdbConnectionManager.connectAndOptimize(context, hostInput.trim(), port)
                        },
                        enabled = connectionState !is AdbConnectionState.Connecting,
                        modifier = Modifier.weight(1.2f),
                        colors = ButtonDefaults.buttonColors(containerColor = StarlinkCyan),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("اتصال و بهینه‌سازی", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = { ShellStatusHelper.openDeveloperOptions(context) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.SettingsSuggest, contentDescription = null, tint = StarlinkCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("مشاهده پورت", color = StarlinkCyan, fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Console Header + Clear Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "کنسول خروجی دستورات ADB:",
                        color = StarlinkTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = { AdbConnectionManager.clearLogs() },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("پاکسازی لاگ", color = StarlinkTextSecondary, fontSize = 10.sp)
                    }
                }

                // Console Log Box
                Surface(
                    color = Color(0xFF090D16),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (logs.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "هنوز دستوری اجرا نشده است.\nبرای اتصال خودکار روی دکمه بالا بزنید.",
                                color = Color(0xFF475569),
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp)
                        ) {
                            items(logs) { log ->
                                Text(
                                    text = log,
                                    color = if (log.contains("❌")) StarlinkRed
                                    else if (log.contains("✅") || log.contains("⚡") || log.contains("🚀")) StarlinkGreen
                                    else if (log.contains("⚠️")) Color(0xFFF59E0B)
                                    else Color(0xFFCBD5E1),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 16.sp,
                                    modifier = Modifier.padding(vertical = 1.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Custom Command Input Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customCommand,
                        onValueChange = { customCommand = it },
                        placeholder = { Text("مثال: cmd wifi start-scan", fontSize = 11.sp, color = Color(0xFF64748B)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = StarlinkCyan,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = StarlinkTextPrimary,
                            unfocusedTextColor = StarlinkTextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Button(
                        onClick = {
                            if (customCommand.isNotBlank()) {
                                AdbConnectionManager.executeCustomCommand(customCommand.trim())
                                customCommand = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("ارسال", color = StarlinkTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
