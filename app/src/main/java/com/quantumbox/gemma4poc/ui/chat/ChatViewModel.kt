package com.quantumbox.gemma4poc.ui.chat

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.litertlm.tool
import com.quantumbox.gemma4poc.data.db.SessionEntity
import com.quantumbox.gemma4poc.data.db.SessionRepository
import com.quantumbox.gemma4poc.engine.GemmaEngine
import com.quantumbox.gemma4poc.engine.TokenStats
import com.quantumbox.gemma4poc.tools.PocTools
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val thinking: String? = null,
    val images: List<Bitmap> = emptyList(),
    val isStreaming: Boolean = false,
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isGenerating: Boolean = false,
    val pendingImages: List<Bitmap> = emptyList(),
    val pendingAudioClips: List<ByteArray> = emptyList(),
    val currentSessionId: String? = null,
    val showSessionDrawer: Boolean = false,
    val tokenStats: TokenStats = TokenStats(),
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val gemmaEngine: GemmaEngine,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val sessions: StateFlow<List<SessionEntity>> = sessionRepository.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // 起動時に新しいセッションを作成
        viewModelScope.launch {
            val sessionId = sessionRepository.createSession()
            _uiState.update { it.copy(currentSessionId = sessionId) }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() && _uiState.value.pendingImages.isEmpty() && _uiState.value.pendingAudioClips.isEmpty()) return

        val sessionId = _uiState.value.currentSessionId ?: return
        val images = _uiState.value.pendingImages.toList()
        val audioClips = _uiState.value.pendingAudioClips.toList()

        val userMessage = ChatMessage(
            text = text,
            isUser = true,
            images = images,
        )

        val aiMessageId = java.util.UUID.randomUUID().toString()
        val aiMessage = ChatMessage(
            id = aiMessageId,
            text = "",
            isUser = false,
            isStreaming = true,
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userMessage + aiMessage,
                isGenerating = true,
                pendingImages = emptyList(),
                pendingAudioClips = emptyList(),
            )
        }

        // ユーザーメッセージをDBに保存
        viewModelScope.launch(Dispatchers.IO) {
            val msgIndex = _uiState.value.messages.size - 2
            sessionRepository.saveMessage(sessionId, userMessage, msgIndex)

            // 最初のメッセージならセッションタイトルを更新
            if (msgIndex == 0) {
                val title = sessionRepository.generateTitle(text)
                sessionRepository.updateSessionTitle(sessionId, title)
            }
        }

        viewModelScope.launch(Dispatchers.Default) {
            var accumulatedText = ""
            var thinkingText: String? = null

            val onResult = { result: com.quantumbox.gemma4poc.engine.InferenceResult ->
                if (result.isDone) {
                    val stats = gemmaEngine.getTokenStats()
                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages.map { msg ->
                                if (msg.id == aiMessageId) msg.copy(isStreaming = false)
                                else msg
                            },
                            isGenerating = false,
                            tokenStats = stats,
                        )
                    }
                    viewModelScope.launch(Dispatchers.IO) {
                        val finalMsg = _uiState.value.messages.find { it.id == aiMessageId }
                        if (finalMsg != null) {
                            val idx = _uiState.value.messages.indexOf(finalMsg)
                            sessionRepository.saveMessage(sessionId, finalMsg, idx)
                        }
                    }
                } else {
                    accumulatedText += result.text
                    if (result.thinking != null) {
                        thinkingText = (thinkingText ?: "") + result.thinking
                    }
                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages.map { msg ->
                                if (msg.id == aiMessageId) {
                                    msg.copy(text = accumulatedText, thinking = thinkingText)
                                } else msg
                            },
                        )
                    }
                }
                Unit
            }

            val onError = { error: String ->
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.map { msg ->
                            if (msg.id == aiMessageId) {
                                msg.copy(text = "Error: $error", isStreaming = false)
                            } else msg
                        },
                        isGenerating = false,
                    )
                }
                Unit
            }

            if (gemmaEngine.isMockMode()) {
                gemmaEngine.sendMessageMock(text, images, audioClips, onResult)
            } else {
                gemmaEngine.sendMessage(text, images, audioClips, onResult, onError)
            }
        }
    }

    fun stopGeneration() {
        gemmaEngine.stopResponse()
    }

    fun addPendingImage(bitmap: Bitmap) {
        _uiState.update { it.copy(pendingImages = it.pendingImages + bitmap) }
    }

    fun removePendingImage(index: Int) {
        _uiState.update {
            it.copy(pendingImages = it.pendingImages.toMutableList().apply { removeAt(index) })
        }
    }

    fun addPendingAudioClip(audioData: ByteArray) {
        _uiState.update { it.copy(pendingAudioClips = it.pendingAudioClips + audioData) }
    }

    fun newSession() {
        viewModelScope.launch {
            gemmaEngine.resetConversation(tools = listOf(tool(PocTools())))
            val sessionId = sessionRepository.createSession()
            _uiState.update {
                it.copy(
                    messages = emptyList(),
                    currentSessionId = sessionId,
                    showSessionDrawer = false,
                )
            }
        }
    }

    fun switchSession(sessionId: String) {
        viewModelScope.launch {
            gemmaEngine.resetConversation(tools = listOf(tool(PocTools())))
            val messages = sessionRepository.loadMessages(sessionId)
            _uiState.update {
                it.copy(
                    messages = messages,
                    currentSessionId = sessionId,
                    showSessionDrawer = false,
                )
            }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            sessionRepository.deleteSession(sessionId)
            if (_uiState.value.currentSessionId == sessionId) {
                newSession()
            }
        }
    }

    fun toggleSessionDrawer() {
        _uiState.update { it.copy(showSessionDrawer = !it.showSessionDrawer) }
    }

    fun resetConversation() {
        val sessionId = _uiState.value.currentSessionId ?: return
        viewModelScope.launch {
            gemmaEngine.resetConversation(tools = listOf(tool(PocTools())))
            sessionRepository.deleteSession(sessionId)
            val newId = sessionRepository.createSession()
            _uiState.update {
                it.copy(messages = emptyList(), currentSessionId = newId)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        gemmaEngine.close()
    }
}
