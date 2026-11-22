package com.starlinks.scanner.service

import android.content.Context
import android.util.Log
import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class AdbConnectionState {
    object Disconnected : AdbConnectionState()
    object Connecting : AdbConnectionState()
    data class Connected(val host: String, val port: Int) : AdbConnectionState()
    data class Error(val message: String) : AdbConnectionState()
}

object AdbConnectionManager {

    private const val TAG = "AdbConnectionManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var dadbInstance: Dadb? = null

    private val _connectionState = MutableStateFlow<AdbConnectionState>(AdbConnectionState.Disconnected)
    val connectionState: StateFlow<AdbConnectionState> = _connectionState.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    var lastHost: String = "127.0.0.1"
    var lastPort: Int = 5555

    fun isConnected(): Boolean {
        return _connectionState.value is AdbConnectionState.Connected && dadbInstance != null
    }

    /**
     * اتصال مستقیم به پورت اشکال‌زدایی بی‌سیم (Wireless Debugging) و اجرای بهینه‌سازی خودکار
     */
    fun connectAndOptimize(context: Context, host: String, port: Int, onComplete: ((Boolean) -> Unit)? = null) {
        lastHost = host
        lastPort = port

        scope.launch {
            _connectionState.value = AdbConnectionState.Connecting
            addLog("در حال تلاش برای برقراری ارتباط با $host:$port...")

            try {
                // Ensure persistent key storage in app filesDir
                val privFile = File(context.filesDir, "adbkey")
                val pubFile = File(context.filesDir, "adbkey.pub")

                val keyPair = try {
                    AdbKeyPair.read(privFile, pubFile)
                } catch (e: Exception) {
                    null
                }

                addLog("ایجاد سوکت اتصال ADB...")
                val dadb = withContext(Dispatchers.IO) {
                    if (keyPair != null) {
                        Dadb.create(host, port, keyPair)
                    } else {
                        Dadb.create(host, port)
                    }
                }

                dadbInstance?.close()
                dadbInstance = dadb

                _connectionState.value = AdbConnectionState.Connected(host, port)
                addLog("✅ با موفقیت به سرویس ADB (UID 2000) متصل شد!")
                addLog("----------------------------------------")

                // Step 1: Disable Scan Throttling
                addLog("⚡ [دستور ۱/۳]: settings put global wifi_scan_throttle_enabled 0")
                val res1 = withContext(Dispatchers.IO) {
                    dadb.shell("settings put global wifi_scan_throttle_enabled 0")
                }
                if (res1.output.isNotBlank()) addLog("خروجی: ${res1.output.trim()}")
                addLog("نتیجه: محدودیت فرکانس اسکن خاموش شد (اسکن توربو فعال گردید).")

                // Step 2: Trigger hardware Wi-Fi scan
                addLog("📡 [دستور ۲/۳]: cmd wifi start-scan")
                val res2 = withContext(Dispatchers.IO) {
                    dadb.shell("cmd wifi start-scan")
                }
                if (res2.output.isNotBlank()) addLog("خروجی: ${res2.output.trim()}")
                addLog("نتیجه: دستور آغاز اسکن سخت‌افزاری اجرا شد.")

                // Step 3: Grant DUMP permission
                addLog("🛡 [دستور ۳/۳]: pm grant com.starlinks.scanner android.permission.DUMP")
                val res3 = withContext(Dispatchers.IO) {
                    dadb.shell("pm grant com.starlinks.scanner android.permission.DUMP")
                }
                if (res3.output.isNotBlank()) addLog("خروجی: ${res3.output.trim()}")
                addLog("نتیجه: دسترسی به اطلاعات تشخیصی شبکه تایید شد.")

                addLog("----------------------------------------")
                addLog("🚀 سیستم بهینه‌سازی شد. اسکنر اکنون با نهایت سرعت و زنده عمل می‌کند.")

                withContext(Dispatchers.Main) {
                    onComplete?.invoke(true)
                }

            } catch (e: Exception) {
                Log.e(TAG, "ADB Connection Error", e)
                val errMsg = e.localizedMessage ?: "خطای ناشناخته در اتصال به ADB"
                _connectionState.value = AdbConnectionState.Error(errMsg)
                addLog("❌ خطا در اتصال: $errMsg")
                addLog("نکته: مطمئن شوید اشکال‌زدایی بی‌سیم فعال است و پورت واردشده دقیقاً همان پورت صفحه Wireless Debugging است.")
                addLog("در صورت مشاهده پاپ‌آپ امنیتی روی صفحه گوشی، گزینه «Always allow» را تایید کنید.")

                withContext(Dispatchers.Main) {
                    onComplete?.invoke(false)
                }
            }
        }
    }

    /**
     * اجرای یک دستور سفارشی شل و ثبت خروجی در لاگ
     */
    fun executeCustomCommand(command: String, onResult: ((String) -> Unit)? = null) {
        val dadb = dadbInstance
        if (dadb == null) {
            addLog("⚠️ خطا: هنوز به ADB متصل نیستید.")
            onResult?.invoke("دستگاه متصل نیست.")
            return
        }

        scope.launch {
            addLog("> $command")
            try {
                val res = withContext(Dispatchers.IO) {
                    dadb.shell(command)
                }
                val out = if (res.output.isBlank()) "(بدون خروجی / انجام شد)" else res.output.trim()
                addLog(out)
                withContext(Dispatchers.Main) {
                    onResult?.invoke(out)
                }
            } catch (e: Exception) {
                val err = "خطا: ${e.message}"
                addLog(err)
                withContext(Dispatchers.Main) {
                    onResult?.invoke(err)
                }
            }
        }
    }

    /**
     * تریگر اسکن سخت‌افزاری از طریق شل در حین اسکن زنده
     */
    fun triggerHardwareScan() {
        val dadb = dadbInstance ?: return
        scope.launch(Dispatchers.IO) {
            try {
                dadb.shell("cmd wifi start-scan")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to trigger scan via ADB: ${e.message}")
            }
        }
    }

    /**
     * قطع اتصال
     */
    fun disconnect() {
        try {
            dadbInstance?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing dadb", e)
        }
        dadbInstance = null
        _connectionState.value = AdbConnectionState.Disconnected
        addLog("اتصال ADB قطع شد.")
    }

    private fun addLog(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val formatted = "[$time] $message"
        _logs.value = _logs.value + formatted
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
