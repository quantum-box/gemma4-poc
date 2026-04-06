package com.quantumbox.gemma4poc.engine

import android.graphics.Bitmap
import android.os.Build
import com.google.ai.edge.litertlm.ToolProvider
import kotlinx.coroutines.delay

object MockGemmaEngine {

    val isEmulator: Boolean
        get() = Build.FINGERPRINT.contains("generic") ||
                Build.FINGERPRINT.contains("emulator") ||
                Build.MODEL.contains("sdk_gphone") ||
                Build.MODEL.contains("Emulator") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                Build.PRODUCT.contains("sdk_gphone")

    private val sampleResponses = listOf(
        "こんにちは！Gemma 4のモックモードで動作しています。\n\n## 機能一覧\n\n- **テキストチャット**: メッセージを送信して応答を受け取れます\n- **画像入力**: カメラやギャラリーから画像を添付できます\n- **音声入力**: マイクで音声を録音して送信できます\n- **ツール呼び出し**: 現在時刻の取得や計算ができます\n\n> これはエミュレータ上のモック応答です。実機では本物のGemma 4が動作します。",
        "これは**モックレスポンス**です。実機では LiteRT LM エンジンが Gemma 4 モデルを使ってオンデバイスで推論を行います。\n\n### コード例\n\n```kotlin\nval engine = Engine(engineConfig)\nengine.initialize()\nval conversation = engine.createConversation(config)\n```\n\nエミュレータでは CPU 命令セットの制限により、ネイティブ推論は実行できません。",
        "画像を受け取りました。モックモードでは画像解析は行えませんが、実機では Gemma 4 のマルチモーダル機能で画像の内容を理解できます。\n\n1. 画像認識\n2. テキスト抽出\n3. 質問への回答",
        "音声データを受け取りました。Gemma 4 は音声入力にも対応しています。\n\n*実機では音声を直接理解して応答を生成します。*",
    )

    private var responseIndex = 0
    private var totalTokens = 0

    suspend fun simulateResponse(
        text: String,
        images: List<Bitmap>,
        audioClips: List<ByteArray>,
        onPartialResult: (InferenceResult) -> Unit,
    ) {
        val response = when {
            images.isNotEmpty() -> sampleResponses[2]
            audioClips.isNotEmpty() -> sampleResponses[3]
            else -> {
                val r = sampleResponses[responseIndex % 2]
                responseIndex++
                r
            }
        }

        // ストリーミングをシミュレート
        val words = response.split(" ", "\n").filter { it.isNotEmpty() }
        val sb = StringBuilder()
        for ((i, word) in words.withIndex()) {
            if (i > 0) sb.append(if (response[response.indexOf(word, sb.length.coerceAtMost(response.length - 1)).coerceAtLeast(0) - 1] == '\n') "\n" else " ")
            sb.append(word)
            delay(30) // 30ms per token simulation
            onPartialResult(InferenceResult(text = word + " "))
        }

        // Estimate tokens (~4 chars per token)
        val estimatedTokens = (text.length + response.length) / 4
        totalTokens += estimatedTokens

        onPartialResult(InferenceResult(text = "", isDone = true))
    }

    fun getTokenStats(maxTokens: Int): TokenStats {
        return TokenStats(
            totalTokensUsed = totalTokens,
            maxTokens = maxTokens,
            lastDecodeTokens = 50,
            decodeSpeed = 35.0,
        )
    }

    fun reset() {
        totalTokens = 0
        responseIndex = 0
    }
}
