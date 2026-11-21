package com.starlink.scanner.ui.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.starlink.scanner.data.model.WifiNetwork
import com.starlink.scanner.scanner.WifiDistanceEstimator
import com.starlink.scanner.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ProximityScreen(networks: List<WifiNetwork>) {
    val context = LocalContext.current
    var selectedBssid by remember { mutableStateOf<String?>(null) }
    var basePressure by remember { mutableStateOf<Float?>(null) }
    var currentPressure by remember { mutableStateOf<Float?>(null) }

    // Barometer Listener
    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val baro = sm.getDefaultSensor(Sensor.TYPE_PRESSURE)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.values?.firstOrNull()?.let { p ->
                    if (basePressure == null) basePressure = p
                    currentPressure = p
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (baro != null) {
            sm.registerListener(listener, baro, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose {
            sm.unregisterListener(listener)
        }
    }

    val selectedNetwork = remember(networks, selectedBssid) {
        networks.find { it.bssid == selectedBssid } ?: networks.firstOrNull { it.score >= 40 } ?: networks.firstOrNull()
    }

    // Rotating Radar Angle Animation
    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StarlinkDarkBg)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "رادار و سنجش مجاورت استارلینک",
            color = StarlinkCyan,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Radar Canvas
        Box(
            modifier = Modifier
                .size(260.dp)
                .background(Color(0xFF0B132B), RoundedCornerShape(130.dp)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(15.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.width / 2f

                // Draw concentric circles
                for (step in 1..4) {
                    val r = radius * (step / 4f)
                    drawCircle(
                        color = Color(0xFF1E293B),
                        radius = r,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                // Crosshairs
                drawLine(Color(0xFF1E293B), Offset(center.x, 0f), Offset(center.x, size.height))
                drawLine(Color(0xFF1E293B), Offset(0f, center.y), Offset(size.width, center.y))

                // Sweep Line
                val rad = Math.toRadians(sweepAngle.toDouble())
                val endX = center.x + radius * cos(rad).toFloat()
                val endY = center.y + radius * sin(rad).toFloat()
                drawLine(StarlinkCyan, center, Offset(endX, endY), strokeWidth = 2.dp.toPx())

                // Draw blips
                networks.take(10).forEachIndexed { i, net ->
                    val distFraction = (net.estimatedDistanceMeters / 50.0).coerceIn(0.1, 0.95).toFloat()
                    val blipAngle = Math.toRadians(((net.bssid.hashCode() % 360) + 360) % 360.0)
                    val bx = center.x + (radius * distFraction) * cos(blipAngle).toFloat()
                    val by = center.y + (radius * distFraction) * sin(blipAngle).toFloat()

                    val blipColor = if (net.score >= 40) StarlinkGreen else Color(0xFF64748B)
                    drawCircle(blipColor, radius = if (net == selectedNetwork) 6.dp.toPx() else 4.dp.toPx(), center = Offset(bx, by))
                }
            }
        }

        Spacer(modifier = Modifier.height(15.dp))

        // Distance & Target Details
        selectedNetwork?.let { target ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = StarlinkCardBg),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "هدف: ${target.ssid} (${target.bssid})",
                        color = StarlinkTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "~${target.estimatedDistanceMeters} متر",
                        color = StarlinkGreen,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "محدوده تخمینی: ${target.distanceMin} تا ${target.distanceMax} متر",
                        color = StarlinkTextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Barometer Floor Estimation
                    val diff = (currentPressure ?: 0f) - (basePressure ?: 0f)
                    val floorEst = WifiDistanceEstimator.estimateHeightDifference(diff)
                    Text(
                        text = "ارتفاع نسبت به مبدأ: $floorEst",
                        color = StarlinkCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
