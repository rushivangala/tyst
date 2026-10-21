package app.tyst.keyboard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tyst.core.icons.Icon
import app.tyst.core.icons.Icons
import app.tyst.core.memory.MemoryItem
import app.tyst.core.style.AppNames
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** A 48dp icon button for list rows. */
@Composable
fun RowAction(icon: Icon, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = label, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { TIcon(icon, MaterialTheme.colorScheme.onSurfaceVariant, size = 22.dp) }
}

@Composable
fun StyleScreen() {
    val env = LocalEnv.current
    val e = env.engine
    val s = env.settings
    var confirmReset by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    Screen("My style") {
        item {
            Body("What Tyst has learned from how you write.")
            val since = e?.style?.firstLearnedAt?.takeIf { it > 0 }?.let { java.text.SimpleDateFormat("MMMM", locale).format(Date(it)) }
            ToggleRow(
                "Learn my style",
                if (s.learning) "From ${e?.style?.messageCount ?: 0} messages you sent${since?.let { " since $it" } ?: ""}" else "Paused. What it learned so far is kept.",
                s.learning,
            ) { v -> env.update { it.copy(learning = v) } }
            SectionLabel("How you write")
        }
        val habits = e?.style?.habits().orEmpty()
        if (habits.isEmpty()) {
            item { Body("Nothing yet. Habits show up here after you’ve sent a few messages with Tyst.") }
        }
        items(habits, key = { it.id }) { h ->
            ToggleRow(h.title, h.detail, h.enabled) { v ->
                e?.style?.setHabitEnabled(h.id, v)
                env.graph.changed()
            }
        }
        item {
            val phrases = e?.style?.commonPhrases().orEmpty()
            if (phrases.isNotEmpty()) {
                SectionLabel("Phrases you use often")
                phrases.forEach { p -> SettingRow("“$p”") }
            }
            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton(if (s.learning) "Pause learning" else "Resume learning") { env.update { it.copy(learning = !it.learning) } }
                SecondaryButton("Reset my style") { confirmReset = true }
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset your style?") },
            text = { Text("Tyst forgets how you write and starts again. Your personal dictionary and private memory stay.") },
            confirmButton = {
                TextButton(onClick = {
                    e?.style?.clear()
                    env.graph.changed()
                    confirmReset = false
                }) { Text("Reset", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun PrivacyScreen() {
    val env = LocalEnv.current
    val e = env.engine
    val s = env.settings
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var confirmAll by remember { mutableStateOf(false) }
    val items = remember(env.tick, query, e) { e?.memory?.search(query).orEmpty() }
    Screen("Privacy & memory") {
        item {
            InfoCard {
                Text("What Tyst keeps", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(8.dp))
                KeepRow("My style", "${e?.style?.messageCount ?: 0} messages learned from")
                KeepRow("Personal dictionary", "${e?.personal?.dictionaryWords?.size ?: 0} words")
                KeepRow("Private memory", "${e?.memory?.size ?: 0} items")
                KeepRow("Stored", "${Platform.formatBytes(env.graph.store.sizeBytes())}, encrypted, not in Google backup")
                Spacer(Modifier.size(4.dp))
                Text("Never saved: passwords, card numbers, one-time codes, or anything typed in private fields.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SettingRow("Internet access", "Not requested. Nothing leaves this phone.", Icons.WIFI_OFF)
            ToggleRow(
                "Private memory",
                if (s.memoryEnabled) "Remembers things you type, like addresses" else "Paused. Saved items are kept.",
                s.memoryEnabled, icon = Icons.LOCK,
            ) { v -> env.update { it.copy(memoryEnabled = v) } }
            Text("Forget unused items after", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            Segmented(listOf(30 to "30 days", 90 to "90 days", 0 to "Never"), s.memoryExpiryDays) { v -> env.update { it.copy(memoryExpiryDays = v) } }
            Body("Browsers are left out, so forms you fill in there aren’t remembered.")
            SectionLabel("Remembered · ${e?.memory?.size ?: 0}")
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Search what’s remembered") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            if (items.isEmpty()) {
                Body(if (query.isNotBlank()) "Nothing matches “$query”." else "Nothing remembered yet. Addresses and codes you type will show up here once private memory is on.")
            }
        }
        items(items, key = { it.id }) { item ->
            MemoryRow(item,
                onPin = {
                    e?.memory?.setPinned(item.id, !item.pinned)
                    env.graph.changed()
                },
                onDelete = {
                    val removed = e?.memory?.remove(item.id) ?: return@MemoryRow
                    env.graph.changed()
                    scope.launch {
                        val r = env.snackbar.showSnackbar("${removed.type.label} deleted", actionLabel = "Undo", withDismissAction = false)
                        if (r == SnackbarResult.ActionPerformed) {
                            e.memory.restore(removed)
                            env.graph.changed()
                        }
                    }
                },
            )
        }
        item {
            Spacer(Modifier.size(16.dp))
            Row(Modifier.padding(horizontal = 20.dp)) { DangerButton("Delete all learned data") { confirmAll = true } }
            Spacer(Modifier.size(32.dp))
        }
    }
    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text("Delete everything Tyst has learned?") },
            text = {
                Text(
                    "This removes your style, your personal dictionary (${e?.personal?.dictionaryWords?.size ?: 0} words), " +
                        "never-correct words, emoji history and ${e?.memory?.size ?: 0} remembered items from this phone, " +
                        "and destroys the key that protected them. It can’t be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    env.graph.deleteAll()
                    confirmAll = false
                    scope.launch { env.snackbar.showSnackbar("All learned data deleted") }
                }) { Text("Delete all", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun KeepRow(title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MemoryRow(item: MemoryItem, onPin: () -> Unit, onDelete: () -> Unit) {
    val date = remember(item.createdAt) { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(item.createdAt)) }
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TIcon(Icons.LOCK, MaterialTheme.colorScheme.onSurfaceVariant, size = 20.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(item.value, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${item.type.label} · ${AppNames.name(item.sourceApp)} · $date${if (item.pinned) " · pinned" else ""}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RowAction(Icons.PIN, if (item.pinned) "Unpin" else "Pin, so it isn’t forgotten", onPin)
        RowAction(Icons.DELETE, "Delete ${item.type.label.lowercase()}", onDelete)
    }
}

@Composable
fun WordsScreen() {
    val env = LocalEnv.current
    val e = env.engine
    var newWord by remember { mutableStateOf("") }
    var neverWord by remember { mutableStateOf("") }
    val words = remember(env.tick, e) { e?.personal?.dictionaryWords?.sortedByDescending { it.learnedAt }.orEmpty() }
    val never = remember(env.tick, e) { e?.personal?.neverCorrectWords?.toList().orEmpty() }
    val blocked = remember(env.tick, e) { e?.personal?.blockedWords?.toList().orEmpty() }
    Screen("Words") {
        item {
            SectionLabel("Personal dictionary · ${words.size}")
            Body("Words you type often, or kept after undoing a correction. Tyst suggests them and never corrects them.")
            AddWordField(newWord, { newWord = it }, "Add a word") {
                e?.personal?.addWord(newWord.trim(), "app.tyst.keyboard", System.currentTimeMillis())
                newWord = ""
                env.graph.changed()
            }
        }
        items(words, key = { "w:" + it.word }) { w ->
            val sv = e?.lexicon?.isSwedish(w.word) == true || w.word.any { it in "åäöÅÄÖ" }
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(w.word + if (sv) "  SV" else "", style = MaterialTheme.typography.bodyLarge)
                    Text("From ${AppNames.name(w.sourceApp)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RowAction(Icons.DELETE, "Remove ${w.word}") { e?.personal?.removeWord(w.word, block = false); env.graph.changed() }
                RowAction(Icons.CLOSE, "Remove and never learn ${w.word}") { e?.personal?.removeWord(w.word, block = true); env.graph.changed() }
            }
        }
        item {
            SectionLabel("Never correct · ${never.size}")
            Body("Swedish words and names are already left alone. Add anything else Tyst should never change.")
            AddWordField(neverWord, { neverWord = it }, "Add a word to leave alone") {
                e?.personal?.addNeverCorrect(neverWord.trim())
                neverWord = ""
                env.graph.changed()
            }
        }
        items(never, key = { "n:$it" }) { w ->
            SettingRow(w, trailing = { RowAction(Icons.DELETE, "Remove $w") { e?.personal?.removeNeverCorrect(w); env.graph.changed() } })
        }
        if (blocked.isNotEmpty()) {
            item { SectionLabel("Never learn · ${blocked.size}") }
            items(blocked, key = { "b:$it" }) { w ->
                SettingRow(w, trailing = { RowAction(Icons.UNDO, "Allow $w again") { e?.personal?.unblock(w); env.graph.changed() } })
            }
        }
        item { Spacer(Modifier.size(32.dp)) }
    }
}

@Composable
private fun AddWordField(value: String, onChange: (String) -> Unit, hint: String, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = value, onValueChange = onChange, singleLine = true, placeholder = { Text(hint) }, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        PrimaryButton("Add", enabled = value.isNotBlank(), onClick = onAdd)
    }
}
