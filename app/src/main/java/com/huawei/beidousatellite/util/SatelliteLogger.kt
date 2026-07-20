package com.huawei.beidousatellite.util

import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SatelliteLogger @Inject constructor() {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        writeToFile("general", tag, msg)
    }
    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
        writeToFile("general", tag, msg)
    }
    fun w(tag: String, msg: String) {
        Log.w(tag, msg)
        writeToFile("general", tag, msg)
    }
    fun e(tag: String, msg: String, thr: Throwable? = null) {
        Log.e(tag, msg, thr)
        writeToFile("error", tag, "$msg ${thr?.message}")
        thr?.let { writeCrash(it) }
    }
    fun network(msg: String) = writeToFile("network", "NETWORK", msg)
    fun sensor(msg: String) = writeToFile("sensor", "SENSOR", msg)
    fun message(msg: String) = writeToFile("message", "MESSAGE", msg)
    fun hms(msg: String) = writeToFile("hms", "HMS", msg)
    fun performance(msg: String) = writeToFile("performance", "PERF", msg)

    private fun writeToFile(category: String, tag: String, msg: String) {
        try {
            val base = Environment.getExternalStorageDirectory()?.let {
                File(it, "Android/data/com.huawei.beidousatellite/files/Documents/BeidouSatellite/Logs/$category")
            } ?: return
            if (!base.exists()) base.mkdirs()
            val fileName = "${fileDateFormat.format(Date())}.log"
            val file = File(base, fileName)
            FileWriter(file, true).use { w ->
                w.append("${dateFormat.format(Date())} $tag: $msg\n")
            }
        } catch (_: Exception) {}
    }

    private fun writeCrash(thr: Throwable) {
        try {
            val base = Environment.getExternalStorageDirectory()?.let {
                File(it, "Android/data/com.huawei.beidousatellite/files/Documents/BeidouSatellite/Logs/crash")
            } ?: return
            if (!base.exists()) base.mkdirs()
            val file = File(base, "${fileDateFormat.format(Date())}_crash.log")
            FileWriter(file, true).use { w ->
                w.append("---- CRASH ${dateFormat.format(Date())} ----\n")
                w.append(thr.stackTraceToString())
                w.append("\n")
            }
        } catch (_: Exception) {}
    }
}
