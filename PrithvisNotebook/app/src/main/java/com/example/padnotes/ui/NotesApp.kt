package com.example.padnotes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.padnotes.data.Note
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@Composable
fun NotesApp(vm: NotesViewModel = viewModel()) {
    val notes by vm.notes.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val filters by vm.filters.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.flush() }

    // Drop a folder/tag filter once no note uses it any more.
    LaunchedEffect(filters, vm.filter) {
        val f = vm.filter
        if (f != null) {
            val exists = if (f.startsWith("f:")) f.drop(2) in filters.folders
            else filters.tags.any { it.equals(f.drop(2), ignoreCase = true) }
            if (!exists) vm.setFilter(null)
        }
    }

    val onDelete: () -> Unit = {
        val hadContent = draft?.note?.hasContent() == true
        vm.delete()
        if (hadContent) scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar("Note deleted", "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) vm.undoDelete()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Xiaomi Pad 6 is well above this in both orientations -> list + editor side by side.
        val twoPane = maxWidth >= 600.dp
        BackHandler(enabled = !twoPane && draft != null) { vm.close() }

        Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            val d = draft
            val list: @Composable (Modifier) -> Unit = { mod ->
                ListPane(
                    notes = notes, filters = filters, query = vm.query, filter = vm.filter,
                    selectedId = d?.note?.id, onQuery = vm::setQuery, onFilter = vm::setFilter,
                    onNew = vm::create, onOpen = vm::open, modifier = mod
                )
            }
            val editor: @Composable (Draft) -> Unit = { dr ->
                key(dr.token) {
                    EditorPane(
                        note = dr.note, showBack = !twoPane,
                        a = EditorActions(
                            onBack = vm::close, onTitle = vm::onTitle, onBody = vm::onBody,
                            onColor = vm::setColor, onPin = vm::togglePin, onDelete = onDelete,
                            onFolder = vm::onFolder, onTags = vm::onTags, onDrawing = vm::onDrawing,
                            onChecklist = vm::setChecklist
                        )
                    )
                }
            }

            Row(Modifier.padding(padding).fillMaxSize()) {
                if (twoPane) {
                    list(Modifier.width(360.dp).fillMaxHeight())
                    VerticalDivider()
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        if (d == null) EmptyEditor() else editor(d)
                    }
                } else {
                    if (d == null) list(Modifier.fillMaxSize()) else editor(d)
                }
            }
        }
    }
}

@Composable
private fun ListPane(
    notes: List<Note>, filters: Filters, query: String, filter: String?, selectedId: Long?,
    onQuery: (String) -> Unit, onFilter: (String?) -> Unit,
    onNew: () -> Unit, onOpen: (Note) -> Unit, modifier: Modifier
) {
    Column(modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query, onValueChange = onQuery, singleLine = true,
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(28.dp),
                placeholder = { Text("Search notes") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Default.Clear, "Clear search")
                    }
                }
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = onNew) { Icon(Icons.Default.Add, "New note") }
        }
        Spacer(Modifier.size(8.dp))
        if (filters.folders.isNotEmpty() || filters.tags.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                item { FilterChip(selected = filter == null, onClick = { onFilter(null) }, label = { Text("All") }) }
                items(filters.folders) { f ->
                    val key = "f:$f"
                    FilterChip(
                        selected = filter == key,
                        onClick = { onFilter(if (filter == key) null else key) },
                        label = { Text(f) },
                        leadingIcon = { Icon(Icons.Filled.Folder, null, Modifier.size(16.dp)) }
                    )
                }
                items(filters.tags) { t ->
                    val key = "t:$t"
                    FilterChip(
                        selected = filter == key,
                        onClick = { onFilter(if (filter == key) null else key) },
                        label = { Text("#$t") }
                    )
                }
            }
        }
        if (notes.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (query.isBlank() && filter == null) "No notes yet.\nTap + to write your first one." else "No matches",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(notes, key = { it.id }) { n ->
                    NoteCard(n, selected = n.id == selectedId) { onOpen(n) }
                }
            }
        }
    }
}

@Composable
private fun NoteCard(note: Note, selected: Boolean, onClick: () -> Unit) {
    val bg = noteColors.getOrNull(note.colorIndex) ?: MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (note.colorIndex == 0) MaterialTheme.colorScheme.onSurface else InkDark
    val meta = buildList {
        if (note.folder.isNotBlank()) add(note.folder)
        addAll(note.tagList().map { "#$it" })
    }.joinToString("  \u00B7  ")
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bg, contentColor = fg),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    note.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (note.drawing.isNotBlank()) Icon(Icons.Filled.Brush, "Has sketch", Modifier.size(16.dp))
                if (note.pinned) Icon(Icons.Filled.PushPin, "Pinned", Modifier.size(16.dp).padding(start = 2.dp))
            }
            val preview = note.previewText()
            if (preview.isNotBlank()) {
                Text(
                    preview, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
            }
            Text(
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(note.updatedAt)),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 8.dp).alpha(0.7f)
            )
        }
    }
}

@Composable
private fun EmptyEditor() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "Select a note or create a new one",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleMedium
        )
    }
}
