package com.jarvis.assistant.data.repository

import com.jarvis.assistant.data.models.MemoryEntity

object MemoryExtractor {

    data class Result(val cleanText: String, val memories: List<MemoryEntity>)

    private const val TAG_START = "<<<MEMORY>>>"
    private const val TAG_END = "<<<END>>>"

    fun extract(raw: String): Result {
        val start = raw.indexOf(TAG_START)
        val end = raw.indexOf(TAG_END)
        if (start == -1 || end == -1 || end <= start) return Result(raw.trim(), emptyList())

        val cleanText = raw.substring(0, start).trim()
        val block = raw.substring(start + TAG_START.length, end).trim()

        val memories = block.lines()
            .filter { it.contains("|") }
            .mapNotNull { line ->
                val parts = line.split("|", limit = 3)
                if (parts.size == 3) MemoryEntity(
                    module = parts[0].trim().uppercase(),
                    key = parts[1].trim(),
                    value = parts[2].trim()
                ) else null
            }

        return Result(cleanText, memories)
    }
}
