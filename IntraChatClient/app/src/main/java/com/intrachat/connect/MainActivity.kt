package com.intrachat.connect

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.fasterxml.jackson.databind.ObjectMapper
import com.intrachat.connect.client.ChatViewModel
import com.intrachat.connect.server.ChatObject
import com.intrachat.connect.server.SocketServerService
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {

    private val viewModel: ChatViewModel by viewModels()
    private val mapper = ObjectMapper()

    var mSocket: Socket? = null
    var connectUser: String? = null
    var serverHost: String? = null

    private val onMessage = Emitter.Listener {
        val data = it[0] as String
        //val decoded = String(Base64.decode(data, Base64.DEFAULT))
        val chat = mapper.readValue(data, ChatObject::class.java)

        viewModel.insertMessage(chat)
    }

    private val onBinaryMessage = Emitter.Listener {
        val data = it[0] as JSONObject

        viewModel.insertBinaryMessage(data)
    }

    private val onUserLists = Emitter.Listener {
        viewModel.updateUserList(it[0] as JSONArray)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val navController = findNavController(R.id.nav_host_fragment)
        val appBarConfiguration = AppBarConfiguration(navController.graph)
        toolbar.setupWithNavController(navController, appBarConfiguration)

        getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.listFiles { f -> f.delete() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        disconnectFromChat()
        super.onDestroy()
    }

    fun connectToChat(socket: Socket) {
        mSocket = socket
        mSocket?.on(SocketServerService.SC_CHAT_EVENT, onMessage)
        mSocket?.on(SocketServerService.SC_BINARY_EVENT, onBinaryMessage)
        mSocket?.on(SocketServerService.SC_CHAT_USERS, onUserLists)
        mSocket?.connect()
    }

    fun disconnectFromChat() {
        if (mSocket?.connected() == true) {
            mSocket?.off(SocketServerService.SC_CHAT_EVENT, onMessage)
            mSocket?.off(SocketServerService.SC_BINARY_EVENT, onBinaryMessage)
            mSocket?.off(SocketServerService.SC_CHAT_USERS, onUserLists)
            mSocket?.disconnect()
            connectUser = null
            serverHost = null
            mSocket = null
            Toast.makeText(this, "Server Disconnected.", Toast.LENGTH_LONG).show()
        }
        mSocket = null
        viewModel.deleteAll()
    }

    companion object {

        @JvmStatic
        fun getIPAddress(): String? {
            val en = NetworkInterface.getNetworkInterfaces()

            while (en.hasMoreElements()) {
                val nf = en.nextElement()
                val addresses = nf.inetAddresses

                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()

                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress
                    }
                }

            }

            return null
        }

    }

}