"""
Windows Wi-Fi Scanner Engine.
Uses Windows WLAN API (wlanapi.dll) to trigger scans and netsh to retrieve BSSIDs.
"""

import re
import time
import ctypes
import threading
import subprocess
from ctypes import wintypes
from typing import List, Dict, Any, Callable, Optional

from core.scorer import StarlinkScorer
from core.distance import WifiDistanceEstimator, Environment
from core.location import LocationService
from core.database import Database

# Windows WLAN API Structures
wlanapi = ctypes.windll.wlanapi

class GUID(ctypes.Structure):
    _fields_ = [
        ("Data1", wintypes.DWORD),
        ("Data2", wintypes.WORD),
        ("Data3", wintypes.WORD),
        ("Data4", wintypes.BYTE * 8)
    ]

class WLAN_INTERFACE_INFO(ctypes.Structure):
    _fields_ = [
        ("InterfaceGuid", GUID),
        ("strInterfaceDescription", wintypes.WCHAR * 256),
        ("isState", wintypes.DWORD)
    ]

class WLAN_INTERFACE_INFO_LIST(ctypes.Structure):
    _fields_ = [
        ("dwNumberOfItems", wintypes.DWORD),
        ("dwIndex", wintypes.DWORD),
        ("InterfaceInfo", WLAN_INTERFACE_INFO * 1)
    ]


