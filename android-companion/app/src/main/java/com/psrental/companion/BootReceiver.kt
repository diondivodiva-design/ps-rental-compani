package com.psrental.companion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.psrental.companion.Prefs.serverUrl
import com.psrental.companion.Prefs.stationId

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (ctx.serverUrl.isNotEmpty() && ctx.stationId.isNotEmpty()) {
            CompanionService.start(ctx)
        }
    }
}
