package com.quantumbox.gemma4poc.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class ModelDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ModelDownloadWorker"
        private const val CHANNEL_ID = "model_download"
        private const val NOTIFICATION_ID = 1001

        const val KEY_MODEL_NAME = "model_name"
        const val KEY_DOWNLOAD_URL = "download_url"
        const val KEY_OUTPUT_DIR = "output_dir"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_RECEIVED_BYTES = "received_bytes"
        const val KEY_DOWNLOAD_RATE = "download_rate"
        const val KEY_REMAINING_MS = "remaining_ms"
    }

    override suspend fun doWork(): Result {
        val modelName = inputData.getString(KEY_MODEL_NAME) ?: return Result.failure()
        val downloadUrl = inputData.getString(KEY_DOWNLOAD_URL) ?: return Result.failure()
        val outputDir = inputData.getString(KEY_OUTPUT_DIR) ?: return Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME) ?: return Result.failure()
        val totalBytes = inputData.getLong(KEY_TOTAL_BYTES, 0)

        createNotificationChannel()
        setForeground(createForegroundInfo("Downloading $modelName..."))

        val dir = File(outputDir)
        if (!dir.exists()) dir.mkdirs()

        val outputFile = File(dir, fileName)
        val tmpFile = File(dir, "$fileName.tmp")

        // Resume support
        var downloadedBytes = if (tmpFile.exists()) tmpFile.length() else 0L

        try {
            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true

            if (downloadedBytes > 0) {
                connection.setRequestProperty("Range", "bytes=$downloadedBytes-")
            }

            connection.connect()
            val responseCode = connection.responseCode

            if (responseCode != HttpURLConnection.HTTP_OK &&
                responseCode != HttpURLConnection.HTTP_PARTIAL
            ) {
                Log.e(TAG, "HTTP error: $responseCode for $downloadUrl")
                return Result.failure()
            }

            val effectiveTotal = if (responseCode == HttpURLConnection.HTTP_PARTIAL) {
                downloadedBytes + connection.contentLengthLong
            } else {
                downloadedBytes = 0L
                connection.contentLengthLong.takeIf { it > 0 } ?: totalBytes
            }

            val inputStream = connection.inputStream
            val outputStream = FileOutputStream(tmpFile, downloadedBytes > 0)
            val buffer = ByteArray(8192)
            var bytesRead: Int
            var lastProgressTime = System.currentTimeMillis()
            var lastProgressBytes = downloadedBytes

            inputStream.use { input ->
                outputStream.use { output ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (isStopped) {
                            Log.i(TAG, "Download cancelled")
                            return Result.failure()
                        }

                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val now = System.currentTimeMillis()
                        val elapsed = now - lastProgressTime
                        if (elapsed >= 500) {
                            val rate = ((downloadedBytes - lastProgressBytes) * 1000) / elapsed
                            val remaining = if (rate > 0) {
                                ((effectiveTotal - downloadedBytes) * 1000) / rate
                            } else 0L

                            setProgress(
                                Data.Builder()
                                    .putLong(KEY_RECEIVED_BYTES, downloadedBytes)
                                    .putLong(KEY_TOTAL_BYTES, effectiveTotal)
                                    .putLong(KEY_DOWNLOAD_RATE, rate)
                                    .putLong(KEY_REMAINING_MS, remaining)
                                    .build()
                            )

                            lastProgressTime = now
                            lastProgressBytes = downloadedBytes
                        }
                    }
                }
            }

            tmpFile.renameTo(outputFile)
            Log.i(TAG, "Download completed: ${outputFile.absolutePath}")
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            return Result.retry()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Model Download",
            NotificationManager.IMPORTANCE_LOW,
        )
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createForegroundInfo(title: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }
}
