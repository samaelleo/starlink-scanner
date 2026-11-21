"""
Radar & Proximity Screen.
Provides an animated radar view with distance estimation and environmental path-loss models.
"""

import math
import tkinter as tk
import customtkinter as ctk
from typing import List, Dict, Any, Optional

from core.distance import WifiDistanceEstimator, Environment, SignalQuality


class RadarTab(ctk.CTkFrame):
    def __init__(self, parent, scanner):
        super().__init__(parent, fg_color="transparent")
        self.scanner = scanner
        self.networks: List[Dict[str, Any]] = []
        self.selected_bssid: Optional[str] = None
        self.current_env = Environment.INDOOR_NORMAL

        self.sweep_angle = 0
        self.is_animating = True

        self._build_ui()
        self._animate_radar()

    def _build_ui(self):
        # Top Controls: Target Selector + Environment Selector
        ctrl_frame = ctk.CTkFrame(self, fg_color="#1E293B", corner_radius=10)
        ctrl_frame.pack(fill="x", padx=10, pady=(10, 5))

        # Target AP Dropdown
        ctk.CTkLabel(ctrl_frame, text="Target Network:", font=ctk.CTkFont(size=12, weight="bold")).pack(side="left", padx=(15, 6), pady=10)
        self.combo_target = ctk.CTkComboBox(
            ctrl_frame,
            values=["No networks scanned yet"],
            command=self._on_target_selected,
            width=280
        )
        self.combo_target.pack(side="left", padx=5, pady=10)

        # Environment Dropdown
        ctk.CTkLabel(ctrl_frame, text="Environment Profile:", font=ctk.CTkFont(size=12, weight="bold")).pack(side="left", padx=(20, 6), pady=10)
        env_names = [e.label for e in Environment]
        self.combo_env = ctk.CTkComboBox(
            ctrl_frame,
            values=env_names,
            command=self._on_env_selected,
            width=260
        )
        self.combo_env.set(self.current_env.label)
        self.combo_env.pack(side="left", padx=5, pady=10)

        # Main Layout: Left Radar Canvas, Right Details Panel
        content_frame = ctk.CTkFrame(self, fg_color="transparent")
        content_frame.pack(fill="both", expand=True, padx=10, pady=5)

        # Left: Radar Canvas
        radar_container = ctk.CTkFrame(content_frame, fg_color="#0F172A", corner_radius=12)
        radar_container.pack(side="left", fill="both", expand=True, padx=(0, 6), pady=5)

        self.canvas_size = 400
        self.canvas = tk.Canvas(
            radar_container,
            width=self.canvas_size,
            height=self.canvas_size,
            bg="#0B132B",
            highlightthickness=0
        )
        self.canvas.pack(expand=True, pady=10)

        # Right: Distance & Target Readout Panel
        self.panel_details = ctk.CTkFrame(content_frame, fg_color="#1E293B", width=300, corner_radius=12)
        self.panel_details.pack(side="right", fill="y", padx=(6, 0), pady=5)
        self.panel_details.pack_propagate(False)

        ctk.CTkLabel(
            self.panel_details,
            text="PROXIMITY & DISTANCE",
            font=ctk.CTkFont(size=15, weight="bold"),
            text_color="#38BDF8"
        ).pack(pady=(16, 10))

        # Target Name
        self.lbl_target_name = ctk.CTkLabel(
            self.panel_details,
            text="No Target Selected",
            font=ctk.CTkFont(size=14, weight="bold"),
            wraplength=270
        )
        self.lbl_target_name.pack(pady=4)

        self.lbl_target_bssid = ctk.CTkLabel(
            self.panel_details,
            text="BSSID: --:--:--:--:--:--",
            font=ctk.CTkFont(size=11),
            text_color="#94A3B8"
        )
        self.lbl_target_bssid.pack(pady=2)

        # Big Distance Display Card
        dist_card = ctk.CTkFrame(self.panel_details, fg_color="#0F172A", corner_radius=10)
        dist_card.pack(fill="x", padx=15, pady=15)

        ctk.CTkLabel(dist_card, text="ESTIMATED DISTANCE", font=ctk.CTkFont(size=11, weight="bold"), text_color="#94A3B8").pack(pady=(8, 0))
        self.lbl_distance_val = ctk.CTkLabel(
            dist_card,
            text="-- m",
            font=ctk.CTkFont(size=30, weight="bold"),
            text_color="#10B981"
        )
        self.lbl_distance_val.pack(pady=2)

        self.lbl_distance_range = ctk.CTkLabel(
            dist_card,
            text="Range: -- to -- meters",
            font=ctk.CTkFont(size=11),
            text_color="#64748B"
        )
        self.lbl_distance_range.pack(pady=(0, 8))

        # Signal Quality
        ctk.CTkLabel(self.panel_details, text="SIGNAL QUALITY", font=ctk.CTkFont(size=11, weight="bold"), text_color="#94A3B8").pack(pady=(10, 2))
        self.lbl_quality = ctk.CTkLabel(
            self.panel_details,
            text="--",
            font=ctk.CTkFont(size=13, weight="bold")
        )
        self.lbl_quality.pack(pady=2)

        self.bar_quality = ctk.CTkProgressBar(self.panel_details, width=240, height=10)
        self.bar_quality.set(0)
        self.bar_quality.pack(pady=6)

        # Tip Label
        self.lbl_tip = ctk.CTkLabel(
            self.panel_details,
            text="💡 Pro-Tip: Move around the building and observe distance changes to triangulate the exact location of the Starlink antenna.",
            font=ctk.CTkFont(size=11),
            text_color="#94A3B8",
            wraplength=260
        )
        self.lbl_tip.pack(side="bottom", pady=20)

    def _on_target_selected(self, choice: str):
        for n in self.networks:
            label = f"{n.get('ssid')} ({n.get('bssid')})"
            if choice.endswith(f"({n.get('bssid')})") or label == choice:
                self.selected_bssid = n.get("bssid")
                self._update_target_display(n)
                break

    def _on_env_selected(self, choice: str):
        for e in Environment:
            if e.label == choice:
                self.current_env = e
                # Recalculate if target is selected
                if self.selected_bssid:
                    for n in self.networks:
                        if n.get("bssid") == self.selected_bssid:
                            self._update_target_display(n)
                            break
                break

    def update_results(self, networks: List[Dict[str, Any]]):
        self.networks = networks

        # Populate combo box
        if networks:
            options = []
            for n in networks:
                prefix = "🛰 " if n.get("is_starlink") else ""
                options.append(f"{prefix}{n.get('ssid')} ({n.get('bssid')})")
            self.combo_target.configure(values=options)

            # Auto-select highest score network or Starlink if none selected
            if not self.selected_bssid or not any(n.get("bssid") == self.selected_bssid for n in networks):
                best = next((n for n in networks if n.get("is_starlink")), networks[0])
                self.selected_bssid = best.get("bssid")
                prefix = "🛰 " if best.get("is_starlink") else ""
                self.combo_target.set(f"{prefix}{best.get('ssid')} ({best.get('bssid')})")
                self._update_target_display(best)
            else:
                for n in networks:
                    if n.get("bssid") == self.selected_bssid:
                        self._update_target_display(n)
                        break

    def _update_target_display(self, net: Dict[str, Any]):
        ssid = net.get("ssid", "[Hidden]")
        bssid = net.get("bssid", "")
        sig_dbm = net.get("signal_dbm", -100)
        channel = net.get("channel", 6)
        is_starlink = net.get("is_starlink", False)

        tag = "  [🛰 STARLINK]" if is_starlink else ""
        self.lbl_target_name.configure(text=f"{ssid}{tag}", text_color="#38BDF8" if is_starlink else "#F8FAFC")
        self.lbl_target_bssid.configure(text=f"BSSID: {bssid}  •  Channel: {channel}")

        # Recalculate distance with current environment
        dist_info = WifiDistanceEstimator.estimate_distance(sig_dbm, channel, self.current_env)
        self.lbl_distance_val.configure(
            text=WifiDistanceEstimator.format_distance(dist_info.estimated_meters),
            text_color=dist_info.signal_quality.color
        )
        self.lbl_distance_range.configure(
            text=f"Range: {dist_info.min_meters:.1f} to {dist_info.max_meters:.1f} m (Accuracy: {dist_info.accuracy_percent}%)"
        )

        self.lbl_quality.configure(
            text=f"{dist_info.signal_quality.label} ({sig_dbm} dBm)",
            text_color=dist_info.signal_quality.color
        )
        sig_pct = net.get("signal_percent", 0)
        self.bar_quality.set(max(0.0, min(1.0, sig_pct / 100.0)))
        self.bar_quality.configure(progress_color=dist_info.signal_quality.color)

    def _animate_radar(self):
        if not self.winfo_exists():
            return

        self.canvas.delete("all")
        cx = self.canvas_size / 2.0
        cy = self.canvas_size / 2.0
        max_r = cx - 20

        # 1. Concentric Range Rings
        rings = [
            (max_r * 0.2, "5m", "#1E293B"),
            (max_r * 0.4, "15m", "#1E293B"),
            (max_r * 0.6, "30m", "#1E293B"),
            (max_r * 0.8, "50m", "#1E293B"),
            (max_r, "100m", "#334155")
        ]
        for r, label, color in rings:
            self.canvas.create_oval(cx - r, cy - r, cx + r, cy + r, outline=color, width=1)
            self.canvas.create_text(cx + 4, cy - r + 8, text=label, fill="#64748B", font=("Segoe UI", 8), anchor="w")

        # 2. Crosshair Grid Lines
        self.canvas.create_line(cx, 10, cx, self.canvas_size - 10, fill="#1E293B", dash=(3, 3))
        self.canvas.create_line(10, cy, self.canvas_size - 10, cy, fill="#1E293B", dash=(3, 3))

        # 3. Rotating Sweep Line
        rad = math.radians(self.sweep_angle)
        sx = cx + max_r * math.cos(rad)
        sy = cy + max_r * math.sin(rad)
        self.canvas.create_line(cx, cy, sx, sy, fill="#06B6D4", width=2)

        # Sweep gradient tail
        for i in range(1, 15):
            trail_rad = math.radians(self.sweep_angle - i * 3)
            tx = cx + max_r * math.cos(trail_rad)
            ty = cy + max_r * math.sin(trail_rad)
            alpha_color = "#0B253A" if i > 8 else "#083344"
            self.canvas.create_line(cx, cy, tx, ty, fill=alpha_color, width=1)

        # 4. Draw AP Blips
        for idx, net in enumerate(self.networks[:15]):
            bssid = net.get("bssid", "")
            is_target = bssid == self.selected_bssid
            is_starlink = net.get("is_starlink", False)
            dist_m = net.get("distance_meters", 20.0)

            # Map distance (1 to 60m) to radius
            normalized_dist = min(1.0, max(0.08, dist_m / 60.0))
            blip_r = normalized_dist * max_r

            # Hash BSSID to a deterministic angle on the radar
            mac_num = sum(int(b, 16) for b in bssid.split(":") if len(b) == 2)
            angle = (mac_num * 37) % 360
            bx = cx + blip_r * math.cos(math.radians(angle))
            by = cy + blip_r * math.sin(math.radians(angle))

            if is_target:
                # Big pulsing target blip
                self.canvas.create_oval(bx - 8, by - 8, bx + 8, by + 8, outline="#38BDF8", width=2)
                self.canvas.create_oval(bx - 4, by - 4, bx + 4, by + 4, fill="#06B6D4", outline="")
                self.canvas.create_text(bx, by - 14, text=net.get("ssid", ""), fill="#38BDF8", font=("Segoe UI", 9, "bold"))
            elif is_starlink:
                # Starlink blip (Cyan glow)
                self.canvas.create_oval(bx - 5, by - 5, bx + 5, by + 5, fill="#10B981", outline="#34D399", width=1)
                self.canvas.create_text(bx, by + 10, text="🛰 Starlink", fill="#34D399", font=("Segoe UI", 8))
            else:
                # Normal AP blip (Small gray/blue dot)
                self.canvas.create_oval(bx - 3, by - 3, bx + 3, by + 3, fill="#475569", outline="")

        # Advance angle
        self.sweep_angle = (self.sweep_angle + 3) % 360
        self.after(40, self._animate_radar)
