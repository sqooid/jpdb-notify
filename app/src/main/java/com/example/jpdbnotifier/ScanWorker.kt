package com.example.jpdbnotifier

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import java.lang.Exception

fun scan(applicationContext: Context): Boolean {
    val prefs =
        applicationContext.getSharedPreferences(Constants.SHARED_PREFS, Context.MODE_PRIVATE)
    val cookies = prefs.getString(Constants.PREFS_COOKIES_KEY, "") ?: ""
    if (cookies.isEmpty()) {
        return true
    }
//    val client = OkHttpClient()
    val client = OkH
    val request = Request.Builder().url(Constants.JPDB_HOME_URL)
        .header("Cookie", cookies)
        .header(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"
        )
        .header(
            "Accept",
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"
        )
        .header("Accept-Encodings", "gzip, deflate, br, zstd")
        .build();

    try {
        val response = client.newCall(request).execute()
        Log.d("app", "response: $response")
        Log.d("app", "body: ${response.body}")
        val body = response.body?.string() ?: ""
        Log.d("app", "body: $body")
        val re = Regex("Learn \\(<span style=\"color: red;\">(\\d+)</span>")
        val countMatch = re.find(body) ?: return false
        Log.d("app", "match: $countMatch")
        val countString = countMatch.groupValues.getOrElse(1) { "" }
        val count = countString.toIntOrNull() ?: return false
        val threshold = prefs.getFloat(Constants.PREFS_CARD_THRESHOLD_KEY, 1f)

        // Skip notification
        if (count < threshold) {
            return true
        }

        val notificationId = 0
        val builder = NotificationCompat.Builder(applicationContext, Constants.CHANNEL_ID)
            .setSmallIcon(R.drawable.jpdb)
            .setContentTitle("jpdb.io $count cards ready for review")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)

        with(NotificationManagerCompat.from(applicationContext)) {
            // notificationId is a unique int for each notification that you must define.
            if (ActivityCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return@with
            }
            notify(notificationId, builder.build())
        }

    } catch (e: Exception) {
        Log.d("app", "Request failed $e")
        return false
    }

    // Indicate whether the work finished successfully with the Result
    return true
}

class ScanWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {
    override fun doWork(): Result {
        if (scan(applicationContext)) {
            return Result.success()
        }
        return Result.failure()
    }
}
