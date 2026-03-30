package com.jarvisai.app.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jarvisai.app.data.model.MessageRole

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun toMessageRole(value: String) = enumValueOf<MessageRole>(value)

    @TypeConverter
    fun fromMessageRole(value: MessageRole) = value.name

    @TypeConverter
    fun fromFloatList(value: List<Float>): String = gson.toJson(value)

    @TypeConverter
    fun toFloatList(value: String): List<Float> {
        val type = object : TypeToken<List<Float>>() {}.type
        return gson.fromJson(value, type) ?: emptyList()
    }
}
