"""
Data Exporter Module.
Exports Starlink scan history and pinned networks to CSV, JSON, and TXT.
"""

import csv
import json
import time
from typing import List, Dict, Any


class HistoryExporter:
    @staticmethod
    def format_timestamp(ts_ms: int) -> str:
        try:
            return time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(ts_ms / 1000.0))
        except Exception:
            return str(ts_ms)

    @classmethod
    def export_to_csv(cls, entries: List[Dict[str, Any]], filepath: str) -> bool:
        if not entries:
            return False

        headers = [
            "BSSID", "SSID", "Score", "Confidence", "Signal_dBm",
            "Channel", "Frequency", "RadioType", "Latitude", "Longitude",
            "LocationSource", "Timestamp", "Comment"
        ]

        with open(filepath, mode="w", newline="", encoding="utf-8-sig") as f:
            writer = csv.writer(f)
            writer.writerow(headers)
            for e in entries:
                writer.writerow([
                    e.get("bssid", ""),
                    e.get("ssid", ""),
                    e.get("score", 0),
                    e.get("confidence", ""),
                    e.get("signal_dbm", -100),
                    e.get("channel", 0),
                    e.get("frequency", ""),
                    e.get("radio_type", ""),
                    e.get("latitude") if e.get("latitude") is not None else "",
                    e.get("longitude") if e.get("longitude") is not None else "",
                    e.get("location_source", ""),
                    cls.format_timestamp(e.get("timestamp", 0)),
                    e.get("comment", "")
                ])
        return True

    @classmethod
    def export_to_json(cls, entries: List[Dict[str, Any]], filepath: str) -> bool:
        formatted = []
        for e in entries:
            copy = dict(e)
            copy["timestamp_formatted"] = cls.format_timestamp(e.get("timestamp", 0))
            formatted.append(copy)

        with open(filepath, mode="w", encoding="utf-8") as f:
            json.dump(formatted, f, indent=2, ensure_ascii=False)
        return True

    @classmethod
    def export_to_txt(cls, entries: List[Dict[str, Any]], filepath: str) -> bool:
        with open(filepath, mode="w", encoding="utf-8") as f:
            f.write("=== STARLINK SCANNER EXPORT REPORT ===\n")
            f.write(f"Generated at: {time.strftime('%Y-%m-%d %H:%M:%S')}\n")
            f.write(f"Total Networks: {len(entries)}\n\n")

            for i, e in enumerate(entries, 1):
                f.write(f"[{i}] {e.get('ssid', 'Unknown')} ({e.get('bssid', '')})\n")
                f.write(f"    Confidence: {e.get('confidence', 'NONE')} (Score: {e.get('score', 0)})\n")
                f.write(f"    Signal: {e.get('signal_dbm', -100)} dBm | Channel: {e.get('channel', 0)} ({e.get('radio_type', '')})\n")
                lat, lon = e.get("latitude"), e.get("longitude")
                if lat is not None and lon is not None:
                    f.write(f"    Location: {lat:.6f}, {lon:.6f} ({e.get('location_source', '')})\n")
                    f.write(f"    Maps Link: https://www.google.com/maps?q={lat},{lon}\n")
                else:
                    f.write("    Location: Not Available\n")
                f.write(f"    Time: {cls.format_timestamp(e.get('timestamp', 0))}\n")
                if e.get("comment"):
                    f.write(f"    Note: {e.get('comment')}\n")
                f.write("-" * 50 + "\n")
        return True
