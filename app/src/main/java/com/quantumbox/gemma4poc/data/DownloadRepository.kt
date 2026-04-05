package com.quantumbox.gemma4poc.data

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.quantumbox.gemma4poc.BuildConfig
import com.quantumbox.gemma4poc.worker.ModelDownloadWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.io.File

data class DownloadProgress(
    val state: DownloadState,
    val receivedBytes: Long = 0,
    val totalBytes: Long = 0,
    val downloadRateBytes: Long = 0,
    val remainingMs: Long = 0,
)

enum class DownloadState {
    IDLE, DOWNLOADING, COMPLETED, FAILED, CANCELLED
}

class DownloadRepository(private val context: Context) {

    private val workManager = WorkManager.getInstance(context)

    companion object {
        // adb push でモデルを配置するパス
        // 使い方: adb push gemma4-e2b-it.litertlm /data/local/tmp/gemma4-e2b-it.litertlm
        private const val DEV_MODEL_DIR = "/data/local/tmp"
    }

    fun isModelDownloaded(model: GemmaModel): Boolean {
        // デバッグ時は /data/local/tmp も探す
        if (BuildConfig.DEBUG && getDevModelFile(model).exists()) return true

        val path = ModelConfig.getModelPath(getBaseDir(), model)
        val file = File(path)
        return file.exists() && file.length() > 0
    }

    fun getModelPath(model: GemmaModel): String {
        // デバッグ時は /data/local/tmp を優先
        if (BuildConfig.DEBUG) {
            val devFile = getDevModelFile(model)
            if (devFile.exists()) return devFile.absolutePath
        }
        return ModelConfig.getModelPath(getBaseDir(), model)
    }

    private fun getDevModelFile(model: GemmaModel): File =
        File(DEV_MODEL_DIR, model.fileName)

    fun startDownload(model: GemmaModel) {
        val inputData = Data.Builder()
            .putString(ModelDownloadWorker.KEY_MODEL_NAME, model.name)
            .putString(ModelDownloadWorker.KEY_DOWNLOAD_URL, ModelConfig.getDownloadUrl(model))
            .putString(ModelDownloadWorker.KEY_OUTPUT_DIR, ModelConfig.getModelDir(getBaseDir(), model))
            .putString(ModelDownloadWorker.KEY_FILE_NAME, model.fileName)
            .putLong(ModelDownloadWorker.KEY_TOTAL_BYTES, model.sizeBytes)
            .build()

        val downloadRequest = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(inputData)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        workManager.enqueueUniqueWork(
            "download_${model.name}",
            ExistingWorkPolicy.KEEP,
            downloadRequest,
        )
    }

    fun cancelDownload(model: GemmaModel) {
        workManager.cancelUniqueWork("download_${model.name}")
    }

    fun observeProgress(model: GemmaModel): Flow<DownloadProgress> {
        return workManager.getWorkInfosForUniqueWorkFlow("download_${model.name}")
            .map { workInfos ->
                val workInfo = workInfos.firstOrNull() ?: return@map DownloadProgress(DownloadState.IDLE)
                when (workInfo.state) {
                    WorkInfo.State.ENQUEUED -> DownloadProgress(DownloadState.DOWNLOADING)
                    WorkInfo.State.RUNNING -> {
                        val progress = workInfo.progress
                        DownloadProgress(
                            state = DownloadState.DOWNLOADING,
                            receivedBytes = progress.getLong(ModelDownloadWorker.KEY_RECEIVED_BYTES, 0),
                            totalBytes = progress.getLong(ModelDownloadWorker.KEY_TOTAL_BYTES, model.sizeBytes),
                            downloadRateBytes = progress.getLong(ModelDownloadWorker.KEY_DOWNLOAD_RATE, 0),
                            remainingMs = progress.getLong(ModelDownloadWorker.KEY_REMAINING_MS, 0),
                        )
                    }
                    WorkInfo.State.SUCCEEDED -> DownloadProgress(DownloadState.COMPLETED)
                    WorkInfo.State.FAILED -> DownloadProgress(DownloadState.FAILED)
                    WorkInfo.State.CANCELLED -> DownloadProgress(DownloadState.CANCELLED)
                    WorkInfo.State.BLOCKED -> DownloadProgress(DownloadState.DOWNLOADING)
                }
            }
    }

    private fun getBaseDir(): String =
        context.getExternalFilesDir(null)?.absolutePath ?: context.filesDir.absolutePath
}
