package com.example.padnotes.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.padnotes.data.Note
import com.example.padnotes.data.NoteDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The note currently open in the editor. [token] identifies one editing session. */
data class Draft(val token: Long, val note: Note)

/** Folders and tags that exist across all notes, used for the filter chips. */
data class Filters(val folders: List<String> = emptyList(), val tags: List<String> = emptyList())

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = NoteDatabase.get(app).dao()

    var query by mutableStateOf("")
        private set

    /** null = all notes, "f:Name" = folder, "t:name" = tag. */
    var filter by mutableStateOf<String?>(null)
        private set

    private val searched = snapshotFlow { query.trim() }
        .distinctUntilChanged()
        .flatMapLatest { dao.observe(it) }

    val notes: StateFlow<List<Note>> = combine(searched, snapshotFlow { filter }) { list, f ->
        when {
            f == null -> list
            f.startsWith("f:") -> list.filter { it.folder == f.drop(2) }
            else -> list.filter { n -> n.tagList().any { it.equals(f.drop(2), ignoreCase = true) } }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filters: StateFlow<Filters> = dao.observe("").map { all ->
        Filters(
            folders = all.map { it.folder }.filter { it.isNotBlank() }.distinct().sorted(),
            tags = all.flatMap { it.tagList() }.distinctBy { it.lowercase() }.sorted()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Filters())

    private val _draft = MutableStateFlow<Draft?>(null)
    val draft: StateFlow<Draft?> = _draft.asStateFlow()

    private var tokenCounter = 0L
    private var saveJob: Job? = null
    private var lastDeleted: Note? = null
    private val mutex = Mutex()
    private val savedIds = mutableMapOf<Long, Long>() // session token -> row id

    fun setQuery(q: String) { query = q }
    fun setFilter(f: String?) { filter = f }

    fun open(note: Note) { commit(); _draft.value = Draft(++tokenCounter, note) }
    fun create() { commit(); _draft.value = Draft(++tokenCounter, Note()) }
    fun close() { commit(); _draft.value = null }
    fun flush() = commit()

    fun onTitle(t: String) = change { it.copy(title = t) }
    fun onBody(b: String) = change { it.copy(body = b) }
    fun onFolder(v: String) = change { it.copy(folder = v.trim()) }
    fun onTags(v: String) = change { it.copy(tags = v) }
    fun onDrawing(v: String) = change { it.copy(drawing = v) }
    fun setChecklist(flag: Boolean, body: String) =
        change(immediate = true) { it.copy(isChecklist = flag, body = body) }
    fun togglePin() = change(immediate = true) { it.copy(pinned = !it.pinned) }
    fun setColor(i: Int) = change(immediate = true) { it.copy(colorIndex = i) }

    private fun change(immediate: Boolean = false, f: (Note) -> Note) {
        _draft.update { d -> d?.copy(note = f(d.note)) }
        if (immediate) commit() else scheduleSave()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(600)
            _draft.value?.let { persist(it) }
        }
    }

    private fun commit() {
        saveJob?.cancel()
        val d = _draft.value ?: return
        viewModelScope.launch { persist(d) }
    }

    private suspend fun persist(d: Draft) = mutex.withLock {
        var n = d.note
        if (n.id == 0L) savedIds[d.token]?.let { n = n.copy(id = it) }
        if (n.id == 0L && !n.hasContent()) return@withLock
        val id = dao.upsert(n.copy(updatedAt = System.currentTimeMillis()))
        if (d.note.id == 0L) {
            savedIds[d.token] = id
            _draft.update { cur ->
                if (cur != null && cur.token == d.token && cur.note.id == 0L)
                    cur.copy(note = cur.note.copy(id = id)) else cur
            }
        }
    }

    fun delete() {
        val d = _draft.value ?: return
        saveJob?.cancel()
        _draft.value = null
        lastDeleted = null
        viewModelScope.launch {
            mutex.withLock {
                val id = d.note.id.takeIf { it != 0L } ?: savedIds[d.token]
                val stored = id?.let { dao.get(it) }
                if (stored != null) {
                    dao.delete(stored)
                    lastDeleted = stored
                }
            }
        }
    }

    fun undoDelete() {
        val n = lastDeleted ?: return
        lastDeleted = null
        viewModelScope.launch { dao.upsert(n) }
    }
}
