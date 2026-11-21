"""
History & GPS Screen.
Displays saved Starlink detections with verified GPS coordinates, map links, and export options.
"""

import webbrowser
import tkinter as tk
from tkinter import filedialog, messagebox
import customtkinter as ctk
from typing import List, Dict, Any

from core.exporter import HistoryExporter


class CommentEditDialog(ctk.CTkToplevel):
    def __init__(self, parent, bssid: str, current_comment: str, on_save):
        super().__init__(parent)
        self.title("Edit Network Note")
        self.geometry("400x220")
        self.resizable(False, False)
        self.attributes("-topmost", True)

        ctk.CTkLabel(self, text="Add / Edit Note for BSSID:", font=ctk.CTkFont(size=13, weight="bold")).pack(pady=(15, 2))
        ctk.CTkLabel(self, text=bssid, font=ctk.CTkFont(size=12), text_color="#38BDF8").pack(pady=(0, 10))

        self.entry = ctk.CTkEntry(self, width=340, placeholder_text="e.g. Rooftop unit, 3rd floor balcony...")
        self.entry.insert(0, current_comment)
        self.entry.pack(pady=10)

        btn_row = ctk.CTkFrame(self, fg_color="transparent")
        btn_row.pack(pady=15)

        ctk.CTkButton(
            btn_row,
            text="Save",
            fg_color="#10B981",
            hover_color="#059669",
            width=90,
            command=lambda: [on_save(bssid, self.entry.get().strip()), self.destroy()]
        ).pack(side="left", padx=5)

        ctk.CTkButton(
            btn_row,
            text="Cancel",
            fg_color="#64748B",
            hover_color="#475569",
            width=90,
            command=self.destroy
        ).pack(side="left", padx=5)


