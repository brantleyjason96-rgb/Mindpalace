package com.mindpalace.app

// ============================================================================
// Main screen (polished capture + browse). Backend contract used:
//   container.commands (ONLY writer), container.database.noteDao() (Flow reads + FTS search).
// The UI never touches DAOs for writes. Delete = soft delete, Undo = restore (audited, §3.2).
// ============================================================================

import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mindpalace.app.core.commands.CreateNoteRequest
import com.mindpalace.app.core.commands.NoteCommandHandler
import com.mindpalace.app.core.db.FtsQuery
import com.mindpalace.app.core.db.MindPalaceDatabase
import com.mindpalace.app.core.db.NoteDao
import com.mindpalace.app.core.db.NoteEntity
import com.mindpalace.app.core.model.NoteSource
import com.mindpalace.app.core.model.NoteStatus
import com.mindpalace.app.ui.theme.MindPalaceTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    database: MindPalaceDatabase,
    private val commands: NoteCommandHandler,
) : ViewModel() {

    private val noteDao: NoteDao = database.noteDao()

    val notes: StateFlow<List<NoteEntity>> =
        noteDao.observeActive()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val query = MutableStateFlow("")

    /** Bumped after a delete/restore so an open search re-runs and never shows stale rows. */
    private val refresh = MutableStateFlow(0)

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val results: StateFlow<List<NoteEntity>> =
        combine(query.debounce(120), refresh) { q, _ -> q }
            .mapLatest { q ->
                val fts = FtsQuery.sanitize(q)
                if (fts == null) emptyList() else noteDao.searchFts(fts, 100)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(value: String) {
        query.value = value
    }

    /** Save-first: returns as soon as the row is durable — the whole capture path. */
    fun capture(title: String, body: String, onSaved: (noteId: String) -> Unit) {
        viewModelScope.launch {
            val note = commands.create(
                CreateNoteRequest(
                    source = NoteSource.TEXT,
                    title = title.ifBlank { null },
                    rawText = body.ifBlank { null },
                ),
            )
            onSaved(note.id)
        }
    }

    fun delete(noteId: String) {
        viewModelScope.launch {
            commands.softDelete(noteId)
            refresh.value++
        }
    }

    fun restore(noteId: String) {
        viewModelScope.launch {
            commands.restore(noteId)
            refresh.value++
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MainViewModel(container.database, container.commands) as T
            }
    }
}

class MainActivity : ComponentActivity() {

    private val container: AppContainer
        get() = (application as MindPalaceApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val model = ViewModelProvider(this, MainViewModel.factory(container))[MainViewModel::class.java]

        setContent {
            MindPalaceTheme {
                MainScreen(model)
            }
        }
    }
}

@Composable
fun MainScreen(model: MainViewModel) {
    val notes by model.notes.collectAsStateWithLifecycle()
    val results by model.results.collectAsStateWithLifecycle()
    val query by model.query.collectAsStateWithLifecycle()

    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    val searching = query.isNotBlank()
    val visible = if (searching) results else notes

    // Snackbar with Undo. Any new snackbar replaces the previous one.
    fun showUndo(message: String, undoLabel: String, onUndo: () -> Unit) {
        scope.launch {
            snackbarHost.currentSnackbarData?.dismiss()
            val result = snackbarHost.showSnackbar(message = message, actionLabel = undoLabel)
            if (result == SnackbarResult.ActionPerformed) onUndo()
        }
    }

    val savedMsg = stringResource(R.string.saved_snackbar)
    val deletedMsg = stringResource(R.string.deleted_snackbar)
    val undoLabel = stringResource(R.string.undo)

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            Composer(
                onSave = { title, body ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    model.capture(title, body) { id ->
                        showUndo(savedMsg, undoLabel) { model.delete(id) }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Header(count = notes.size)
            SearchField(
                value = query,
                onValueChange = model::setQuery,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(8.dp))

            Box(Modifier.fillMaxSize()) {
                if (visible.isEmpty()) {
                    EmptyState(searching = searching, query = query)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(visible, key = { it.id }) { note ->
                            SwipeableNote(
                                note = note,
                                modifier = Modifier.animateItem(),
                                onDelete = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    model.delete(note.id)
                                    showUndo(deletedMsg, undoLabel) { model.restore(note.id) }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(count: Int) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = if (count == 0) {
                stringResource(R.string.header_subtitle_zero)
            } else {
                pluralStringResource(R.plurals.header_subtitle, count, count)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.search_hint)) },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
        ),
    )
}

@Composable
private fun EmptyState(searching: Boolean, query: String) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(112.dp)) {
            Box(
                Modifier
                    .size(112.dp)
                    .scale(scale)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), CircleShape),
            )
            Box(
                Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(if (searching) R.string.empty_search_title else R.string.empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (searching) {
                stringResource(R.string.empty_search_body, query.trim())
            } else {
                stringResource(R.string.empty_body)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SwipeableNote(note: NoteEntity, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val currentOnDelete by rememberUpdatedState(onDelete)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                currentOnDelete()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val armed = state.targetValue == SwipeToDismissBoxValue.EndToStart
            val color by animateColorAsState(
                if (armed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer,
                label = "swipeBackground",
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(color, RoundedCornerShape(24.dp))
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    stringResource(R.string.delete),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        NoteCard(note)
    }
}

@Composable
private fun NoteCard(note: NoteEntity) {
    var expanded by rememberSaveable(note.id) { mutableStateOf(false) }
    val body = note.rawText ?: note.transcript ?: note.ocrText
    val title = note.title

    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            Modifier
                .padding(16.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(sourceLabel(note.source), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                if (note.status != NoteStatus.ACTIVE) {
                    Chip(statusLabel(note.status), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (body != null && body != title) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (title == null && body == null) {
                Text(stringResource(R.string.untitled), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                DateUtils.getRelativeTimeSpanString(
                    note.createdAt,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                ).toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun Chip(label: String, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
    Surface(color = container, contentColor = content, shape = CircleShape) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun sourceLabel(source: NoteSource): String = stringResource(
    when (source) {
        NoteSource.TEXT -> R.string.source_text
        NoteSource.VOICE -> R.string.source_voice
        NoteSource.PHOTO -> R.string.source_photo
        NoteSource.SHARE -> R.string.source_share
    },
)

@Composable
private fun statusLabel(status: NoteStatus): String = stringResource(
    when (status) {
        NoteStatus.INBOX -> R.string.status_inbox
        NoteStatus.ACTIVE -> R.string.status_active
        NoteStatus.DONE -> R.string.status_done
        NoteStatus.ARCHIVED -> R.string.status_archived
    },
)

/**
 * Bottom capture bar. Collapsed it is one pill; focusing (or typing) expands an optional title
 * field and reveals Save. Fields clear only after onSave is called (the ViewModel saves first).
 */
@Composable
private fun Composer(onSave: (title: String, body: String) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var titleFocused by remember { mutableStateOf(false) }
    var bodyFocused by remember { mutableStateOf(false) }

    val canSave = title.isNotBlank() || body.isNotBlank()
    val expanded = titleFocused || bodyFocused || canSave

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { titleFocused = it.isFocused },
                    placeholder = { Text(stringResource(R.string.capture_hint_title)) },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = fieldColors,
                )
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { bodyFocused = it.isFocused },
                    placeholder = { Text(stringResource(R.string.capture_hint_text)) },
                    minLines = 1,
                    maxLines = 5,
                    shape = RoundedCornerShape(24.dp),
                    colors = fieldColors,
                )
                AnimatedVisibility(
                    visible = canSave,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                ) {
                    Button(
                        onClick = {
                            onSave(title, body)
                            title = ""
                            body = ""
                        },
                        modifier = Modifier.height(56.dp),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Text(stringResource(R.string.capture_save), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
