package com.example.mouseapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.widget.Button // Kept for buttonConnect if it's a standard Button
import com.google.android.material.textfield.TextInputEditText // For IP and Port
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.io.IOException
import java.io.OutputStream
import java.net.Socket
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    // UI
    private lateinit var editTextServerIp: TextInputEditText // Changed to TextInputEditText
    private lateinit var editTextServerPort: TextInputEditText // Changed to TextInputEditText
    private lateinit var buttonConnect: Button // Or MaterialButton if that's what you used
    private lateinit var textViewStatus: TextView
    private lateinit var touchpadView: View

    // Networking
    private var clientSocket: Socket? = null
    private var outputStream: OutputStream? = null
    private var isConnected = false
    private val scope = CoroutineScope(Dispatchers.IO)

    // Touchpad state
    private var lastTouchX: Float = 0f
    private var lastTouchY: Float = 0f
    private var isDragging: Boolean = false
    private var downTime: Long = 0L
    private var downX: Float = 0f
    private var downY: Float = 0f
    private var clickMoveThresholdPx: Float = 0f // Pixel value, initialized in onCreate

    // Constants
    companion object {
        private const val TAG = "LazyControllerApp"
        // Touchpad specific constants
        private const val MOUSE_SENSITIVITY = 1.2f // Adjust for mouse speed
        private const val CLICK_THRESHOLD_MS = 200L // Max time for a tap to be a click
        private const val CLICK_MOVE_THRESHOLD_DP = 10f // Max movement in DP for a tap
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize clickMoveThresholdPx from DP
        clickMoveThresholdPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            CLICK_MOVE_THRESHOLD_DP,
            resources.displayMetrics
        )

        // Find views
        editTextServerIp = findViewById(R.id.editTextServerIp)
        editTextServerPort = findViewById(R.id.editTextServerPort)
        buttonConnect = findViewById(R.id.buttonConnect) // Ensure this ID and type matches your XML
        textViewStatus = findViewById(R.id.textViewStatus)
        touchpadView = findViewById(R.id.touchpadView)

        // Connection button logic
        buttonConnect.setOnClickListener {
            if (isConnected) {
                disconnectFromServer()
            } else {
                val ip = editTextServerIp.text.toString()
                val port = editTextServerPort.text.toString().toIntOrNull()
                if (ip.isNotBlank() && port != null) {
                    connectToServer(ip, port)
                } else {
                    updateStatus("Invalid IP or Port", isError = true)
                }
            }
        }

        // Setup other command buttons
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnShutdown).setOnClickListener {
            sendCommand("SHUTDOWN")
        }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnVolumeUp).setOnClickListener {
            sendCommand("VOLUME_UP")
        }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnVolumeDown).setOnClickListener {
            sendCommand("VOLUME_DOWN")
        }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnArrowLeft).setOnClickListener {
            sendCommand("ARROW_LEFT")
        }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnArrowRight).setOnClickListener {
            sendCommand("ARROW_RIGHT")
        }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnScrollUp).setOnClickListener {
            sendCommand("SCROLL_UP")
        }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnScrollDown).setOnClickListener {
            sendCommand("SCROLL_DOWN")
        }

        // Setup Touchpad
        setupTouchpad()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchpad() {
        touchpadView.setOnTouchListener { _, event ->
            val currentX = event.x
            val currentY = event.y

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = currentX
                    lastTouchY = currentY
                    isDragging = false
                    downTime = System.currentTimeMillis()
                    downX = currentX
                    downY = currentY
                    true // Consume the event
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (currentX - lastTouchX) * MOUSE_SENSITIVITY
                    val dy = (currentY - lastTouchY) * MOUSE_SENSITIVITY

                    if (abs(currentX - downX) > clickMoveThresholdPx || abs(currentY - downY) > clickMoveThresholdPx) {
                        isDragging = true
                    }

                    if (isDragging && (abs(dx) > 0.1f || abs(dy) > 0.1f)) {
                        sendMouseMove(dx.toInt(), dy.toInt())
                    }
                    lastTouchX = currentX
                    lastTouchY = currentY
                    true // Consume the event
                }
                MotionEvent.ACTION_UP -> {
                    val upTime = System.currentTimeMillis()
                    if (!isDragging && (upTime - downTime < CLICK_THRESHOLD_MS)) {
                        sendMouseClick("LEFT_CLICK")
                        Log.d(TAG, "Touchpad: Single Tap (Left Click)")
                    }
                    isDragging = false
                    true // Consume the event
                }
                MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    true
                }
                else -> false
            }
        }
    }

    private fun sendMouseMove(dx: Int, dy: Int) {
        if (dx == 0 && dy == 0) return
        sendCommand("MOUSE_MOVE $dx $dy")
    }

    private fun sendMouseClick(clickType: String) {
        sendCommand(clickType)
    }

    private fun connectToServer(ip: String, port: Int) {
        scope.launch {
            try {
                updateStatus("Connecting...")
                clientSocket = Socket(ip, port)
                outputStream = clientSocket!!.getOutputStream()
                isConnected = true
                runOnUiThread { buttonConnect.text = getString(R.string.disconnect_button_text) } // Use string resource
                updateStatus("Connected to $ip:$port")
            } catch (e: IOException) {
                Log.e(TAG, "Connection error", e)
                isConnected = false
                updateStatus("Connection failed: ${e.message}", true)
                runOnUiThread { buttonConnect.text = getString(R.string.connect_button_text) } // Use string resource
            }
        }
    }

    private fun disconnectFromServer() {
        scope.launch {
            try {
                outputStream?.close()
                clientSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing socket", e)
            } finally {
                outputStream = null
                clientSocket = null
                isConnected = false
                updateStatus("Disconnected")
                runOnUiThread { buttonConnect.text = getString(R.string.connect_button_text) } // Use string resource
            }
        }
    }

    private fun sendCommand(command: String) {
        if (!isConnected) {
            Log.w(TAG, "Not connected; dropping command: $command")
            // Consider showing a Toast here for user feedback
            // runOnUiThread { Toast.makeText(this, "Not connected to server", Toast.LENGTH_SHORT).show() }
            return
        }
        scope.launch {
            try {
                outputStream?.write((command + "\n").toByteArray(Charsets.UTF_8))
                outputStream?.flush()
                Log.d(TAG, "Sent: $command")
            } catch (e: IOException) {
                Log.e(TAG, "Error sending command", e)
                updateStatus("Error sending data. Disconnecting.", true)
                disconnectFromServer() // Attempt to disconnect cleanly
            }
        }
    }

    private fun updateStatus(status: String, isError: Boolean = false) {
        runOnUiThread {
            textViewStatus.text = "Status: $status" // Consider using string resources for "Status: "
            val color = if (isError) {
                getColor(R.color.error_text_color) // Define this in your colors.xml
            } else {
                getColor(R.color.status_text_color) // Define this in your colors.xml
            }
            textViewStatus.setTextColor(color)
        }
        Log.d(TAG, if (isError) "Error Status: $status" else "Status: $status")
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnectFromServer()
        scope.cancel() // Cancel all coroutines started by this scope
    }
}
