package com.huawei.beidousatellite.ui.message

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.huawei.beidousatellite.R
import com.huawei.beidousatellite.data.repository.SatelliteRepository
import com.huawei.beidousatellite.data.hms.HmsSmcManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MessageHistoryActivity : AppCompatActivity() {

    @Inject lateinit var repository: SatelliteRepository
    @Inject lateinit var hmsManager: HmsSmcManager

    private lateinit var recycler: RecyclerView
    private lateinit var clearButton: Button
    private lateinit var simulateButton: Button
    private lateinit var adapter: MessageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_message_history)

        recycler = findViewById(R.id.recyclerView)
        clearButton = findViewById(R.id.clearButton)
        simulateButton = findViewById(R.id.simulateButton)

        adapter = MessageAdapter()
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        clearButton.setOnClickListener {
            lifecycleScope.launch {
                repository.clearAll()
                loadMessages()
            }
        }

        simulateButton.setOnClickListener {
            hmsManager.simulateIncomingMessage("Hello from BeiDou! Test message at ${System.currentTimeMillis()}", "+8613800138000")
            loadMessages()
        }

        // Observe HMS manager messages as well
        lifecycleScope.launch {
            hmsManager.messages.collect { msgs ->
                // Merge with repo? For now just show HMS messages
                val allRepo = repository.getAllMessages()
                val combined = (allRepo + msgs).distinctBy { it.messageId }.sortedByDescending { it.utcTime.toEpochMilli() }
                adapter.submitList(combined)
            }
        }

        loadMessages()
    }

    private fun loadMessages() {
        lifecycleScope.launch {
            val msgs = repository.getAllMessages()
            // Also include HMS in-memory
            val hmsMsgs = hmsManager.messages.value
            val combined = (msgs + hmsMsgs).distinctBy { it.messageId }.sortedByDescending { it.utcTime.toEpochMilli() }
            adapter.submitList(combined)
        }
    }
}
