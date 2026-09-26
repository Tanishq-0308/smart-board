package com.smartboard.teach.feature.settings

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.StatFs
import android.text.format.Formatter
import android.view.InputDevice
import android.webkit.WebView
import androidx.annotation.StringRes
import com.smartboard.teach.BuildConfig
import com.smartboard.teach.R

/** One line of the diagnostics report. [ok] null means informational. */
data class DiagnosticItem(@StringRes val label: Int, val value: String, val ok: Boolean? = null)

/**
 * A snapshot of what this panel offers, for installers setting up a school.
 * Every probe is guarded: a diagnostics screen that crashes on odd hardware
 * is useless on exactly the boards it exists for.
 */
fun collectDiagnostics(context: Context): List<DiagnosticItem> {
    val pm = context.packageManager
    val s = { id: Int, arg: Any? -> context.getString(id, arg) }
    val yes = context.getString(R.string.diag_yes)
    val no = context.getString(R.string.diag_no)
    val size = { bytes: Long -> Formatter.formatShortFileSize(context, bytes) }
    val items = mutableListOf<DiagnosticItem>()

    items += DiagnosticItem(
        R.string.diag_android,
        "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        Build.VERSION.SDK_INT >= 28,
    )
    items += DiagnosticItem(R.string.diag_device, "${Build.MANUFACTURER} ${Build.MODEL}")
    items += DiagnosticItem(R.string.diag_app_version, BuildConfig.VERSION_NAME)

    runCatching {
        val am = context.getSystemService(ActivityManager::class.java)
        val mem = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        items += DiagnosticItem(
            R.string.diag_ram,
            s(R.string.diag_ram_value, "${size(mem.totalMem)} · ${size(mem.availMem)}"),
            mem.totalMem >= 1_800L * 1024 * 1024 && !mem.lowMemory,
        )
        items += DiagnosticItem(R.string.diag_app_heap, size(Runtime.getRuntime().maxMemory()))
        if (am.isLowRamDevice) items += DiagnosticItem(R.string.diag_low_ram, yes, false)
    }

    runCatching {
        val stat = StatFs(context.filesDir.absolutePath)
        items += DiagnosticItem(
            R.string.diag_storage,
            s(R.string.diag_storage_value, "${size(stat.availableBytes)} · ${size(stat.totalBytes)}"),
            stat.availableBytes >= 500L * 1024 * 1024,
        )
    }

    val metrics = context.resources.displayMetrics
    items += DiagnosticItem(
        R.string.diag_screen,
        "${metrics.widthPixels}×${metrics.heightPixels} px · ${metrics.densityDpi} dpi",
    )

    val touch = when {
        pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_JAZZHAND) -> "5+"
        pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT) -> "2"
        pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH) -> "2*"
        pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) -> "1"
        else -> "0"
    }
    items += DiagnosticItem(R.string.diag_touch_declared, touch, touch != "0")

    val stylus = runCatching {
        InputDevice.getDeviceIds().any {
            InputDevice.getDevice(it)?.supportsSource(InputDevice.SOURCE_STYLUS) == true
        }
    }.getOrDefault(false)
    items += DiagnosticItem(R.string.diag_stylus, if (stylus) yes else no)

    val gms = runCatching { pm.getPackageInfo("com.google.android.gms", 0).versionName }.getOrNull()
    items += DiagnosticItem(R.string.diag_gms, gms ?: no)

    val webView = runCatching { WebView.getCurrentWebViewPackage() }.getOrNull()
    items += DiagnosticItem(
        R.string.diag_webview,
        webView?.let { "${it.packageName} ${it.versionName}" } ?: context.getString(R.string.diag_missing),
        webView != null,
    )

    runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val kind = when {
            caps == null -> null
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> context.getString(R.string.status_network_ethernet)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> context.getString(R.string.status_network_wifi)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> context.getString(R.string.status_network_mobile)
            else -> context.getString(R.string.status_network_other)
        }
        val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        items += DiagnosticItem(
            R.string.diag_network,
            when {
                kind == null -> context.getString(R.string.diag_offline)
                online -> s(R.string.diag_online, kind)
                else -> s(R.string.diag_no_internet, kind)
            },
            online,
        )
    }

    return items
}
