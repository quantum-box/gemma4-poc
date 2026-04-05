package com.quantumbox.gemma4poc.engine

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ToolProvider
import java.io.ByteArrayOutputStream
import java.util.concurrent.CancellationException

private const val TAG = "GemmaEngine"

data class InferenceResult(
    val text: String,
    val thinking: String? = null,
    val isDone: Boolean = false,
)

class GemmaEngine(private val context: Context) {

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var currentModelPath: String? = null
    private var currentTools: List<ToolProvider> = emptyList()
    private var currentBackend: Backend? = null

    val isInitialized: Boolean get() = engine != null && conversation != null

    fun initialize(
        modelPath: String,
        backend: Backend = Backend.CPU(),
        tools: List<ToolProvider> = emptyList(),
        maxTokens: Int = 4096,
        topK: Int = 40,
        topP: Double = 0.95,
        temperature: Double = 0.8,
    ): String {
        currentModelPath = modelPath
        currentTools = tools

        // GPU で試し、失敗したら CPU にフォールバック
        val backendsToTry = if (backend is Backend.GPU) {
            listOf(Backend.GPU() to Backend.GPU(), Backend.CPU() to Backend.CPU())
        } else {
            listOf(backend to Backend.CPU())
        }

        for ((mainBackend, visionBack) in backendsToTry) {
            try {
                val engineConfig = EngineConfig(
                    modelPath = modelPath,
                    backend = mainBackend,
                    visionBackend = visionBack,
                    audioBackend = Backend.CPU(),
                    maxNumTokens = maxTokens,
                    cacheDir = context.getExternalFilesDir(null)?.absolutePath,
                )

                val newEngine = Engine(engineConfig)
                newEngine.initialize()

                val conversationConfig = ConversationConfig(
                    samplerConfig = SamplerConfig(
                        topK = topK,
                        topP = topP,
                        temperature = temperature,
                    ),
                    tools = tools,
                )
                val newConversation = newEngine.createConversation(conversationConfig)

                engine = newEngine
                conversation = newConversation
                currentBackend = mainBackend

                Log.i(TAG, "Engine initialized with backend=$mainBackend, model=$modelPath")
                return ""
            } catch (e: Exception) {
                Log.w(TAG, "Failed to initialize with backend=$mainBackend, trying next...", e)
            }
        }

        val errorMsg = "All backends failed for model: $modelPath"
        Log.e(TAG, errorMsg)
        return errorMsg
    }

    private fun reinitializeWithCpu(): Boolean {
        val path = currentModelPath ?: return false
        Log.i(TAG, "Reinitializing with CPU backend due to GPU runtime error")
        close()
        val error = initialize(
            modelPath = path,
            backend = Backend.CPU(),
            tools = currentTools,
        )
        return error.isEmpty()
    }

    fun sendMessage(
        text: String,
        images: List<Bitmap> = emptyList(),
        audioClips: List<ByteArray> = emptyList(),
        onPartialResult: (InferenceResult) -> Unit,
        onError: (String) -> Unit,
    ) {
        val conv = conversation ?: run {
            onError("Engine not initialized")
            return
        }

        val contents = mutableListOf<Content>()
        for (image in images) {
            contents.add(Content.ImageBytes(image.toPngByteArray()))
        }
        for (audioClip in audioClips) {
            contents.add(Content.AudioBytes(audioClip))
        }
        if (text.isNotBlank()) {
            contents.add(Content.Text(text))
        }

        conv.sendMessageAsync(
            Contents.of(contents),
            object : MessageCallback {
                override fun onMessage(message: Message) {
                    onPartialResult(
                        InferenceResult(
                            text = message.toString(),
                            thinking = message.channels["thought"],
                        )
                    )
                }

                override fun onDone() {
                    onPartialResult(InferenceResult(text = "", isDone = true))
                }

                override fun onError(throwable: Throwable) {
                    if (throwable is CancellationException) {
                        Log.i(TAG, "Inference cancelled")
                        onPartialResult(InferenceResult(text = "", isDone = true))
                    } else if (throwable.message?.contains("OpenCL") == true &&
                        currentBackend is Backend.GPU
                    ) {
                        Log.w(TAG, "OpenCL error on GPU, falling back to CPU and retrying...")
                        if (reinitializeWithCpu()) {
                            sendMessage(text, images, audioClips, onPartialResult, onError)
                        } else {
                            onError("GPU/CPU both failed: ${throwable.message}")
                        }
                    } else {
                        Log.e(TAG, "Inference error", throwable)
                        onError(throwable.message ?: "Inference failed")
                    }
                }
            },
            emptyMap(),
        )
    }

    fun stopResponse() {
        conversation?.cancelProcess()
    }

    fun resetConversation(tools: List<ToolProvider> = emptyList()) {
        try {
            conversation?.close()
            val conv = engine?.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.8),
                    tools = tools,
                )
            )
            conversation = conv
            Log.i(TAG, "Conversation reset")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset conversation", e)
        }
    }

    fun close() {
        try {
            conversation?.close()
            engine?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close engine", e)
        }
        conversation = null
        engine = null
    }

    private fun Bitmap.toPngByteArray(): ByteArray {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }
}
