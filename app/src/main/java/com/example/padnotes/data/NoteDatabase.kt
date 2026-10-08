package com.example.padnotes.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Note::class], version = 2, exportSchema = false)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun dao(): NoteDao

    companion object {
        @Volatile private var instance: NoteDatabase? = null

        fun get(context: Context): NoteDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, NoteDatabase::class.java, "notes.db"
            ).fallbackToDestructiveMigration().build().also { instance = it }
        }
    }
}
