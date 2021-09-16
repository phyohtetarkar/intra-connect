package com.genz.connect.client

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.databinding.ViewDataBinding
import androidx.paging.PagedListAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.genz.connect.databinding.LayoutInfoBinding
import com.genz.connect.databinding.LayoutMessageReceiveBinding
import com.genz.connect.databinding.LayoutMessageSendBinding
import com.genz.connect.server.ChatObject

class ChatMessageAdapter(
    private val host: String
) : PagedListAdapter<Message, ChatMessageAdapter.ChatMessageViewHolder>(DIFF_UTIL) {

    var onItemLongClick: ((Message) -> Unit)? = null
    var onItemClick: ((Message) -> Unit)? = null

    inner class ChatMessageViewHolder(
        val binding: ViewDataBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnLongClickListener {
                getItem(adapterPosition)?.apply {
                    if (type == ChatObject.Type.CONVERSATION) {
                        onItemLongClick?.invoke(this)
                    }
                }
                true
            }

            binding.root.setOnClickListener {
                getItem(adapterPosition)?.apply {
                    if (type == ChatObject.Type.CONVERSATION && binary) {
                        onItemClick?.invoke(this)
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatMessageViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        val binding: ViewDataBinding = when (viewType) {
            VIEW_TYPE_SEND -> LayoutMessageSendBinding.inflate(inflater, parent, false)
            VIEW_TYPE_RECEIVE -> LayoutMessageReceiveBinding.inflate(inflater, parent, false)
            else -> LayoutInfoBinding.inflate(inflater, parent, false)
        }

        return ChatMessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatMessageViewHolder, position: Int) {
        val prev = if (position > 0) getItem(position - 1) else null
        getItem(position)?.also { m ->
            val time = m.time
            when (getItemViewType(position)) {
                VIEW_TYPE_SEND -> {
                    (holder.binding as? LayoutMessageSendBinding)?.apply {
                        heading = prev?.let {
                            it.host != m.host || time != it.time || it.type != ChatObject.Type.CONVERSATION
                        } ?: true

                        msg = m

                    }
                }
                VIEW_TYPE_RECEIVE -> {
                    (holder.binding as? LayoutMessageReceiveBinding)?.apply {
                        heading = prev?.let {
                            it.host != m.host || time != it.time || it.type != ChatObject.Type.CONVERSATION
                        } ?: true

                        msg = m
                    }
                }
                else -> {
                    (holder.binding as? LayoutInfoBinding)?.apply {
                        val status = when (m.type) {
                            ChatObject.Type.ACTION_JOINED -> "joined"
                            ChatObject.Type.ACTION_LEFT -> "left"
                            else -> ""
                        }
                        val user = if (host == m.host) "You" else m.sender
                        info = "$user $status"
                    }
                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        return when (item?.type) {
            ChatObject.Type.CONVERSATION -> {
                return if (host == item.host) {
                    VIEW_TYPE_SEND
                } else {
                    VIEW_TYPE_RECEIVE
                }
            }
            else -> VIEW_TYPE_INFO
        }

    }

    fun getItemAt(position: Int) = getItem(position)

    companion object {
        private val DIFF_UTIL = object : DiffUtil.ItemCallback<Message>() {
            override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean {
                return oldItem == newItem
            }
        }

        private const val VIEW_TYPE_SEND = 1
        private const val VIEW_TYPE_RECEIVE = 2
        private const val VIEW_TYPE_INFO = 0
    }

}