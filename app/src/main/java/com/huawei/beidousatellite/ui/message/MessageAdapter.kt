package com.huawei.beidousatellite.ui.message

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.huawei.beidousatellite.R
import com.huawei.beidousatellite.data.model.SmcMessage
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MessageAdapter : ListAdapter<SmcMessage, MessageAdapter.VH>(Diff()) {

    class Diff : DiffUtil.ItemCallback<SmcMessage>() {
        override fun areItemsTheSame(a: SmcMessage, b: SmcMessage) = a.messageId == b.messageId
        override fun areContentsTheSame(a: SmcMessage, b: SmcMessage) = a == b
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val content: TextView = view.findViewById(R.id.contentText)
        val meta: TextView = view.findViewById(R.id.metaText)
        val status: TextView = view.findViewById(R.id.statusText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val msg = getItem(position)
        holder.content.text = msg.content
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
        holder.meta.text = "From: ${msg.senderNumber} To: ${msg.recipientNumber}\nTime: ${fmt.format(msg.utcTime)} Type: ${msg.messageType} Pri: ${msg.priority}"
        holder.status.text = "Status: ${msg.status} Retry: ${msg.retryCount} Ack: ${msg.ackReceived}\nLat: ${msg.latitude} Lon: ${msg.longitude}"
    }
}
