package com.kairo.app.feature.ai

import android.content.Context
import com.kairo.app.KairoApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * Manages persistent agentic memory and learned user habits for the AI assistant.
 * Enables the AI to remember user corrections, preferences, and personal rules
 * so it never makes the same mistake twice.
 */
object AiMemoryManager {

    private const val PREFS_NAME = "kairo_ai_memory"
    private const val KEY_LEARNED_RULES = "learned_rules"
    private const val KEY_USER_PREFERENCES = "user_preferences"

    private val prefs by lazy {
        KairoApplication.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val _rulesFlow = MutableStateFlow<List<String>>(loadRules())
    val rulesFlow: StateFlow<List<String>> = _rulesFlow.asStateFlow()

    private fun loadRules(): List<String> {
        val raw = prefs.getString(KEY_LEARNED_RULES, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(raw)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.optString(i)
                if (!item.isNullOrBlank()) list.add(item)
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveRules(list: List<String>) {
        val jsonArray = JSONArray()
        list.distinct().forEach { jsonArray.put(it) }
        prefs.edit().putString(KEY_LEARNED_RULES, jsonArray.toString()).apply()
        _rulesFlow.value = list.distinct()
    }

    /**
     * Learns a new rule or correction provided by the user.
     * e.g. "Never set priority above LOW for shopping tasks."
     */
    fun learnRule(rule: String) {
        val clean = rule.trim()
        if (clean.isBlank()) return
        val current = loadRules().toMutableList()
        if (!current.contains(clean)) {
            current.add(clean)
            saveRules(current)
        }
    }

    /**
     * Removes an existing learned rule.
     */
    fun forgetRule(rule: String) {
        val current = loadRules().toMutableList()
        current.remove(rule.trim())
        saveRules(current)
    }

    /**
     * Returns all learned rules formatted for injection into the system prompt.
     */
    fun getFormattedRulesForPrompt(): String {
        val rules = _rulesFlow.value
        if (rules.isEmpty()) return ""
        return buildString {
            append("\n\n### LEARNED USER PREFERENCES & RULES (STRICTLY ADHERE):\n")
            rules.forEachIndexed { index, rule ->
                append("${index + 1}. $rule\n")
            }
        }
    }

    fun clearAll() {
        prefs.edit().clear().apply()
        _rulesFlow.value = emptyList()
    }
}
