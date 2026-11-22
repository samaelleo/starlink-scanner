"""
Main Window for Starlink Scanner (Windows Desktop Edition).
Coordinates tabs, background scanning, sound notifications, and UI updates.
"""

import os
import winsound
import customtkinter as ctk
from typing import Dict, Any, List

from core.scanner import WifiScanner
from core.database import Database
from ui.scan_tab import ScanTab
from ui.radar_tab import RadarTab
from ui.history_tab import HistoryTab
from ui.pinned_tab import PinnedTab
from ui.settings_tab import SettingsTab

ctk.set_appearance_mode("dark")
ctk.set_default_color_theme("blue")


class MainWindow(ctk.CTk):
    def __init__(self):
        super().__init__()

        self.title("Starlink Scanner - Satellite & Router Tracker (Windows Edition)")
        self.geometry("1100x740")
        self.minsize(960, 660)

        # Set Window Icon
        base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        icon_path = os.path.join(base_dir, "app_icon.ico")
        if os.path.exists(icon_path):
            try:
                self.iconbitmap(icon_path)
            except Exception:
                pass

        # Initialize Core Services
        self.db = Database()
        self.scanner = WifiScanner()

        # Connect Scanner Callbacks
        self.scanner.on_results = self._on_scan_results
        self.scanner.on_starlink_detected = self._on_starlink_detected

        self._alerted_bssids = set()

        self._build_header()
        self._build_tabs()

        self.protocol("WM_DELETE_WINDOW", self._on_closing)

    def _build_header(self):
        header = ctk.CTkFrame(self, height=58, fg_color="#0F172A", corner_radius=0)
        header.pack(fill="x", side="top")

        # Title & Branding (Left)
        title_box = ctk.CTkFrame(header, fg_color="transparent")
        title_box.pack(side="left", padx=20, pady=8)

        ctk.CTkLabel(
            title_box,
            text="🛰 STARLINK SCANNER",
            font=ctk.CTkFont(size=18, weight="bold"),
            text_color="#38BDF8"
        ).pack(side="left")

        ctk.CTkLabel(
            title_box,
            text="v1.0.6 Professional Edition",
            font=ctk.CTkFont(size=11),
            text_color="#64748B"
        ).pack(side="left", padx=(12, 0))

        # Status Indicator (Right)
        status_box = ctk.CTkFrame(header, fg_color="transparent")
        status_box.pack(side="right", padx=20, pady=8)

        self.lbl_global_status = ctk.CTkLabel(
            status_box,
            text="● Ready to Scan",
            font=ctk.CTkFont(size=12, weight="bold"),
            text_color="#94A3B8"
        )
        self.lbl_global_status.pack(side="right", padx=8)

    def _build_tabs(self):
        self.tabview = ctk.CTkTabview(self, fg_color="#0B132B")
        self.tabview.pack(fill="both", expand=True, padx=12, pady=(6, 10))

        # Tab 1: Scanner
        tab_scan = self.tabview.add("📡 Live Scanner")
        self.scan_tab = ScanTab(tab_scan, self.scanner, self.db, on_pin_callback=self._on_pin_updated)
        self.scan_tab.pack(fill="both", expand=True)

        # Tab 2: Radar
        tab_radar = self.tabview.add("🎯 Proximity Radar")
        self.radar_tab = RadarTab(tab_radar, self.scanner)
        self.radar_tab.pack(fill="both", expand=True)

        # Tab 3: History & GPS
        tab_history = self.tabview.add("📍 History & GPS")
        self.history_tab = HistoryTab(tab_history, self.db)
        self.history_tab.pack(fill="both", expand=True)

        # Tab 4: Pinned
        tab_pinned = self.tabview.add("⭐ Pinned Networks")
        self.pinned_tab = PinnedTab(tab_pinned, self.db, on_unpin_callback=self._on_pin_updated)
        self.pinned_tab.pack(fill="both", expand=True)

        # Tab 5: Settings
        tab_settings = self.tabview.add("⚙️ Settings")
        self.settings_tab = SettingsTab(tab_settings, self.scanner, self.db)
        self.settings_tab.pack(fill="both", expand=True)

    def _on_scan_results(self, networks: List[Dict[str, Any]]):
        # Dispatch to main UI thread safely
        self.after(0, lambda: self._apply_scan_results(networks))

    def _apply_scan_results(self, networks: List[Dict[str, Any]]):
        self.scan_tab.update_results(networks)
        self.radar_tab.update_results(networks)

        sl_count = sum(1 for n in networks if n.get("is_starlink"))
        status_text = f"Scan #{self.scanner.scan_count}  •  {len(networks)} Networks Found  •  {sl_count} Starlink Detected"
        self.lbl_global_status.configure(
            text=f"● {status_text}",
            text_color="#10B981" if sl_count > 0 else "#38BDF8"
        )

        if sl_count > 0:
            self.history_tab.refresh_history()

    def _on_starlink_detected(self, network: Dict[str, Any]):
        bssid = network.get("bssid")
        # Play alert sound once per BSSID per session
        if bssid not in self._alerted_bssids:
            self._alerted_bssids.add(bssid)
            if self.db.get_setting("sound_alert_enabled", "1") == "1":
                self.after(0, self._play_alert_sound)

    def _play_alert_sound(self):
        try:
            winsound.MessageBeep(winsound.MB_ICONEXCLAMATION)
        except Exception:
            pass

    def _on_pin_updated(self):
        self.pinned_tab.refresh_pinned()
        self.scan_tab.refresh_list()

    def _on_closing(self):
        self.scanner.stop_scan()
        self.destroy()
