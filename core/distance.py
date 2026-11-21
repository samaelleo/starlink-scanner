"""
Wi-Fi Signal Distance and Proximity Estimation Module.
Implements Free Space Path Loss (FSPL) and Log-Distance Path Loss models.
"""

from dataclasses import dataclass
from enum import Enum
import math


class Environment(Enum):
    OPEN_SPACE = ("Open Space / Free Line-of-Sight", 2.0)
    INDOOR_LIGHT = ("Light Indoor (Open Office/Partition)", 2.5)
    INDOOR_NORMAL = ("Normal Indoor (Standard Walls)", 3.0)
    INDOOR_DENSE = ("Dense Indoor (Concrete / Multiple Walls)", 3.8)
    MULTI_FLOOR = ("Multi-Floor (Ceiling & Concrete)", 4.5)

    def __init__(self, label: str, path_loss_exponent: float):
        self.label = label
        self.path_loss_exponent = path_loss_exponent


class SignalQuality(Enum):
    EXCELLENT = ("Excellent", "#10B981")     # Green
    GOOD = ("Good", "#06B6D4")               # Cyan
    FAIR = ("Fair", "#F59E0B")               # Amber
    WEAK = ("Weak", "#F97316")              # Orange
    VERY_WEAK = ("Very Weak", "#EF4444")     # Red

    def __init__(self, label: str, color: str):
        self.label = label
        self.color = color


@dataclass
class DistanceResult:
    estimated_meters: float
    min_meters: float
    max_meters: float
    signal_quality: SignalQuality
    accuracy_percent: int


class WifiDistanceEstimator:
    @staticmethod
    def get_reference_power(band_or_channel: str or int) -> float:
        """Reference power (P0) at 1 meter distance in dBm."""
        # 5 GHz / 6 GHz attenuates faster than 2.4 GHz
        if isinstance(band_or_channel, str) and ("5" in band_or_channel or "6" in band_or_channel):
            return -45.0
        elif isinstance(band_or_channel, int) and band_or_channel > 14:
            return -45.0
        return -40.0

    @classmethod
    def classify_signal(cls, rssi: int) -> SignalQuality:
        if rssi >= -50:
            return SignalQuality.EXCELLENT
        elif rssi >= -60:
            return SignalQuality.GOOD
        elif rssi >= -70:
            return SignalQuality.FAIR
        elif rssi >= -80:
            return SignalQuality.WEAK
        else:
            return SignalQuality.VERY_WEAK

    @classmethod
    def estimate_distance(
        cls,
        rssi_dbm: int,
        channel: int = 6,
        env: Environment = Environment.INDOOR_NORMAL
    ) -> DistanceResult:
        """
        Calculates distance using Log-Distance Path Loss Model:
        RSSI = P0 - 10 * n * log10(d)  =>  d = 10^((P0 - RSSI) / (10 * n))
        """
        p0 = cls.get_reference_power(channel)
        n = env.path_loss_exponent

        exponent = (p0 - rssi_dbm) / (10.0 * n)
        estimated = math.pow(10.0, exponent)

        # Range variation: ±25% for open space, up to ±40% for dense environments
        uncertainty = 0.25 if n <= 2.5 else (0.35 if n <= 3.5 else 0.45)
        min_dist = max(0.5, estimated * (1.0 - uncertainty))
        max_dist = estimated * (1.0 + uncertainty)

        # Accuracy score based on signal strength
        if rssi_dbm >= -50:
            accuracy = 90
        elif rssi_dbm >= -65:
            accuracy = 75
        elif rssi_dbm >= -75:
            accuracy = 60
        elif rssi_dbm >= -85:
            accuracy = 40
        else:
            accuracy = 25

        quality = cls.classify_signal(rssi_dbm)
        return DistanceResult(
            estimated_meters=round(estimated, 1),
            min_meters=round(min_dist, 1),
            max_meters=round(max_dist, 1),
            signal_quality=quality,
            accuracy_percent=accuracy
        )

    @classmethod
    def format_distance(cls, meters: float) -> str:
        if meters < 1.0:
            return "< 1 m"
        elif meters < 1000.0:
            return f"{meters:.1f} m"
        else:
            return f"{meters / 1000.0:.2f} km"
