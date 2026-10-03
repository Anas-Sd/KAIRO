package com.kairo.app.feature.ai

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * AiFeatureFacade
 *
 * The single public service window ("door") for the AI Assistant Box.
 * Exposes real-time agentic task execution, multi-LLM engine, voice communication,
 * and persistent memory.
 */
object AiFeatureFacade {

    @Composable
    fun AiAssistantView(
        onBack: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        AiAssistantScreen(onBack = onBack, modifier = modifier)
    }

    suspend fun generatePromptResponse(
        context: Context,
        prompt: String,
        maxTokens: Int = 120
    ): String? {
        val response = AiEngine.chat(prompt)
        return response.text
    }

    fun getApiKey(context: Context): String? {
        return AiEngine.getGroqApiKey(context)
    }

    fun startVoiceListening(context: Context, onResult: (String) -> Unit) {
        AiVoiceManager.startListening(context, onResult)
    }

    fun stopVoiceListening() {
        AiVoiceManager.stopListening()
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        AiVoiceManager.speak(text, onDone)
    }

    fun learnUserRule(rule: String) {
        AiMemoryManager.learnRule(rule)
    }
}
