package com.remote.controller

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class ConnectActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connect)
        findViewById<MaterialButton>(R.id.btnConnect).setOnClickListener {
            val url = findViewById<TextInputEditText>(R.id.etServerUrl).text.toString().trim()
            val room = findViewById<TextInputEditText>(R.id.etRoomId).text.toString().trim()
            if (url.isEmpty() || room.isEmpty()) { Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            startActivity(Intent(this, ControlActivity::class.java).apply { putExtra("url", url); putExtra("room", room) })
        }
    }
}