class HistoryTab(ctk.CTkFrame):
    def __init__(self, parent, db):
        super().__init__(parent, fg_color="transparent")
        self.db = db
        self.entries: List[Dict[str, Any]] = []
        self.search_query = ""

        self._build_ui()
        self.refresh_history()

    def _build_ui(self):
        # 1. Top Bar: Search + Export Buttons + Clear History
        top_bar = ctk.CTkFrame(self, fg_color="#1E293B", corner_radius=10)
        top_bar.pack(fill="x", padx=10, pady=(10, 5))

        # Search box
        self.entry_search = ctk.CTkEntry(top_bar, placeholder_text="🔍 Search history...", width=200)
        self.entry_search.pack(side="right", padx=10, pady=8)
        self.entry_search.bind("<KeyRelease>", self._on_search)

        # Export Buttons
        ctk.CTkButton(
            top_bar,
            text="📥 CSV",
            width=80,
            height=28,
            fg_color="#0284C7",
            hover_color="#0369A1",
            command=self._export_csv
        ).pack(side="left", padx=(10, 4), pady=8)

        ctk.CTkButton(
            top_bar,
            text="📥 JSON",
            width=80,
            height=28,
            fg_color="#0284C7",
            hover_color="#0369A1",
            command=self._export_json
        ).pack(side="left", padx=4, pady=8)

        ctk.CTkButton(
            top_bar,
            text="📥 TXT",
            width=80,
            height=28,
            fg_color="#0284C7",
            hover_color="#0369A1",
            command=self._export_txt
        ).pack(side="left", padx=4, pady=8)

        ctk.CTkButton(
            top_bar,
            text="🗑 Clear All",
            width=100,
            height=28,
            fg_color="#DC2626",
            hover_color="#B91C1C",
            command=self._clear_all
        ).pack(side="left", padx=(15, 10), pady=8)

        # 2. Scrollable List of History Cards
        self.scroll_frame = ctk.CTkScrollableFrame(self, fg_color="transparent")
        self.scroll_frame.pack(fill="both", expand=True, padx=5, pady=5)

    def _on_search(self, event=None):
        self.search_query = self.entry_search.get().strip().lower()
        self._render_entries()

    def refresh_history(self):
        self.entries = self.db.get_all_starlinks()
        self._render_entries()

    def _render_entries(self):
        for widget in self.scroll_frame.winfo_children():
            widget.destroy()

        filtered = []
        for e in self.entries:
            if self.search_query:
                q = self.search_query
                if q not in e.get("ssid", "").lower() and q not in e.get("bssid", "").lower() and q not in e.get("comment", "").lower():
                    continue
            filtered.append(e)

        if not filtered:
            msg = "No records match your search." if self.entries else "No Starlink routers recorded yet. Detections are automatically saved here during scanning."
            ctk.CTkLabel(
                self.scroll_frame,
                text=msg,
                font=ctk.CTkFont(size=13),
                text_color="#64748B"
            ).pack(pady=40)
            return

        for e in filtered:
            self._render_history_card(e)

    def _render_history_card(self, entry: Dict[str, Any]):
        card = ctk.CTkFrame(self.scroll_frame, fg_color="#1E293B", corner_radius=10, border_width=1, border_color="#334155")
        card.pack(fill="x", padx=4, pady=4)

        # Top row: SSID + Score Badge
        row1 = ctk.CTkFrame(card, fg_color="transparent")
        row1.pack(fill="x", padx=12, pady=(8, 2))

        conf = entry.get("confidence", "NONE")
        score = entry.get("score", 0)
        badge_bg = "#065F46" if conf == "VERY_HIGH" else ("#0E7490" if conf == "HIGH" else "#B45309")
        badge_fg = "#34D399" if conf == "VERY_HIGH" else ("#38BDF8" if conf == "HIGH" else "#FBBF24")

        badge = ctk.CTkFrame(row1, fg_color=badge_bg, corner_radius=6)
        badge.pack(side="left")
        ctk.CTkLabel(badge, text=f"{score}% {conf}", font=ctk.CTkFont(size=11, weight="bold"), text_color=badge_fg, padx=8, pady=2).pack()

        ts_str = HistoryExporter.format_timestamp(entry.get("timestamp", 0))
        ctk.CTkLabel(row1, text=ts_str, font=ctk.CTkFont(size=11), text_color="#94A3B8").pack(side="left", padx=12)

        ssid = entry.get("ssid", "[Hidden]")
        ctk.CTkLabel(
            row1,
            text=f"🛰 {ssid}",
            font=ctk.CTkFont(size=14, weight="bold"),
            text_color="#38BDF8",
            anchor="w"
        ).pack(side="left", fill="x", expand=True)

        # Mid row: BSSID, Signal, Channel, Frequency
        row2 = ctk.CTkFrame(card, fg_color="transparent")
        row2.pack(fill="x", padx=12, pady=2)

        bssid = entry.get("bssid", "")
        sig_dbm = entry.get("signal_dbm", -100)
        chan = entry.get("channel", 0)
        freq = entry.get("frequency", "")
        radio = entry.get("radio_type", "")

        info = f"BSSID: {bssid}   •   Signal: {sig_dbm} dBm   •   Channel {chan} ({freq})   •   {radio}"
        ctk.CTkLabel(row2, text=info, font=ctk.CTkFont(size=12), text_color="#CBD5E1").pack(side="left")

        # Bottom row: GPS Coordinates + Map Button + Comment Button + Delete Button
        row3 = ctk.CTkFrame(card, fg_color="transparent")
        row3.pack(fill="x", padx=12, pady=(4, 8))

        lat, lon = entry.get("latitude"), entry.get("longitude")
        loc_src = entry.get("location_source", "")

        loc_frame = ctk.CTkFrame(row3, fg_color="transparent")
        loc_frame.pack(side="left")

        if lat is not None and lon is not None:
            loc_text = f"📍 GPS: {lat:.5f}, {lon:.5f} ({loc_src})"
            ctk.CTkLabel(loc_frame, text=loc_text, font=ctk.CTkFont(size=12, weight="bold"), text_color="#10B981").pack(side="left", padx=(0, 8))

            btn_map = ctk.CTkButton(
                loc_frame,
                text="🗺 Google Maps",
                width=110,
                height=24,
                fg_color="#0F766E",
                hover_color="#115E59",
                font=ctk.CTkFont(size=11),
                command=lambda la=lat, lo=lon: webbrowser.open(f"https://www.google.com/maps?q={la},{lo}")
            )
            btn_map.pack(side="left")
        else:
            ctk.CTkLabel(loc_frame, text="📍 GPS: Not Recorded", font=ctk.CTkFont(size=12), text_color="#94A3B8").pack(side="left")

        # Actions (Right)
        btn_del = ctk.CTkButton(
            row3,
            text="Delete",
            width=60,
            height=24,
            fg_color="#EF4444",
            hover_color="#DC2626",
            font=ctk.CTkFont(size=11),
            command=lambda sid=entry.get("id"): self._delete_entry(sid)
        )
        btn_del.pack(side="right", padx=(4, 0))

        comment = entry.get("comment", "")
        comment_btn_text = f"📝 {comment[:12]}..." if comment else "📝 Add Note"
        btn_comment = ctk.CTkButton(
            row3,
            text=comment_btn_text,
            width=110,
            height=24,
            fg_color="#334155",
            hover_color="#475569",
            font=ctk.CTkFont(size=11),
            command=lambda b=bssid, c=comment: CommentEditDialog(self, b, c, self._on_save_comment)
        )
        btn_comment.pack(side="right", padx=4)

    def _on_save_comment(self, bssid: str, new_comment: str):
        self.db.update_comment(bssid, new_comment)
        self.refresh_history()

    def _delete_entry(self, entry_id: int):
        self.db.delete_starlink(entry_id)
        self.refresh_history()

    def _clear_all(self):
        if not self.entries:
            return
        if messagebox.askyesno("Confirm Delete", "Are you sure you want to delete all saved Starlink history records?"):
            self.db.clear_history()
            self.refresh_history()

    def _export_csv(self):
        if not self.entries:
            messagebox.showinfo("Export", "No history entries to export.")
            return
        path = filedialog.asksaveasfilename(defaultextension=".csv", filetypes=[("CSV Files", "*.csv")])
        if path:
            HistoryExporter.export_to_csv(self.entries, path)
            messagebox.showinfo("Export Success", f"History successfully exported to CSV:\n{path}")

    def _export_json(self):
        if not self.entries:
            messagebox.showinfo("Export", "No history entries to export.")
            return
        path = filedialog.asksaveasfilename(defaultextension=".json", filetypes=[("JSON Files", "*.json")])
        if path:
            HistoryExporter.export_to_json(self.entries, path)
            messagebox.showinfo("Export Success", f"History successfully exported to JSON:\n{path}")

    def _export_txt(self):
        if not self.entries:
            messagebox.showinfo("Export", "No history entries to export.")
            return
        path = filedialog.asksaveasfilename(defaultextension=".txt", filetypes=[("Text Files", "*.txt")])
        if path:
            HistoryExporter.export_to_txt(self.entries, path)
            messagebox.showinfo("Export Success", f"History successfully exported to TXT:\n{path}")
