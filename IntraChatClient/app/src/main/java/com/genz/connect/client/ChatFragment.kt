package com.genz.connect.client

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.view.*
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.Target
import com.fasterxml.jackson.databind.ObjectMapper
import com.genz.connect.MainActivity
import com.genz.connect.R
import com.genz.connect.databinding.ChatFragmentBinding
import com.genz.connect.server.ChatObject
import com.genz.connect.server.SocketServerService
import com.genz.connect.support.ImageWorker
import com.genz.connect.support.appActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import io.socket.engineio.client.transports.WebSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.joda.time.DateTime
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.net.ssl.*

class ChatFragment : Fragment() {

    private lateinit var binding: ChatFragmentBinding

    private val mapper = ObjectMapper()
    private val viewModel: ChatViewModel by activityViewModels()
    private val host = MainActivity.getIPAddress() ?: ""
    private val adapter = ChatMessageAdapter(host)
    private val mHandler = Handler(Looper.getMainLooper())
    private val typingUsers = mutableSetOf<String>()
    private var typingDelay = 2000L
    private var typingHandler = Handler()
    private var typing = false
    private var reply: Message? = null

    private val mSocket: Socket?
        get() = appActivity()?.mSocket

    private val onConnect = Emitter.Listener {
        mHandler.post {
            binding.layoutLoading.isVisible = false
            Toast.makeText(context, "Server Connected.", Toast.LENGTH_SHORT).show()
            val user = appActivity()?.connectUser ?: "Anonymous"
            val chat = ChatObject(
                id = Base64.encodeToString("${host}-${DateTime.now().millis}".toByteArray(), Base64.DEFAULT),
                host = host,
                sender = user,
                content = "",
                type = ChatObject.Type.ACTION_JOINED
            )
//            val encoded = Base64.encodeToString(
//                mapper.writeValueAsString(chat).toByteArray(),
//                Base64.DEFAULT
//            )
            val data = mapper.writeValueAsString(chat)
            mSocket?.emit(SocketServerService.SC_CHAT_EVENT, data)
        }

    }

    private val onDisconnect = Emitter.Listener {
        mHandler.post {
            //Log.d(ChatFragment::class.java.simpleName, "Server Disconnected.")
            Toast.makeText(context, "Server Disconnected.", Toast.LENGTH_LONG).show()
        }
    }

    private val onConnectError = Emitter.Listener {
        mHandler.post {
            binding.layoutLoading.isVisible = false
            //Log.d(ChatFragment::class.java.simpleName, it[0].toString())
            Toast.makeText(context, "Error Connecting.", Toast.LENGTH_LONG).show()
            disconnect()
            findNavController().navigateUp()
        }

    }

    private val onBinaryMessage = Emitter.Listener {
        mHandler.post {
            val data = it[0] as ByteArray
        }
    }

    private val onTyping = Emitter.Listener {
        mHandler.post {
            val data = it[0] as String
            if (!typingUsers.contains(data)) {
                typingUsers.add(data)
                toggleTyping()
            }
        }
    }

    private val onTypingStop = Emitter.Listener {
        mHandler.post {
            val data = it[0] as String
            if (typingUsers.remove(data)) {
                toggleTyping()
            }
        }
    }

    private val typingStopRunnable = Runnable {
        typing = false
        val mSocket = appActivity()?.mSocket
        mSocket?.emit(SocketServerService.SC_TYPE_STOP_EVENT)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)

