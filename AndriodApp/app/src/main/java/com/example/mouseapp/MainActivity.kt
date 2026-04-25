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
import android.widget.ArrayAdapter
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText // For Port
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.io.IOException
import java.io.OutputStream
import java.net.Socket
import kotlin.math.abs
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.IBinder

class MainActivity : AppCompatActivity() {

    // UI
    private lateinit var editTextServerIp: MaterialAutoCompleteTextView
    private lateinit var editTextServerPort: TextInputEditText
    private lateinit var buttonConnect: Button
    private lateinit var textViewStatus: TextView
    private lateinit var touchpadView: View

    private lateinit var sharedPreferences: SharedPreferences
    private val savedIps = mutableListOf<String>()
    private lateinit var ipAdapter: ArrayAdapter<String>

    // Networking Service
    private var connectionService: ConnectionService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, service: IBinder?) {
            val binder = service as ConnectionService.LocalBinder
            connectionService = binder.getService()
            isBound = true
            
            // Check if already connected (from previous session)
            if (connectionService?.isConnected == true) {
                updateUIForConnected()
            } else {
                // Auto-connect on open if we have a saved IP
                attemptAutoConnect()
            }
        }

        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            connectionService = null
            isBound = false
        }
    }

    // Networking
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

        // Request notification permission for Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        // Initialize clickMoveThresholdPx from DP
        clickMoveThresholdPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            CLICK_MOVE_THRESHOLD_DP,
            resources.displayMetrics
        )

        // Find views
        editTextServerIp = findViewById(R.id.editTextServerIp)
        editTextServerPort = findViewById(R.id.editTextServerPort)
        buttonConnect = findViewById(R.id.buttonConnect)
        textViewStatus = findViewById(R.id.textViewStatus)
        touchpadView = findViewById(R.id.touchpadView)

        // Setup IP dropdown
        sharedPreferences = getSharedPreferences("LazyControllerPrefs", MODE_PRIVATE)
        loadSavedIps()
        ipAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, savedIps)
        editTextServerIp.setAdapter(ipAdapter)
        if (savedIps.isNotEmpty()) {
            editTextServerIp.setText(savedIps[0], false)
        }

        // Connection button logic
        buttonConnect.setOnClickListener {
            if (connectionService?.isConnected == true) {
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

        // Bind to ConnectionService
        val intent = Intent(this, ConnectionService::class.java)
        startService(intent) // Start it so it persists
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
    }

    private fun attemptAutoConnect() {
        val lastIp = savedIps.firstOrNull()
        val portStr = editTextServerPort.text.toString()
        val port = portStr.toIntOrNull() ?: 9999
        
        if (lastIp != null && lastIp.isNotBlank()) {
            updateStatus("Auto-connecting to $lastIp...")
            connectionService?.connect(lastIp, port) { success, message ->
                if (success) {
                    updateUIForConnected()
                    updateStatus("Auto-connected")
                } else {
                    updateStatus("Auto-connect failed: $message", isError = true)
                }
            }
        }
    }

    private fun updateUIForConnected() {
        runOnUiThread {
            buttonConnect.text = getString(R.string.disconnect_button_text)
            // If we have current IP from service, set it
            connectionService?.currentIp?.let {
                if (editTextServerIp.text.toString() != it) {
                    editTextServerIp.setText(it, false)
                }
            }
        }
    }

    private fun updateUIForDisconnected() {
        runOnUiThread {
            buttonConnect.text = getString(R.string.connect_button_text)
        }
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
        updateStatus("Connecting...")
        connectionService?.connect(ip, port) { success, message ->
            if (success) {
                updateUIForConnected()
                updateStatus("Connected to $ip:$port")
                saveIp(ip)
            } else {
                updateStatus("Connection failed: $message", isError = true)
                updateUIForDisconnected()
            }
        }
    }

    private fun disconnectFromServer() {
        connectionService?.disconnect()
        updateUIForDisconnected()
        updateStatus("Disconnected")
    }

    private fun sendCommand(command: String) {
        if (connectionService?.isConnected != true) {
            Log.w(TAG, "Not connected; dropping command: $command")
            return
        }
        connectionService?.sendCommand(command)
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

    private fun loadSavedIps() {
        val ipsString = sharedPreferences.getString("saved_ips", "")
        if (!ipsString.isNullOrBlank()) {
            savedIps.clear()
            savedIps.addAll(ipsString.split(",").filter { it.isNotBlank() })
        }
        if (savedIps.isEmpty()) {
            // Default IP if none saved
            savedIps.add("192.168.2.123")
        }
    }

    private fun saveIp(ip: String) {
        if (!savedIps.contains(ip)) {
            savedIps.add(0, ip)
            // Limit to 10 saved IPs
            if (savedIps.size > 10) savedIps.removeAt(savedIps.size - 1)
            
            sharedPreferences.edit().putString("saved_ips", savedIps.joinToString(",")).apply()
            
            runOnUiThread {
                ipAdapter.notifyDataSetChanged()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        scope.cancel() // Cancel all coroutines started by this scope
    }
}
