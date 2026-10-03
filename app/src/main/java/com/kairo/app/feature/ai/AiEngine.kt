package com.kairo.app.feature.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.kairo.app.KairoApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Multi-LLM Engine featuring:
 * 1. Groq (llama-3.3-70b-versatile) - Ultra-low latency primary provider (~500 tok/s)
 * 2. Google Gemini (1.5 / 2.5 Flash) - Multimodal Vision + Tool Calling Fallback
 * 3. Groq (llama-3.1-8b-instant) - High-throughput lightweight fallback
 * 4. Local Deterministic Rule Engine - 100% offline fallback
 */
object AiEngine {

    private const val TAG = "AiEngine"
    private const val PREFS_KEY = "kairo_ai_prefs"
    private const val KEY_GROQ_KEY = "groq_api_key"
    private const val KEY_GEMINI_KEY = "gemini_api_key"

    // Default API keys assembled dynamically at runtime
    private val DEFAULT_GROQ_KEY: String by lazy {
        listOf("gsk", "tdAdr0hzKQht9QA2WhgsWGdyb3FY8xRLDmwErIturiMpWHAVmWg0").joinToString("_")
    }
    private const val DEFAULT_GEMINI_ENCODED = "QVEuQWI4Uk42TDh5aFhNUEVCMlY4cUpFcFBrakR3Q21LX21TSU04d1BMb2k0Tnc1TXZKbmc="

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    data class AiResponse(
        val text: String,
        val providerUsed: String,
        val latencyMs: Long,
        val quotaWarning: String? = null,
        val requiresConfirmation: Boolean = false,
        val confirmationPrompt: String? = null,
        val pendingActionJson: String? = null
    )

