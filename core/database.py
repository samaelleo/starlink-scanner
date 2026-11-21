"""
SQLite Database Layer.
Persists Starlink history (with full GPS coordinates), pinned networks, and settings.
"""

import os
import sys
import sqlite3
import threading
import time
from typing import List, Dict, Any, Optional


def get_default_db_path() -> str:
    """
    Deterministically resolves an absolute, writable path for the SQLite database.
    Order of preference:
    1. Directory of the executable or project root (portable mode if writable)
    2. %LOCALAPPDATA%/StarlinkScanner/starlink_scanner.db (Windows standard app data)
    3. User home ~/.starlink_scanner/starlink_scanner.db
    """
    db_name = "starlink_scanner.db"

    # Determine executable/script base folder
    if getattr(sys, "frozen", False):
        base_dir = os.path.dirname(os.path.abspath(sys.executable))
    else:
        # parent of core/ is project root
        base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

    # Test if base_dir is writable
    try:
        test_file = os.path.join(base_dir, f".write_test_{os.getpid()}_{int(time.time())}")
        with open(test_file, "w") as f:
            f.write("1")
        os.remove(test_file)
        return os.path.join(base_dir, db_name)
    except Exception:
        pass

    # Fallback 1: LocalAppData (always writable on Windows for current user)
    local_appdata = os.environ.get("LOCALAPPDATA") or os.environ.get("APPDATA")
    if local_appdata:
        app_dir = os.path.join(local_appdata, "StarlinkScanner")
        try:
            os.makedirs(app_dir, exist_ok=True)
            return os.path.join(app_dir, db_name)
        except Exception:
            pass

    # Fallback 2: User Home directory
    home_dir = os.path.expanduser("~")
    app_dir = os.path.join(home_dir, ".starlink_scanner")
    try:
        os.makedirs(app_dir, exist_ok=True)
        return os.path.join(app_dir, db_name)
    except Exception:
        pass

    return db_name


