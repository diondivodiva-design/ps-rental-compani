package com.psrental.companion

import android.content.Context

object Prefs {
    private const val NAME = "ps_rental_companion"
    private const val KEY_SERVER = "server_url"
    private const val KEY_STATION = "station_id"

    fun get(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var Context.serverUrl: String
        get() = get(this).getString(KEY_SERVER, "") ?: ""
        set(v) { get(this).edit().putString(KEY_SERVER, v).apply() }

    var Context.stationId: String
        get() = get(this).getString(KEY_STATION, "") ?: ""
        set(v) { get(this).edit().putString(KEY_STATION, v).apply() }

    fun wsUrl(ctx: Context): String? {
        val base = ctx.serverUrl.trim().trimEnd('/')
        val sid = ctx.stationId.trim()
        if (base.isEmpty() || sid.isEmpty()) return null
        val ws = when {
            base.startsWith("https://") -> "wss://" + base.removePrefix("https://")
            base.startsWith("http://")  -> "ws://"  + base.removePrefix("http://")
            else -> "wss://$base"
        }
        return "$ws/api/tv/ws/$sid"
    }
}
