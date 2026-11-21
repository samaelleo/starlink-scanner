"""
Starlink detection and confidence scoring engine.
Ported and enhanced from the Starlink Scanner algorithm.
"""

from dataclasses import dataclass, field
from typing import List, Dict, Set, Optional

# Default known SpaceX / Starlink and partner OUIs
DEFAULT_SPACEX_OUIS = {
    "74:24:9F",  # SpaceX Starlink direct OUI
    "00:26:12",  # SpaceX Starlink direct OUI
    "F4:92:BF",  # Starlink hardware partner
    "14:ED:BB",  # Starlink hardware partner
    "90:3A:E6",  # Starlink hardware partner
    "00:15:6D",  # Ubiquiti / auxiliary network equipment
}

# Standard Starlink Wi-Fi channels (2.4 GHz & 5 GHz)
DEFAULT_STARLINK_CHANNELS = {1, 6, 11, 36, 40, 44, 48}


@dataclass
class ScoringWeights:
    oui_match: int = 50
    vendor_ie: int = 25
    ht_vht: int = 20
    wifi_6: int = 10
    hidden_ssid: int = 10
    starlink_channel: int = 10
    bssid_cluster: int = 10
    cluster_size_min: int = 3


@dataclass
class ScoringFactor:
    name: str
    points: int
    max_points: int
    matched: bool


@dataclass
class ScoreBreakdown:
    total: int
    confidence: str
    factors: List[ScoringFactor] = field(default_factory=list)


class StarlinkScorer:
    def __init__(self, weights: Optional[ScoringWeights] = None):
        self.weights = weights or ScoringWeights()
        self.active_ouis: Set[str] = set(DEFAULT_SPACEX_OUIS)
        self.target_channels: Set[int] = set(DEFAULT_STARLINK_CHANNELS)

    def add_custom_oui(self, oui: str):
        clean = oui.upper().replace("-", ":").strip()
        if len(clean) == 8 and clean.count(":") == 2:
            self.active_ouis.add(clean)

    def remove_custom_oui(self, oui: str):
        clean = oui.upper().replace("-", ":").strip()
        if clean in self.active_ouis:
            self.active_ouis.remove(clean)

    def is_oui_match(self, bssid: str) -> bool:
        clean = bssid.upper().replace("-", ":").strip()
        prefix = clean[:8]
        return prefix in self.active_ouis

    def is_locally_administered(self, bssid: str) -> bool:
        """Checks if the MAC address has the U/L bit set (locally administered / randomized)."""
        clean = bssid.upper().replace("-", ":").replace(":", "")
        if len(clean) >= 2:
            try:
                first_byte = int(clean[:2], 16)
                return bool(first_byte & 0x02)
            except ValueError:
                return False
        return False

    def calculate_confidence(self, score: int) -> str:
        if score >= 90:
            return "VERY_HIGH"
        elif score >= 70:
            return "HIGH"
        elif score >= 40:
            return "MEDIUM"
        elif score >= 20:
            return "LOW"
        else:
            return "NONE"

    def evaluate(
        self,
        bssid: str,
        ssid: str,
        channel: int,
        radio_type: str = "",
        cluster_size: int = 1,
        vendor_ie_found: bool = False
    ) -> ScoreBreakdown:
        factors: List[ScoringFactor] = []
        total_score = 0

        # 1. OUI Match
        oui_matched = self.is_oui_match(bssid)
        pts = self.weights.oui_match if oui_matched else 0
        total_score += pts
        factors.append(ScoringFactor("OUI Match (SpaceX/Starlink)", pts, self.weights.oui_match, oui_matched))

        # 2. Vendor IE
        pts = self.weights.vendor_ie if vendor_ie_found else 0
        total_score += pts
        factors.append(ScoringFactor("Vendor IE (Hardware Tag)", pts, self.weights.vendor_ie, vendor_ie_found))

        # 3. HT + VHT (802.11n + 802.11ac)
        radio_lower = radio_type.lower()
        ht_vht_matched = "802.11ac" in radio_lower or "802.11n" in radio_lower or "vht" in radio_lower
        pts = self.weights.ht_vht if ht_vht_matched else 0
        total_score += pts
        factors.append(ScoringFactor("HT/VHT (High Throughput)", pts, self.weights.ht_vht, ht_vht_matched))

        # 4. Wi-Fi 6 HE (802.11ax)
        wifi6_matched = "802.11ax" in radio_lower or "wifi 6" in radio_lower or "he" in radio_lower
        pts = self.weights.wifi_6 if wifi6_matched else 0
        total_score += pts
        factors.append(ScoringFactor("WiFi 6 HE (802.11ax)", pts, self.weights.wifi_6, wifi6_matched))

        # 5. Hidden SSID
        is_hidden = not ssid or ssid.strip() == "" or ssid.strip() == "[Hidden]"
        pts = self.weights.hidden_ssid if is_hidden else 0
        total_score += pts
        factors.append(ScoringFactor("Hidden SSID", pts, self.weights.hidden_ssid, is_hidden))

        # 6. Starlink Channel
        channel_matched = channel in self.target_channels
        pts = self.weights.starlink_channel if channel_matched else 0
        total_score += pts
        factors.append(ScoringFactor(f"Starlink Channel ({channel})", pts, self.weights.starlink_channel, channel_matched))

        # 7. BSSID Cluster
        cluster_matched = cluster_size >= self.weights.cluster_size_min
        pts = self.weights.bssid_cluster if cluster_matched else 0
        total_score += pts
        factors.append(ScoringFactor(f"BSSID Cluster ({cluster_size} APs)", pts, self.weights.bssid_cluster, cluster_matched))

        confidence = self.calculate_confidence(total_score)
        return ScoreBreakdown(total=total_score, confidence=confidence, factors=factors)
