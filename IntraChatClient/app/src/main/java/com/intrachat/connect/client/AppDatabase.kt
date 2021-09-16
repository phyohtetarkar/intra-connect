package com.intrachat.connect.client

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.intrachat.connect.server.ChatObject

@Database(entities = [Message::class], version = 1)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao

}

class Converters {
    @TypeConverter
    fun fromType(value: String): ChatObject.Type {
        return value.let { ChatObject.Type.valueOf(it) }
    }

    @TypeConverter
    fun typeToString(type: ChatObject.Type): String {
        return type.name
    }
}