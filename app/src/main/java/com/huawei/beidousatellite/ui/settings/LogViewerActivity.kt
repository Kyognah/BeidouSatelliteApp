package com.huawei.beidousatellite.ui.settings

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.huawei.beidousatellite.R
import com.huawei.beidousatellite.util.SatelliteLogger
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LogViewerActivity : AppCompatActivity() {

    @Inject lateinit var logger: SatelliteLogger

    private lateinit var pathText: TextView
    private lateinit var logContent: TextView
    private lateinit var listView: ListView
    private lateinit var refreshButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_viewer)

        pathText = findViewById(R.id.pathText)
        logContent = findViewById(R.id.logContent)
        listView = findViewById(R.id.listView)
        refreshButton = findViewById(R.id.refreshButton)

        pathText.text = "Log Path:\n${logger.getLogPath()}\n\nOn Android 11+, use Files app -> Android/data/com.huawei.beidousatellite/files/Documents/BeidouSatellite/Logs/"

        refreshButton.setOnClickListener { loadLogs() }

        listView.setOnItemClickListener { _, _, position, _ ->
            val files = logger.getAllLogs()
            if (position < files.size) {
                val file = files[position]
                logContent.text = "File: ${file.absolutePath}\nSize: ${file.length()} bytes\n\n" + logger.readLog(file, 300)
            }
        }

        loadLogs()
    }

    private fun loadLogs() {
        val files = logger.getAllLogs()
        if (files.isEmpty()) {
            pathText.text = "Log Path: ${logger.getLogPath()}\n\nNo logs yet. Logs are created when you use satellite features."
            listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, listOf("No logs"))
            return
        }
        val names = files.map { "${it.parentFile?.name}/${it.name} (${it.length()} bytes)" }
        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, names)
        logContent.text = "${files.size} log files found. Tap one to view."
    }
}
