package com.starlink.scanner.data.model

enum class StarlinkConfidence(val label: String, val colorHex: Long) {
    VERY_HIGH("بسیار بالا (VERY HIGH)", 0xFF10B981),
    HIGH("بالا (HIGH)", 0xFF06B6D4),
    MEDIUM("متوسط (MEDIUM)", 0xFFF59E0B),
    LOW("پایین (LOW)", 0xFFF97316),
    NONE("نامعتبر (NONE)", 0xFF64748B)
}

data class WifiNetwork(
    val bssid: String,
    val ssid: String,
    val signalStrength: Int, // dBm
    val frequency: Int,
    val channel: Int,
    val capabilities: String = "",
    val isHidden: Boolean = false,
    val score: Int = 0,
    val confidence: StarlinkConfidence = StarlinkConfidence.NONE,
    val vendorIEFound: Boolean = false,
    val vendorIEOUI: String? = null,
    val htCapable: Boolean = false,
    val vhtCapable: Boolean = false,
    val heCapable: Boolean = false,
    val isPinned: Boolean = false,
    val estimatedDistanceMeters: Double = 0.0,
    val distanceMin: Double = 0.0,
    val distanceMax: Double = 0.0,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)
