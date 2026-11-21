package com.starlink.scanner.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Speed
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
import com.starlink.scanner.scanner.StarlinkScorer
import com.starlink.scanner.service.ScanForegroundService
import com.starlink.scanner.ui.theme.*

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var soundEnabled by remember { mutableStateOf(ScanForegroundService.soundEnabled) }
    var vibrationEnabled by remember { mutableStateOf(ScanForegroundService.vibrationEnabled) }
    var scanIntervalSeconds by remember { mutableStateOf((ScanForegroundService.scanIntervalMs / 1000).toFloat()) }

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

        // 1. Alert Settings Card
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

            Divider(color = Color(0xFF334155), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

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

        // 2. Scan Throttling & Timing
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
                text = "💡 در گوشی‌های روت‌نشده با اندروید ۹ و بالاتر، سیستم‌عامل فرکانس اسکن را محدود (Throttle) می‌کند. بازه پیشنهادی ۳ الی ۵ ثانیه است.",
                color = StarlinkTextSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. SpaceX Active OUIs
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

        // 4. About & Version Info
        SettingsCard(
            title = "درباره و بروزرسانی (About & Signature)",
            icon = Icons.Default.Info
        ) {
            Text(
                text = "نسخه: 1.0.3 (امضا شده با کلید دائمی انتشار)",
                color = StarlinkGreen,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "این نسخه دارای کلید امضای اختصاصی و گواهینامه معتبر RSA است که امکان آپدیت مستقیم روی نسخه‌های قبلی بدون نیاز به حذف برنامه را فراهم می‌کند.",
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
