package com.kairo.app.feature.ai

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * AiFeatureFacade
 *
 * The single public service window ("door") for the AI Assistant Box.
 * The rest of the app interacts with Gemini AI capabilities via this facade.
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
        return AiClient.generateContent(context, prompt, maxTokens)
    }

    fun getApiKey(context: Context): String? {
        return AiClient.getApiKey(context)
    }
}
