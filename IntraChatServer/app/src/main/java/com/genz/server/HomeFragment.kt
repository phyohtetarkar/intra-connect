package com.genz.server

import android.content.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.genz.server.core.SocketServerService
import com.genz.server.databinding.HomeFragmentBinding
import com.genz.server.support.appActivity

class HomeFragment : Fragment() {

    lateinit var binding: HomeFragmentBinding

    private val broadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == null) return

            when (intent.action) {
                SocketServerService.ACTION_SERVER_STATUS -> {
                    if (intent.getBooleanExtra(SocketServerService.KEY_RUNNING, false)) {
                        binding.btnStartServer.apply {
                            text = getString(R.string.stop_server)
                            isEnabled = true
                        }
                    } else {
                        binding.btnStartServer.apply {
                            text = getString(R.string.start_server)
                            isEnabled = true
                        }
                    }
                }

                SocketServerService.ACTION_STOP_SERVER -> {
                    binding.btnStartServer.apply {
                        text = getString(R.string.start_server)
                        isEnabled = true
                    }
                    Toast.makeText(context, "Server stopped.", Toast.LENGTH_SHORT).show()
                }

                SocketServerService.ACTION_SERVER_FAIL -> {
                    binding.btnStartServer.apply {
                        text = getString(R.string.start_server)
                        isEnabled = true
                    }
                    val reason = "Fail to start server. Please try again."
                    Toast.makeText(context, reason, Toast.LENGTH_SHORT).show()
                }
            }
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launchWhenStarted {
            binding.layoutInitializeIndicator.isVisible = true
            Handler(Looper.getMainLooper()).postDelayed({
                binding.layoutInitializeIndicator.isVisible = false
            }, 3000)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = HomeFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.textViewVersion.text = getString(R.string.version_name_args, BuildConfig.VERSION_NAME)

        binding.btnStartServer.setOnClickListener {
            binding.btnStartServer.isEnabled = false
            val intent = Intent(view.context, SocketServerService::class.java)
            requireActivity().startService(intent)
        }

        binding.tvIpAddress.setOnClickListener {
            val clipboard = view.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("IP Address", MainActivity.getIPAddress()))
            Toast.makeText(view.context, "Copied IP address!", Toast.LENGTH_SHORT).show()
        }

        binding.tvIpAddress.text = getString(R.string.ip_address_args, MainActivity.getIPAddress())
    }

    override fun onStart() {
        super.onStart()
        val intentFilter = IntentFilter().apply {
            addAction(SocketServerService.ACTION_STOP_SERVER)
            addAction(SocketServerService.ACTION_SERVER_STATUS)
            addAction(SocketServerService.ACTION_SERVER_FAIL)
        }
        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(broadcastReceiver, intentFilter)
    }

    override fun onResume() {
        super.onResume()
        val intent = Intent(requireContext(), SocketServerService::class.java)
        intent.action = SocketServerService.ACTION_SERVER_STATUS
        appActivity()?.startService(intent)
    }

    override fun onStop() {
        super.onStop()
        LocalBroadcastManager.getInstance(requireContext())
            .unregisterReceiver(broadcastReceiver)
    }

}