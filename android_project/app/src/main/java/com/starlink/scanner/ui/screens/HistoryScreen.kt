package com.starlink.scanner.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starlink.scanner.data.local.SavedStarlink
import com.starlink.scanner.ui.theme.*
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
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "تاریخچه کشف استارلینک (${savedList.size})",
                color = StarlinkCyan,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            if (savedList.isNotEmpty()) {
                Button(
                    onClick = onClearAll,
                    colors = ButtonDefaults.buttonColors(containerColor = StarlinkRed),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("پاکسازی کل", fontSize = 11.sp)
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
                    text = "هنوز روتر استارلینکی ثبت نشده است.",
                    color = StarlinkTextSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(savedList, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = StarlinkCardBg),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Row 1: SSID + Date
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = dateFormat.format(Date(item.timestamp)),
                                    color = StarlinkTextSecondary,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "🛰 ${item.ssid}",
                                    color = StarlinkCyan,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "BSSID: ${item.bssid}  •  سیگنال: ${item.signalStrength} dBm  •  کانال: ${item.channel}",
                                color = StarlinkTextSecondary,
                                fontSize = 12.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Row 2: GPS Coordinates + Google Maps Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (item.latitude != null && item.longitude != null) {
                                    Text(
                                        text = "📍 ${"%.5f".format(item.latitude)}, ${"%.5f".format(item.longitude)}",
                                        color = StarlinkGreen,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Button(
                                        onClick = {
                                            val uri = Uri.parse("geo:${item.latitude},${item.longitude}?q=${item.latitude},${item.longitude}(${Uri.encode(item.ssid)})")
                                            val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                                            context.startActivity(mapIntent)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                    ) {
                                        Text("مشاهده روی نقشه", fontSize = 11.sp)
                                    }
                                } else {
                                    Text(
                                        text = "📍 موقعیت: ثبت نشده",
                                        color = StarlinkTextSecondary,
                                        fontSize = 12.sp
                                    )
                                }

                                TextButton(
                                    onClick = { onDelete(item.id) },
                                    colors = ButtonDefaults.textButtonColors(contentColor = StarlinkRed)
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
