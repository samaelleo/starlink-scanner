package com.starlinks.scanner.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starlinks.scanner.scanner.StarlinkScorer
import com.starlinks.scanner.service.AdbConnectionManager
import com.starlinks.scanner.service.ScanForegroundService
import com.starlinks.scanner.service.ShellPrivilegeLevel
import com.starlinks.scanner.service.ShellStatusHelper
import com.starlinks.scanner.service.ThrottlingStatus
import com.starlinks.scanner.ui.theme.*

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var soundEnabled by remember { mutableStateOf(ScanForegroundService.soundEnabled) }
    var vibrationEnabled by remember { mutableStateOf(ScanForegroundService.vibrationEnabled) }
    var scanIntervalSeconds by remember { mutableStateOf((ScanForegroundService.scanIntervalMs / 1000).toFloat()) }

    var showGuideDialog by remember { mutableStateOf(false) }
    var showAdbDialog by remember { mutableStateOf(false) }
    var throttlingStatus by remember { mutableStateOf(ShellStatusHelper.getScanThrottlingStatus(context)) }
    var privilegeLevel by remember { mutableStateOf(ShellStatusHelper.getShellPrivilegeLevel()) }

    if (showGuideDialog) {
        TurboGuideDialog(
            onDismiss = {
                showGuideDialog = false
                throttlingStatus = ShellStatusHelper.getScanThrottlingStatus(context)
                privilegeLevel = ShellStatusHelper.getShellPrivilegeLevel()
            }
        )
    }

    if (showAdbDialog) {
        AdbConnectDialog(
            onDismiss = {
                showAdbDialog = false
                throttlingStatus = ShellStatusHelper.getScanThrottlingStatus(context)
                privilegeLevel = ShellStatusHelper.getShellPrivilegeLevel()
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StarlinkDarkBg)
            .padding(12.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = "تنظیمات برنامه (Settings)",
            color = StarlinkCyan,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // 1. Ultra-Fast Scan / ADB & Shell Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = StarlinkCardBg),
            border = BorderStroke(1.dp, if (throttlingStatus == ThrottlingStatus.DISABLED) StarlinkGreen.copy(alpha = 0.6f) else Color(0xFFF59E0B).copy(alpha = 0.6f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = if (throttlingStatus == ThrottlingStatus.DISABLED) StarlinkGreen else Color(0xFFF59E0B),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "وضعیت سرعت اسکن (Turbo / ADB)",
                            color = StarlinkTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Refresh Button
                    IconButton(
                        onClick = {
                            throttlingStatus = ShellStatusHelper.getScanThrottlingStatus(context)
                            privilegeLevel = ShellStatusHelper.getShellPrivilegeLevel()
                            Toast.makeText(context, "وضعیت سیستم به‌روزرسانی شد", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "بررسی مجدد",
                            tint = StarlinkCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Status Item 1: Throttling
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "محدودیت سرعت اسکن:",
                        color = StarlinkTextSecondary,
                        fontSize = 12.sp
                    )
                    Surface(
                        color = if (throttlingStatus == ThrottlingStatus.DISABLED) StarlinkGreen.copy(alpha = 0.2f) else Color(0xFFF59E0B).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = throttlingStatus.label,
                            color = if (throttlingStatus == ThrottlingStatus.DISABLED) StarlinkGreen else Color(0xFFF59E0B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Status Item 2: Privilege Level
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "سطح دسترسی پردازش:",
                        color = StarlinkTextSecondary,
                        fontSize = 12.sp
                    )
                    Surface(
                        color = if (privilegeLevel.isElevated) StarlinkCyan.copy(alpha = 0.2f) else Color(0xFF334155),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = privilegeLevel.title,
                            color = if (privilegeLevel.isElevated) StarlinkCyan else StarlinkTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Connect to Wireless Debugging Button (Primary Action)
                Button(
                    onClick = { showAdbDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (AdbConnectionManager.isConnected()) StarlinkGreen else Color(0xFF0284C7)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (AdbConnectionManager.isConnected()) "مدیریت اتصال ADB (متصل به شل ✅)" else "اتصال خودکار به Wireless Debugging (ADB)",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action Buttons Row (Guide & Dev Options)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showGuideDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = StarlinkCyan),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("راهنمای فعال‌سازی", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = { ShellStatusHelper.openDeveloperOptions(context) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.SettingsSuggest, contentDescription = null, tint = StarlinkCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Developer Options", color = StarlinkCyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Alert Settings Card
        SettingsCard(
            title = "هشدارهای کشف استارلینک (Detection Alerts)",
            icon = Icons.Default.Notifications
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("پخش آلارم صوتی", color = StarlinkTextPrimary, fontSize = 14.sp)
                    Text("پخش بوق هشدار در لحظه شناسایی روتر استارلینک", color = StarlinkTextSecondary, fontSize = 11.sp)
                }
                Switch(
                    checked = soundEnabled,
                    onCheckedChange = {
                        soundEnabled = it
                        ScanForegroundService.soundEnabled = it
                    }
                )
            }

            HorizontalDivider(color = Color(0xFF334155), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("لرزش دستگاه (ویبره)", color = StarlinkTextPrimary, fontSize = 14.sp)
                    Text("لرزش گوشی هنگام دریافت سیگنال استارلینک", color = StarlinkTextSecondary, fontSize = 11.sp)
                }
                Switch(
                    checked = vibrationEnabled,
                    onCheckedChange = {
                        vibrationEnabled = it
                        ScanForegroundService.vibrationEnabled = it
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Scan Timing Card
        SettingsCard(
            title = "بازه زمانی اسکن (Scan Timing)",
            icon = Icons.Default.Speed
        ) {
            Text(
                text = "فاصله بین اسکن‌ها: ${scanIntervalSeconds.toInt()} ثانیه",
                color = StarlinkTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Slider(
                value = scanIntervalSeconds,
                onValueChange = {
                    scanIntervalSeconds = it
                    ScanForegroundService.scanIntervalMs = (it * 1000).toLong()
                },
                valueRange = 2f..15f,
                steps = 12
            )
            Text(
                text = if (throttlingStatus == ThrottlingStatus.DISABLED)
                    "⚡ حالت توربو فعال است: می‌توانید بازه اسکن را روی ۲ الی ۳ ثانیه تنظیم کنید تا امواج به صورت زنده ثبت شوند."
                else
                    "💡 کاهش سرعت اسکن وای‌فای فعال است (محدود به ۴ اسکن در ۲ دقیقه). برای سرعت بالا از بخش «وضعیت سرعت اسکن» راهنما را مطالعه کنید.",
                color = if (throttlingStatus == ThrottlingStatus.DISABLED) StarlinkGreen else StarlinkTextSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 4. SpaceX Active OUIs
        SettingsCard(
            title = "پیشوندهای مک‌آدرس استارلینک (SpaceX OUIs)",
            icon = Icons.Default.Router
        ) {
            Text(
                text = "پیشوندهای فعال سخت‌افزاری SpaceX که در شناسایی استفاده می‌شوند:",
                color = StarlinkTextSecondary,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            val ouis = StarlinkScorer.DEFAULT_SPACEX_OUIS.toList()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ouis.chunked(3).forEach { rowOuis ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowOuis.forEach { oui ->
                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = oui,
                                    color = StarlinkCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 5. About & Version Info
        SettingsCard(
            title = "درباره و شناسه پکیج (About & Package)",
            icon = Icons.Default.Info
        ) {
            Text(
                text = "نسخه: 1.0.7 (com.starlinks.scanner)",
                color = StarlinkGreen,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "پکیج رسمی Starlink Scanner با امضای معتبر Release Keystore، مجهز به ماژول پایش اسکن توربو و بررسی وضعیت اشکال‌زدایی بی‌سیم (UID 2000 / ADB).",
                color = StarlinkTextSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = StarlinkCardBg),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(icon, contentDescription = null, tint = StarlinkCyan, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, color = StarlinkCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}
