package com.intrachat.connect.client

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import androidx.paging.PagedList
import androidx.paging.toLiveData
import com.intrachat.connect.GenZConnectApplication
import com.intrachat.connect.server.ChatObject
import com.intrachat.connect.support.ImageWorker
import kotlinx.coroutines.launch
import org.joda.time.DateTime
import org.json.JSONArray
import org.json.JSONObject

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val dao: MessageDao

    val messages: LiveData<PagedList<Message>>
    val userList = mutableMapOf<String, String>()

    init {
        val _app = app as GenZConnectApplication
        dao = _app.db.messageDao()
        messages = dao.findAll().toLiveData(pageSize = 50)
    }

    fun insertMessage(chat: ChatObject) {
        if (chat.type == ChatObject.Type.ACTION_JOINED) {
            userList[chat.host] = chat.sender
        }

        viewModelScope.launch {
            val msg = Message.fromChat(chat)
            insert(msg)
        }
    }

    fun insertBinaryMessage(data: JSONObject) {
        viewModelScope.launch {
            val sender = data.getString("sender")

            val name = ImageWorker.writeToCache(
                getApplication(),
                data.getJSONArray("image"),
                sender,
                data.optBoolean("is_gif", false)
            )

            val msg = Message(
                id = data.getString("id"),
                sender = sender,
                host = data.getString("host"),
                content = name,
                type = ChatObject.Type.CONVERSATION,
                sendAt = DateTime.now().millis,
                binary = true,
                replyId = if (data.has("reply_id")) data.getString("reply_id") else null
            )

            insert(msg)
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            dao.deleteAll()
        }
    }

    fun updateUserList(data: JSONArray) {
        userList.clear()
        viewModelScope.launch {
            val len = data.length()
            var i = 0
            while (i < len) {
                val obj = data.getString(0).split(":")
                userList[obj[0]] = obj[1]
                i += 1
            }
        }
    }

    private suspend fun insert(msg: Message) {
        val reply = msg.replyId?.let { dao.findById(it) }

        reply?.also {
            msg.replyBinary = it.binary
            msg.replyContent = it.content
        }

        dao.insert(msg)
    }

}