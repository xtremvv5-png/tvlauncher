package com.example.tvlauncher

import android.content.Context
import android.provider.Settings

object Dns {
    const val HOST = "dns.adguard-dns.com"

    /** Включает/выключает системный Private DNS (DoT) на AdGuard. false = нет разрешения. */
    fun set(ctx: Context, on: Boolean): Boolean = try {
        val cr = ctx.contentResolver
        if (on) {
            Settings.Global.putString(cr, "private_dns_specifier", HOST)
            Settings.Global.putString(cr, "private_dns_mode", "hostname")
        } else {
            Settings.Global.putString(cr, "private_dns_mode", "opportunistic")
        }
        true
    } catch (e: SecurityException) {
        false
    }
}
