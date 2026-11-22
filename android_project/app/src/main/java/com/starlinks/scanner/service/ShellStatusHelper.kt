package com.starlinks.scanner.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import java.io.File

enum class ThrottlingStatus(val label: String, val isOptimal: Boolean) {
    DISABLED("غیرفعال (سرعت حداکثری و زنده)", true),
    ENABLED("فعال (محدود به ۴ اسکن در ۲ دقیقه)", false),
    UNKNOWN("نامشخص", false)
}

enum class ShellPrivilegeLevel(val title: String, val badge: String, val isElevated: Boolean) {
    ROOT("دسترسی ریشه (Root - UID 0)", "ROOT", true),
    ADB_SHELL("اشکال‌زدایی بی‌سیم / ADB (UID 2000)", "ADB SHELL", true),
    STANDARD("کاربر استاندارد اندروید", "STANDARD", false)
}

object ShellStatusHelper {

    const val ADB_CMD_DISABLE_THROTTLE = "adb shell settings put global wifi_scan_throttle_enabled 0"
    const val ADB_CMD_FORCE_SCAN = "adb shell cmd wifi start-scan"

    /**
     * بررسی وضعیت Wi-Fi Scan Throttling از طریق Settings.Global
     */
    fun getScanThrottlingStatus(context: Context): ThrottlingStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            // Android 8 and lower do not throttle foreground Wi-Fi scans
            return ThrottlingStatus.DISABLED
        }

        return try {
            val throttleValue = Settings.Global.getInt(
                context.contentResolver,
                "wifi_scan_throttle_enabled",
                1
            )
            if (throttleValue == 0) {
                ThrottlingStatus.DISABLED
            } else {
                ThrottlingStatus.ENABLED
            }
        } catch (e: Exception) {
            ThrottlingStatus.UNKNOWN
        }
    }

    /**
     * بررسی سطح دسترسی پروسه فعلی (Root / Shell / Standard)
     */
    fun getShellPrivilegeLevel(): ShellPrivilegeLevel {
        val myUid = Process.myUid()
        if (myUid == 0) {
            return ShellPrivilegeLevel.ROOT
        }
        if (myUid == 2000) {
            return ShellPrivilegeLevel.ADB_SHELL
        }

        // Check if device is rooted and su binary is accessible
        if (isSuBinaryPresent()) {
            return ShellPrivilegeLevel.ROOT
        }

        return ShellPrivilegeLevel.STANDARD
    }

    /**
     * بررسی وجود فایل اجرایی su در مسیرهای سیستمی
     */
    fun isSuBinaryPresent(): Boolean {
        val paths = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }
        return false
    }

    /**
     * هدایت مستقیم کاربر به منوی تنظیمات توسعه‌دهندگان (Developer Options)
     */
    fun openDeveloperOptions(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                true
            } catch (ex: Exception) {
                false
            }
        }
    }

    /**
     * کپی کردن دستور در کلیپ‌بورد سیستم همراه با بازخورد تصویری Toast
     */
    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "دستور در حافظه کپی شد", Toast.LENGTH_SHORT).show()
    }
}
