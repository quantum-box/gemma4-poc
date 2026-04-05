package com.quantumbox.gemma4poc.data.db

import com.quantumbox.gemma4poc.ui.chat.ChatMessage
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class SessionRepository(private val chatDao: ChatDao) {

    fun getAllSessions(): Flow<List<SessionEntity>> = chatDao.getAllSessions()

    suspend fun createSession(title: String = "New Chat"): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        chatDao.insertSession(
            SessionEntity(id = id, title = title, createdAt = now, updatedAt = now)
        )
        return id
    }

    suspend fun updateSessionTitle(sessionId: String, title: String) {
        val session = chatDao.getSession(sessionId) ?: return
        chatDao.updateSession(session.copy(title = title, updatedAt = System.currentTimeMillis()))
    }

    suspend fun touchSession(sessionId: String) {
        val session = chatDao.getSession(sessionId) ?: return
        chatDao.updateSession(session.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteSession(sessionId: String) {
        chatDao.deleteSession(sessionId)
    }

    suspend fun loadMessages(sessionId: String): List<ChatMessage> {
        return chatDao.getMessages(sessionId).map { entity ->
            ChatMessage(
                id = entity.id,
                text = entity.text,
                isUser = entity.isUser,
                thinking = entity.thinking,
            )
        }
    }

    suspend fun saveMessage(sessionId: String, message: ChatMessage, orderIndex: Int) {
        chatDao.insertMessage(
            MessageEntity(
                id = message.id,
                sessionId = sessionId,
                text = message.text,
                isUser = message.isUser,
                thinking = message.thinking,
                timestamp = System.currentTimeMillis(),
                orderIndex = orderIndex,
            )
        )
        touchSession(sessionId)
    }

    suspend fun updateMessage(messageId: String, text: String, thinking: String?) {
        chatDao.updateMessageContent(messageId, text, thinking)
    }

    suspend fun generateTitle(firstMessage: String): String {
        return firstMessage.take(30).let { if (firstMessage.length > 30) "$it..." else it }
    }
}
