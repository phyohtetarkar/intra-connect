package com.intrachat.connect.server

import androidx.annotation.Keep
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@Keep
@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatObject(
    @JsonProperty("id") val id: String,
    @JsonProperty("reply_id") val replyId: String? = null,
    @JsonProperty("host") val host: String,
    @JsonProperty("sender") val sender: String,
    @JsonProperty("content") val content: String,
    @JsonProperty("type") val type: Type,
) {

    enum class Type {
        ACTION_JOINED, ACTION_LEFT, CONVERSATION
    }

}
