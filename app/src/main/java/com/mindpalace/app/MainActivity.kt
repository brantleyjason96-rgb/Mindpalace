package com.mindpalace.app

// ============================================================================
// PLACEHOLDER UI (Phase 0). The real interface is intentionally left for the
// later frontend session; this screen exists ONLY to demonstrate Checkpoint 0:
// install, type a note, see it saved, kill the app, reopen — still there.
// Backend contract it uses: container.commands (only writer), container.noteDao
// (Flow reads + FTS search), container.exporter (manual exports).
// ============================================================================

import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mindpalace.app.core.commands.CreateNoteRequest
import com.mindpalace.app.core.commands.NoteCommandHandler
import com.mindpalace.app.core.db.FtsQuery
import com.mindpalace.app.core.db.MindPalaceDatabase
import com.mindpalace.app.core.db.NoteDao
import com.mindpalace.app.core.db.NoteEntity
import com.mindpalace.app.core.model.NoteSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaceholderViewModel(
    database: MindPalaceDatabase,
    private val commands: NoteCommandHandler,
) : ViewModel() {

    private val noteDao: NoteDao = database.noteDao()

    val notes: StateFlow<List<NoteEntity>> =
        noteDao.observeActive()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val searchResults = MutableStateFlow<List<NoteEntity>>(emptyList())
    val results: StateFlow<List<NoteEntity>> = searchResults

    /** Save-first: this returns as soon as the row is durable — the whole capture path. */
    fun createNote(title: String, body: String, onSaved: (revision: Int) -> Unit) {
        viewModelScope.launch {
            val note = commands.create(
                CreateNoteRequest(
                    source = NoteSource.TEXT,
                    title = title.ifBlank { null },
                    rawText = body.ifBlank { null },
                ),
            )
            onSaved(note.revision)
        }
    }

    fun search(query: String) {
        viewModelScope.launch {
            val fts = FtsQuery.sanitize(query)
            searchResults.value = if (fts == null) emptyList() else noteDao.searchFts(fts, 100)
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PlaceholderViewModel(container.database, container.commands) as T
            }
    }
}

class MainActivity : ComponentActivity() {

    private val container: AppContainer
        get() = (application as MindPalaceApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val model = ViewModelProvider(
            this,
            PlaceholderViewModel.factory(container),
        )[PlaceholderViewModel::class.java]

        setContent {
            MaterialTheme {
                PlaceholderScreen(model)
            }
        }
    }
}

@Composable
fun PlaceholderScreen(model: PlaceholderViewModel) {
    val notes by model.notes.collectAsState()
    val results by model.results.collectAsState()

    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var savedRevision by remember { mutableStateOf<Int?>(null) }
    var query by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Mind Palace", style = MaterialTheme.typography.headlineMedium)
        Text(
            "placeholder build — capture + list only",
            style = MaterialTheme.typography.labelSmall,
        )

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(R.string.capture_hint_title)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            label = { Text(stringResource(R.string.capture_hint_text)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    model.createNote(title, body) { revision ->
                        savedRevision = revision
                        title = ""
                        body = ""
                    }
                },
                enabled = title.isNotBlank() || body.isNotBlank(),
            ) {
                Text(stringResource(R.string.capture_save))
            }
            savedRevision?.let {
                Text(
                    stringResource(R.string.saved_indicator, it),
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                model.search(it)
            },
            label = { Text(stringResource(R.string.search_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        val list = if (query.isBlank()) notes else results
        if (list.isEmpty()) {
            Text(stringResource(R.string.empty_list), style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(list, key = { it.id }) { note ->
                    NoteRow(note)
                }
            }
        }
    }
}

@Composable
private fun NoteRow(note: NoteEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                note.title ?: note.rawText ?: note.id,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
            )
            Text(
                "${note.status.name.lowercase()} · rev ${note.revision} · " +
                    DateUtils.getRelativeTimeSpanString(note.createdAt),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

