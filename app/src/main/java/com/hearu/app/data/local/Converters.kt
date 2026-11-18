package com.hearu.app.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromIntList(value: List<Int>?): String {
        if (value.isNullOrEmpty()) return ""
        return value.joinToString(",")
    }

    @TypeConverter
    fun toIntList(value: String?): List<Int> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split(",").mapNotNull { it.trim().toIntOrNull() }
    }
}
