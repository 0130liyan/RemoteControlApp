package com.remote.controlled

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Base64
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.ByteBuffer

class ScreenCaptureService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var webSocket: WebSocketClient? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val width = 720
    private val height = 1280

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1001, NotificationCompat.Builder(this, "rc").setContentTitle("远程被控端").setContentText("屏幕共享中").setSmallIcon(android.R.drawable.ic_menu_share).build())
        if (intent != null) {
            val resultCode = intent.getIntExtra("result_code", Activity.RESULT_CANCELED)
            val data: Intent? = intent.getParcelableExtra("data")
            val serverUrl = intent.getStringExtra("server_url") ?: return START_NOT_STICKY
            val roomId = intent.getStringExtra("room_id") ?: return START_NOT_STICKY
            if (data != null) setupProjection(resultCode, data)
            connectWebSocket(serverUrl, roomId)
        }
        return START_STICKY
    }

    private fun setupProjection(resultCode: Int, data: Intent) {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = manager.getMediaProjection(resultCode, data)
        val metrics = DisplayMetrics()
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getMetrics(metrics)
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        mediaProjection?.createVirtualDisplay("RC", width, height, metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader?.surface, null, null)
        scope.launch { captureLoop() }
    }

    private suspend fun captureLoop() {
        while (isActive) {
            val frame = captureFrame()
            if (frame != null) {
                val json = JsonObject().apply { addProperty("type", "frame"); addProperty("data", frame) }
                webSocket?.send(json.toString())
            }
            delay(66)
        }
    }

    private fun captureFrame(): String? {
        val image = imageReader?.acquireLatestImage() ?: return null
        return try {
            val buffer = image.planes[0].buffer
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, output)
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) { null }
        finally { image.close() }
    }

    private fun connectWebSocket(url: String, roomId: String) {
        webSocket = object : WebSocketClient(URI(url)) {
            override fun onOpen(handshakedata: ServerHandshake?) {
                val reg = JsonObject().apply { addProperty("type", "register"); addProperty("role", "controlled"); addProperty("room_id", roomId) }
                send(reg.toString())
            }
            override fun onMessage(message: String) {
                try {
                    val json = JsonParser.parseString(message).asJsonObject
                    if (json.get("type")?.asString == "control") executeControl(json)
                } catch (_: Exception) {}
            }
            override fun onClose(code: Int, reason: String?, remote: Boolean) {}
            override fun onError(ex: Exception?) {}
        }
        webSocket?.connect()
    }

    private fun executeControl(json: JsonObject) {
        val service = RemoteAccessibilityService.getInstance() ?: return
        when (json.get("action")?.asString) {
            "tap" -> service.performTap(json.get("x").asFloat, json.get("y").asFloat)
            "swipe" -> service.performSwipe(json.get("x1").asFloat, json.get("y1").asFloat, json.get("x2").asFloat, json.get("y2").asFloat, json.get("duration")?.asLong ?: 300L)
            "back" -> service.performBack()
            "home" -> service.performHome()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { super.onDestroy(); mediaProjection?.stop(); webSocket?.close(); scope.cancel() }
}
