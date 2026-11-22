package com.starlinks.scanner.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starlinks.scanner.data.model.WifiNetwork
import com.starlinks.scanner.service.ShellStatusHelper
import com.starlinks.scanner.service.ThrottlingStatus
import com.starlinks.scanner.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
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
    val context = LocalContext.current
    var showGuideDialog by remember { mutableStateOf(false) }
    var throttlingStatus by remember { mutableStateOf(ShellStatusHelper.getScanThrottlingStatus(context)) }

    if (showGuideDialog) {
        TurboGuideDialog(
            onDismiss = {
                showGuideDialog = false
                throttlingStatus = ShellStatusHelper.getScanThrottlingStatus(context)
            }
        )
    }

    val filtered = remember(networks, starlinkOnly) {
        if (starlinkOnly) networks.filter { it.score >= 40 } else networks
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StarlinkDarkBg)
            .padding(12.dp)
    ) {
        // 1. Stats Row (Material 3 ElevatedCards)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            M3StatChip("کل شبکه‌ها", networks.size.toString(), Modifier.weight(1f))
            M3StatChip(
                "استارلینک",
                networks.count { it.score >= 40 }.toString(),
                Modifier.weight(1f),
                color = StarlinkCyan
            )
            M3StatChip("اسکن #", scanCount.toString(), Modifier.weight(1f), color = StarlinkGreen)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Turbo Scan Status Banner (Clickable to open activation guide)
        Surface(
            color = if (throttlingStatus == ThrottlingStatus.DISABLED) StarlinkGreen.copy(alpha = 0.12f) else Color(0xFFF59E0B).copy(alpha = 0.12f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(0.5.dp, if (throttlingStatus == ThrottlingStatus.DISABLED) StarlinkGreen.copy(alpha = 0.4f) else Color(0xFFF59E0B).copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showGuideDialog = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = if (throttlingStatus == ThrottlingStatus.DISABLED) StarlinkGreen else Color(0xFFF59E0B),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (throttlingStatus == ThrottlingStatus.DISABLED)
                            "اسکن توربو فعال است (اسکن بی‌وقفه و زنده)"
                        else
                            "اسکن استاندارد (محدود به ۴ اسکن در ۲ دقیقه)",
                        color = if (throttlingStatus == ThrottlingStatus.DISABLED) StarlinkGreen else Color(0xFFF59E0B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "راهنما ⚙️",
                    color = StarlinkCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Control Bar (M3 Buttons and FilterChip)
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
                shape = RoundedCornerShape(10.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Icon(
                    imageVector = if (isScanning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isScanning) "توقف اسکن" else "شروع اسکن زنده",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            FilterChip(
                selected = starlinkOnly,
                onClick = { starlinkOnly = !starlinkOnly },
                label = { Text("فقط استارلینک", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = StarlinkCyan.copy(alpha = 0.2f),
                    selectedLabelColor = StarlinkCyan,
                    labelColor = StarlinkTextSecondary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = starlinkOnly,
                    borderColor = if (starlinkOnly) StarlinkCyan else Color(0xFF334155),
                    selectedBorderColor = StarlinkCyan
                ),
                shape = RoundedCornerShape(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Network Cards List
        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (networks.isEmpty()) "دکمه «شروع اسکن زنده» را برای دریافت امواج لمس کنید." else "هیچ شبکه استارلینکی یافت نشد.",
                    color = StarlinkTextSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.bssid }) { net ->
                    ExpandableNetworkCard(net = net, onPin = { onPinNetwork(net) })
                }
            }
        }
    }
}

@Composable
fun M3StatChip(label: String, value: String, modifier: Modifier = Modifier, color: Color = StarlinkTextPrimary) {
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(containerColor = StarlinkCardBg),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, color = StarlinkTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, color = color, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ExpandableNetworkCard(net: WifiNetwork, onPin: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val isStarlink = net.score >= 40
    val borderStroke = if (isStarlink) BorderStroke(1.5.dp, StarlinkCyan.copy(alpha = 0.8f)) else null

    val arrowRotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "arrowRotation")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = StarlinkCardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = if (expanded) 6.dp else 2.dp),
        border = borderStroke,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Badge + SSID + Expand Arrow
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Score Badge
                val badgeColor = Color(net.confidence.colorHex)
                Surface(
                    color = badgeColor.copy(alpha = 0.18f),
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                ) {
                    Text(
                        text = if (isStarlink) "🛰 ${net.ssid}" else net.ssid,
                        color = if (isStarlink) StarlinkCyan else StarlinkTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "بستن جزئیات" else "مشاهده تفکیک امتیاز",
                        tint = StarlinkTextSecondary,
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(arrowRotation)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Specs Row
            Text(
                text = "BSSID: ${net.bssid}  •  CH ${net.channel}  •  ${net.frequency} MHz",
                color = StarlinkTextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Signal Bar (Material 3 LinearProgressIndicator)
            val signalFraction = ((net.signalStrength + 100) / 70f).coerceIn(0f, 1f)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = { signalFraction },
                    modifier = Modifier.weight(1f).height(6.dp),
                    color = if (isStarlink) StarlinkCyan else StarlinkGreen,
                    trackColor = Color(0xFF334155)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "${net.signalStrength} dBm",
                    color = StarlinkTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer: Distance + Action Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "فاصله تقریبی: ~${net.estimatedDistanceMeters} متر",
                    color = StarlinkTextSecondary,
                    fontSize = 12.sp
                )

                FilledTonalButton(
                    onClick = onPin,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF334155)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (net.isPinned) "حذف نشان" else "📌 نشان کردن", fontSize = 11.sp, color = StarlinkTextPrimary)
                }
            }

            // EXPANDABLE BREAKDOWN ACCORDION (Material Design 3)
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    HorizontalDivider(
                        color = Color(0xFF334155),
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    // Breakdown Section Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "تفکیک پارامترهای امتیازی (Score Breakdown)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = StarlinkCyan
                        )
                        Surface(
                            color = if (isStarlink) Color(0xFF0F766E) else Color(0xFF1E293B),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "مجموع: ${net.score} / 135",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isStarlink) StarlinkGreen else StarlinkTextSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Factor rows
                    if (net.factors.isEmpty()) {
                        Text(
                            text = "اطلاعات تفکیکی برای این اسکن موجود نیست.",
                            color = StarlinkTextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            net.factors.forEach { factor ->
                                Surface(
                                    color = if (factor.matched) Color(0xFF0F2538) else Color(0xFF0B1220),
                                    shape = RoundedCornerShape(8.dp),
                                    border = if (factor.matched) BorderStroke(0.5.dp, StarlinkCyan.copy(alpha = 0.5f)) else null,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            // Checkmark or Cancel icon
                                            if (factor.matched) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Matched",
                                                    tint = StarlinkGreen,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.Cancel,
                                                    contentDescription = "Not matched",
                                                    tint = Color(0xFF64748B),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = factor.name,
                                                fontSize = 12.sp,
                                                color = if (factor.matched) StarlinkTextPrimary else StarlinkTextSecondary,
                                                fontWeight = if (factor.matched) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                        }

                                        // Points Badge
                                        Surface(
                                            color = if (factor.matched) StarlinkGreen.copy(alpha = 0.2f) else Color.Transparent,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "${factor.points}/${factor.maxPoints} pt",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (factor.matched) StarlinkGreen else Color(0xFF64748B),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
