"""
Scanner Screen / Tab.
Real-time list of detected Wi-Fi networks with Starlink scoring badges and details.
"""

import customtkinter as ctk
from typing import List, Dict, Any, Callable


class ScoreBreakdownDialog(ctk.CTkToplevel):
    def __init__(self, parent, net: Dict[str, Any]):
        super().__init__(parent)
        self.title(f"Score Breakdown: {net.get('ssid', 'Unknown')}")
        self.geometry("480x440")
        self.resizable(False, False)
        self.attributes("-topmost", True)

        # Header
        ctk.CTkLabel(
            self,
            text=f"Starlink Confidence Analysis ({net.get('bssid')})",
            font=ctk.CTkFont(size=16, weight="bold")
        ).pack(pady=(15, 5))

        conf = net.get("confidence", "NONE")
        score = net.get("score", 0)
        conf_color = "#10B981" if conf == "VERY_HIGH" else ("#06B6D4" if conf == "HIGH" else ("#F59E0B" if conf == "MEDIUM" else "#94A3B8"))

        ctk.CTkLabel(
            self,
            text=f"Total Score: {score}/135  •  Confidence: {conf}",
            font=ctk.CTkFont(size=14),
            text_color=conf_color
        ).pack(pady=(0, 15))

        # Factor list frame
        frame = ctk.CTkScrollableFrame(self, width=430, height=270)
        frame.pack(padx=15, pady=5, fill="both", expand=True)

        factors = net.get("factors", [])
        for f in factors:
            row = ctk.CTkFrame(frame, fg_color="#1E293B" if f.matched else "#0F172A", corner_radius=6)
            row.pack(fill="x", pady=3, padx=5)

            status_icon = "✔" if f.matched else "✖"
            status_color = "#10B981" if f.matched else "#64748B"

            ctk.CTkLabel(
                row,
                text=status_icon,
                text_color=status_color,
                font=ctk.CTkFont(size=14, weight="bold"),
                width=24
            ).pack(side="left", padx=(10, 5))

            ctk.CTkLabel(
                row,
                text=f.name,
                font=ctk.CTkFont(size=12),
                anchor="w"
            ).pack(side="left", fill="x", expand=True, padx=5)

            ctk.CTkLabel(
                row,
                text=f"{f.points}/{f.max_points} pt",
                text_color=status_color,
                font=ctk.CTkFont(size=12, weight="bold"),
                width=70,
                anchor="e"
            ).pack(side="right", padx=10)

        ctk.CTkButton(self, text="Close", command=self.destroy, width=110).pack(pady=10)


