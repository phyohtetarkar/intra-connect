package com.intrachat.server.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.*
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.corundumstudio.socketio.Configuration
import com.corundumstudio.socketio.SocketIOServer
import com.fasterxml.jackson.databind.ObjectMapper
import com.intrachat.server.MainActivity
import com.intrachat.server.R
import org.joda.time.DateTime

class SocketServerService : Service() {

    private var serviceLooper: Looper? = null
    private var serviceHandler: ServiceHandler? = null
    private var messenger: Messenger? = null
    private var server: SocketIOServer? = null
    private val map = mutableMapOf<String, String>()
    private val mapper = ObjectMapper()

    private inner class ServiceHandler(looper: Looper) : Handler(looper) {

        override fun handleMessage(msg: Message) {
            when (msg.what) {
                1 -> {
                    val intent = Intent(ACTION_SERVER_STATUS)
                    intent.putExtra(KEY_RUNNING, server != null)
                    LocalBroadcastManager.getInstance(this@SocketServerService)
                        .sendBroadcast(intent)
                }
            }
        }

    }

    override fun onCreate() {
        super.onCreate()
        HandlerThread("ServiceStartArguments", Process.THREAD_PRIORITY_BACKGROUND).apply {
            start()

            // Get the HandlerThread's Looper and use it for our Handler
            serviceLooper = looper
            serviceHandler = ServiceHandler(looper)
            messenger = Messenger(serviceHandler)
        }


    }

