package com.psrental.companion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class CompanionService : Service() {

    private var ws: WebSocket? = null
    private val client by lazy {
        OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
    private val main = Handler(Looper.getMainLooper())
    private val heartbeat = Runnable { sendHeartbeat() }
    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        if (!running) {
            running = true
            acquireWake()
            connect()
        }
        return START_STICKY
    }

    private fun acquireWake() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "psrental:companion")
        wakeLock?.setReferenceCounted(false)
        wakeLock?.acquire()
    }

    private fun connect() {
        val url = Prefs.wsUrl(this) ?: run { log("URL belum di-set"); return }
        broadcast("Reconnecting…", "-> connect $url")
        val req = Request.Builder().url(url).build()
        ws = client.newWebSocket(req, Listener(url))
    }

    private fun sendHeartbeat() {
        val meta = JSONObject().apply {
            put("model", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("version", "1.0")
            put("sdk", Build.VERSION.SDK_INT)
        }
        val msg = JSONObject().apply {
            put("type", "heartbeat")
            put("meta", meta)
        }
        ws?.send(msg.toString())
        main.postDelayed(heartbeat, 15_000)
    }

    private inner class Listener(private val url: String) : WebSocketListener() {
        override fun onOpen(w: WebSocket, response: Response) {
            broadcast("ONLINE", "<- connected")
            main.removeCallbacks(heartbeat)
            main.post(heartbeat)
        }
        override fun onMessage(w: WebSocket, text: String) {
            log("<- $text")
            try { handleCommand(JSONObject(text)) } catch (t: Throwable) { log("parse err: ${t.message}") }
        }
        override fun onFailure(w: WebSocket, t: Throwable, response: Response?) {
            broadcast("Reconnecting…", "x failure: ${t.message}")
            main.removeCallbacks(heartbeat)
            main.postDelayed({ if (running) connect() }, 5_000)
        }
        override fun onClosed(w: WebSocket, code: Int, reason: String) {
            broadcast("Reconnecting…", "x closed $code $reason")
            main.removeCallbacks(heartbeat)
            main.postDelayed({ if (running) connect() }, 5_000)
        }
    }

    private fun handleCommand(cmd: JSONObject) {
        if (cmd.optString("type") != "command") return
        val action = cmd.optString("action")
        val message = cmd.optString("message", "")
        val seconds = cmd.optInt("seconds", 0)
        when (action) {
            "power_off" -> showOverlay("off", getString(R.string.session_ended), getString(R.string.contact_operator), 0)
            "power_on"  -> dismissOverlay()
            "lock"      -> showOverlay("lock", getString(R.string.session_locked), getString(R.string.contact_operator), 0)
            "unlock"    -> dismissOverlay()
            "show_message" -> showOverlay("msg", "PESAN OPERATOR", message, 5)
            "shutdown_timer" -> showOverlay("off", getString(R.string.session_ended), getString(R.string.contact_operator), seconds)
        }
    }

    private fun showOverlay(mode: String, title: String, message: String, seconds: Int) {
        val i = Intent(this, OverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                     Intent.FLAG_ACTIVITY_CLEAR_TOP or
                     Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("mode", mode)
            putExtra("title", title)
            putExtra("message", message)
            putExtra("seconds", seconds)
        }
        startActivity(i)
    }

    private fun dismissOverlay() {
        val i = Intent(OverlayActivity.ACTION_DISMISS)
        LocalBroadcastManager.getInstance(this).sendBroadcast(i)
    }

    private fun broadcast(status: String, logLine: String?) {
        val i = Intent(ACTION_STATUS).apply {
            putExtra("status", status)
            if (logLine != null) putExtra("log", "[${System.currentTimeMillis()}] $logLine")
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(i)
        Log.i(TAG, "status=$status log=$logLine")
    }
    private fun log(line: String) = broadcast(if (ws != null) "ONLINE" else "Reconnecting…", line)

    private fun startInForeground() {
        val channelId = "companion_status"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(channelId) == null) {
                mgr.createNotificationChannel(NotificationChannel(
                    channelId, getString(R.string.notif_channel),
                    NotificationManager.IMPORTANCE_LOW
                ))
            }
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText("Standby — menerima perintah dari operator")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        startForeground(1, notif)
    }

    override fun onDestroy() {
        running = false
        main.removeCallbacks(heartbeat)
        try { ws?.close(1000, "service destroyed") } catch (_: Throwable) {}
        try { wakeLock?.release() } catch (_: Throwable) {}
        super.onDestroy()
    }

    companion object {
        const val TAG = "CompanionSvc"
        const val ACTION_STATUS = "com.psrental.companion.STATUS"

        fun start(ctx: Context) {
            val i = Intent(ctx, CompanionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, CompanionService::class.java))
        }
    }
}
