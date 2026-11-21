package com.starlink.scanner.scanner

import com.starlink.scanner.data.local.ScoringWeights
import com.starlink.scanner.data.model.StarlinkConfidence

data class ScoringFactor(
    val name: String,
    val points: Int,
    val maxPoints: Int,
    val matched: Boolean
)

data class ScoreBreakdown(
    val total: Int,
    val confidence: StarlinkConfidence,
    val factors: List<ScoringFactor>
)

class StarlinkScorer(var weights: ScoringWeights = ScoringWeights()) {

    companion object {
        val DEFAULT_SPACEX_OUIS = hashSetOf(
            "74:24:9F",
            "00:26:12",
            "F4:92:BF",
            "14:ED:BB",
            "90:3A:E6",
            "00:15:6D"
        )
        val DEFAULT_STARLINK_CHANNELS = hashSetOf(1, 6, 11, 36, 40, 44, 48)
    }

    var activeOUIs: MutableSet<String> = HashSet(DEFAULT_SPACEX_OUIS)
    var starlinkChannels: MutableSet<Int> = HashSet(DEFAULT_STARLINK_CHANNELS)

    fun isOuiMatch(bssid: String): Boolean {
        val clean = bssid.uppercase().replace("-", ":")
        if (clean.length >= 8) {
            val prefix = clean.substring(0, 8)
            return activeOUIs.contains(prefix)
        }
        return false
    }

    fun confidence(score: Int): StarlinkConfidence {
        return when {
            score >= 90 -> StarlinkConfidence.VERY_HIGH
            score >= 70 -> StarlinkConfidence.HIGH
            score >= 40 -> StarlinkConfidence.MEDIUM
            score >= 20 -> StarlinkConfidence.LOW
            else -> StarlinkConfidence.NONE
        }
    }

    fun scoreWithBreakdown(
        bssid: String,
        ssid: String,
        channel: Int,
        htCapable: Boolean,
        vhtCapable: Boolean,
        heCapable: Boolean,
        vendorIEFound: Boolean,
        clusterSize: Int
    ): ScoreBreakdown {
        val factors = mutableListOf<ScoringFactor>()
        var total = 0

        // 1. OUI Match
        val ouiMatched = isOuiMatch(bssid)
        val pOui = if (ouiMatched) weights.ouiMatch else 0
        total += pOui
        factors.add(ScoringFactor("OUI Match (SpaceX/Starlink)", pOui, weights.ouiMatch, ouiMatched))

        // 2. Vendor IE
        val pVendor = if (vendorIEFound) weights.vendorIE else 0
        total += pVendor
        factors.add(ScoringFactor("Vendor IE (Hardware Tag)", pVendor, weights.vendorIE, vendorIEFound))

        // 3. HT + VHT
        val htVhtMatched = htCapable || vhtCapable
        val pHtVht = if (htVhtMatched) weights.htVht else 0
        total += pHtVht
        factors.add(ScoringFactor("HT/VHT (High Throughput)", pHtVht, weights.htVht, htVhtMatched))

        // 4. WiFi 6 (HE)
        val pHe = if (heCapable) weights.he else 0
        total += pHe
        factors.add(ScoringFactor("WiFi 6 HE (802.11ax)", pHe, weights.he, heCapable))

        // 5. Hidden SSID
        val isHidden = ssid.isBlank() || ssid == "[Hidden]"
        val pHidden = if (isHidden) weights.hidden else 0
        total += pHidden
        factors.add(ScoringFactor("Hidden SSID", pHidden, weights.hidden, isHidden))

        // 6. Starlink Channel
        val chanMatched = starlinkChannels.contains(channel)
        val pChan = if (chanMatched) weights.starlinkChannel else 0
        total += pChan
        factors.add(ScoringFactor("Starlink Channel ($channel)", pChan, weights.starlinkChannel, chanMatched))

        // 7. BSSID Cluster
        val clustMatched = clusterSize >= weights.clusterSizeMin
        val pClust = if (clustMatched) weights.cluster else 0
        total += pClust
        factors.add(ScoringFactor("BSSID Cluster ($clusterSize APs)", pClust, weights.cluster, clustMatched))

        return ScoreBreakdown(total, confidence(total), factors)
    }
}
