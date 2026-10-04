package com.psrental.companion

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.psrental.companion.Prefs.serverUrl
import com.psrental.companion.Prefs.stationId
import com.psrental.companion.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val statusReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(ctx: android.content.Context?, intent: Intent?) {
            val st = intent?.getStringExtra("status") ?: return
            val log = intent.getStringExtra("log") ?: ""
            runOnUiThread {
                binding.txtStatus.text = st
                if (log.isNotEmpty()) {
                    binding.txtLog.text = (log + "\n" + binding.txtLog.text).take(3000)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.inpServer.setText(serverUrl)
        binding.inpStation.setText(stationId)

        binding.btnSave.setOnClickListener {
            val s = binding.inpServer.text.toString().trim()
            val sid = binding.inpStation.text.toString().trim()
            if (s.isEmpty() || sid.isEmpty()) {
                Toast.makeText(this, "Isi semua field", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            serverUrl = s
            stationId = sid
            ensureOverlayPermission()
            CompanionService.start(this)
            Toast.makeText(this, "Companion dimulai", Toast.LENGTH_SHORT).show()
        }

        binding.btnStop.setOnClickListener {
            CompanionService.stop(this)
            binding.txtStatus.text = getString(R.string.status_offline)
            Toast.makeText(this, "Companion dihentikan", Toast.LENGTH_SHORT).show()
        }

        if (serverUrl.isNotEmpty() && stationId.isNotEmpty()) {
            CompanionService.start(this)
        }
    }

    override fun onStart() {
        super.onStart()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            statusReceiver, android.content.IntentFilter(CompanionService.ACTION_STATUS)
        )
    }

    override fun onStop() {
        super.onStop()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(statusReceiver)
    }

    private fun ensureOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Beri izin 'Display over other apps'", Toast.LENGTH_LONG).show()
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            } catch (_: Throwable) {}
        }
    }
}
