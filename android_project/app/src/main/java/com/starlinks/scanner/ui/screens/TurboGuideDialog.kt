package com.starlinks.scanner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.starlinks.scanner.service.ShellStatusHelper
import com.starlinks.scanner.ui.theme.*

@Composable
fun TurboGuideDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(20.dp),
            color = StarlinkDarkBg,
            border = androidx.compose.foundation.BorderStroke(1.dp, StarlinkCyan.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = StarlinkCyan,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "راهنمای فعال‌سازی اسکن توربو",
                            color = StarlinkTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "بستن",
                            tint = StarlinkTextSecondary
                        )
                    }
                }

                Text(
                    text = "اندروید به صورت پیش‌فرض سرعت اسکن وای‌فای را به ۴ بار در هر ۲ دقیقه محدود می‌کند. با انجام یکی از روش‌های زیر، این محدودیت برداشته شده و امواج استارلینک هر ۱ ثانیه دریافت می‌شوند:",
                    color = StarlinkTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                )

                HorizontalDivider(color = Color(0xFF334155), thickness = 0.5.dp)

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Method 1 (Recommended)
                    GuideMethodCard(
                        methodNumber = "روش ۱ (سریع‌ترین - بدون نیاز به کامپیوتر)",
                        badge = "پیشنهادی ⭐",
                        badgeColor = StarlinkGreen,
                        icon = Icons.Default.SettingsSuggest,
                        steps = listOf(
                            "۱. وارد «تنظیمات گوشی» (Settings) شوید.",
                            "۲. به بخش «درباره تلفن» (About Phone) رفته و ۷ مرتبه روی «شماره ساخت» (Build Number) ضربه بزنید تا Developer Options فعال شود.",
                            "۳. وارد «گزینه‌های توسعه‌دهنده» (Developer Options) شوید.",
                            "۴. گزینه «کاهش سرعت اسکن Wi-Fi» (Wi-Fi scan throttling) را خاموش (Disable) کنید."
                        ),
                        actionButtonText = "🚀 رفتن مستقیم به Developer Options",
                        onAction = {
                            ShellStatusHelper.openDeveloperOptions(context)
                        }
                    )

                    // Method 2 (Wireless Debugging & Shizuku)
                    GuideMethodCard(
                        methodNumber = "روش ۲ (اشکال‌زدایی بی‌سیم و Shizuku - UID 2000)",
                        badge = "بی‌سیم 📶",
                        badgeColor = StarlinkCyan,
                        icon = Icons.Default.Sensors,
                        steps = listOf(
                            "۱. در اندروید ۱۱ و بالاتر، در منوی Developer Options گزینه «اشکال‌زدایی بی‌سیم» (Wireless Debugging) را فعال کنید.",
                            "۲. اپلیکیشن رایگان Shizuku را نصب کرده و آن را با کد جفت‌سازی (Pairing Code) راه‌اندازی کنید.",
                            "۳. با اجرای Shizuku، دسترسی کامل کاربر shell (شناسه UID 2000) در پس‌زمینه برقرار می‌شود و اپلیکیشن محدودیت را خودکار خنثی می‌کند."
                        )
                    )

                    // Method 3 (Single Line ADB Command)
                    GuideMethodCard(
                        methodNumber = "روش ۳ (دستور تک‌خطی ADB با کابل یا لپ‌تاپ)",
                        badge = "ADB 💻",
                        badgeColor = Color(0xFFF59E0B),
                        icon = Icons.Default.Terminal,
                        steps = listOf(
                            "گوشی را با کابل به کامپیوتر متصل کرده و دستور زیر را اجرا کنید:"
                        ),
                        codeSnippet = ShellStatusHelper.ADB_CMD_DISABLE_THROTTLE,
                        actionButtonText = "📋 کپی دستور ADB",
                        onAction = {
                            ShellStatusHelper.copyToClipboard(
                                context,
                                "ADB Command",
                                ShellStatusHelper.ADB_CMD_DISABLE_THROTTLE
                            )
                        }
                    )
                }

                // Bottom Action
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = StarlinkCyan),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("متوجه شدم و بررسی وضعیت", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun GuideMethodCard(
    methodNumber: String,
    badge: String,
    badgeColor: Color,
    icon: ImageVector,
    steps: List<String>,
    codeSnippet: String? = null,
    actionButtonText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = StarlinkCardBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(icon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = methodNumber,
                        color = StarlinkTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Surface(
                    color = badgeColor.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = badge,
                        color = badgeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Steps
            steps.forEach { step ->
                Text(
                    text = step,
                    color = StarlinkTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }

            // Code snippet if any
            if (codeSnippet != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF334155)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = codeSnippet,
                        color = StarlinkCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Action button if any
            if (actionButtonText != null && onAction != null) {
                Spacer(modifier = Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Text(
                        text = actionButtonText,
                        color = StarlinkCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