class WifiScanner:
    def __init__(self, scorer: Optional[StarlinkScorer] = None):
        self.scorer = scorer or StarlinkScorer()
        self.distance_estimator = WifiDistanceEstimator()
        self.location_service = LocationService()
        self.db = Database()

        self.is_scanning = False
        self.scan_interval = 3.0  # seconds
        self.scan_count = 0
        self._thread: Optional[threading.Thread] = None
        self._stop_event = threading.Event()

        # Callbacks: on_results(networks), on_starlink_detected(network)
        self.on_results: Optional[Callable[[List[Dict[str, Any]]], None]] = None
        self.on_starlink_detected: Optional[Callable[[Dict[str, Any]], None]] = None

    def trigger_active_scan(self) -> bool:
        """Invokes WlanScan on all available WLAN interfaces via wlanapi.dll."""
        client_handle = wintypes.HANDLE()
        negotiated_version = wintypes.DWORD()
        ret = wlanapi.WlanOpenHandle(2, None, ctypes.byref(negotiated_version), ctypes.byref(client_handle))
        if ret != 0:
            return False

        try:
            p_interface_list = ctypes.POINTER(WLAN_INTERFACE_INFO_LIST)()
            ret = wlanapi.WlanEnumInterfaces(client_handle, None, ctypes.byref(p_interface_list))
            if ret != 0:
                return False

            num_items = p_interface_list.contents.dwNumberOfItems
            for i in range(num_items):
                guid = p_interface_list.contents.InterfaceInfo[i].InterfaceGuid
                wlanapi.WlanScan(client_handle, ctypes.byref(guid), None, None, None)
            return True
        except Exception:
            return False
        finally:
            wlanapi.WlanCloseHandle(client_handle, None)

    def scan_once(self) -> List[Dict[str, Any]]:
        """Performs a single scan, parses netsh output, and evaluates networks."""
        # 1. Trigger fresh scan
        self.trigger_active_scan()
        # Short settle delay for Windows driver
        time.sleep(0.4)

        # 2. Query BSSID list
        try:
            r = subprocess.run(
                ["netsh", "wlan", "show", "networks", "mode=bssid"],
                capture_output=True,
                text=True,
                timeout=5
            )
            raw_output = r.stdout
        except Exception:
            raw_output = ""

        networks = self._parse_netsh_output(raw_output)

        # 3. Calculate BSSID clusters (grouping APs by first 5 MAC octets)
        cluster_counts: Dict[str, int] = {}
        for net in networks:
            prefix = net["bssid"][:14].upper()
            cluster_counts[prefix] = cluster_counts.get(prefix, 0) + 1

        # 4. Score and enrich each network
        evaluated_networks: List[Dict[str, Any]] = []
        starlinks_found: List[Dict[str, Any]] = []

        # Current location (instant from cache or background fetcher)
        lat, lon, loc_source = self.location_service.get_current_location()

        for net in networks:
            prefix = net["bssid"][:14].upper()
            cluster_size = cluster_counts.get(prefix, 1)

            breakdown = self.scorer.evaluate(
                bssid=net["bssid"],
                ssid=net["ssid"],
                channel=net["channel"],
                radio_type=net["radio_type"],
                cluster_size=cluster_size
            )

            dist_info = self.distance_estimator.estimate_distance(
                rssi_dbm=net["signal_dbm"],
                channel=net["channel"],
                env=Environment.INDOOR_NORMAL
            )

            is_starlink = breakdown.confidence in ["VERY_HIGH", "HIGH", "MEDIUM"]
            is_pinned = self.db.is_pinned(net["bssid"])

            enriched = {
                **net,
                "score": breakdown.total,
                "confidence": breakdown.confidence,
                "factors": breakdown.factors,
                "distance_meters": dist_info.estimated_meters,
                "distance_min": dist_info.min_meters,
                "distance_max": dist_info.max_meters,
                "signal_quality": dist_info.signal_quality.label,
                "signal_color": dist_info.signal_quality.color,
                "is_starlink": is_starlink,
                "is_pinned": is_pinned,
                "latitude": lat,
                "longitude": lon,
                "location_source": loc_source
            }
            evaluated_networks.append(enriched)

            if is_starlink:
                starlinks_found.append(enriched)
                # Auto-save to database with valid coordinates!
                self.db.save_starlink(
                    bssid=enriched["bssid"],
                    ssid=enriched["ssid"],
                    score=enriched["score"],
                    confidence=enriched["confidence"],
                    signal_dbm=enriched["signal_dbm"],
                    channel=enriched["channel"],
                    frequency=enriched["band"],
                    radio_type=enriched["radio_type"],
                    latitude=lat,
                    longitude=lon,
                    location_source=loc_source
                )

        # Sort: Highest score first, then strongest signal
        evaluated_networks.sort(key=lambda x: (x["score"], x["signal_dbm"]), reverse=True)

        self.scan_count += 1

        if self.on_results:
            self.on_results(evaluated_networks)

        for sl in starlinks_found:
            if self.on_starlink_detected:
                self.on_starlink_detected(sl)

        return evaluated_networks

    def start_continuous_scan(self):
        if self.is_scanning:
            return
        self.is_scanning = True
        self._stop_event.clear()
        self._thread = threading.Thread(target=self._scan_loop, daemon=True)
        self._thread.start()

    def stop_scan(self):
        self.is_scanning = False
        self._stop_event.set()

    def _scan_loop(self):
        while not self._stop_event.is_set():
            try:
                self.scan_once()
            except Exception as e:
                print(f"[Scanner Error] {e}")

            # Wait for next interval
            self._stop_event.wait(self.scan_interval)

    def _parse_netsh_output(self, output: str) -> List[Dict[str, Any]]:
        networks = []
        current_net = None
        current_bssid = None

        for line in output.splitlines():
            line = line.strip()
            if not line:
                continue

            ssid_match = re.match(r"^SSID\s+\d+\s*:\s*(.*)$", line)
            if ssid_match:
                raw_ssid = ssid_match.group(1).strip()
                current_net = {
                    "ssid": raw_ssid if raw_ssid else "[Hidden]",
                    "auth": "Unknown",
                    "encryption": "Unknown",
                    "bssids": []
                }
                networks.append(current_net)
                current_bssid = None
                continue

            if current_net is None:
                continue

            if line.startswith("Authentication"):
                current_net["auth"] = line.split(":", 1)[1].strip()
            elif line.startswith("Encryption"):
                current_net["encryption"] = line.split(":", 1)[1].strip()

            bssid_match = re.match(r"^BSSID\s+\d+\s*:\s*([0-9a-fA-F:-]{17})", line)
            if bssid_match:
                current_bssid = {
                    "bssid": bssid_match.group(1).upper().replace("-", ":"),
                    "signal_percent": 0,
                    "signal_dbm": -100,
                    "radio_type": "802.11",
                    "band": "2.4 GHz",
                    "channel": 1
                }
                current_net["bssids"].append(current_bssid)
                continue

            if current_bssid is not None:
                if line.startswith("Signal"):
                    sig_str = line.split(":", 1)[1].strip().replace("%", "")
                    try:
                        pct = int(sig_str)
                        current_bssid["signal_percent"] = pct
                        current_bssid["signal_dbm"] = round((pct / 2.0) - 100)
                    except ValueError:
                        pass
                elif line.startswith("Radio type"):
                    current_bssid["radio_type"] = line.split(":", 1)[1].strip()
                elif line.startswith("Band"):
                    current_bssid["band"] = line.split(":", 1)[1].strip()
                elif line.startswith("Channel"):
                    try:
                        current_bssid["channel"] = int(line.split(":", 1)[1].strip())
                    except ValueError:
                        pass

        # Flatten BSSIDs
        flat = []
        for n in networks:
            for b in n["bssids"]:
                flat.append({
                    "ssid": n["ssid"],
                    "bssid": b["bssid"],
                    "signal_percent": b["signal_percent"],
                    "signal_dbm": b["signal_dbm"],
                    "radio_type": b["radio_type"],
                    "band": b["band"],
                    "channel": b["channel"],
                    "auth": n["auth"],
                    "encryption": n["encryption"]
                })
        return flat
