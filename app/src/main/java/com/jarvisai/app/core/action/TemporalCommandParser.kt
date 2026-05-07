package com.jarvisai.app.core.action

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object TemporalCommandParser {
    data class ReminderParseResult(
        val title: String,
        val message: String,
        val triggerAtMillis: Long
    )

    fun parseReminder(text: String): ReminderParseResult? {
        val normalized = text.trim().replace(Regex("\\s+"), " ")
        val lower = normalized.lowercase(Locale.getDefault())

        val time = extractTime(lower) ?: return null
        val date = when {
            "tomorrow" in lower -> LocalDate.now().plusDays(1)
            else -> LocalDate.now()
        }

        val message = extractReminderMessage(normalized) ?: return null
        val title = buildTitle(message)
        val dateTime = LocalDateTime.of(date, time)
        val millis = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return ReminderParseResult(title, message, millis)
    }

    fun parseAlarm(text: String): ReminderParseResult? {
        val normalized = text.trim().replace(Regex("\\s+"), " ")
        val lower = normalized.lowercase(Locale.getDefault())
        if (!lower.contains("alarm")) return null

        val time = extractTime(lower) ?: return null
        val date = when {
            "tomorrow" in lower -> LocalDate.now().plusDays(1)
            else -> LocalDate.now()
        }

        val label = buildAlarmLabel(normalized)
        val dateTime = LocalDateTime.of(date, time)
        val millis = dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return ReminderParseResult("Alarm", label, millis)
    }

    private fun extractReminderMessage(text: String): String? {
        val lower = text.lowercase(Locale.getDefault())
        return when {
            lower.contains("remind me to ") -> text.substringAfter("remind me to ", "")
            lower.contains("remind me at ") && lower.contains(" to ") -> text.substringAfter(" to ", "")
            lower.contains("remind me tomorrow to ") -> text.substringAfter("remind me tomorrow to ", "")
            lower.contains("remember to ") -> text.substringAfter("remember to ", "")
            else -> null
        }?.trim()?.trimEnd('.', '!')
    }

    private fun buildTitle(message: String): String {
        val words = message.split(" ").filter { it.isNotBlank() }.take(5)
        return if (words.isEmpty()) "Reminder" else words.joinToString(" ")
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    private fun buildAlarmLabel(text: String): String {
        val lower = text.lowercase(Locale.getDefault())
        return when {
            " for " in lower -> text.substringAfter(" for ", "").trim().ifBlank { "Jarvis Alarm" }
            else -> "Jarvis Alarm"
        }
    }

    private fun extractTime(text: String): LocalTime? {
        val twelveHour = Regex("""\b(1[0-2]|0?[1-9])(?::([0-5]\d))?\s*(am|pm)\b""")
        val twentyFourHour = Regex("""\b([01]?\d|2[0-3]):([0-5]\d)\b""")

        twelveHour.find(text)?.let { match ->
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].ifBlank { "00" }.toInt()
            val meridiem = match.groupValues[3]
            val adjustedHour = when {
                meridiem == "am" && hour == 12 -> 0
                meridiem == "pm" && hour != 12 -> hour + 12
                else -> hour
            }
            return LocalTime.of(adjustedHour, minute)
        }

        twentyFourHour.find(text)?.let { match ->
            return LocalTime.of(match.groupValues[1].toInt(), match.groupValues[2].toInt())
        }

        return null
    }

    fun formatTriggerTime(triggerAtMillis: Long): String {
        val formatter = DateTimeFormatter.ofPattern("EEE, d MMM • h:mm a", Locale.getDefault())
        return LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(triggerAtMillis),
            ZoneId.systemDefault()
        ).format(formatter)
    }
}
