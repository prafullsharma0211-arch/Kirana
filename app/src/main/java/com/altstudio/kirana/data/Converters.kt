package com.altstudio.kirana.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromUnitType(value: UnitType): String {
        return value.name
    }

    @TypeConverter
    fun toUnitType(value: String): UnitType {
        return UnitType.valueOf(value)
    }
}
