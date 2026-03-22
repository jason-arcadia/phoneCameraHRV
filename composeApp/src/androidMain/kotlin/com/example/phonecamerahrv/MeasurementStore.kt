package com.example.phonecamerahrv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class MeasurementStore(context: Context) {

    private val prefs = context.getSharedPreferences("hrv_history", Context.MODE_PRIVATE)

    fun save(record: MeasurementRecord) {
        val all = loadAll().toMutableList()
        all.add(record)
        if (all.size > MAX_RECORDS) all.removeAt(0)

        val arr = JSONArray()
        all.forEach { r ->
            arr.put(JSONObject().apply {
                put("ts",    r.timestampMs)
                put("rmssd", r.rmssd)
                put("hr",    r.heartRate)
                put("rr",    r.lastRR)
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun loadAll(): List<MeasurementRecord> {
        val json = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                MeasurementRecord(
                    timestampMs = o.getLong("ts"),
                    rmssd       = o.getDouble("rmssd"),
                    heartRate   = o.getDouble("hr"),
                    lastRR      = o.getDouble("rr")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Returns baseline computed from all stored records, or null if fewer than 5 exist. */
    fun computeBaseline(): BaselineStats? {
        val records = loadAll()
        if (records.size < MIN_FOR_BASELINE) return null
        return BaselineStats(
            rmssd       = records.map { it.rmssd }.average(),
            heartRate   = records.map { it.heartRate }.average(),
            lastRR      = records.map { it.lastRR }.average(),
            sampleCount = records.size
        )
    }

    fun recordCount(): Int = loadAll().size

    companion object {
        private const val KEY = "records"
        private const val MAX_RECORDS = 50
        const val MIN_FOR_BASELINE = 5
    }
}
