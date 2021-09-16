package com.intrachat.connect.client

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.intrachat.connect.server.ChatObject
import org.joda.time.DateTime
import org.joda.time.LocalDateTime

@Entity
data class Message(
    @PrimaryKey var id: String = "",
    val host: String,
    val sender: String,
    val content: String,
    val type: ChatObject.Type,
    val binary: Boolean = false,
    @ColumnInfo(name = "reply_id") val replyId: String? = null,
    @ColumnInfo(name = "reply_content") var replyContent: String? = null,
    @ColumnInfo(name = "reply_binary") var replyBinary: Boolean = false,
    @ColumnInfo(name = "send_at") val sendAt: Long
) {

    val time: String
        get() = LocalDateTime(sendAt).toString("MMM dd hh:mm a")

    companion object {
        @JvmStatic
        fun fromChat(chat: ChatObject): Message {
            return Message(
                id = chat.id,
                host = chat.host,
                sender = chat.sender,
                content = chat.content,
                type = chat.type,
                sendAt = DateTime.now().millis,
                replyId = chat.replyId
            )
        }
    }
}
