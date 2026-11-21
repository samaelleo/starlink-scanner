"""
Pinned Networks Screen.
Manages user-bookmarked Wi-Fi networks with custom annotations.
"""

import customtkinter as ctk
from typing import List, Dict, Any

from core.exporter import HistoryExporter
from ui.history_tab import CommentEditDialog


class PinnedTab(ctk.CTkFrame):
    def __init__(self, parent, db, on_unpin_callback=None):
        super().__init__(parent, fg_color="transparent")
        self.db = db
        self.on_unpin_callback = on_unpin_callback
        self.pinned: List[Dict[str, Any]] = []

        self._build_ui()
        self.refresh_pinned()

    def _build_ui(self):
        # Header Info
        header = ctk.CTkFrame(self, fg_color="#1E293B", corner_radius=10)
        header.pack(fill="x", padx=10, pady=(10, 5))

        self.lbl_count = ctk.CTkLabel(
            header,
            text="PINNED NETWORKS: 0",
            font=ctk.CTkFont(size=14, weight="bold"),
            text_color="#38BDF8"
        )
        self.lbl_count.pack(side="left", padx=15, pady=10)

        ctk.CTkLabel(
            header,
            text="Bookmark critical APs and attach inspection notes for field reconnaissance.",
            font=ctk.CTkFont(size=12),
            text_color="#94A3B8"
        ).pack(side="right", padx=15, pady=10)

        # Scrollable container
        self.scroll_frame = ctk.CTkScrollableFrame(self, fg_color="transparent")
        self.scroll_frame.pack(fill="both", expand=True, padx=5, pady=5)

    def refresh_pinned(self):
        self.pinned = self.db.get_pinned_networks()
        self.lbl_count.configure(text=f"PINNED NETWORKS: {len(self.pinned)}")

        for w in self.scroll_frame.winfo_children():
            w.destroy()

        if not self.pinned:
            ctk.CTkLabel(
                self.scroll_frame,
                text="No networks bookmarked yet. Use the 'Pin' button on any network card in the Scanner tab to track it here.",
                font=ctk.CTkFont(size=13),
                text_color="#64748B"
            ).pack(pady=40)
            return

        for p in self.pinned:
            self._render_pinned_card(p)

    def _render_pinned_card(self, item: Dict[str, Any]):
        card = ctk.CTkFrame(self.scroll_frame, fg_color="#1E293B", corner_radius=10, border_width=1, border_color="#0284C7")
        card.pack(fill="x", padx=4, pady=4)

        row1 = ctk.CTkFrame(card, fg_color="transparent")
        row1.pack(fill="x", padx=12, pady=(8, 2))

        bssid = item.get("bssid", "")
        ssid = item.get("ssid", "[Hidden]")
        score = item.get("score", 0)

        ctk.CTkLabel(row1, text=f"⭐ {ssid}", font=ctk.CTkFont(size=14, weight="bold"), text_color="#F8FAFC").pack(side="left")
        ctk.CTkLabel(row1, text=f"Score: {score}%", font=ctk.CTkFont(size=12, weight="bold"), text_color="#38BDF8").pack(side="right")

        row2 = ctk.CTkFrame(card, fg_color="transparent")
        row2.pack(fill="x", padx=12, pady=2)

        sig = item.get("signal_dbm", -100)
        chan = item.get("channel", 0)
        radio = item.get("radio_type", "")
        ts = HistoryExporter.format_timestamp(item.get("pinned_at", 0))

        ctk.CTkLabel(
            row2,
            text=f"BSSID: {bssid}   •   Signal: {sig} dBm   •   Channel {chan}   •   {radio}   •   Pinned: {ts}",
            font=ctk.CTkFont(size=11),
            text_color="#94A3B8"
        ).pack(side="left")

        # Comment & Actions Row
        row3 = ctk.CTkFrame(card, fg_color="transparent")
        row3.pack(fill="x", padx=12, pady=(4, 8))

        comment = item.get("comment", "")
        comment_text = f"📝 {comment}" if comment else "📝 No notes attached"
        ctk.CTkLabel(row3, text=comment_text, font=ctk.CTkFont(size=12), text_color="#E2E8F0").pack(side="left")

        ctk.CTkButton(
            row3,
            text="Unpin",
            width=70,
            height=24,
            fg_color="#EF4444",
            hover_color="#DC2626",
            font=ctk.CTkFont(size=11),
            command=lambda b=bssid: self._unpin(b)
        ).pack(side="right", padx=(4, 0))

        ctk.CTkButton(
            row3,
            text="Edit Note",
            width=90,
            height=24,
            fg_color="#334155",
            hover_color="#475569",
            font=ctk.CTkFont(size=11),
            command=lambda b=bssid, c=comment: CommentEditDialog(self, b, c, self._on_save_comment)
        ).pack(side="right", padx=4)

    def _unpin(self, bssid: str):
        self.db.unpin_network(bssid)
        self.refresh_pinned()
        if self.on_unpin_callback:
            self.on_unpin_callback()

    def _on_save_comment(self, bssid: str, new_comment: str):
        self.db.update_comment(bssid, new_comment)
        self.refresh_pinned()
