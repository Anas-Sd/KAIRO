package com.kairo.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object SupabaseClient {
    private const val SUPABASE_URL = "https://lswzgwzbvuiolkzvyrof.supabase.co"
    private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imxzd3pnd3pidnVpb2xrenZ5cm9mIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTA3ODIxMzIsImV4cCI6MjEwNjM1ODEzMn0.u7iQyOKyjKPblLtDYgoiLAHv4p01lEU2LD5HQQ9-7SA"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    suspend fun getTasks(): Result<List<TaskDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/tasks?select=*&order=created_at.desc")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $SUPABASE_KEY")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Failed to fetch tasks: HTTP ${response.code} $body")
            }
            json.decodeFromString<List<TaskDto>>(body)
        }
    }

    suspend fun insertTask(taskDto: TaskDto): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val bodyJson = json.encodeToString(taskDto)
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/tasks")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $SUPABASE_KEY")
                .header("Prefer", "return=minimal")
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                error("Failed to insert task: HTTP ${response.code} $errorBody")
            }
        }
    }

    suspend fun updateTaskCompletion(taskId: String, isCompleted: Boolean, section: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val completedAtVal = if (isCompleted) System.currentTimeMillis() else null
            val patchJson = """{"is_completed":$isCompleted,"section":"$section","completed_at":$completedAtVal,"updated_at":${System.currentTimeMillis()}}"""
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/tasks?id=eq.$taskId")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $SUPABASE_KEY")
                .header("Prefer", "return=minimal")
                .patch(patchJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                error("Failed to update task completion: HTTP ${response.code} $errorBody")
            }
        }
    }

    suspend fun updateTask(taskDto: TaskDto): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val bodyJson = json.encodeToString(taskDto)
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/tasks?id=eq.${taskDto.id}")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $SUPABASE_KEY")
                .header("Prefer", "return=minimal")
                .patch(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                error("Failed to update task: HTTP ${response.code} $errorBody")
            }
        }
    }

    suspend fun deleteTask(taskId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/tasks?id=eq.$taskId")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $SUPABASE_KEY")
                .delete()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                error("Failed to delete task: HTTP ${response.code} $errorBody")
            }
        }
    }
}