class Database:
    _instance = None
    _lock = threading.Lock()

    def __new__(cls, db_path: Optional[str] = None):
        with cls._lock:
            if cls._instance is None:
                cls._instance = super(Database, cls).__new__(cls)
                cls._instance._init_db(db_path)
            return cls._instance

    def _init_db(self, db_path: Optional[str] = None):
        if not db_path:
            db_path = get_default_db_path()
        self.db_path = os.path.abspath(db_path)
        # Ensure parent directory exists
        try:
            os.makedirs(os.path.dirname(self.db_path), exist_ok=True)
        except Exception:
            pass
        self._conn_lock = threading.Lock()
        self._create_tables()

    def _get_connection(self):
        conn = sqlite3.connect(self.db_path, timeout=10.0, check_same_thread=False)
        try:
            conn.execute("PRAGMA journal_mode=WAL;")
        except Exception:
            pass
        return conn

    def _create_tables(self):
        with self._conn_lock:
            conn = self._get_connection()
            cursor = conn.cursor()

            # Saved Starlink Detections (History)
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS saved_starlinks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    bssid TEXT NOT NULL UNIQUE,
                    ssid TEXT NOT NULL,
                    score INTEGER NOT NULL,
                    confidence TEXT NOT NULL,
                    signal_dbm INTEGER NOT NULL,
                    channel INTEGER NOT NULL,
                    frequency TEXT NOT NULL,
                    radio_type TEXT NOT NULL,
                    latitude REAL,
                    longitude REAL,
                    location_source TEXT,
                    timestamp INTEGER NOT NULL,
                    comment TEXT NOT NULL DEFAULT ''
                )
            """)

            # Pinned Networks
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS pinned_networks (
                    bssid TEXT PRIMARY KEY,
                    ssid TEXT NOT NULL,
                    score INTEGER NOT NULL,
                    signal_dbm INTEGER NOT NULL,
                    channel INTEGER NOT NULL,
                    radio_type TEXT NOT NULL,
                    pinned_at INTEGER NOT NULL,
                    comment TEXT NOT NULL DEFAULT ''
                )
            """)

            # Custom User OUIs
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS custom_ouis (
                    oui TEXT PRIMARY KEY,
                    vendor_name TEXT NOT NULL,
                    is_active INTEGER NOT NULL DEFAULT 1,
                    added_at INTEGER NOT NULL
                )
            """)

            # Key-Value Settings
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS settings (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )
            """)

            conn.commit()
            conn.close()

    def save_starlink(
        self,
        bssid: str,
        ssid: str,
        score: int,
        confidence: str,
        signal_dbm: int,
        channel: int,
        frequency: str = "",
        radio_type: str = "",
        latitude: Optional[float] = None,
        longitude: Optional[float] = None,
        location_source: str = "",
        comment: str = ""
    ) -> bool:
        """Upsert a detected Starlink network into history."""
        with self._conn_lock:
            conn = self._get_connection()
            cursor = conn.cursor()
            now = int(time.time() * 1000)

            # Check if exists
            cursor.execute("SELECT id, latitude, longitude, comment FROM saved_starlinks WHERE bssid = ?", (bssid,))
            row = cursor.fetchone()

            if row:
                # Update existing - preserve location if new location is null
                existing_lat, existing_lon, existing_comment = row[1], row[2], row[3]
                save_lat = latitude if latitude is not None else existing_lat
                save_lon = longitude if longitude is not None else existing_lon
                save_comment = comment if comment else existing_comment

                cursor.execute("""
                    UPDATE saved_starlinks SET
                        ssid = ?, score = ?, confidence = ?, signal_dbm = ?,
                        channel = ?, frequency = ?, radio_type = ?,
                        latitude = ?, longitude = ?, location_source = ?,
                        timestamp = ?, comment = ?
                    WHERE bssid = ?
                """, (
                    ssid, score, confidence, signal_dbm,
                    channel, frequency, radio_type,
                    save_lat, save_lon, location_source,
                    now, save_comment, bssid
                ))
            else:
                cursor.execute("""
                    INSERT INTO saved_starlinks (
                        bssid, ssid, score, confidence, signal_dbm, channel,
                        frequency, radio_type, latitude, longitude,
                        location_source, timestamp, comment
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (
                    bssid, ssid, score, confidence, signal_dbm, channel,
                    frequency, radio_type, latitude, longitude,
                    location_source, now, comment
                ))

            conn.commit()
            conn.close()
            return True

    def get_all_starlinks(self) -> List[Dict[str, Any]]:
        with self._conn_lock:
            conn = self._get_connection()
            conn.row_factory = sqlite3.Row
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM saved_starlinks ORDER BY timestamp DESC")
            rows = [dict(r) for r in cursor.fetchall()]
            conn.close()
            return rows

    def delete_starlink(self, starlink_id: int):
        with self._conn_lock:
            conn = self._get_connection()
            cursor = conn.cursor()
            cursor.execute("DELETE FROM saved_starlinks WHERE id = ?", (starlink_id,))
            conn.commit()
            conn.close()

    def clear_history(self):
        with self._conn_lock:
            conn = self._get_connection()
            cursor = conn.cursor()
            cursor.execute("DELETE FROM saved_starlinks")
            conn.commit()
            conn.close()

    def update_comment(self, bssid: str, comment: str):
        with self._conn_lock:
            conn = self._get_connection()
            cursor = conn.cursor()
            cursor.execute("UPDATE saved_starlinks SET comment = ? WHERE bssid = ?", (comment, bssid))
            cursor.execute("UPDATE pinned_networks SET comment = ? WHERE bssid = ?", (comment, bssid))
            conn.commit()
            conn.close()

    def pin_network(self, bssid: str, ssid: str, score: int, signal_dbm: int, channel: int, radio_type: str, comment: str = ""):
        with self._conn_lock:
            conn = self._get_connection()
            cursor = conn.cursor()
            now = int(time.time() * 1000)
            cursor.execute("""
                INSERT OR REPLACE INTO pinned_networks (
                    bssid, ssid, score, signal_dbm, channel, radio_type, pinned_at, comment
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, (bssid, ssid, score, signal_dbm, channel, radio_type, now, comment))
            conn.commit()
            conn.close()

    def unpin_network(self, bssid: str):
        with self._conn_lock:
            conn = self._get_connection()
            cursor = conn.cursor()
            cursor.execute("DELETE FROM pinned_networks WHERE bssid = ?", (bssid,))
            conn.commit()
            conn.close()

    def get_pinned_networks(self) -> List[Dict[str, Any]]:
        with self._conn_lock:
            conn = self._get_connection()
            conn.row_factory = sqlite3.Row
            cursor = conn.cursor()
            cursor.execute("SELECT * FROM pinned_networks ORDER BY pinned_at DESC")
            rows = [dict(r) for r in cursor.fetchall()]
            conn.close()
            return rows

    def is_pinned(self, bssid: str) -> bool:
        with self._conn_lock:
            conn = self._get_connection()
            cursor = conn.cursor()
            cursor.execute("SELECT 1 FROM pinned_networks WHERE bssid = ?", (bssid,))
            exists = cursor.fetchone() is not None
            conn.close()
            return exists

    def set_setting(self, key: str, value: str):
        with self._conn_lock:
            conn = self._get_connection()
            cursor = conn.cursor()
            cursor.execute("INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)", (key, str(value)))
            conn.commit()
            conn.close()

    def get_setting(self, key: str, default: str = "") -> str:
        with self._conn_lock:
            conn = self._get_connection()
            cursor = conn.cursor()
            cursor.execute("SELECT value FROM settings WHERE key = ?", (key,))
            row = cursor.fetchone()
            conn.close()
            return row[0] if row else default
