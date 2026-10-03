package com.kairo.app.feature.ai

import androidx.compose.runtime.mutableStateListOf

/**
 * Singleton repository holding persistent AI conversational history across screens and app sessions.
 */
object AiChatRepository {

    val messages = mutableStateListOf<ChatMessage>()

    init {
        resetIfEmpty()
    }

    private fun resetIfEmpty() {
        if (messages.isEmpty()) {
            messages.add(
                ChatMessage(
                    text = "Hey Buddy! I'm your KAIRO personal task executive. I can create, organize, group, snooze, or query your tasks. You can speak to me or send photos of lists.",
                    isUser = false,
                    providerUsed = "KAIRO Executive Core"
                )
            )
        }
    }

    fun addMessage(message: ChatMessage) {
        messages.add(message)
    }

    fun updateMessage(index: Int, updated: ChatMessage) {
        if (index in 0 until messages.size) {
            messages[index] = updated
        }
    }

    fun getConversationHistory(): List<Pair<String, Boolean>> {
        return messages.map { it.text to it.isUser }
    }

    fun clearAll() {
        messages.clear()
        resetIfEmpty()
    }
}