        viewModel.messages.observe(this) {
            adapter.submitList(it) {
                if (!binding.recyclerView.canScrollVertically(1)) {
                    val pos = if (adapter.itemCount > 0) adapter.itemCount - 1 else 0
                    binding.recyclerView.scrollToPosition(pos)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ChatFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnCloseReply.setOnClickListener {
            binding.layoutReplyInfo.isVisible = false
            binding.textViewReply.text = null
            reply = null
        }

        adapter.onItemLongClick = {
            if (it.binary) {
                binding.textViewReply.text = "Photo"
            } else {
                binding.textViewReply.text = it.content
            }

            binding.layoutReplyInfo.isVisible = true
            reply = it
        }

        adapter.onItemClick = {
            if (it.binary) {
                val file = view.context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                //val uri = Uri.fromFile(File(file, it.content))

                val imageView = AppCompatImageView(view.context)
                imageView.adjustViewBounds = true

                val dialog = MaterialAlertDialogBuilder(view.context)
                    .setView(imageView)
                    .create()

                dialog.setOnShowListener { _ ->
                    Glide.with(view.context)
                        .load(File(file, it.content))
                        .override(Target.SIZE_ORIGINAL)
                        .into(imageView)
                }

                dialog.show()
            }
        }

        val layoutManager = LinearLayoutManager(view.context)
        layoutManager.stackFromEnd = true

        binding.recyclerView.apply {
            setLayoutManager(layoutManager)
            setHasFixedSize(true)
            adapter = this@ChatFragment.adapter

            addOnScrollListener(object : RecyclerView.OnScrollListener() {

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    val lastPos = layoutManager.findLastCompletelyVisibleItemPosition()
                    val count = layoutManager.itemCount
                    binding.btnScrollBottom.isVisible = lastPos < (count - 5)
                }

            })
        }

        binding.btnSend.setOnClickListener {

            if (mSocket?.connected() != true) {
                Toast.makeText(view.context, "Server disconnected. Please reconnect again.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            stopTyping()
            val msg = binding.edMessage.text.toString()

            if (msg.isNotEmpty()) {
                val user = appActivity()?.connectUser ?: "Anonymous"
                val chat = ChatObject(
                    id = Base64.encodeToString("${host}-${DateTime.now().millis}".toByteArray(), Base64.DEFAULT),
                    host = host,
                    sender = user,
                    content = msg,
                    type = ChatObject.Type.CONVERSATION,
                    replyId = reply?.id
                )
//                val encoded = Base64.encodeToString(
//                    mapper.writeValueAsString(chat).toByteArray(),
//                    Base64.DEFAULT
//                )
                val data = mapper.writeValueAsString(chat)
                mSocket?.emit(SocketServerService.SC_CHAT_EVENT, data)
                binding.edMessage.text = null
                binding.layoutReplyInfo.isVisible = false
                binding.textViewReply.text = null
                reply = null
            }
        }

        binding.btnImage.setOnClickListener { dispatchChoosePicture() }

        binding.edMessage.doAfterTextChanged { s ->
            if (!s.isNullOrEmpty()) {
                if (!typing) {
                    typing = true
                    mSocket?.emit(SocketServerService.SC_TYPE_EVENT)
                }
                typingHandler.removeCallbacks(typingStopRunnable)
                typingHandler.postDelayed(typingStopRunnable, typingDelay)
            }
        }

        binding.btnScrollBottom.setOnClickListener {
            val pos = if (adapter.itemCount > 0) adapter.itemCount - 1 else 0
            binding.recyclerView.smoothScrollToPosition(pos)
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        connectToServer()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_chat, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_disconnect) {
            disconnect()
            findNavController().navigateUp()
            return true
        }

        if (item.itemId == R.id.action_users) {

            val users = viewModel.userList.map { it.value }.toTypedArray()

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.users)
                .setItems(users) { _, _ -> }
                .show()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != Activity.RESULT_OK) {
            return
        }

        if (requestCode == REQUEST_PICK_IMAGE) {
            binding.layoutReplyInfo.isVisible = false
            binding.textViewReply.text = null

            data?.data?.also {
                lifecycleScope.launch {
                    try {
                        val type = requireContext().contentResolver.getType(it)
                        val obj = JSONObject()
                        val user = appActivity()?.connectUser ?: "Anonymous"

                        obj.put("host", host)
                        obj.put("sender", user)
                        obj.put("id", Base64.encodeToString("${host}-${DateTime.now().millis}".toByteArray(), Base64.DEFAULT))
                        obj.put("reply_id", reply?.id)

                        if (type == "image/gif") {
                            val byte = ImageWorker.compressGIF { requireContext().contentResolver.openInputStream(it) }
                            obj.put("image", JSONArray(byte))
                            obj.putOpt("is_gif", true)
                        } else {
                            val byte = ImageWorker.compressImage { requireContext().contentResolver.openInputStream(it) }
                            obj.put("image", JSONArray(byte))
                        }

//                        val byte = ImageWorker.compressImage { requireContext().contentResolver.openInputStream(it) }
//                        obj.put("image", JSONArray(byte))

//                    if (byte.size > (4096 * 4096)) {
//                        Toast.makeText(requireContext(), "Image size too big!", Toast.LENGTH_SHORT).show()
//                        return@launchWhenStarted
//                    }

                        mSocket?.emit(SocketServerService.SC_BINARY_EVENT, obj)

                        reply = null
                    } catch (e: Exception) {
                        e.printStackTrace()
                        launch(Dispatchers.Main) {
                            Toast.makeText(requireContext(), "Failed to send image.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        unsubscribe()
        super.onDestroyView()
    }

    private fun toggleTyping() {
        binding.tvTyping.isVisible = typingUsers.isNotEmpty()
    }

    private fun subscribe() {
        mSocket?.on(Socket.EVENT_CONNECT, onConnect)
        mSocket?.on(Socket.EVENT_DISCONNECT, onDisconnect)
        mSocket?.on(Socket.EVENT_CONNECT_ERROR, onConnectError)
        //mSocket?.on(SocketServerService.SC_BINARY_EVENT, onBinaryMessage)
        mSocket?.on(SocketServerService.SC_TYPE_EVENT, onTyping)
        mSocket?.on(SocketServerService.SC_TYPE_STOP_EVENT, onTypingStop)
    }

    private fun unsubscribe() {
        mSocket?.off(Socket.EVENT_CONNECT, onConnect)
        mSocket?.off(Socket.EVENT_DISCONNECT, onDisconnect)
        mSocket?.off(Socket.EVENT_CONNECT_ERROR, onConnectError)
        //mSocket?.off(SocketServerService.SC_BINARY_EVENT, onBinaryMessage)
        mSocket?.off(SocketServerService.SC_TYPE_EVENT, onTyping)
        mSocket?.off(SocketServerService.SC_TYPE_STOP_EVENT, onTypingStop)

        stopTyping()
    }

    private fun connectToServer() {
        if (mSocket?.connected() == true) {
            subscribe()
            return
        }

        try {
            val serverUrl = "https://${appActivity()?.serverHost}:${SocketServerService.SERVER_PORT}"

            //Log.d("TAG", serverUrl)

            viewModel.deleteAll()
            binding.layoutLoading.isVisible = true

            val sOkHttpClient = prepareOkHttpClient()
            val opts = IO.Options().apply {
                reconnection = false
                transports = arrayOf(WebSocket.NAME)
                callFactory = sOkHttpClient
                webSocketFactory = sOkHttpClient
            }

            val mSocket = IO.socket(serverUrl, opts)
            mSocket?.on(Socket.EVENT_CONNECT, onConnect)
            mSocket?.on(Socket.EVENT_DISCONNECT, onDisconnect)
            mSocket?.on(Socket.EVENT_CONNECT_ERROR, onConnectError)
            //mSocket?.on(SocketServerService.SC_BINARY_EVENT, onBinaryMessage)
            mSocket?.on(SocketServerService.SC_TYPE_EVENT, onTyping)
            mSocket?.on(SocketServerService.SC_TYPE_STOP_EVENT, onTypingStop)
            appActivity()?.connectToChat(mSocket)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun disconnect() {
        unsubscribe()
        appActivity()?.disconnectFromChat()
    }

    private fun prepareOkHttpClient(): OkHttpClient {
        val password = SocketServerService.KEYSTORE_PASSWORD
        val ks = KeyStore.getInstance("BKS")
        ks.load(requireContext().assets.open("keystore.bks"), password.toCharArray())

        val kmf = KeyManagerFactory.getInstance("X509")
        kmf.init(ks, password.toCharArray())

        val tmf = TrustManagerFactory.getInstance("X509")
        tmf.init(ks)

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(kmf.keyManagers, tmf.trustManagers, null)

        return OkHttpClient.Builder()
            .hostnameVerifier { hostname, _ -> hostname == appActivity()?.serverHost }
            .sslSocketFactory(
                sslContext.socketFactory,
                tmf.trustManagers[0] as X509TrustManager
            )
            .build()
    }

    private fun dispatchChoosePicture() {
        val contentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
        }

        val pickImageIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/*"
        }

        val chooserIntent = Intent.createChooser(contentIntent, "Browse Image")
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(pickImageIntent))

        startActivityForResult(pickImageIntent, REQUEST_PICK_IMAGE)
    }

    private fun stopTyping() {
        if (typing) {
            typing = false
            val mSocket = appActivity()?.mSocket
            mSocket?.emit(SocketServerService.SC_TYPE_STOP_EVENT)
        }
        typingHandler.removeCallbacks(typingStopRunnable)
    }

    companion object {
        const val KEY_SERVER_URL = "server_url"
        const val KEY_USER_NAME = "user_name"
        const val REQUEST_PICK_IMAGE = 100
    }

}