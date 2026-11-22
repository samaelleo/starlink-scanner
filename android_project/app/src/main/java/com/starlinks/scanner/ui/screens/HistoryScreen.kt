package com.starlinks.scanner.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starlinks.scanner.data.local.SavedStarlink
import com.starlinks.scanner.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(
    savedList: List<SavedStarlink>,
    onDelete: (Long) -> Unit,
    onClearAll: () -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StarlinkDarkBg)
            .padding(12.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "تاریخچه کشف استارلینک (${savedList.size})",
                color = StarlinkCyan,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            if (savedList.isNotEmpty()) {
                FilledTonalButton(
                    onClick = onClearAll,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = StarlinkRed.copy(alpha = 0.8f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("پاکسازی کل", fontSize = 11.sp, color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (savedList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "هنوز روتر استارلینکی ثبت نشده است.\nبه محض شناسایی امواج، موقعیت و اطلاعات به طور خودکار ذخیره می‌شود.",
                    color = StarlinkTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(savedList, key = { it.id }) { item ->
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(containerColor = StarlinkCardBg),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Row 1: SSID + Date
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = dateFormat.format(Date(item.timestamp)),
                                    color = StarlinkTextSecondary,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "🛰 ${item.ssid}",
                                    color = StarlinkCyan,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "BSSID: ${item.bssid}  •  سیگنال: ${item.signalStrength} dBm  •  کانال: ${item.channel}",
                                color = StarlinkTextSecondary,
                                fontSize = 12.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            HorizontalDivider(color = Color(0xFF334155), thickness = 0.5.dp)

                            Spacer(modifier = Modifier.height(8.dp))

                            // Row 2: GPS Coordinates + Google Maps Button + Delete
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (item.latitude != null && item.longitude != null) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "📍 ${"%.5f".format(item.latitude)}, ${"%.5f".format(item.longitude)}",
                                            color = StarlinkGreen,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            val uri = Uri.parse("geo:${item.latitude},${item.longitude}?q=${item.latitude},${item.longitude}(${Uri.encode(item.ssid)})")
                                            val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                                            context.startActivity(mapIntent)
                                        },
                                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF0F766E)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 3.dp)
                                    ) {
                                        Icon(Icons.Default.Map, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("مشاهده روی نقشه", fontSize = 11.sp, color = Color.White)
                                    }
                                } else {
                                    Text(
                                        text = "📍 موقعیت: ثبت نشده",
                                        color = StarlinkTextSecondary,
                                        fontSize = 12.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                TextButton(
                                    onClick = { onDelete(item.id) },
                                    colors = ButtonDefaults.textButtonColors(contentColor = StarlinkRed),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("حذف", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
