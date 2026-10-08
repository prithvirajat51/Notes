package com.example.padnotes.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query(
        "SELECT * FROM notes WHERE title LIKE '%' || :query || '%' " +
            "OR body LIKE '%' || :query || '%' ORDER BY pinned DESC, updatedAt DESC"
    )
    fun observe(query: String): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): Note?

    @Upsert
    suspend fun upsert(note: Note): Long

    @Delete
    suspend fun delete(note: Note)
}
