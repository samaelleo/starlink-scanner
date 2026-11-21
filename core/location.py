"""
Location Resolver Module.
Provides instant, multi-tier geographical coordinates (Latitude & Longitude)
to fix the location-saving bug found in the original APK.
"""

import time
import subprocess
import threading
from typing import Optional, Tuple
import requests


class LocationService:
    _instance = None
    _lock = threading.Lock()

    def __new__(cls):
        with cls._lock:
            if cls._instance is None:
                cls._instance = super(LocationService, cls).__new__(cls)
                cls._instance._init_service()
            return cls._instance

    def _init_service(self):
        self.cached_lat: Optional[float] = None
        self.cached_lon: Optional[float] = None
        self.cached_source: str = "نامشخص"
        self.last_update_time: float = 0
        self.manual_lat: Optional[float] = None
        self.manual_lon: Optional[float] = None
        self.use_manual: bool = False
        self._fetching = False

        # Pre-fetch location in background on startup
        self.refresh_location_async()

    def set_manual_coordinates(self, lat: float, lon: float, enabled: bool = True):
        self.manual_lat = lat
        self.manual_lon = lon
        self.use_manual = enabled

    def get_current_location(self) -> Tuple[Optional[float], Optional[float], str]:
        """
        Instantly returns coordinates. If cached and fresh (< 10 mins), returns cached.
        Otherwise triggers background refresh and returns latest known.
        """
        if self.use_manual and self.manual_lat is not None and self.manual_lon is not None:
            return self.manual_lat, self.manual_lon, "مختصات دستی (Manual)"

        now = time.time()
        # If cache is valid (within 10 minutes), return it immediately
        if self.cached_lat is not None and (now - self.last_update_time) < 600:
            return self.cached_lat, self.cached_lon, self.cached_source

        # If cache is expired or empty, trigger refresh
        self.refresh_location_async()

        # If we have any cached value (even older), return it to avoid blocking!
        if self.cached_lat is not None:
            return self.cached_lat, self.cached_lon, f"{self.cached_source} (Cache)"

        return None, None, "در حال دریافت موقعیت..."

    def refresh_location_async(self):
        if self._fetching:
            return
        threading.Thread(target=self._fetch_location_worker, daemon=True).start()

    def _fetch_location_worker(self):
        self._fetching = True
        try:
            # Step 1: Try Windows Hardware Sensor
            lat, lon, src = self._query_windows_sensor()
            if lat is not None and lon is not None:
                self.cached_lat = lat
                self.cached_lon = lon
                self.cached_source = src
                self.last_update_time = time.time()
                return

            # Step 2: Try IP-based Geolocation fallback
            lat, lon, src = self._query_ip_geo()
            if lat is not None and lon is not None:
                self.cached_lat = lat
                self.cached_lon = lon
                self.cached_source = src
                self.last_update_time = time.time()
                return
        finally:
            self._fetching = False

    def _query_windows_sensor(self) -> Tuple[Optional[float], Optional[float], str]:
        ps_cmd = """
        try {
            Add-Type -AssemblyName System.Device
            $watcher = New-Object System.Device.Location.GeoCoordinateWatcher
            $watcher.Start()
            Start-Sleep -Milliseconds 600
            $loc = $watcher.Position.Location
            if ($loc.IsUnknown -eq $false) {
                Write-Output "$($loc.Latitude),$($loc.Longitude)"
                exit
            }
        } catch {}
        Write-Output "NONE"
        """
        try:
            startupinfo = None
            creationflags = 0
            if hasattr(subprocess, "CREATE_NO_WINDOW"):
                creationflags |= subprocess.CREATE_NO_WINDOW
            if hasattr(subprocess, "STARTF_USESHOWWINDOW"):
                startupinfo = subprocess.STARTUPINFO()
                startupinfo.dwFlags |= subprocess.STARTF_USESHOWWINDOW
                startupinfo.wShowWindow = subprocess.SW_HIDE

            r = subprocess.run(
                ["powershell", "-NoProfile", "-Command", ps_cmd],
                capture_output=True,
                text=True,
                timeout=2,
                startupinfo=startupinfo,
                creationflags=creationflags
            )
            out = r.stdout.strip()
            if out and out != "NONE" and "," in out:
                parts = out.split(",")
                return float(parts[0]), float(parts[1]), "Windows GPS/Sensor"
        except Exception:
            pass
        return None, None, ""

    def _query_ip_geo(self) -> Tuple[Optional[float], Optional[float], str]:
        # Fast provider 1: ip-api.com
        try:
            resp = requests.get("http://ip-api.com/json/", timeout=2.5)
            if resp.status_code == 200:
                data = resp.json()
                if data.get("status") == "success":
                    lat = float(data.get("lat"))
                    lon = float(data.get("lon"))
                    city = data.get("city", "Unknown")
                    return lat, lon, f"موقعیت IP ({city})"
        except Exception:
            pass

        # Fast provider 2: ipinfo.io
        try:
            resp = requests.get("https://ipinfo.io/json", timeout=2.5)
            if resp.status_code == 200:
                data = resp.json()
                loc = data.get("loc")
                if loc and "," in loc:
                    lat_str, lon_str = loc.split(",")
                    city = data.get("city", "")
                    return float(lat_str), float(lon_str), f"موقعیت IP ({city})"
        except Exception:
            pass

        return None, None, ""

    @staticmethod
    def get_maps_url(lat: Optional[float], lon: Optional[float]) -> str:
        if lat is None or lon is None:
            return ""
        return f"https://www.google.com/maps?q={lat},{lon}"
