package com.cruciblelab.trafficlogger.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromProtocol(value: Protocol): String = value.name

    @TypeConverter
    fun toProtocol(value: String): Protocol = Protocol.valueOf(value)

    @TypeConverter
    fun fromDirection(value: Direction): String = value.name

    @TypeConverter
    fun toDirection(value: String): Direction = Direction.valueOf(value)
}
