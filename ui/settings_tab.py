"""
Settings & OUI Configuration Screen.
Allows customizing scoring weights, scan intervals, alerts, manual GPS, and custom MAC OUIs.
"""

import customtkinter as ctk
from tkinter import messagebox
from typing import Dict, Any

from core.scorer import ScoringWeights, DEFAULT_SPACEX_OUIS


class SettingsTab(ctk.CTkFrame):
    def __init__(self, parent, scanner, db, on_settings_changed=None):
        super().__init__(parent, fg_color="transparent")
        self.scanner = scanner
        self.db = db
        self.on_settings_changed = on_settings_changed

        self._build_ui()

    def _build_ui(self):
        self.scroll_frame = ctk.CTkScrollableFrame(self, fg_color="transparent")
        self.scroll_frame.pack(fill="both", expand=True, padx=10, pady=5)

        # 1. General Scanner Settings Card
        sec1 = ctk.CTkFrame(self.scroll_frame, fg_color="#1E293B", corner_radius=10)
        sec1.pack(fill="x", pady=6, padx=4)

        ctk.CTkLabel(sec1, text="⚙️ تنظیمات عمومی اسکنر", font=ctk.CTkFont(size=14, weight="bold"), text_color="#38BDF8").pack(anchor="w", padx=15, pady=(10, 5))

        # Scan Interval
        row_int = ctk.CTkFrame(sec1, fg_color="transparent")
        row_int.pack(fill="x", padx=15, pady=6)
        ctk.CTkLabel(row_int, text="فاصله زمانی بین اسکن‌ها (ثانیه):", font=ctk.CTkFont(size=12)).pack(side="left")
        self.lbl_interval_val = ctk.CTkLabel(row_int, text=f"{self.scanner.scan_interval:.1f}s", font=ctk.CTkFont(size=12, weight="bold"), text_color="#10B981")
        self.lbl_interval_val.pack(side="right")
        self.slider_interval = ctk.CTkSlider(row_int, from_=1.0, to=15.0, number_of_steps=28, command=self._on_interval_slider)
        self.slider_interval.set(self.scanner.scan_interval)
        self.slider_interval.pack(side="right", padx=15)

        # Sound Alert
        row_snd = ctk.CTkFrame(sec1, fg_color="transparent")
        row_snd.pack(fill="x", padx=15, pady=(6, 12))
        ctk.CTkLabel(row_snd, text="پخش هشدار صوتی هنگام کشف استارلینک:", font=ctk.CTkFont(size=12)).pack(side="left")
        snd_val = self.db.get_setting("sound_alert_enabled", "1") == "1"
        self.sw_sound = ctk.CTkSwitch(row_snd, text="", command=self._on_sound_toggle, onvalue=True, offvalue=False)
        if snd_val:
            self.sw_sound.select()
        else:
            self.sw_sound.deselect()
        self.sw_sound.pack(side="right")

        # 2. Location & GPS Settings Card
        sec_loc = ctk.CTkFrame(self.scroll_frame, fg_color="#1E293B", corner_radius=10)
        sec_loc.pack(fill="x", pady=6, padx=4)

        ctk.CTkLabel(sec_loc, text="📍 تنظیمات موقعیت مکانی (GPS)", font=ctk.CTkFont(size=14, weight="bold"), text_color="#38BDF8").pack(anchor="w", padx=15, pady=(10, 5))

        # Current status
        row_stat = ctk.CTkFrame(sec_loc, fg_color="transparent")
        row_stat.pack(fill="x", padx=15, pady=4)
        ctk.CTkLabel(row_stat, text="وضعیت فعلی:", font=ctk.CTkFont(size=12)).pack(side="left")

        lat, lon, src = self.scanner.location_service.get_current_location()
        coords_str = f"{lat:.5f}, {lon:.5f} ({src})" if lat is not None else "شناسایی نشده"
        self.lbl_loc_status = ctk.CTkLabel(row_stat, text=coords_str, font=ctk.CTkFont(size=12, weight="bold"), text_color="#10B981" if lat else "#F59E0B")
        self.lbl_loc_status.pack(side="left", padx=10)

        ctk.CTkButton(
            row_stat,
            text="بروزرسانی موقعیت",
            width=110,
            height=26,
            fg_color="#0F766E",
            hover_color="#115E59",
            command=self._refresh_location
        ).pack(side="right")

        # Manual Override
        row_man = ctk.CTkFrame(sec_loc, fg_color="transparent")
        row_man.pack(fill="x", padx=15, pady=(6, 12))
        self.sw_manual_loc = ctk.CTkSwitch(row_man, text="ثبت دستی مختصات (Override):", command=self._on_manual_loc_toggle)
        self.sw_manual_loc.pack(side="left")

        self.entry_lon = ctk.CTkEntry(row_man, placeholder_text="طول جغرافیایی (Lon)", width=120)
        self.entry_lon.pack(side="right", padx=(4, 0))
        self.entry_lat = ctk.CTkEntry(row_man, placeholder_text="عرض جغرافیایی (Lat)", width=120)
        self.entry_lat.pack(side="right", padx=4)

        # 3. Scoring Weights Card
        sec_weights = ctk.CTkFrame(self.scroll_frame, fg_color="#1E293B", corner_radius=10)
        sec_weights.pack(fill="x", pady=6, padx=4)

        row_wh = ctk.CTkFrame(sec_weights, fg_color="transparent")
        row_wh.pack(fill="x", padx=15, pady=(10, 5))
        ctk.CTkLabel(row_wh, text="🎯 ضرایب و وزن‌های سیستم امتیازدهی (Scoring Weights)", font=ctk.CTkFont(size=14, weight="bold"), text_color="#38BDF8").pack(side="left")
        ctk.CTkButton(row_wh, text="بازنشانی پیش‌فرض", width=110, height=24, fg_color="#334155", hover_color="#475569", command=self._reset_weights).pack(side="right")

        w = self.scanner.scorer.weights
        self.slider_oui, self.lbl_oui = self._create_weight_slider(sec_weights, "تطابق OUI سازنده (OUI Match):", w.oui_match, 0, 100)
        self.slider_ie, self.lbl_ie = self._create_weight_slider(sec_weights, "برچسب اختصاصی قطعه‌ساز (Vendor IE):", w.vendor_ie, 0, 50)
        self.slider_ht, self.lbl_ht = self._create_weight_slider(sec_weights, "پشتیبانی استاندارد HT/VHT:", w.ht_vht, 0, 50)
        self.slider_he, self.lbl_he = self._create_weight_slider(sec_weights, "پشتیبانی استاندارد WiFi 6 (HE):", w.wifi_6, 0, 50)
        self.slider_chan, self.lbl_chan = self._create_weight_slider(sec_weights, "فعالیت روی کانال‌های متداول استارلینک:", w.starlink_channel, 0, 50)
        self.slider_hid, self.lbl_hid = self._create_weight_slider(sec_weights, "شبکه مخفی (Hidden SSID):", w.hidden_ssid, 0, 50)
        self.slider_clust, self.lbl_clust = self._create_weight_slider(sec_weights, "خوشه‌بندی فرستنده‌ها (Cluster Bonus):", w.bssid_cluster, 0, 50, is_last=True)

        # 4. Custom OUIs Manager Card
        sec_oui = ctk.CTkFrame(self.scroll_frame, fg_color="#1E293B", corner_radius=10)
        sec_oui.pack(fill="x", pady=(6, 15), padx=4)

        ctk.CTkLabel(sec_oui, text="📡 لیست پیشوندهای مک‌آدرس استارلینک (Active OUIs)", font=ctk.CTkFont(size=14, weight="bold"), text_color="#38BDF8").pack(anchor="w", padx=15, pady=(10, 5))

        # Add OUI Row
        row_add = ctk.CTkFrame(sec_oui, fg_color="transparent")
        row_add.pack(fill="x", padx=15, pady=6)
        self.entry_new_oui = ctk.CTkEntry(row_add, placeholder_text="پیشوند OUI مثل 74:24:9F", width=200)
        self.entry_new_oui.pack(side="left", padx=(0, 10))

        ctk.CTkButton(
            row_add,
            text="افزودن OUI جدید",
            width=120,
            fg_color="#10B981",
            hover_color="#059669",
            command=self._add_oui
        ).pack(side="left")

        # Active OUIs list container
        self.oui_container = ctk.CTkFrame(sec_oui, fg_color="transparent")
        self.oui_container.pack(fill="x", padx=15, pady=(4, 12))
        self._refresh_oui_chips()

    def _create_weight_slider(self, parent, label: str, val: int, min_v: int, max_v: int, is_last: bool = False):
        row = ctk.CTkFrame(parent, fg_color="transparent")
        row.pack(fill="x", padx=15, pady=(4, 12 if is_last else 4))

        ctk.CTkLabel(row, text=label, font=ctk.CTkFont(size=12)).pack(side="left")
        lbl_v = ctk.CTkLabel(row, text=f"{val} pt", font=ctk.CTkFont(size=12, weight="bold"), text_color="#10B981")
        lbl_v.pack(side="right")

        slider = ctk.CTkSlider(
            row,
            from_=min_v,
            to=max_v,
            number_of_steps=max_v - min_v,
            command=lambda v, l=lbl_v: self._on_weight_slider(l, v)
        )
        slider.set(val)
        slider.pack(side="right", padx=15)
        return slider, lbl_v

    def _on_weight_slider(self, label_widget, val):
        iv = int(val)
        label_widget.configure(text=f"{iv} pt")
        self._apply_weights()

    def _apply_weights(self):
        w = self.scanner.scorer.weights
        w.oui_match = int(self.slider_oui.get())
        w.vendor_ie = int(self.slider_ie.get())
        w.ht_vht = int(self.slider_ht.get())
        w.wifi_6 = int(self.slider_he.get())
        w.starlink_channel = int(self.slider_chan.get())
        w.hidden_ssid = int(self.slider_hid.get())
        w.bssid_cluster = int(self.slider_clust.get())

    def _reset_weights(self):
        def_w = ScoringWeights()
        self.slider_oui.set(def_w.oui_match)
        self.lbl_oui.configure(text=f"{def_w.oui_match} pt")

        self.slider_ie.set(def_w.vendor_ie)
        self.lbl_ie.configure(text=f"{def_w.vendor_ie} pt")

        self.slider_ht.set(def_w.ht_vht)
        self.lbl_ht.configure(text=f"{def_w.ht_vht} pt")

        self.slider_he.set(def_w.wifi_6)
        self.lbl_he.configure(text=f"{def_w.wifi_6} pt")

        self.slider_chan.set(def_w.starlink_channel)
        self.lbl_chan.configure(text=f"{def_w.starlink_channel} pt")

        self.slider_hid.set(def_w.hidden_ssid)
        self.lbl_hid.configure(text=f"{def_w.hidden_ssid} pt")

        self.slider_clust.set(def_w.bssid_cluster)
        self.lbl_clust.configure(text=f"{def_w.bssid_cluster} pt")

        self._apply_weights()
        messagebox.showinfo("موفقیت", "ضرایب به مقادیر پیش‌فرض بازگردانی شدند.")

    def _on_interval_slider(self, val):
        self.scanner.scan_interval = float(val)
        self.lbl_interval_val.configure(text=f"{val:.1f}s")

    def _on_sound_toggle(self):
        val = "1" if self.sw_sound.get() else "0"
        self.db.set_setting("sound_alert_enabled", val)

    def _refresh_location(self):
        self.scanner.location_service.refresh_location_async()
        self.after(1200, self._update_loc_label)

    def _update_loc_label(self):
        lat, lon, src = self.scanner.location_service.get_current_location()
        if lat is not None:
            self.lbl_loc_status.configure(text=f"{lat:.5f}, {lon:.5f} ({src})", text_color="#10B981")
        else:
            self.lbl_loc_status.configure(text="یافت نشد", text_color="#F59E0B")

    def _on_manual_loc_toggle(self):
        enabled = self.sw_manual_loc.get()
        if enabled:
            try:
                lat = float(self.entry_lat.get().strip())
                lon = float(self.entry_lon.get().strip())
                self.scanner.location_service.set_manual_coordinates(lat, lon, True)
                self.lbl_loc_status.configure(text=f"{lat:.5f}, {lon:.5f} (مختصات دستی)", text_color="#10B981")
            except ValueError:
                messagebox.showerror("خطا", "لطفاً مقادیر عددی معتبر برای عرض و طول جغرافیایی وارد کنید.")
                self.sw_manual_loc.deselect()
        else:
            self.scanner.location_service.set_manual_coordinates(0, 0, False)
            self._update_loc_label()

    def _refresh_oui_chips(self):
        for w in self.oui_container.winfo_children():
            w.destroy()

        ouis = sorted(list(self.scanner.scorer.active_ouis))
        for oui in ouis:
            chip = ctk.CTkFrame(self.oui_container, fg_color="#0F172A", corner_radius=6)
            chip.pack(side="left", padx=4, pady=4)

            ctk.CTkLabel(chip, text=oui, font=ctk.CTkFont(size=12, weight="bold"), text_color="#38BDF8").pack(side="left", padx=(8, 4), pady=2)
            btn_x = ctk.CTkButton(
                chip,
                text="✕",
                width=16,
                height=16,
                fg_color="transparent",
                hover_color="#DC2626",
                text_color="#94A3B8",
                command=lambda o=oui: self._remove_oui(o)
            )
            btn_x.pack(side="left", padx=(0, 4))

    def _add_oui(self):
        new_oui = self.entry_new_oui.get().strip()
        if not new_oui:
            return
        clean = new_oui.upper().replace("-", ":")
        if len(clean) == 8 and clean.count(":") == 2:
            self.scanner.scorer.add_custom_oui(clean)
            self.entry_new_oui.delete(0, "end")
            self._refresh_oui_chips()
        else:
            messagebox.showerror("فرمت نامعتبر", "پیشوند OUI باید به فرمت AA:BB:CC باشد (مانند 74:24:9F).")

    def _remove_oui(self, oui: str):
        self.scanner.scorer.remove_custom_oui(oui)
        self._refresh_oui_chips()
