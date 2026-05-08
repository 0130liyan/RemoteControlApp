package com.remote.controller

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.MotionEvent
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI

class ControlActivity : AppCompatActivity() {
    private var ws: WebSocketClient? = null
    private lateinit var ivScreen: ImageView
    private var screenW = 720f
    private var screenH = 1280f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_control)
        ivScreen = findViewById(R.id.ivScreen)
        val url = intent.getStringExtra("url") ?: return
        val room = intent.getStringExtra("room") ?: return
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnHome).setOnClickListener { sendControl("home") }
        connect(url, room)
        setupTouch()
    }

    private fun connect(url: String, room: String) {
        ws = object : WebSocketClient(URI(url)) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                val reg = JsonObject().apply { addProperty("type", "register"); addProperty("role", "controller"); addProperty("room_id", room) }
                send(reg.toString())
            }
            override fun onMessage(message: String) {
                try {
                    val json = JsonParser.parseString(message).asJsonObject
                    if (json.get("type")?.asString == "frame") {
                        val data = json.get("data")?.asString ?: return
                        val bytes = Base64.decode(data, Base64.DEFAULT)
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        runOnUiThread {
                            ivScreen.setImageBitmap(bmp)
                            screenW = bmp.width.toFloat()
                            screenH = bmp.height.toFloat()
                        }
                    }
                } catch (_: Exception) {}
            }
            override fun onClose(code: Int, reason: String?, remote: Boolean) {}
            override fun onError(ex: Exception?) {}
        }
        ws?.connect()
    }

    private fun setupTouch() {
        var startX = 0f; var startY = 0f
        ivScreen.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { startX = event.x; startY = event.y; true }
                MotionEvent.ACTION_UP -> {
                    val dx = event.x - startX; val dy = event.y - startY
                    val rx = startX / ivScreen.width * screenW
                    val ry = startY / ivScreen.height * screenH
                    val rx2 = event.x / ivScreen.width * screenW
                    val ry2 = event.y / ivScreen.height * screenH
                    if (kotlin.math.abs(dx) > 30 || kotlin.math.abs(dy) > 30) {
                        sendControl("swipe", mapOf("x1" to rx, "y1" to ry, "x2" to rx2, "y2" to ry2, "duration" to 300L))
                    } else {
                        sendControl("tap", mapOf("x" to rx, "y" to ry))
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun sendControl(action: String, params: Map<String, Any> = emptyMap()) {
        val json = JsonObject().apply {
            addProperty("type", "control")
            addProperty("action", action)
            params.forEach { (k, v) -> when (v) { is Number -> addProperty(k, v); is String -> addProperty(k, v) } }
        }
        ws?.send(json.toString())
    }

    override fun onDestroy() { super.onDestroy(); ws?.close() }
}
