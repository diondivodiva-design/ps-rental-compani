package com.psrental.companion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.CountDownTimer
import android.view.KeyEvent
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.psrental.companion.databinding.ActivityOverlayBinding

class OverlayActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOverlayBinding
    private var countdown: CountDownTimer? = null
    private var mode: String = "off"

    private val dismiss = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { finish() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        binding = ActivityOverlayBinding.inflate(layoutInflater)
        setContentView(binding.root)
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(dismiss, IntentFilter(ACTION_DISMISS))
        applyIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyIntent(intent)
    }

    private fun applyIntent(i: Intent) {
        mode = i.getStringExtra("mode") ?: "off"
        binding.txtTitle.text = i.getStringExtra("title") ?: getString(R.string.session_ended)
        binding.txtMessage.text = i.getStringExtra("message") ?: getString(R.string.contact_operator)
        val secs = i.getIntExtra("seconds", 0)
        countdown?.cancel()
        if (secs > 0 && mode == "msg") {
            // show message for N seconds then auto-close
            binding.txtTimer.text = "$secs"
            countdown = object : CountDownTimer(secs * 1000L, 1000L) {
                override fun onTick(ms: Long) { binding.txtTimer.text = "${(ms / 1000) + 1}" }
                override fun onFinish() { finish() }
            }.start()
        } else if (secs > 0 && mode == "off") {
            // shutdown timer countdown before locking
            binding.txtTimer.text = "$secs"
            countdown = object : CountDownTimer(secs * 1000L, 1000L) {
                override fun onTick(ms: Long) { binding.txtTimer.text = "Shutdown dalam ${(ms / 1000) + 1}s" }
                override fun onFinish() { binding.txtTimer.text = "" }
            }.start()
        } else {
            binding.txtTimer.text = ""
        }
    }

    override fun onBackPressed() {
        if (mode == "lock" || mode == "off") return  // block back
        super.onBackPressed()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Block TV remote keys while locked / off (except volume so user masih bisa reaksi)
        if (mode == "lock" || mode == "off") {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                return super.onKeyDown(keyCode, event)
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        countdown?.cancel()
        try { LocalBroadcastManager.getInstance(this).unregisterReceiver(dismiss) } catch (_: Throwable) {}
    }

    companion object {
        const val ACTION_DISMISS = "com.psrental.companion.OVERLAY_DISMISS"
    }
}
