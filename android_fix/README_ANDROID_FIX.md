# راهنمای رفع باگ عدم ذخیره موقعیت مکانی (GPS) در نسخه اندروید

## ۱. علت دقیق بروز باگ در کد فعلی APK
پس از مهندسی معکوس و بررسی بایت‌کد کلاس‌های `ScanForegroundService` و `LocationProvider` در فایل `classes2.dex`:

1. **مهلت زمانی بیش از حد کوتاه (۲ ثانیه Timeout):**
   در متد `saveStarlink`، برای دریافت موقعیت از دستور زیر استفاده شده است:
   ```kotlin
   val location = withTimeoutOrNull(2000L) { // فقط ۲ ثانیه مهلت!
       locationProvider.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationToken)
   }
   ```
   درخواست موقعیت با اولویت `PRIORITY_HIGH_ACCURACY` نیازمند اتصال سخت‌افزاری به ماهواره‌های GPS است که در فضای بسته یا حتی در فضای باز معمولاً بین **۵ الی ۲۰ ثانیه** زمان می‌برد. در نتیجه این متد تقریباً **همیشه** با گذشت ۲ ثانیه به `Timeout` خورده و مقدار `null` بازمی‌گرداند.

2. **عدم استفاده از موقعیت کش‌شده قبلی (`lastLocation`):**
   سرویس هیچ‌گاه مقدار `lastLocation` در `FusedLocationProviderClient` یا موقعیت‌های کش‌شده شبکه (`NETWORK_PROVIDER`) را بررسی نمی‌کند. به همین دلیل اگر GPS ماهواره‌ای در ۲ ثانیه قفل نشود، مقادیر `latitude` و `longitude` به صورت `NULL` در پایگاه داده SQLite ذخیره می‌شوند.

---

## ۲. کد اصلاح‌شده برای کلاس `LocationProvider.kt`

کلاس `LocationProvider.kt` را با کد اصلاح‌شده و استاندارد زیر جایگزین کنید که به صورت سه‌مرحله‌ای (سریع، کش‌شده، و شبکه) موقعیت را تضمین می‌کند:

```kotlin
package com.starlink.scanner.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class LocationProvider(private val context: Context) {
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context.applicationContext)
    private val locationManager =
        context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    // آخرین موقعیت معتبر جهت دسترسی آنی (0 میلی‌ثانیه)
    private var lastValidLocation: Location? = null

    @SuppressLint("MissingPermission")
    suspend fun getBestLocation(): Location? {
        // ۱. ابتدا آخرین موقعیت کش شده گوشی را بررسی می‌کنیم (آنی و بدون معطلی)
        val cached = getLastKnownLocation()
        if (cached != null) {
            val ageMs = System.currentTimeMillis() - cached.time
            // اگر سن موقعیت کمتر از ۵ دقیقه باشد، بلافاصله استفاده می‌شود
            if (ageMs < 5 * 60 * 1000) {
                lastValidLocation = cached
                return cached
            }
        }

        // ۲. درخواست موقعیت تازه با ترکیب دکل/وای‌فای/GPS با مهلت منطقی (۸ ثانیه)
        val fresh = withTimeoutOrNull(8000L) {
            requestFreshLocation()
        }

        if (fresh != null) {
            lastValidLocation = fresh
            return fresh
        }

        // ۳. در صورت عدم دریافت موقعیت تازه، بازگشت به موقعیت قبلی ذخیره‌شده
        return cached ?: lastValidLocation ?: getFallbackSystemLocation()
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLastKnownLocation(): Location? = suspendCancellableCoroutine { cont ->
        fusedClient.lastLocation
            .addOnSuccessListener { loc ->
                if (cont.isActive) cont.resume(loc)
            }
            .addOnFailureListener {
                if (cont.isActive) cont.resume(null)
            }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestFreshLocation(): Location? = suspendCancellableCoroutine { cont ->
        val cts = CancellationTokenSource()
        cont.invokeOnCancellation { cts.cancel() }

        // استفاده از BALANCED_POWER_ACCURACY برای سرعت بالا و استفاده از Wi-Fi/دکل در محیط سرپوشیده
        fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
            .addOnSuccessListener { loc ->
                if (cont.isActive) cont.resume(loc)
            }
            .addOnFailureListener {
                if (cont.isActive) cont.resume(null)
            }
    }

    @SuppressLint("MissingPermission")
    private fun getFallbackSystemLocation(): Location? {
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        for (provider in providers) {
            try {
                val loc = locationManager?.getLastKnownLocation(provider)
                if (loc != null) return loc
            } catch (_: Exception) {}
        }
        return null
    }
}
```

---

## ۳. کد اصلاح‌شده برای متد `saveStarlink` در `ScanForegroundService.kt`

در `ScanForegroundService.kt` متد ذخیره‌سازی را به صورت زیر ویرایش کنید:

```kotlin
private suspend fun saveStarlink(net: WifiNetwork) {
    try {
        // دریافت موقعیت به صورت تضمین‌شده (بدون قطعی و بدون نال شدن)
        val location = locationProvider.getBestLocation()
        val lat = location?.latitude
        val lon = location?.longitude

        val savedEntity = SavedStarlink(
            bssid = net.bssid,
            ssid = net.ssid,
            score = net.score,
            signalStrength = net.signalStrength,
            channel = net.channel,
            frequency = net.frequency,
            vendorIEFound = net.vendorIEFound,
            vendorIEOUI = net.vendorIEOUI,
            htCapable = net.htCapable,
            vhtCapable = net.vhtCapable,
            heCapable = net.heCapable,
            latitude = lat,       // مختصات عرض جغرافیایی به درستی ذخیره می‌شود
            longitude = lon,     // مختصات طول جغرافیایی به درستی ذخیره می‌شود
            timestamp = System.currentTimeMillis(),
            comment = ""
        )

        db.savedStarlinkDao().upsert(savedEntity)
        Log.d("StarlinkScanner", "Starlink saved successfully with location: $lat, $lon")
    } catch (e: Exception) {
        Log.e("StarlinkScanner", "Failed to save starlink: ${e.message}", e)
    }
}
```
