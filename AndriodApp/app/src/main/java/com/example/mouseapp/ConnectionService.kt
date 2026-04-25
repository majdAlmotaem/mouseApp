package com.example.mouseapp

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.IOException
import java.io.OutputStream
import java.net.Socket

class ConnectionService : Service() {
    private val binder = LocalBinder()
    private var clientSocket: Socket? = null
    private var outputStream: OutputStream? = null
    
    var isConnected = false
        private set
    
    var currentIp: String? = null
        private set
        
    var currentPort: Int? = null
        private set

    private val scope = CoroutineScope(Dispatchers.IO)
    private val CHANNEL_ID = "ConnectionServiceChannel"
    private val NOTIFICATION_ID = 1

    inner class LocalBinder : Binder() {
        fun getService(): ConnectionService = this@ConnectionService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    fun connect(ip: String, port: Int, onResult: (Boolean, String) -> Unit) {
        scope.launch {
            try {
                // Close existing connection if any
                disconnect()
                
                Log.d("ConnectionService", "Connecting to $ip:$port")
                clientSocket = Socket(ip, port)
                outputStream = clientSocket!!.getOutputStream()
                isConnected = true
                currentIp = ip
                currentPort = port
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        createNotification("Connected to $ip"),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, createNotification("Connected to $ip"))
                }
                withContext(Dispatchers.Main) {
                    onResult(true, "Connected to $ip")
                }
            } catch (e: IOException) {
                Log.e("ConnectionService", "Connection failed", e)
                isConnected = false
                withContext(Dispatchers.Main) {
                    onResult(false, e.message ?: "Connection failed")
                }
            }
        }
    }

    fun disconnect() {
        scope.launch {
            try {
                outputStream?.close()
                clientSocket?.close()
            } catch (e: IOException) {
                Log.e("ConnectionService", "Error disconnecting", e)
            } finally {
                outputStream = null
                clientSocket = null
                isConnected = false
                currentIp = null
                currentPort = null
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
        }
    }

    fun sendCommand(command: String) {
        if (!isConnected) return
        scope.launch {
            try {
                outputStream?.write((command + "\n").toByteArray(Charsets.UTF_8))
                outputStream?.flush()
            } catch (e: IOException) {
                Log.e("ConnectionService", "Error sending command", e)
                isConnected = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                // Optionally notify activity via broadcast or shared flow if needed
            }
        }
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Lazy Controller")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_lan_connect)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Mouse Connection Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Maintains connection to the mouse server"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        disconnect()
        scope.cancel()
        super.onDestroy()
    }
}
