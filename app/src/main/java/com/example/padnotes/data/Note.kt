package com.example.padnotes.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val body: String = "",
    val pinned: Boolean = false,
    val colorIndex: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    val isChecklist: Boolean = false,
    val folder: String = "",
    val tags: String = "",
    val drawing: String = ""
) {
    fun tagList(): List<String> =
        tags.split(',', ' ').map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() }

    fun hasContent(): Boolean {
        val text = if (isChecklist) body.lines().joinToString("") { stripMarker(it) } else body
        return title.isNotBlank() || text.isNotBlank() || drawing.isNotBlank()
    }

    fun previewText(): String =
        if (!isChecklist) body
        else body.lines().filter { stripMarker(it).isNotBlank() }
            .joinToString("\n") { (if (it.startsWith("[x] ")) "\u2611 " else "\u2610 ") + stripMarker(it) }
}

fun stripMarker(line: String): String =
    if (line.startsWith("[x] ") || line.startsWith("[ ] ")) line.drop(4) else line
