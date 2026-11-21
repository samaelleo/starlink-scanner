package com.starlink.scanner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starlink.scanner.data.model.WifiNetwork
import com.starlink.scanner.ui.theme.*

@Composable
fun ScanScreen(
    networks: List<WifiNetwork>,
    isScanning: Boolean,
    scanCount: Int,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onPinNetwork: (WifiNetwork) -> Unit
) {
    var starlinkOnly by remember { mutableStateOf(false) }

    val filtered = remember(networks, starlinkOnly) {
        if (starlinkOnly) networks.filter { it.score >= 40 } else networks
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StarlinkDarkBg)
            .padding(12.dp)
    ) {
        // 1. Stats Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatChip("کل شبکه‌ها", networks.size.toString(), Modifier.weight(1f))
            StatChip(
                "استارلینک",
                networks.count { it.score >= 40 }.toString(),
                Modifier.weight(1f),
                color = StarlinkCyan
            )
            StatChip("اسکن #", scanCount.toString(), Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Control Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = { if (isScanning) onStopScan() else onStartScan() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isScanning) StarlinkRed else StarlinkGreen
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (isScanning) "توقف اسکن" else "شروع اسکن زنده",
                    fontWeight = FontWeight.Bold
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("فقط استارلینک", color = StarlinkTextPrimary, fontSize = 13.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Switch(
                    checked = starlinkOnly,
                    onCheckedChange = { starlinkOnly = it }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Network Cards List
        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (networks.isEmpty()) "دکمه «شروع اسکن» را برای دریافت امواج بزنید." else "هیچ شبکه استارلینکی یافت نشد.",
                    color = StarlinkTextSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.bssid }) { net ->
                    NetworkCard(net = net, onPin = { onPinNetwork(net) })
                }
            }
        }
    }
}

@Composable
fun StatChip(label: String, value: String, modifier: Modifier = Modifier, color: Color = StarlinkTextPrimary) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = StarlinkCardBg),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, color = StarlinkTextSecondary, fontSize = 11.sp)
            Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun NetworkCard(net: WifiNetwork, onPin: () -> Unit) {
    val isStarlink = net.score >= 40
    val borderColor = if (isStarlink) StarlinkCyan else Color.Transparent

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(containerColor = StarlinkCardBg),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Score Badge
                val badgeColor = Color(net.confidence.colorHex)
                Surface(
                    color = badgeColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${net.score}% ${net.confidence.name}",
                        color = badgeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Text(
                    text = if (isStarlink) "🛰 ${net.ssid}" else net.ssid,
                    color = if (isStarlink) StarlinkCyan else StarlinkTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Info
            Text(
                text = "BSSID: ${net.bssid}  •  کانال ${net.channel}  •  ${net.frequency} MHz",
                color = StarlinkTextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Footer: Signal + Distance + Pin
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "سیگنال: ${net.signalStrength} dBm  |  فاصله: ~${net.estimatedDistanceMeters}m",
                    color = StarlinkTextSecondary,
                    fontSize = 11.sp
                )

                Button(
                    onClick = onPin,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("نشان کردن", fontSize = 11.sp)
                }
            }
        }
    }
}
