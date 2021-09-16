package com.intrachat.connect.client

import android.content.*
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.intrachat.connect.BuildConfig
import com.intrachat.connect.MainActivity
import com.intrachat.connect.R
import com.intrachat.connect.databinding.HomeFragmentBinding
import com.intrachat.connect.support.appActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout

class HomeFragment : Fragment() {

    lateinit var binding: HomeFragmentBinding

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

        binding.btnConnectServer.setOnClickListener {

            val mainActivity = (requireActivity() as MainActivity)
            if (mainActivity.mSocket?.connected() == true) {
                findNavController().navigate(R.id.action_homeFragment_to_chatFragment)
                return@setOnClickListener
            }

            val inputView = layoutInflater.inflate(R.layout.layout_input_dialog, null)
            val layoutYourName = inputView.findViewById<TextInputLayout>(R.id.layoutYourName)
            val layoutIpAddress = inputView.findViewById<TextInputLayout>(R.id.layoutIpAddress)
            val edYourName = inputView.findViewById<EditText>(R.id.edYourName)
            val edIPAddress = inputView.findViewById<EditText>(R.id.edIPAddress)
            edIPAddress.setText(MainActivity.getIPAddress())
            val btnConnect = inputView.findViewById<MaterialButton>(R.id.btnConnect)
            val btnCancel = inputView.findViewById<MaterialButton>(R.id.btnCancel)

            val dialog = MaterialAlertDialogBuilder(view.context)
                .setTitle("Connect Info")
                .setView(inputView)
                .setCancelable(false)
                .show()

            btnCancel.setOnClickListener { dialog.dismiss() }

            btnConnect.setOnClickListener {
                layoutYourName.error = null
                layoutIpAddress.error = null

                val name = edYourName.text.toString()
                val ip = edIPAddress.text.toString()
                var valid = true

                if (name.isEmpty()) {
                    layoutYourName.error = "Please enter your name."
                    valid = false
                }

                if (ip.isEmpty()) {
                    layoutIpAddress.error = "Invalid IP address."
                    valid = false
                }

                if (valid) {
//                    val intent = Intent(view.context, ChatActivity::class.java).apply {
//                        putExtra(ChatActivity.KEY_USER_NAME, name)
//                        putExtra(
//                            ChatActivity.KEY_SERVER_URL,
//                            "http://${ip}:${SocketServerService.SERVER_PORT}"
//                        )
//                    }

                    mainActivity.connectUser = name
                    mainActivity.serverHost = ip

//                    val args = bundleOf(
//                        ChatFragment.KEY_USER_NAME to name,
//                        ChatFragment.KEY_SERVER_URL to serverUrl
//                    )

                    dialog.dismiss()

                    findNavController().navigate(R.id.action_homeFragment_to_chatFragment)
                }
            }

        }

        binding.tvIpAddress.setOnClickListener {
            val clipboard = view.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("IP Address", MainActivity.getIPAddress()))
            Toast.makeText(view.context, "Copied IP address!", Toast.LENGTH_SHORT).show()
        }

        binding.tvIpAddress.text = getString(R.string.ip_address_args, MainActivity.getIPAddress())
    }

    override fun onResume() {
        super.onResume()
        toggleConnect()
    }

    private fun toggleConnect() {
        if (appActivity()?.mSocket?.connected() == true) {
            binding.btnConnectServer.text = getString(R.string.enter_chat)
        } else {
            binding.btnConnectServer.text = getString(R.string.connect_server)
        }
    }

}