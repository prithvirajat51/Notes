package com.example.padnotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.padnotes.data.Note

class EditorActions(
    val onBack: () -> Unit,
    val onTitle: (String) -> Unit,
    val onBody: (String) -> Unit,
    val onColor: (Int) -> Unit,
    val onPin: () -> Unit,
    val onDelete: () -> Unit,
    val onFolder: (String) -> Unit,
    val onTags: (String) -> Unit,
    val onDrawing: (String) -> Unit,
    val onChecklist: (Boolean, String) -> Unit
)

class CheckItem(val id: Long, text: String, done: Boolean) {
    var text by mutableStateOf(text)
    var done by mutableStateOf(done)
}

private var idSeq = 0L
private fun nextId() = ++idSeq

private fun parseChecklist(body: String): List<CheckItem> {
    if (body.isEmpty()) return listOf(CheckItem(nextId(), "", false))
    return body.split("\n").map { line ->
        CheckItem(nextId(), stripMarker(line), line.startsWith("[x] "))
    }
}

private fun serializeChecklist(items: List<CheckItem>): String =
    items.joinToString("\n") { (if (it.done) "[x] " else "[ ] ") + it.text }

@Composable
fun EditorPane(note: Note, showBack: Boolean, a: EditorActions) {
    val bg = noteColors.getOrNull(note.colorIndex) ?: MaterialTheme.colorScheme.surface
    val fg = if (note.colorIndex == 0) MaterialTheme.colorScheme.onSurface else InkDark
    // Local state is the source of truth while typing (avoids cursor jumps);
    // the ViewModel autosaves in the background.
    var title by remember { mutableStateOf(note.title) }
    var body by remember { mutableStateOf(note.body) }
    var folder by remember { mutableStateOf(note.folder) }
    var tags by remember { mutableStateOf(note.tags) }
    var checklist by remember { mutableStateOf(note.isChecklist) }
    var sketchOpen by remember { mutableStateOf(note.drawing.isNotBlank()) }
    var focusId by remember { mutableStateOf<Long?>(null) }
    val items = remember { mutableStateListOf<CheckItem>().also { if (note.isChecklist) it.addAll(parseChecklist(note.body)) } }
    val focus = LocalFocusManager.current

    fun syncItems() = a.onBody(serializeChecklist(items))

    fun addItemAfter(index: Int) {
        val n = CheckItem(nextId(), "", false)
        items.add(index + 1, n)
        focusId = n.id
        syncItems()
    }

    fun toggleChecklist() {
        if (!checklist) {
            items.clear()
            body.lines().filter { it.isNotBlank() }.forEach { items.add(CheckItem(nextId(), it.trim(), false)) }
            if (items.isEmpty()) items.add(CheckItem(nextId(), "", false))
            checklist = true
            a.onChecklist(true, serializeChecklist(items))
        } else {
            body = items.joinToString("\n") { it.text }.trimEnd()
            checklist = false
            a.onChecklist(false, body)
        }
    }

    Surface(color = bg, contentColor = fg, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showBack) IconButton(onClick = a.onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { toggleChecklist() }) {
                    Icon(
                        Icons.Filled.Checklist,
                        if (checklist) "Switch to plain text" else "Switch to checklist",
                        tint = if (checklist) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }
                IconButton(onClick = { sketchOpen = !sketchOpen }) {
                    Icon(
                        Icons.Filled.Brush,
                        if (sketchOpen) "Hide sketch" else "Show sketch",
                        tint = if (sketchOpen) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }
                IconButton(onClick = a.onPin) {
                    Icon(
                        if (note.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        if (note.pinned) "Unpin" else "Pin"
                    )
                }
                IconButton(onClick = a.onDelete) { Icon(Icons.Filled.Delete, "Delete note") }
            }
            Row(Modifier.padding(horizontal = 12.dp).horizontalScroll(rememberScrollState())) {
                noteColors.forEachIndexed { i, c ->
                    ColorDot(c, selected = i == note.colorIndex) { a.onColor(i) }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = 760.dp).fillMaxSize()
                        .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
                ) {
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it; a.onTitle(it) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.headlineMedium.copy(color = fg),
                        cursorBrush = SolidColor(fg),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        decorationBox = { inner ->
                            Box {
                                if (title.isEmpty()) Text(
                                    "Title", style = MaterialTheme.typography.headlineMedium,
                                    color = fg.copy(alpha = 0.4f)
                                )
                                inner()
                            }
                        }
                    )

                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        MetaField(folder, "Folder", fg, Modifier.weight(1f)) { folder = it; a.onFolder(it) }
                        Spacer(Modifier.width(8.dp))
                        MetaField(tags, "Tags (comma separated)", fg, Modifier.weight(1f)) { tags = it; a.onTags(it) }
                    }

                    if (sketchOpen) SketchPad(initial = note.drawing, ink = fg, onChange = a.onDrawing)

                    if (checklist) {
                        items.forEachIndexed { idx, item ->
                            key(item.id) {
                                val fr = remember { FocusRequester() }
                                LaunchedEffect(focusId) {
                                    if (focusId == item.id) {
                                        runCatching { fr.requestFocus() }
                                        focusId = null
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = item.done,
                                        onCheckedChange = { item.done = it; syncItems() },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = fg, checkmarkColor = bg,
                                            uncheckedColor = fg.copy(alpha = 0.7f)
                                        )
                                    )
                                    BasicTextField(
                                        value = item.text,
                                        onValueChange = { item.text = it; syncItems() },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                                            color = fg.copy(alpha = if (item.done) 0.5f else 1f),
                                            textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None
                                        ),
                                        cursorBrush = SolidColor(fg),
                                        keyboardOptions = KeyboardOptions(
                                            capitalization = KeyboardCapitalization.Sentences,
                                            imeAction = ImeAction.Next
                                        ),
                                        keyboardActions = KeyboardActions(onNext = { addItemAfter(idx) }),
                                        modifier = Modifier.weight(1f).focusRequester(fr).padding(vertical = 10.dp)
                                    )
                                    IconButton(onClick = {
                                        items.remove(item)
                                        if (items.isEmpty()) items.add(CheckItem(nextId(), "", false))
                                        syncItems()
                                    }) { Icon(Icons.Filled.Close, "Remove item", Modifier.size(18.dp)) }
                                }
                            }
                        }
                        TextButton(onClick = { addItemAfter(items.lastIndex) }) {
                            Text("+ Add item", color = fg)
                        }
                    } else {
                        BasicTextField(
                            value = body,
                            onValueChange = { body = it; a.onBody(it) },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = fg),
                            cursorBrush = SolidColor(fg),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 300.dp),
                            decorationBox = { inner ->
                                Box {
                                    if (body.isEmpty()) Text(
                                        "Start writing...", style = MaterialTheme.typography.bodyLarge,
                                        color = fg.copy(alpha = 0.4f)
                                    )
                                    inner()
                                }
                            }
                        )
                    }
                }
            }

            val footer = if (checklist) "${items.count { it.done }} of ${items.size} done"
            else "${body.trim().split(Regex("\\s+")).count { it.isNotEmpty() }} words"
            Text(
                "$footer  ·  autosaved",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).alpha(0.6f)
            )
        }
    }
}

@Composable
private fun MetaField(value: String, label: String, fg: Color, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        label = { Text(label) }, modifier = modifier,
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = fg, unfocusedTextColor = fg, cursorColor = fg,
            focusedBorderColor = fg.copy(alpha = 0.6f), unfocusedBorderColor = fg.copy(alpha = 0.3f),
            focusedLabelColor = fg, unfocusedLabelColor = fg.copy(alpha = 0.6f)
        )
    )
}

@Composable
private fun ColorDot(color: Color?, selected: Boolean, onClick: () -> Unit) {
    val fill = color ?: MaterialTheme.colorScheme.surfaceContainerHighest
    val ring = if (selected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f)
    Box(
        Modifier.padding(5.dp).size(28.dp).clip(CircleShape).background(fill)
            .border(if (selected) 3.dp else 1.dp, ring, CircleShape)
            .clickable(onClick = onClick)
    )
}