class ScanTab(ctk.CTkFrame):
    def __init__(self, parent, scanner, db, on_pin_callback=None):
        super().__init__(parent, fg_color="transparent")
        self.scanner = scanner
        self.db = db
        self.on_pin_callback = on_pin_callback

        self.networks: List[Dict[str, Any]] = []
        self.show_starlink_only = False
        self.search_query = ""

        self._build_ui()

    def _build_ui(self):
        # 1. Top Stat Chips
        stats_frame = ctk.CTkFrame(self, fg_color="#1E293B", corner_radius=10)
        stats_frame.pack(fill="x", padx=10, pady=(10, 5))

        self.chip_total = self._create_stat_chip(stats_frame, "TOTAL NETWORKS", "0", 0)
        self.chip_starlink = self._create_stat_chip(stats_frame, "STARLINK DETECTED", "0", 1, color="#06B6D4")
        self.chip_scans = self._create_stat_chip(stats_frame, "SCANS COMPLETED", "0", 2)
        self.chip_location = self._create_stat_chip(stats_frame, "GPS LOCATION", "Acquiring...", 3, color="#10B981")

        # 2. Control Toolbar
        toolbar = ctk.CTkFrame(self, fg_color="transparent")
        toolbar.pack(fill="x", padx=10, pady=5)

        self.btn_toggle_scan = ctk.CTkButton(
            toolbar,
            text="▶ Start Live Scan",
            fg_color="#10B981",
            hover_color="#059669",
            font=ctk.CTkFont(size=13, weight="bold"),
            command=self._toggle_scan,
            width=140
        )
        self.btn_toggle_scan.pack(side="left", padx=(0, 8))

        self.btn_single_scan = ctk.CTkButton(
            toolbar,
            text="⚡ Single Scan",
            fg_color="#334155",
            hover_color="#475569",
            font=ctk.CTkFont(size=12),
            command=self._scan_once,
            width=110
        )
        self.btn_single_scan.pack(side="left", padx=4)

        self.sw_starlink_only = ctk.CTkSwitch(
            toolbar,
            text="🛰 Starlink Only",
            command=self._on_filter_changed,
            onvalue=True,
            offvalue=False
        )
        self.sw_starlink_only.pack(side="left", padx=15)

        # Search bar
        self.entry_search = ctk.CTkEntry(toolbar, placeholder_text="🔍 Search SSID or BSSID...", width=240)
        self.entry_search.pack(side="right", padx=(6, 0))
        self.entry_search.bind("<KeyRelease>", self._on_search_changed)

        # 3. Networks List (Scrollable)
        self.scroll_frame = ctk.CTkScrollableFrame(self, fg_color="transparent")
        self.scroll_frame.pack(fill="both", expand=True, padx=5, pady=5)

    def _create_stat_chip(self, parent, label: str, val: str, col: int, color: str = "#F8FAFC"):
        card = ctk.CTkFrame(parent, fg_color="#0F172A", corner_radius=8)
        card.grid(row=0, column=col, padx=6, pady=8, sticky="nsew")
        parent.grid_columnconfigure(col, weight=1)

        ctk.CTkLabel(card, text=label, font=ctk.CTkFont(size=10, weight="bold"), text_color="#94A3B8").pack(pady=(4, 0))
        val_lbl = ctk.CTkLabel(card, text=val, font=ctk.CTkFont(size=15, weight="bold"), text_color=color)
        val_lbl.pack(pady=(0, 4))
        return val_lbl

    def _toggle_scan(self):
        if self.scanner.is_scanning:
            self.scanner.stop_scan()
            self.btn_toggle_scan.configure(text="▶ Start Live Scan", fg_color="#10B981", hover_color="#059669")
        else:
            self.scanner.start_continuous_scan()
            self.btn_toggle_scan.configure(text="⏹ Stop Scan", fg_color="#EF4444", hover_color="#DC2626")

    def _scan_once(self):
        import threading
        threading.Thread(target=self.scanner.scan_once, daemon=True).start()

    def _on_filter_changed(self):
        self.show_starlink_only = self.sw_starlink_only.get()
        self.refresh_list()

    def _on_search_changed(self, event=None):
        self.search_query = self.entry_search.get().strip().lower()
        self.refresh_list()

    def update_results(self, networks: List[Dict[str, Any]]):
        self.networks = networks
        # Update chips
        starlink_count = sum(1 for n in networks if n.get("is_starlink", False))
        self.chip_total.configure(text=str(len(networks)))
        self.chip_starlink.configure(text=str(starlink_count))
        self.chip_scans.configure(text=f"#{self.scanner.scan_count}")

        # Update location chip
        lat, lon, src = self.scanner.location_service.get_current_location()
        if lat is not None and lon is not None:
            self.chip_location.configure(text=f"{lat:.4f}, {lon:.4f}")
        else:
            self.chip_location.configure(text="Unavailable")

        self.refresh_list()

    def refresh_list(self):
        # Clear previous cards
        for widget in self.scroll_frame.winfo_children():
            widget.destroy()

        filtered = []
        for n in self.networks:
            if self.show_starlink_only and not n.get("is_starlink", False):
                continue
            if self.search_query:
                bssid = n.get("bssid", "").lower()
                ssid = n.get("ssid", "").lower()
                if self.search_query not in bssid and self.search_query not in ssid:
                    continue
            filtered.append(n)

        if not filtered:
            msg = "No Wi-Fi networks found matching criteria." if self.networks else "Click 'Start Live Scan' to begin discovering nearby routers."
            ctk.CTkLabel(
                self.scroll_frame,
                text=msg,
                font=ctk.CTkFont(size=13),
                text_color="#64748B"
            ).pack(pady=40)
            return

        for net in filtered:
            self._render_network_card(net)

    def _render_network_card(self, net: Dict[str, Any]):
        is_starlink = net.get("is_starlink", False)
        card_border = "#06B6D4" if is_starlink else "#1E293B"

        card = ctk.CTkFrame(
            self.scroll_frame,
            fg_color="#1E293B",
            border_width=2 if is_starlink else 1,
            border_color=card_border,
            corner_radius=10
        )
        card.pack(fill="x", padx=4, pady=5)

        # Header Row: SSID + Score Badge
        header_row = ctk.CTkFrame(card, fg_color="transparent")
        header_row.pack(fill="x", padx=12, pady=(8, 2))

        # Score Badge (Left)
        conf = net.get("confidence", "NONE")
        score = net.get("score", 0)
        badge_bg = "#065F46" if conf == "VERY_HIGH" else ("#0E7490" if conf == "HIGH" else ("#B45309" if conf == "MEDIUM" else "#334155"))
        badge_fg = "#34D399" if conf == "VERY_HIGH" else ("#38BDF8" if conf == "HIGH" else ("#FBBF24" if conf == "MEDIUM" else "#94A3B8"))

        badge_frame = ctk.CTkFrame(header_row, fg_color=badge_bg, corner_radius=6)
        badge_frame.pack(side="left")
        ctk.CTkLabel(
            badge_frame,
            text=f"{score}% {conf}",
            font=ctk.CTkFont(size=11, weight="bold"),
            text_color=badge_fg,
            padx=8,
            pady=2
        ).pack()

        # SSID (Right)
        ssid_text = net.get("ssid", "[Hidden]")
        starlink_tag = "  [🛰 STARLINK AP]" if is_starlink else ""
        ctk.CTkLabel(
            header_row,
            text=f"{ssid_text}{starlink_tag}",
            font=ctk.CTkFont(size=14, weight="bold"),
            text_color="#38BDF8" if is_starlink else "#F8FAFC",
            anchor="w"
        ).pack(side="left", padx=12, fill="x", expand=True)

        # Middle Details Row
        mid_row = ctk.CTkFrame(card, fg_color="transparent")
        mid_row.pack(fill="x", padx=12, pady=4)

        bssid = net.get("bssid", "")
        sig_dbm = net.get("signal_dbm", -100)
        sig_pct = net.get("signal_percent", 0)
        channel = net.get("channel", 0)
        band = net.get("band", "")
        radio = net.get("radio_type", "")
        dist_m = net.get("distance_meters", 0)

        info_text = f"BSSID: {bssid}   •   Channel: {channel} ({band})   •   Standard: {radio}   •   Est. Distance: ~{dist_m}m"
        ctk.CTkLabel(
            mid_row,
            text=info_text,
            font=ctk.CTkFont(size=12),
            text_color="#94A3B8",
            anchor="w"
        ).pack(side="left")

        # Bottom Row: Signal bar & Action Buttons
        bot_row = ctk.CTkFrame(card, fg_color="transparent")
        bot_row.pack(fill="x", padx=12, pady=(2, 8))

        # Signal progress
        sig_frame = ctk.CTkFrame(bot_row, fg_color="transparent")
        sig_frame.pack(side="left", fill="x", expand=True)

        ctk.CTkLabel(
            sig_frame,
            text=f"Signal: {sig_dbm} dBm ({sig_pct}%)",
            font=ctk.CTkFont(size=11),
            text_color=net.get("signal_color", "#94A3B8")
        ).pack(side="left", padx=(0, 8))

        sig_bar = ctk.CTkProgressBar(sig_frame, width=120, height=8)
        sig_bar.set(max(0.0, min(1.0, sig_pct / 100.0)))
        sig_bar.configure(progress_color=net.get("signal_color", "#06B6D4"))
        sig_bar.pack(side="left", padx=5)

        # Actions
        btn_details = ctk.CTkButton(
            bot_row,
            text="📊 Breakdown",
            width=90,
            height=26,
            fg_color="#334155",
            hover_color="#475569",
            font=ctk.CTkFont(size=11),
            command=lambda n=net: ScoreBreakdownDialog(self, n)
        )
        btn_details.pack(side="right", padx=(4, 0))

        is_pinned = self.db.is_pinned(bssid)
        pin_text = "Unpin" if is_pinned else "📌 Pin"
        pin_color = "#DC2626" if is_pinned else "#0284C7"

        btn_pin = ctk.CTkButton(
            bot_row,
            text=pin_text,
            width=80,
            height=26,
            fg_color=pin_color,
            hover_color="#0369A1",
            font=ctk.CTkFont(size=11),
            command=lambda n=net: self._toggle_pin(n)
        )
        btn_pin.pack(side="right", padx=4)

    def _toggle_pin(self, net: Dict[str, Any]):
        bssid = net.get("bssid", "")
        if self.db.is_pinned(bssid):
            self.db.unpin_network(bssid)
        else:
            self.db.pin_network(
                bssid=bssid,
                ssid=net.get("ssid", ""),
                score=net.get("score", 0),
                signal_dbm=net.get("signal_dbm", -100),
                channel=net.get("channel", 0),
                radio_type=net.get("radio_type", "")
            )
        self.refresh_list()
