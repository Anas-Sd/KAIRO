package com.kairo.app.data.remote

object TaskMetadataCodec {
    private const val META_START = "\n[#kairo_meta:"
    private const val META_END = "#]"

    data class Decoded(
        val cleanNotes: String?,
        val completedAt: Long?,
        val updatedAt: Long?,
        val attachmentUri: String?
    )

    fun encode(userNotes: String?, completedAt: Long?, updatedAt: Long?, attachmentUri: String?): String? {
        val hasMeta = completedAt != null || updatedAt != null || !attachmentUri.isNullOrBlank()
        val clean = userNotes?.trim()?.ifBlank { null }
        if (!hasMeta) return clean

        val parts = mutableListOf<String>()
        completedAt?.let { parts.add("c=$it") }
        updatedAt?.let { parts.add("u=$it") }
        if (!attachmentUri.isNullOrBlank()) {
            parts.add("a=$attachmentUri")
        }

        val metaPayload = "$META_START${parts.joinToString(",")}$META_END"
        return if (clean != null) "$clean$metaPayload" else metaPayload
    }

    fun decode(rawNotes: String?): Decoded {
        if (rawNotes.isNullOrBlank()) return Decoded(null, null, null, null)

        val startIndex = rawNotes.indexOf(META_START)
        if (startIndex == -1) {
            return Decoded(rawNotes.trim().ifBlank { null }, null, null, null)
        }

        val endIndex = rawNotes.indexOf(META_END, startIndex)
        if (endIndex == -1) {
            return Decoded(rawNotes.trim().ifBlank { null }, null, null, null)
        }

        val cleanNotes = rawNotes.substring(0, startIndex).trim().ifBlank { null }
        val metaString = rawNotes.substring(startIndex + META_START.length, endIndex)

        var completedAt: Long? = null
        var updatedAt: Long? = null
        var attachmentUri: String? = null

        metaString.split(",").forEach { pair ->
            val split = pair.split("=", limit = 2)
            if (split.size == 2) {
                when (split[0]) {
                    "c" -> completedAt = split[1].toLongOrNull()
                    "u" -> updatedAt = split[1].toLongOrNull()
                    "a" -> attachmentUri = split[1].ifBlank { null }
                }
            }
        }

        return Decoded(cleanNotes, completedAt, updatedAt, attachmentUri)
    }
}