    override fun onBind(intent: Intent?): IBinder? {
        return messenger?.binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SERVER_STATUS) {
            val statusIntent = Intent(ACTION_SERVER_STATUS)
            statusIntent.putExtra(KEY_RUNNING, server != null)
            LocalBroadcastManager.getInstance(this).sendBroadcast(statusIntent)
        } else if (intent?.action == ACTION_STOP_SERVER || server != null) {
            stopForeground(true)
            stopServer()
            val stopIntent = Intent(ACTION_STOP_SERVER)
            LocalBroadcastManager.getInstance(this).sendBroadcast(stopIntent)
        } else {
            val host = MainActivity.getIPAddress()
            try {
                startServer(host)

                createNotificationChannel()
                val contentIntent = Intent(this, MainActivity::class.java).apply {
                    setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }

                val pendingIntent = PendingIntent.getActivity(this, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT)

//                val stopIntent = Intent(this, SocketServerService::class.java).apply {
//                    action = ACTION_STOP_SERVER
//                }
//
//                val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, 0)
                val notification = NotificationCompat.Builder(this, NOTIFICATION_ID.toString())
                    .setSmallIcon(R.mipmap.ic_launcher_round)
                    .setContentTitle("Chat Server Created")
                    .setContentText("Server running on: $host")
                    .setContentIntent(pendingIntent)
                    .build()

                startForeground(NOTIFICATION_ID, notification)

                val statusIntent = Intent(ACTION_SERVER_STATUS)
                statusIntent.putExtra(KEY_RUNNING, server != null)
                LocalBroadcastManager.getInstance(this).sendBroadcast(statusIntent)
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e(SocketServerService::class.java.simpleName, e.message ?: "Unknown error occur.")
                val failIntent = Intent(ACTION_SERVER_FAIL)
                failIntent.putExtra(KEY_REASON, e.message)
                LocalBroadcastManager.getInstance(this).sendBroadcast(failIntent)
                stopServer()
            }

        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    private fun startServer(host: String?) {
        if (host == null) {
            throw RuntimeException("Unable to start server.")
        }
        Log.d(SocketServerService::class.java.simpleName, host)
        if (server == null) {
            val config = Configuration().apply {
                port = SERVER_PORT
                keyStorePassword = KEYSTORE_PASSWORD
                keyStoreFormat = "BKS"
                keyStore = assets.open("keystore.bks")
                maxFramePayloadLength = 4096 * 4096
                maxHttpContentLength = 4096 * 4096
            }

            server = SocketIOServer(config)

            server?.addConnectListener {
                Log.d(SocketServerService::class.java.simpleName, "Connect ${it.remoteAddress}")

                it.sendEvent(SC_CHAT_USERS, map.map { en -> "${en.key.substring(1, en.key.indexOf(":"))}:${en.value}" })
            }

            server?.addDisconnectListener {
                val user = map.remove(it.remoteAddress.toString())
                Log.d(SocketServerService::class.java.simpleName, "Disconnect $user")

                val clientAddress = it.remoteAddress.toString().substring(1)
                val address = clientAddress.substring(0, clientAddress.indexOf(':'))

                user?.also { u ->
                    val chat = ChatObject(
                        id = Base64.encodeToString("${address}-${DateTime.now().millis}".toByteArray(), Base64.DEFAULT),
                        host = address,
                        sender = u,
                        content = "",
                        type = ChatObject.Type.ACTION_LEFT
                    )
                    //val encoded = Base64.encodeToString(mapper.writeValueAsString(chat).toByteArray(), Base64.DEFAULT)
                    val data = mapper.writeValueAsString(chat)
                    server?.broadcastOperations?.sendEvent(SC_CHAT_EVENT, data)
                    server?.broadcastOperations?.sendEvent(SC_TYPE_STOP_EVENT, address)
                    server?.broadcastOperations?.sendEvent(SC_CHAT_USERS, map.map { en -> "${en.key.substring(1, en.key.indexOf(":"))}:${en.value}" })
                }
            }

            server?.addEventListener(SC_CHAT_EVENT, String::class.java) { client, data, _ ->
                //Log.d(SocketServerService::class.java.simpleName, "Data: $data")
                //val decoded = String(Base64.decode(data, Base64.DEFAULT))
                val chat = mapper.readValue(data, ChatObject::class.java)
                if (chat.type == ChatObject.Type.ACTION_JOINED) {
                    map[client.remoteAddress.toString()] = chat.sender
                }
                server?.broadcastOperations?.sendEvent(SC_CHAT_EVENT, data)
            }

            server?.addEventListener(SC_BINARY_EVENT, Any::class.java) { client, data, _ ->
//                val clientAddress = client.remoteAddress.toString().substring(1)
//
//                val user = map[client.remoteAddress.toString()]
//                val address = clientAddress.substring(0, clientAddress.indexOf(':'))
//                val id = Base64.encodeToString("${address}-${DateTime.now().millis}".toByteArray(), Base64.DEFAULT)
                server?.broadcastOperations?.sendEvent(SC_BINARY_EVENT, data)
            }

            server?.addEventListener(SC_TYPE_EVENT, String::class.java) { client, _, _ ->
                val clientAddress = client.remoteAddress.toString().substring(1)
                //Log.d(SocketServerService::class.java.simpleName, "Type start: $clientAddress")
                server?.broadcastOperations?.sendEvent(SC_TYPE_EVENT, client, clientAddress.substring(0, clientAddress.indexOf(':')))
            }

            server?.addEventListener(SC_TYPE_STOP_EVENT, String::class.java) { client, _, _ ->
                val clientAddress = client.remoteAddress.toString().substring(1)
                //Log.d(SocketServerService::class.java.simpleName, "Type stop: $clientAddress")
                server?.broadcastOperations?.sendEvent(SC_TYPE_STOP_EVENT, client, clientAddress.substring(0, clientAddress.indexOf(':')))
            }

            server?.start()
        }
    }

    private fun stopServer() {
        server?.also {
            it.stop()
        }
        server = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_ID.toString(), "Foreground Service Channel", NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    companion object {
        const val ACTION_STOP_SERVER = "com.genz.connect.STOP_SERVER"
        const val ACTION_SERVER_STATUS = "com.genz.connect.SERVER_STATUS"
        const val ACTION_SERVER_FAIL = "com.genz.connect.SERVER_FAIL"

        const val NOTIFICATION_ID = 22222
        const val SERVER_PORT = 22222
        const val KEYSTORE_PASSWORD = "genz22222"

        const val SC_CHAT_EVENT = "chat_event"
        const val SC_BINARY_EVENT = "binary_event"
        const val SC_TYPE_EVENT = "type_event"
        const val SC_TYPE_STOP_EVENT = "type_stop_event"
        const val SC_CHAT_USERS = "chat_users"

        const val KEY_RUNNING = "SERVER_RUNNING"
        const val KEY_REASON = "FAIL_REASON"
    }

}