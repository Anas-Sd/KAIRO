package com.kairo.app.feature.habits.model

import org.json.JSONObject

/**
 * Helper to manage 1-indexed count notes for Target habits.
 * Notes are stored as a JSON hashmap: {"1":"Fajr", "3":"Asr"}
 * This ensures exact 1:1 binding per count index without accidental LIFO removal.
 */
object HabitNoteHelper {

    fun parseIncrementNotes(rawNote: String?): Map<Int, String> {
        if (rawNote.isNullOrBlank()) return emptyMap()
        val trimmed = rawNote.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return try {
                val json = JSONObject(trimmed)
                val map = mutableMapOf<Int, String>()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val countIndex = k.toIntOrNull()
                    val text = json.optString(k, "").trim()
                    if (countIndex != null && countIndex > 0 && text.isNotBlank()) {
                        map[countIndex] = text
                    }
                }
                map.toSortedMap()
            } catch (e: Exception) {
                emptyMap()
            }
        }

        // Backward compatibility for legacy comma-separated notes (e.g. "Fajr, Dhuhr")
        val map = mutableMapOf<Int, String>()
        val segments = trimmed.split(",", "\n", ";").map { it.trim() }.filter { it.isNotBlank() }
        segments.forEachIndexed { index, segment ->
            map[index + 1] = segment
        }
        return map.toSortedMap()
    }

    fun serializeIncrementNotes(map: Map<Int, String>): String? {
        val filtered = map.filter { it.key > 0 && it.value.isNotBlank() }
        if (filtered.isEmpty()) return null
        val json = JSONObject()
        filtered.toSortedMap().forEach { (k, v) ->
            json.put(k.toString(), v.trim())
        }
        return json.toString()
    }

    fun formatDisplayNote(rawNote: String?): String {
        if (rawNote.isNullOrBlank()) return ""
        val trimmed = rawNote.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            val map = parseIncrementNotes(trimmed)
            if (map.isEmpty()) return ""
            return map.entries.joinToString(", ") { (count, note) ->
                "#$count: $note"
            }
        }
        return trimmed
    }

    fun extractAllNotes(rawNote: String?): List<String> {
        if (rawNote.isNullOrBlank()) return emptyList()
        val trimmed = rawNote.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return parseIncrementNotes(trimmed).values.toList()
        }
        return trimmed.split(",", "\n", ";").map { it.trim() }.filter { it.isNotBlank() }
    }
}
