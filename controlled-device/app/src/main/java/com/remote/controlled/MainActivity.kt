package com.remote.controlled

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import android.widget.TextView

class MainActivity : AppCompatActivity() {
    private lateinit var etServerUrl: TextInputEditText
    private lateinit var etRoomId: TextInputEditText
    private lateinit var btnStart: MaterialButton
    private lateinit var btnStop: MaterialButton
    private lateinit var tvStatus: TextView
    private lateinit var tvHint: TextView
    private lateinit var btnAccessibility: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        etServerUrl = findViewById(R.id.etServerUrl)
        etRoomId = findViewById(R.id.etRoomId)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        tvStatus = findViewById(R.id.tvStatus)
        tvHint = findViewById(R.id.tvHint)
        btnAccessibility = findViewById(R.id.btnAccessibility)
        btnAccessibility.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        btnStart.setOnClickListener { startService() }
        btnStop.setOnClickListener { stopService() }
    }

    override fun onResume() {
        super.onResume()
        tvHint.visibility = if (RemoteAccessibilityService.isRunning()) TextView.GONE else TextView.VISIBLE
        btnAccessibility.visibility = if (RemoteAccessibilityService.isRunning()) MaterialButton.GONE else MaterialButton.VISIBLE
    }

    private fun startService() {
        val serverUrl = etServerUrl.text.toString().trim()
        val roomId = etRoomId.text.toString().trim()
        if (serverUrl.isEmpty() || roomId.isEmpty()) { Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show(); return }
        if (!RemoteAccessibilityService.isRunning()) { Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_LONG).show(); return }
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(projectionManager.createScreenCaptureIntent(), 1001)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == Activity.RESULT_OK && data != null) {
            val intent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra("server_url", etServerUrl.text.toString().trim())
                putExtra("room_id", etRoomId.text.toString().trim())
                putExtra("result_code", resultCode)
                putExtra("data", data)
            }
            startForegroundService(intent)
            btnStart.isEnabled = false
            btnStop.isEnabled = true
            tvStatus.text = "状态: 服务运行中"
        }
    }

    private fun stopService() {
        stopService(Intent(this, ScreenCaptureService::class.java))
        btnStart.isEnabled = true
        btnStop.isEnabled = false
        tvStatus.text = "状态: 已停止"
    }
}
