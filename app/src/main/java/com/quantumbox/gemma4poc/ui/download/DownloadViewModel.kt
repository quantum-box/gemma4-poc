package com.quantumbox.gemma4poc.ui.download

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quantumbox.gemma4poc.data.DownloadProgress
import com.quantumbox.gemma4poc.data.DownloadRepository
import com.quantumbox.gemma4poc.data.DownloadState
import com.quantumbox.gemma4poc.data.ModelConfig
import com.quantumbox.gemma4poc.engine.GemmaEngine
import com.google.ai.edge.litertlm.tool
import com.quantumbox.gemma4poc.tools.PocTools
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadUiState(
    val progress: DownloadProgress = DownloadProgress(DownloadState.IDLE),
    val isModelReady: Boolean = false,
    val isInitializing: Boolean = false,
    val initError: String? = null,
)

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val gemmaEngine: GemmaEngine,
) : ViewModel() {

    private val model = ModelConfig.DEFAULT_MODEL

    private val _uiState = MutableStateFlow(DownloadUiState())
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    init {
        if (downloadRepository.isModelDownloaded(model)) {
            initializeEngine()
        } else {
            observeDownload()
        }
    }

    fun startDownload() {
        downloadRepository.startDownload(model)
        observeDownload()
    }

    fun cancelDownload() {
        downloadRepository.cancelDownload(model)
    }

    private fun observeDownload() {
        viewModelScope.launch {
            downloadRepository.observeProgress(model).collect { progress ->
                _uiState.value = _uiState.value.copy(progress = progress)
                if (progress.state == DownloadState.COMPLETED) {
                    initializeEngine()
                }
            }
        }
    }

    private fun initializeEngine() {
        _uiState.value = _uiState.value.copy(isInitializing = true)
        viewModelScope.launch(Dispatchers.Default) {
            val modelPath = downloadRepository.getModelPath(model)
            val error = gemmaEngine.initialize(
                modelPath = modelPath,
                tools = listOf(tool(PocTools())),
            )
            _uiState.value = if (error.isEmpty()) {
                _uiState.value.copy(isModelReady = true, isInitializing = false)
            } else {
                _uiState.value.copy(initError = error, isInitializing = false)
            }
        }
    }
}
