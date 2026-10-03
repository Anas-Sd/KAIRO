package com.kairo.app.feature.ai

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

internal object AiClient {

    private const val TAG = "AiClient"
    private const val DEFAULT_ENCODED_KEY = "QVEuQWI4Uk42TDh5aFhNUEVCMlY4cUpFcFBrakR3Q21LX21TSU04d1BMb2k0Tnc1TXZKbmc="
    private const val PREFS_KEY = "kairo_ai_prefs"
    private const val KEY_GEMINI_API_KEY = "gemini_api_key"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun getApiKey(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE)
        val userSaved = prefs.getString(KEY_GEMINI_API_KEY, null)?.takeIf { it.isNotBlank() }
        if (!userSaved.isNullOrBlank()) return userSaved

        try {
            val decoded = String(Base64.decode(DEFAULT_ENCODED_KEY, Base64.DEFAULT), Charsets.UTF_8).trim()
            if (decoded.isNotBlank() && decoded.startsWith("AIzaSy")) return decoded
        } catch (_: Exception) {}

        return null
    }

    suspend fun generateContent(context: Context, prompt: String, maxTokens: Int = 120): String? = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(context) ?: return@withContext null
        try {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
            val bodyJson = """
                {
                  "contents": [{
                    "parts": [{"text": ${Json.encodeToString(kotlinx.serialization.serializer(), prompt)}}]
                  }],
                  "generationConfig": {
                    "temperature": 0.5,
                    "maxOutputTokens": $maxTokens
                  }
                }
            """.trimIndent()

            val request = Request.Builder()
                .url(endpoint)
                .post(bodyJson.toRequestBody("application/json".toMediaType()))
                .build()

            val response = withTimeoutOrNull(5000L) {
                httpClient.newCall(request).execute()
            }

            if (response != null && response.isSuccessful) {
                val respBody = response.body?.string().orEmpty()
                val parsed = json.parseToJsonElement(respBody).jsonObject
                val text = parsed["candidates"]?.jsonArray?.getOrNull(0)
                    ?.jsonObject?.get("content")?.jsonObject
                    ?.get("parts")?.jsonArray?.getOrNull(0)
                    ?.jsonObject?.get("text")?.jsonPrimitive?.content

                if (!text.isNullOrBlank()) {
                    return@withContext text.trim()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gemini generation error: ${e.message}")
        }
        null
    }
}