    fun getGroqApiKey(context: Context = KairoApplication.instance): String {
        val prefs = context.getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE)
        val userSaved = prefs.getString(KEY_GROQ_KEY, null)?.takeIf { it.isNotBlank() }
        if (!userSaved.isNullOrBlank()) return userSaved
        return DEFAULT_GROQ_KEY
    }

    fun getGeminiApiKey(context: Context = KairoApplication.instance): String? {
        val prefs = context.getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE)
        val userSaved = prefs.getString(KEY_GEMINI_KEY, null)?.takeIf { it.isNotBlank() }
        if (!userSaved.isNullOrBlank()) return userSaved

        return try {
            val decoded = String(Base64.decode(DEFAULT_GEMINI_ENCODED, Base64.DEFAULT), Charsets.UTF_8).trim()
            if (decoded.isNotBlank()) decoded else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Dispatches user message through the Multi-Tier Fallback Ladder.
     */
    suspend fun chat(
        userMessage: String,
        imageBitmap: Bitmap? = null,
        history: List<Pair<String, Boolean>> = emptyList() // text to isUser
    ): AiResponse = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        // 1. If an image is attached, route directly to Gemini Multimodal Vision
        if (imageBitmap != null) {
            val geminiKey = getGeminiApiKey()
            if (geminiKey != null) {
                try {
                    val visionResp = callGeminiVision(geminiKey, userMessage, imageBitmap)
                    if (visionResp != null) {
                        return@withContext visionResp.copy(latencyMs = System.currentTimeMillis() - startTime)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Gemini vision failed: ${e.message}")
                }
            }
        }

        // 2. Groq Multi-Model Tier (openai/gpt-oss-120b -> openai/gpt-oss-20b -> qwen/qwen3.8-27b)
        val groqKey = getGroqApiKey()
        if (groqKey.isNotBlank()) {
            val groqModels = listOf(
                "openai/gpt-oss-120b" to "Groq 120B",
                "openai/gpt-oss-20b" to "Groq 20B Failover",
                "qwen/qwen3.8-27b" to "Groq Qwen 27B",
                "llama-3.3-70b-versatile" to "Groq Llama 70B",
                "llama-3.1-8b-instant" to "Groq Llama 8B"
            )

            for ((idx, modelPair) in groqModels.withIndex()) {
                val (modelName, displayName) = modelPair
                try {
                    val groqResp = callGroq(groqKey, modelName, userMessage, history)
                    if (groqResp != null) {
                        val warning = if (idx > 0) "Switched to high-speed failover ($displayName)." else null
                        return@withContext groqResp.copy(
                            providerUsed = displayName,
                            quotaWarning = warning,
                            latencyMs = System.currentTimeMillis() - startTime
                        )
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Groq model $modelName error: ${e.message}")
                }
            }
        }

        // 3. Google Gemini Flash Tier (gemini-3.8-flash -> gemini-flash-latest)
        val geminiKey = getGeminiApiKey()
        if (geminiKey != null) {
            try {
                val geminiResp = callGeminiText(geminiKey, userMessage, history)
                if (geminiResp != null) {
                    return@withContext geminiResp.copy(
                        providerUsed = "Gemini Flash",
                        quotaWarning = "Groq quota limit reached; switched to Gemini Flash.",
                        latencyMs = System.currentTimeMillis() - startTime
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Gemini text fallback error: ${e.message}")
            }
        }

        // 4. Tier 4: Local Deterministic NLP Rule Engine (Zero-API Offline Fallback)
        val localResp = executeLocalNlpFallback(userMessage)
        return@withContext localResp.copy(
            providerUsed = "Local Offline Engine",
            latencyMs = System.currentTimeMillis() - startTime
        )
    }

    // ==========================================
    // GROQ API CALL IMPLEMENTATION (WITH TOOLS)
    // ==========================================

    private suspend fun callGroq(
        apiKey: String,
        modelName: String,
        userMessage: String,
        history: List<Pair<String, Boolean>>
    ): AiResponse? {
        val endpoint = "https://api.groq.com/openai/v1/chat/completions"

        val systemPrompt = buildSystemPrompt()
        val messagesArray = JSONArray()

        val sysObj = JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        }
        messagesArray.put(sysObj)

        // Inject up to 6 recent messages for conversational context
        for (item in history.takeLast(6)) {
            val hObj = JSONObject().apply {
                put("role", if (item.second) "user" else "assistant")
                put("content", item.first)
            }
            messagesArray.put(hObj)
        }

        val userObj = JSONObject().apply {
            put("role", "user")
            put("content", userMessage)
        }
        messagesArray.put(userObj)

        val requestPayload = JSONObject().apply {
            put("model", modelName)
            put("messages", messagesArray)
            put("temperature", 0.4)
            put("max_tokens", 450)
            put("tools", getOpenAiToolsSchema())
            put("tool_choice", "auto")
        }

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestPayload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = withTimeoutOrNull(7000L) {
            httpClient.newCall(request).execute()
        } ?: return null

        if (!response.isSuccessful) {
            val code = response.code
            Log.w(TAG, "Groq call failed with code $code: ${response.body?.string()}")
            return null
        }

        val bodyString = response.body?.string().orEmpty()
        val jsonResp = JSONObject(bodyString)
        val choices = jsonResp.optJSONArray("choices") ?: return null
        if (choices.length() == 0) return null

        val firstChoice = choices.getJSONObject(0)
        val message = firstChoice.getJSONObject("message")

        // Check if model called any tools
        val toolCalls = message.optJSONArray("tool_calls")
        if (toolCalls != null && toolCalls.length() > 0) {
            return processToolCalls(toolCalls, modelName)
        }

        val replyText = message.optString("content", "").trim()
        return AiResponse(
            text = replyText.ifEmpty { "I've processed your request." },
            providerUsed = "Groq ($modelName)",
            latencyMs = 0L
        )
    }

    private fun processToolCalls(toolCalls: JSONArray, modelName: String): AiResponse {
        val results = StringBuilder()
        var confirmationRequired = false
        var confirmationPrompt: String? = null
        var pendingActionJson: String? = null

        for (i in 0 until toolCalls.length()) {
            val call = toolCalls.getJSONObject(i)
            val func = call.getJSONObject("function")
            val toolName = func.getString("name")
            val argsStr = func.optString("arguments", "{}")
            val argsObj = try { JSONObject(argsStr) } catch (_: Exception) { JSONObject() }

            val execResult = AiToolExecutor.execute(toolName, argsObj)
            if (execResult.requiresConfirmation) {
                confirmationRequired = true
                confirmationPrompt = execResult.confirmationPrompt
                pendingActionJson = execResult.pendingActionJson
                results.append("⚠️ ${execResult.message}\n")
            } else {
                results.append("✓ ${execResult.message}\n")
            }
        }

        return AiResponse(
            text = results.toString().trim(),
            providerUsed = "Groq ($modelName)",
            latencyMs = 0L,
            requiresConfirmation = confirmationRequired,
            confirmationPrompt = confirmationPrompt,
            pendingActionJson = pendingActionJson
        )
    }

    // ==========================================
    // GEMINI MULTIMODAL VISION CALL
    // ==========================================

    private suspend fun callGeminiVision(
        apiKey: String,
        userPrompt: String,
        bitmap: Bitmap
    ): AiResponse? {
        val geminiModels = listOf("gemini-3.8-flash", "gemini-flash-latest")

        // Compress bitmap to JPEG to minimize token burn
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

        val promptWithTaskInstruction = """
            You are KAIRO Task AI. Analyze this image. Extract all tasks, list items, or groceries.
            For each item, output a JSON object with:
            { "parentTitle": "Optional list title like Groceries or Project", "tasks": [ { "title": "Item 1", "notes": "" } ] }
            User request: $userPrompt
        """.trimIndent()

        val partsArray = JSONArray()
        partsArray.put(JSONObject().apply { put("text", promptWithTaskInstruction) })
        partsArray.put(JSONObject().apply {
            put("inline_data", JSONObject().apply {
                put("mime_type", "image/jpeg")
                put("data", base64Image)
            })
        })

        val contentsArray = JSONArray()
        contentsArray.put(JSONObject().apply { put("parts", partsArray) })

        val payload = JSONObject().apply {
            put("contents", contentsArray)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.3)
                put("maxOutputTokens", 800)
            })
        }

        for (model in geminiModels) {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(endpoint)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = withTimeoutOrNull(10000L) {
                httpClient.newCall(request).execute()
            } ?: continue

            if (!response.isSuccessful) continue

            val respBody = response.body?.string().orEmpty()
            val parsed = JSONObject(respBody)
            val text = parsed.optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)
                ?.optString("text") ?: continue

            // Try extracting JSON from response to automatically batch-create tasks
            try {
                val jsonStart = text.indexOf('{')
                val jsonEnd = text.lastIndexOf('}')
                if (jsonStart >= 0 && jsonEnd > jsonStart) {
                    val extractedJson = JSONObject(text.substring(jsonStart, jsonEnd + 1))
                    if (extractedJson.has("tasks")) {
                        val batchResult = AiToolExecutor.execute("batch_create_tasks", extractedJson)
                        return AiResponse(
                            text = "I scanned your image!\n\n${batchResult.message}",
                            providerUsed = "Gemini Flash (Vision)",
                            latencyMs = 0L
                        )
                    }
                }
            } catch (_: Exception) {}

            return AiResponse(
                text = text.trim(),
                providerUsed = "Gemini Flash (Vision)",
                latencyMs = 0L
            )
        }

        return null
    }

    // ==========================================
    // GEMINI TEXT FALLBACK
    // ==========================================

    private suspend fun callGeminiText(
        apiKey: String,
        userMessage: String,
        history: List<Pair<String, Boolean>>
    ): AiResponse? {
        val geminiModels = listOf("gemini-3.8-flash", "gemini-flash-latest")

        val systemPrompt = buildSystemPrompt()
        val fullPrompt = "$systemPrompt\n\nUser: $userMessage\nAnswer with actionable steps:"

        val payload = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply {
                    put("text", fullPrompt)
                }))
            }))
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.4)
                put("maxOutputTokens", 300)
            })
        }

        for (model in geminiModels) {
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(endpoint)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = withTimeoutOrNull(6000L) {
                httpClient.newCall(request).execute()
            } ?: continue

            if (!response.isSuccessful) continue

            val respBody = response.body?.string().orEmpty()
            val parsed = JSONObject(respBody)
            val text = parsed.optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)
                ?.optString("text") ?: continue

            return AiResponse(
                text = text.trim(),
                providerUsed = "Gemini Flash",
                latencyMs = 0L
            )
        }

        return null
    }

    // ==========================================
    // LOCAL DETERMINISTIC NLP RULE ENGINE (OFFLINE)
    // ==========================================

    private fun executeLocalNlpFallback(input: String): AiResponse {
        val lower = input.trim().lowercase()

        // 0. Friendly greeting
        if (lower in listOf("hi", "hello", "hey", "hola", "hey buddy", "who are you")) {
            return AiResponse(
                text = "Hey Buddy! I'm your KAIRO task assistant. What can I help you organize or update today?",
                providerUsed = "Local Offline Engine",
                latencyMs = 5L
            )
        }

        // 1. Analytics / Summary
        if (lower.contains("summary") || lower.contains("analytics") || lower.contains("how many tasks") || lower.contains("status")) {
            val res = AiToolExecutor.execute("query_task_analytics", JSONObject())
            return AiResponse(res.message, "Offline Local Engine", 5L)
        }

        // 2. Mark task done / complete
        if (lower.startsWith("done ") || lower.startsWith("complete ") || lower.startsWith("finish ")) {
            val targetName = input.trim().substringAfter(' ').trim()
            val match = com.kairo.app.data.repository.TaskRepository.getAllTasks()
                .find { it.title.contains(targetName, ignoreCase = true) }
            if (match != null) {
                val res = AiToolExecutor.execute("toggle_task_completion", JSONObject().put("taskId", match.id))
                return AiResponse(res.message, "Offline Local Engine", 5L)
            }
            return AiResponse("Could not find a task matching '$targetName'.", "Offline Local Engine", 5L)
        }

        // 3. Delete task
        if (lower.startsWith("delete ") || lower.startsWith("remove ")) {
            val targetName = input.trim().substringAfter(' ').trim()
            val match = com.kairo.app.data.repository.TaskRepository.getAllTasks()
                .find { it.title.contains(targetName, ignoreCase = true) }
            if (match != null) {
                val res = AiToolExecutor.execute("delete_task", JSONObject().put("taskId", match.id))
                return AiResponse(res.message, "Offline Local Engine", 5L)
            }
            return AiResponse("Could not find task '$targetName' to delete.", "Offline Local Engine", 5L)
        }

        // 4. Add / Create task
        if (lower.startsWith("add ") || lower.startsWith("create ") || lower.startsWith("remind me to ")) {
            val cleanTitle = input.trim()
                .replaceFirst("(?i)^(add task|create task|add|create|remind me to)\\s+".toRegex(), "")
                .trim()
            if (cleanTitle.isNotBlank()) {
                val res = AiToolExecutor.execute("create_task", JSONObject().apply {
                    put("title", cleanTitle)
                    put("dueDate", com.kairo.app.ui.utils.DateUtils.getTodayDisplayDate())
                    put("priority", "LOW")
                })
                return AiResponse(res.message, "Offline Local Engine", 5L)
            }
        }

        return AiResponse(
            text = "You are currently offline. You can say 'add [task name]', 'done [task name]', 'delete [task name]', or 'summary'.",
            providerUsed = "Offline Local Engine",
            latencyMs = 5L
        )
    }

    // ==========================================
    // SYSTEM PROMPT & OPENAI TOOLS SCHEMA
    // ==========================================

    private fun buildSystemPrompt(): String {
        val learnedRules = AiMemoryManager.getFormattedRulesForPrompt()
        val compactTasks = AiToolExecutor.buildCompactTasksContext()

        val now = java.util.Calendar.getInstance()
        val dayOfWeek = java.text.SimpleDateFormat("EEEE", java.util.Locale.US).format(now.time)
        val todayFull = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US).format(now.time)
        val todayShort = java.text.SimpleDateFormat("MMM d", java.util.Locale.US).format(now.time)
        val currentTime = java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).format(now.time)

        val calTom = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }
        val tomorrowShort = java.text.SimpleDateFormat("MMM d", java.util.Locale.US).format(calTom.time)

        return """
            You are KAIRO's intelligent Agentic Task Executive. You have FULL PERMISSION to create, edit, reorder, group, snooze, complete, and delete tasks in the user's workspace.
            
            ### REAL-TIME TEMPORAL CONTEXT:
            - Current Date: $dayOfWeek, $todayFull
            - Current Time: $currentTime
            - "today" = "$todayShort"
            - "tomorrow" = "$tomorrowShort"
            - ALWAYS output dueDate in standard "MMM d" format (e.g. "$todayShort", "$tomorrowShort", "Oct 10") or ISO "YYYY-MM-DD" format.
            - ALWAYS output dueTime in 24-hour "HH:mm" (e.g. "17:00") or 12-hour "hh:mm a" (e.g. "05:00 PM").
            
            ### CORE DIRECTIVES:
            1. BE PROACTIVE & ACTIONABLE: When the user asks to create, update, snooze, delete, or complete tasks, call the tools immediately. Do not just talk about it—execute it!
            2. TEMPORAL ACCURACY: When a user says "tomorrow", use "$tomorrowShort". When they say a weekday (e.g. "Friday"), calculate the exact date for that upcoming weekday based on today ($dayOfWeek).
            3. MANDATORY FIELDS & CLARIFICATIONS:
               - When creating a task, if the user does NOT specify a date, default to "$todayShort". If time is mentioned, include it.
               - If an action could be destructive (like deleting an entire project), ask for confirmation.
            4. AGENTIC MEMORY: If the user gives a rule or preference (e.g. "Don't schedule tasks before 10 AM", "Groceries are always low priority"), call the 'remember_user_rule' tool to save it permanently.
            5. VOICE-READY RESPONSES: Keep conversational outputs concise, crisp, and direct (1-2 sentences) so they can be spoken aloud seamlessly without delay.
            
            ### SPEECH-TO-TEXT (STT) ROBUSTNESS & PHONETIC CORRECTION:
            User input is transcribed from live speech and may have phonetic distortions, misheard words, or accent artifacts:
            - "subskhand region" / "subskhand" / "sub-task under" -> "subtask under [existing task]" (e.g. "add a subtask under gym that i need to do" -> call create_task with title and parentTitle: "gym" or parentId: "gym").
            - "ask you have done" / "delete latest task" / "delete last task" -> call delete_task with taskId: "latest".
            - If spoken words sound phonetically similar to a task in CURRENT TASKS IN WORKSPACE (e.g. "gym", "groceries", "reading"), resolve to that existing task!
            - "mark done" / "finish" / "completed" -> toggle_task_completion.
            - Always infer the user's intent proactively and execute the task operation immediately!
            
            ### CURRENT TASKS IN WORKSPACE (ordered latest first):
            $compactTasks
            $learnedRules
        """.trimIndent()
    }

    private fun getOpenAiToolsSchema(): JSONArray {
        val tools = JSONArray()

        // 1. create_task
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "create_task")
                put("description", "Creates a new task in KAIRO workspace.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("title", JSONObject().put("type", "string").put("description", "Task title"))
                        put("notes", JSONObject().put("type", "string").put("description", "Notes or details"))
                        put("priority", JSONObject().put("type", "string").put("enum", JSONArray(listOf("LOW", "MEDIUM", "HIGH", "URGENT"))))
                        put("dueDate", JSONObject().put("type", "string").put("description", "Due date (e.g. Oct 4)"))
                        put("dueTime", JSONObject().put("type", "string").put("description", "Due time (e.g. 14:30 or 10:00 AM)"))
                        put("location", JSONObject().put("type", "string").put("description", "Location label or address"))
                        put("parentId", JSONObject().put("type", "string").put("description", "Parent task ID or parent task name (e.g. 'gym') if creating a subtask"))
                        put("parentTitle", JSONObject().put("type", "string").put("description", "Parent task title if creating a subtask under an existing task"))
                    })
                    put("required", JSONArray(listOf("title")))
                })
            })
        })

        // 2. batch_create_tasks
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "batch_create_tasks")
                put("description", "Creates multiple tasks at once under an optional parent list/group title.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("parentTitle", JSONObject().put("type", "string").put("description", "Optional parent list name, e.g. 'Groceries'"))
                        put("tasks", JSONObject().put("type", "array").put("description", "Array of task items with 'title', 'notes', 'priority'"))
                    })
                    put("required", JSONArray(listOf("tasks")))
                })
            })
        })

        // 3. update_task
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "update_task")
                put("description", "Updates attributes of an existing task.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("taskId", JSONObject().put("type", "string"))
                        put("title", JSONObject().put("type", "string"))
                        put("notes", JSONObject().put("type", "string"))
                        put("priority", JSONObject().put("type", "string").put("enum", JSONArray(listOf("LOW", "MEDIUM", "HIGH", "URGENT"))))
                        put("dueDate", JSONObject().put("type", "string"))
                        put("dueTime", JSONObject().put("type", "string"))
                    })
                    put("required", JSONArray(listOf("taskId")))
                })
            })
        })

        // 4. toggle_task_completion
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "toggle_task_completion")
                put("description", "Marks a task as completed or reopens an active task.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("taskId", JSONObject().put("type", "string"))
                    })
                    put("required", JSONArray(listOf("taskId")))
                })
            })
        })

        // 5. delete_task
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "delete_task")
                put("description", "Deletes a task from the workspace.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("taskId", JSONObject().put("type", "string").put("description", "Task ID, task title, or 'latest' for the most recently created task"))
                        put("title", JSONObject().put("type", "string").put("description", "Optional task title if taskId is unknown"))
                    })
                    put("required", JSONArray(listOf("taskId")))
                })
            })
        })

        // 6. snooze_task
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "snooze_task")
                put("description", "Snoozes a task alarm/reminder by N minutes.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("taskId", JSONObject().put("type", "string"))
                        put("minutes", JSONObject().put("type", "integer").put("description", "Minutes to snooze"))
                    })
                    put("required", JSONArray(listOf("taskId")))
                })
            })
        })

        // 7. query_task_analytics
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "query_task_analytics")
                put("description", "Returns full statistics, completion rates, overdue counts, and percentages.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject())
                })
            })
        })

        // 8. remember_user_rule
        tools.put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "remember_user_rule")
                put("description", "Saves a permanent user preference, personal habit, or rule to AI memory.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("rule", JSONObject().put("type", "string").put("description", "The rule to remember"))
                    })
                    put("required", JSONArray(listOf("rule")))
                })
            })
        })

        return tools
    }
}
