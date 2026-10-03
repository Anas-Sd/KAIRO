package com.kairo.app.feature.sync

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

    suspend fun getTasks(userCode: String? = com.kairo.app.data.auth.AuthManager.getUserCode()): Result<List<TaskDto>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = if (!userCode.isNullOrBlank()) {
                val encodedCode = java.net.URLEncoder.encode(userCode, "UTF-8")
                "$SUPABASE_URL/rest/v1/tasks?user_code=eq.$encodedCode&select=*&order=created_at.desc"
            } else {
                "$SUPABASE_URL/rest/v1/tasks?select=*&order=created_at.desc"
            }

            val request = Request.Builder()
                .url(url)
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $SUPABASE_KEY")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                // If user_code column doesn't exist yet, fallback to fetching all tasks
                if (body.contains("user_code") && !userCode.isNullOrBlank()) {
                    val fallbackReq = Request.Builder()
                        .url("$SUPABASE_URL/rest/v1/tasks?select=*&order=created_at.desc")
                        .header("apikey", SUPABASE_KEY)
                        .header("Authorization", "Bearer $SUPABASE_KEY")
                        .get()
                        .build()
                    val fallbackResp = client.newCall(fallbackReq).execute()
                    val fallbackBody = fallbackResp.body?.string().orEmpty()
                    if (fallbackResp.isSuccessful) {
                        return@runCatching json.decodeFromString<List<TaskDto>>(fallbackBody)
                    }
                }
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
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                error("Failed to insert/upsert task: HTTP ${response.code} $errorBody")
            }
        }
    }

    suspend fun updateTaskCompletion(
        taskId: String,
        isCompleted: Boolean,
        section: String,
        completedAt: Long? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val encodedId = java.net.URLEncoder.encode(taskId, "UTF-8")
            val now = System.currentTimeMillis()
            val completedAtJson = if (completedAt != null) "$completedAt" else "null"
            val patchJson = """{"is_completed":$isCompleted,"section":"$section","completed_at":$completedAtJson,"updated_at":$now}"""
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/tasks?id=eq.$encodedId")
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
            val encodedId = java.net.URLEncoder.encode(taskDto.id, "UTF-8")
            val bodyJson = json.encodeToString(taskDto)
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/tasks?id=eq.$encodedId")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $SUPABASE_KEY")
                .header("Prefer", "return=minimal")
                .patch(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                // If patch fails (e.g. record not in Supabase yet), upsert via insertTask
                insertTask(taskDto).getOrThrow()
            }
        }
    }

    suspend fun batchUpdateTasks(taskDtos: List<TaskDto>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            for (dto in taskDtos) {
                updateTask(dto).getOrThrow()
            }
        }
    }

    suspend fun deleteTask(taskId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val encodedId = java.net.URLEncoder.encode(taskId, "UTF-8")
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/tasks?id=eq.$encodedId")
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

    suspend fun batchDeleteTasks(taskIds: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            for (id in taskIds) {
                deleteTask(id).getOrThrow()
            }
        }
    }

    // ==========================================
    // AUTH & DATA ISOLATION (NO SIGNUP)
    // ==========================================

    suspend fun validateAccessCode(code: String): Result<AccessCodeDto> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = code.trim()
            if (trimmed.isEmpty()) {
                error("Please enter your access code")
            }
            val encodedCode = java.net.URLEncoder.encode(trimmed, "UTF-8")
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/access_codes?code=eq.$encodedCode&select=code,name")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $SUPABASE_KEY")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Failed to verify access code: HTTP ${response.code} $body")
            }
            val list = json.decodeFromString<List<AccessCodeDto>>(body)
            if (list.isEmpty()) {
                error("Invalid access code. Please check and try again.")
            }
            list.first()
        }
    }

    suspend fun rotateAccessCode(oldCode: String, newCode: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanOld = oldCode.trim()
            val cleanNew = newCode.trim()
            if (cleanNew.isEmpty()) {
                error("New code cannot be empty")
            }
            if (cleanOld == cleanNew) {
                error("New code must be different from current code")
            }

            // 1. Fetch current name
            val currentDto = validateAccessCode(cleanOld).getOrThrow()
            val encodedOld = java.net.URLEncoder.encode(cleanOld, "UTF-8")

            // 2. Try PATCH access_codes primary key
            val patchBody = """{"code":"$cleanNew"}""".toRequestBody(JSON_MEDIA_TYPE)
            val patchReq = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/access_codes?code=eq.$encodedOld")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $SUPABASE_KEY")
                .header("Prefer", "return=minimal")
                .patch(patchBody)
                .build()

            val patchResp = client.newCall(patchReq).execute()
            var insertedNewRow = false
            if (!patchResp.isSuccessful) {
                // Fallback: insert new row and delete old row
                val newDtoJson = json.encodeToString(AccessCodeDto(code = cleanNew, name = currentDto.name))
                val insertReq = Request.Builder()
                    .url("$SUPABASE_URL/rest/v1/access_codes")
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer $SUPABASE_KEY")
                    .header("Prefer", "return=minimal")
                    .post(newDtoJson.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                val insertResp = client.newCall(insertReq).execute()
                if (!insertResp.isSuccessful) {
                    error("Failed to update access code: ${insertResp.body?.string()}")
                }
                insertedNewRow = true
            }

            // 3. Migrate tasks from old code to new code
            val updateTasksBody = """{"user_code":"$cleanNew"}""".toRequestBody(JSON_MEDIA_TYPE)
            val updateTasksReq = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/tasks?user_code=eq.$encodedOld")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $SUPABASE_KEY")
                .header("Prefer", "return=minimal")
                .patch(updateTasksBody)
                .build()

            client.newCall(updateTasksReq).execute()

            // 4. If we inserted a new row, delete the old access_codes row
            if (insertedNewRow) {
                val deleteOldReq = Request.Builder()
                    .url("$SUPABASE_URL/rest/v1/access_codes?code=eq.$encodedOld")
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer $SUPABASE_KEY")
                    .delete()
                    .build()
                client.newCall(deleteOldReq).execute()
            }
        }
    }

    suspend fun deleteAllUserData(userCode: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanCode = userCode.trim()
            val encodedCode = java.net.URLEncoder.encode(cleanCode, "UTF-8")
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/tasks?user_code=eq.$encodedCode")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $SUPABASE_KEY")
                .delete()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                error("Failed to delete user tasks: HTTP ${response.code} ${response.body?.string()}")
            }
        }
    }

    suspend fun deleteUserCodeAndData(userCode: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanCode = userCode.trim()
            val encodedCode = java.net.URLEncoder.encode(cleanCode, "UTF-8")

            // 1. Delete all tasks for this user
            val deleteTasksReq = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/tasks?user_code=eq.$encodedCode")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $SUPABASE_KEY")
                .delete()
                .build()
            client.newCall(deleteTasksReq).execute()

            // 2. Delete access code
            val deleteCodeReq = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/access_codes?code=eq.$encodedCode")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $SUPABASE_KEY")
                .delete()
                .build()

            val codeResp = client.newCall(deleteCodeReq).execute()
            if (!codeResp.isSuccessful) {
                error("Failed to delete access code: HTTP ${codeResp.code} ${codeResp.body?.string()}")
            }
        }
    }
}
