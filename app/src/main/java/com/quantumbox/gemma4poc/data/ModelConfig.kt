package com.quantumbox.gemma4poc.data

data class GemmaModel(
    val name: String,
    val displayName: String,
    val repoId: String,
    val fileName: String,
    val sizeBytes: Long,
    val minMemoryGb: Int,
)

object ModelConfig {
    val GEMMA4_E2B = GemmaModel(
        name = "gemma4-e2b",
        displayName = "Gemma 4 E2B (2.6GB)",
        repoId = "litert-community/gemma-4-E2B-it-litert-lm",
        fileName = "gemma-4-E2B-it.litertlm",
        sizeBytes = 2_583_085_056L,
        minMemoryGb = 8,
    )

    val GEMMA4_E4B = GemmaModel(
        name = "gemma4-e4b",
        displayName = "Gemma 4 E4B (3.7GB)",
        repoId = "litert-community/gemma-4-E4B-it-litert-lm",
        fileName = "gemma-4-E4B-it.litertlm",
        sizeBytes = 3_650_000_000L,
        minMemoryGb = 8,
    )

    val DEFAULT_MODEL = GEMMA4_E2B

    fun getModelDir(basePath: String, model: GemmaModel): String =
        "$basePath/${model.name}"

    fun getModelPath(basePath: String, model: GemmaModel): String =
        "${getModelDir(basePath, model)}/${model.fileName}"

    fun getDownloadUrl(model: GemmaModel): String =
        "https://huggingface.co/${model.repoId}/resolve/main/${model.fileName}"
}
