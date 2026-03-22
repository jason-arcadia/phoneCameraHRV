package com.example.phonecamerahrv

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "MeasurementApiClient"

private const val SUPABASE_URL = "https://yczqytmdhxkxsyxlgiql.supabase.co"
private const val SUPABASE_ANON_KEY =
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
    "eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InljenF5dG1kaHhreHN5eGxnaXFsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzQxNTk2NTQsImV4cCI6MjA4OTczNTY1NH0." +
    "_VNVcbFXpZSwKXF7Ys69-SMdR3oKDWfGhSaZOTq_iOg"

object MeasurementApiClient {

    /**
     * Upload a raw RR-interval text file to Supabase Storage (bucket: rr-files).
     * Returns the public URL of the uploaded object.
     */
    suspend fun uploadRRFile(
        email: String,
        timestamp: String,
        content: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val safeEmail = email.replace("@", "_").replace(".", "_")
            val safeTs    = timestamp.replace(Regex("[ :]"), "-")
            val filename  = "${safeEmail}_${safeTs}.txt"

            val urlStr = "$SUPABASE_URL/storage/v1/object/rr-files/$filename"
            Log.d(TAG, "uploadRRFile → POST $urlStr (${content.length} bytes)")

            val url  = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type",  "text/plain; charset=utf-8")
                conn.setRequestProperty("apikey",        SUPABASE_ANON_KEY)
                conn.setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
                conn.setRequestProperty("x-upsert",     "true")
                conn.doOutput       = true
                conn.connectTimeout = 10_000
                conn.readTimeout    = 10_000

                conn.outputStream.use { it.write(content.toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                val body = try {
                    (if (code in 200..299) conn.inputStream else conn.errorStream)
                        ?.bufferedReader()?.readText() ?: ""
                } catch (e: Exception) { "" }

                Log.d(TAG, "uploadRRFile ← $code  body=$body")

                if (code !in 200..299) {
                    error("Storage $code: $body")
                }

                "$SUPABASE_URL/storage/v1/object/public/rr-files/$filename"
            } finally {
                conn.disconnect()
            }
        }
    }

    /**
     * Insert a measurement row into the Supabase measurements table.
     */
    suspend fun upload(
        email: String,
        timestamp: String,
        rmssd: Double,
        heartRate: Double,
        peakToPeak: Double,
        meanRr: Double,
        stressScore: Int,
        energyScore: Int,
        recoveryScore: Int,
        rrFileUrl: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url  = URL("$SUPABASE_URL/rest/v1/measurements")
            val body = JSONObject().apply {
                put("email",          email)
                put("timestamp",      timestamp)
                put("rmssd",          rmssd)
                put("heart_rate",     heartRate)
                put("peak_to_peak",   peakToPeak)
                put("mean_rr",        meanRr)
                put("stress_score",   stressScore)
                put("energy_score",   energyScore)
                put("recovery_score", recoveryScore)
                put("rr_file_url",    rrFileUrl)
            }.toString()

            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type",  "application/json; charset=utf-8")
                conn.setRequestProperty("apikey",        SUPABASE_ANON_KEY)
                conn.setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
                conn.setRequestProperty("Prefer",        "return=minimal")
                conn.doOutput       = true
                conn.connectTimeout = 10_000
                conn.readTimeout    = 10_000

                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                if (code !in 200..299) {
                    val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
                    error("Supabase error $code: $err")
                }
            } finally {
                conn.disconnect()
            }
        }
    }
}
