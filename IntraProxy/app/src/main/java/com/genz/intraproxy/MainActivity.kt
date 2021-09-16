package com.genz.intraproxy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.genz.intraproxy.core.ProxyService
import com.genz.intraproxy.databinding.MainActivityBinding
import java.net.Inet4Address
import java.net.NetworkInterface

class MainActivity : AppCompatActivity() {

    private lateinit var binding: MainActivityBinding

    private val broadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == null) return

            when (intent.action) {
                ProxyService.ACTION_SERVER_STATUS -> {
                    if (intent.getBooleanExtra(ProxyService.KEY_SERVER_RUNNING, false)) {
                        binding.btnToggleServer.apply {
                            text = getString(R.string.stop_server)
                            isEnabled = true
                            serverRunning = true
                        }
                    } else {
                        binding.btnToggleServer.apply {
                            text = getString(R.string.start_server)
                            isEnabled = true
                            serverRunning = false
                        }
                    }
                }

                ProxyService.ACTION_SERVER_STOP -> {
                    binding.btnToggleServer.apply {
                        text = getString(R.string.start_server)
                        isEnabled = true
                        serverRunning = false
                    }
                    Toast.makeText(context, "Server stopped.", Toast.LENGTH_SHORT).show()
                }
            }
        }

    }

    private var serverRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = MainActivityBinding.inflate(layoutInflater)

        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        binding.btnToggleServer.setOnClickListener {

            if (!serverRunning && getIPAddress().isNullOrEmpty()) {
                Toast.makeText(this, "Mobile internet not available.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            it.isEnabled = false
            val intent = Intent(this, ProxyService::class.java)
            startService(intent)
        }

    }

    override fun onStart() {
        super.onStart()
        val intentFilter = IntentFilter().apply {
            addAction(ProxyService.ACTION_SERVER_STATUS)
            addAction(ProxyService.ACTION_SERVER_STOP)
        }
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(broadcastReceiver, intentFilter)
    }

    override fun onResume() {
        super.onResume()
        val intent = Intent(this, ProxyService::class.java)
        intent.action = ProxyService.ACTION_SERVER_STATUS
        startService(intent)
    }

    override fun onStop() {
        super.onStop()
        LocalBroadcastManager.getInstance(this)
            .unregisterReceiver(broadcastReceiver)
    }

    companion object {

        @JvmStatic
        fun getIPAddress(): String? {

            try {
                val nf = NetworkInterface.getByName("rmnet0")
                val addresses = nf.inetAddresses

                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()

                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress
                    }
                }
            } catch (e: Exception) {

            }

            return null
        }

    }
}