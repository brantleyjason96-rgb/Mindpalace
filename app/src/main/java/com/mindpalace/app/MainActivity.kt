package com.mindpalace.app

// ============================================================================
// Main screen (polished capture + browse). Backend contract used:
//   container.commands (ONLY writer), container.database.noteDao() (Flow reads + FTS search).
// The UI never touches DAOs for writes. Delete = soft delete, Undo = restore (audited, §3.2).
// ============================================================================

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlin.math.abs
import kotlin.math.roundToInt
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
import com.mindpalace.app.core.db.RuleDao
import com.mindpalace.app.core.db.RuleEntity
import com.mindpalace.app.core.model.NoteSource
import com.mindpalace.app.core.model.NoteStatus
import com.mindpalace.app.core.model.DEFAULT_CATEGORIES
import com.mindpalace.app.core.ocr.OcrEngine
import com.mindpalace.app.core.rules.Rule
import com.mindpalace.app.core.rules.RuleAction
import com.mindpalace.app.core.rules.RuleEngine
import com.mindpalace.app.core.rules.RuleMatcher
import com.mindpalace.app.core.rules.toEntity
import com.mindpalace.app.core.rules.toRule
import com.mindpalace.app.export.NoteExporter
import com.mindpalace.app.ui.theme.MindPalaceTheme
import com.mindpalace.app.ui.theme.ThemeMode
import com.mindpalace.app.ui.theme.ThemePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class MainViewModel(
    database: MindPalaceDatabase,
    private val commands: NoteCommandHandler,
    private val exporter: NoteExporter,
    private val exportDir: File,
    val photosDir: File,
) : ViewModel() {

    private val noteDao: NoteDao = database.noteDao()
    private val ruleDao: RuleDao = database.ruleDao()

    val rules: StateFlow<List<RuleEntity>> =
        ruleDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setRuleEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { ruleDao.setEnabled(id, enabled) }
    }

    fun addRule(keywords: List<String>, category: String) {
        viewModelScope.launch {
            ruleDao.upsert(
                Rule(
                    id = UUID.randomUUID().toString(),
                    version = 1,
                    enabled = true,
                    matcher = RuleMatcher(keywords),
                    action = RuleAction(category),
                ).toEntity(),
            )
        }
    }

    fun deleteRule(id: String) {
        viewModelScope.launch { ruleDao.delete(id) }
    }

    /** Unfiltered (ignores [statusFilter]) — the detail screen needs to find a note by id
     *  regardless of which status filter the browse list currently has applied. */
    val allNotes: StateFlow<List<NoteEntity>> =
        noteDao.observeActive()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** null = no filter (All). */
    val statusFilter = MutableStateFlow<NoteStatus?>(null)

    val notes: StateFlow<List<NoteEntity>> =
        combine(allNotes, statusFilter) { list, filter ->
            if (filter == null) list else list.filter { it.status == filter }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val query = MutableStateFlow("")

    /** Bumped after a delete/restore so an open search (and Trash) re-run and never show stale rows. */
    private val refresh = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val trash: StateFlow<List<NoteEntity>> =
        refresh.mapLatest { noteDao.recentlyDeleted(100) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Id of the note that just finished the save "settle" animation; self-clears. */
    val justSavedId = MutableStateFlow<String?>(null)

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

    fun setStatusFilter(filter: NoteStatus?) {
        statusFilter.value = filter
    }

    /** Triage swipe (and its Undo) — the status write already exists in the command handler. */
    fun setStatus(noteId: String, status: NoteStatus) {
        viewModelScope.launch {
            commands.setStatus(noteId, status)
            refresh.value++
        }
    }

    /** Detail/edit screen save. Title isn't editable — the backend has no command for it yet. */
    fun updateText(noteId: String, newText: String) {
        viewModelScope.launch {
            commands.updateRawText(noteId, newText)
            refresh.value++
        }
    }

    suspend fun exportJson(): File = exporter.writeJson(exportDir)

    suspend fun exportMarkdown(): File = exporter.writeMarkdown(exportDir)

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
            justSavedId.value = note.id
            onSaved(note.id)
            delay(SETTLE_ANIMATION_MS)
            if (justSavedId.value == note.id) justSavedId.value = null
        }
    }

    /** Same save-first contract, source=VOICE: the transcript IS the note, no raw audio kept. */
    fun captureVoice(transcript: String, onSaved: (noteId: String) -> Unit) {
        viewModelScope.launch {
            val note = commands.create(
                CreateNoteRequest(source = NoteSource.VOICE, transcript = transcript.ifBlank { null }),
            )
            justSavedId.value = note.id
            onSaved(note.id)
            delay(SETTLE_ANIMATION_MS)
            if (justSavedId.value == note.id) justSavedId.value = null
        }
    }

    /** Same save-first contract, source=PHOTO: the image stays in app-private storage; the OCR
     *  text is reviewable/editable right up to this call — after it, it's sacred like any note. */
    fun capturePhoto(imagePath: String, ocrText: String, onSaved: (noteId: String) -> Unit) {
        viewModelScope.launch {
            val note = commands.create(
                CreateNoteRequest(source = NoteSource.PHOTO, imagePath = imagePath, ocrText = ocrText.ifBlank { null }),
            )
            justSavedId.value = note.id
            onSaved(note.id)
            delay(SETTLE_ANIMATION_MS)
            if (justSavedId.value == note.id) justSavedId.value = null
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
        /** Duration of the save "settle" moment (NoteCard); matches the spring used there. */
        const val SETTLE_ANIMATION_MS = 500L

        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MainViewModel(
                        container.database,
                        container.commands,
                        container.exporter,
                        container.exportDir,
                        container.photosDir,
                    ) as T
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
            var themeMode by remember { mutableStateOf(ThemePrefs.load(this)) }
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            var openNoteId by rememberSaveable { mutableStateOf<String?>(null) }
            var showSettings by rememberSaveable { mutableStateOf(false) }
            var showVoiceCapture by rememberSaveable { mutableStateOf(false) }
            var showTrash by rememberSaveable { mutableStateOf(false) }
            var showRules by rememberSaveable { mutableStateOf(false) }
            var showPhotoCapture by rememberSaveable { mutableStateOf(false) }

            MindPalaceTheme(darkTheme = darkTheme) {
                val currentOpenId = openNoteId
                when {
                    showVoiceCapture -> VoiceCaptureScreen(
                        model = model,
                        onBack = { showVoiceCapture = false },
                        onSaved = { showVoiceCapture = false },
                    )
                    showPhotoCapture -> PhotoCaptureScreen(
                        model = model,
                        onBack = { showPhotoCapture = false },
                        onSaved = { showPhotoCapture = false },
                    )
                    showTrash -> TrashScreen(model = model, onBack = { showTrash = false })
                    showRules -> RulesScreen(model = model, onBack = { showRules = false })
                    showSettings -> SettingsScreen(
                        model = model,
                        onBack = { showSettings = false },
                        onOpenTrash = { showTrash = true },
                        onOpenRules = { showRules = true },
                    )
                    currentOpenId != null -> NoteDetailScreen(
                        model = model,
                        noteId = currentOpenId,
                        onBack = { openNoteId = null },
                    )
                    else -> MainScreen(
                        model = model,
                        themeMode = themeMode,
                        onThemeModeChange = { mode ->
                            themeMode = mode
                            ThemePrefs.save(this, mode)
                        },
                        onOpenNote = { openNoteId = it },
                        onOpenSettings = { showSettings = true },
                        onOpenVoiceCapture = { showVoiceCapture = true },
                        onOpenPhotoCapture = { showPhotoCapture = true },
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    model: MainViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVoiceCapture: () -> Unit,
    onOpenPhotoCapture: () -> Unit,
) {
    val notes by model.notes.collectAsStateWithLifecycle()
    val results by model.results.collectAsStateWithLifecycle()
    val query by model.query.collectAsStateWithLifecycle()
    val justSavedId by model.justSavedId.collectAsStateWithLifecycle()
    val statusFilter by model.statusFilter.collectAsStateWithLifecycle()

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
    val movedMsgs = mapOf(
        NoteStatus.INBOX to stringResource(R.string.moved_inbox),
        NoteStatus.ACTIVE to stringResource(R.string.moved_active),
        NoteStatus.DONE to stringResource(R.string.moved_done),
        NoteStatus.ARCHIVED to stringResource(R.string.moved_archived),
    )

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
                onVoice = onOpenVoiceCapture,
                onPhoto = onOpenPhotoCapture,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Header(
                count = notes.size,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                onOpenSettings = onOpenSettings,
            )
            SearchField(
                value = query,
                onValueChange = model::setQuery,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(10.dp))
            if (!searching) {
                StatusFilterRow(
                    selected = statusFilter,
                    onSelect = model::setStatusFilter,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(4.dp))
            }

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
                                justSaved = note.id == justSavedId,
                                modifier = Modifier.animateItem(),
                                onDelete = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    model.delete(note.id)
                                    showUndo(deletedMsg, undoLabel) { model.restore(note.id) }
                                },
                                onAdvanceStatus = { from, to ->
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    model.setStatus(note.id, to)
                                    showUndo(movedMsgs.getValue(to), undoLabel) {
                                        model.setStatus(note.id, from)
                                    }
                                },
                                onOpen = { onOpenNote(note.id) },
                                highlightQuery = if (searching) query else "",
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(
    count: Int,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 12.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column {
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = {
                    onThemeModeChange(
                        when (themeMode) {
                            ThemeMode.SYSTEM -> ThemeMode.LIGHT
                            ThemeMode.LIGHT -> ThemeMode.DARK
                            ThemeMode.DARK -> ThemeMode.SYSTEM
                        },
                    )
                },
            ) {
                Text(
                    when (themeMode) {
                        ThemeMode.SYSTEM -> stringResource(R.string.theme_mode_system)
                        ThemeMode.LIGHT -> stringResource(R.string.theme_mode_light)
                        ThemeMode.DARK -> stringResource(R.string.theme_mode_dark)
                    },
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.settings), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun StatusFilterRow(
    selected: NoteStatus?,
    onSelect: (NoteStatus?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options: List<Pair<NoteStatus?, String>> = listOf(
        null to stringResource(R.string.filter_all),
        NoteStatus.INBOX to stringResource(R.string.status_inbox),
        NoteStatus.ACTIVE to stringResource(R.string.status_active),
        NoteStatus.DONE to stringResource(R.string.status_done),
        NoteStatus.ARCHIVED to stringResource(R.string.status_archived),
    )
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (status, label) ->
            FilterChip(
                selected = selected == status,
                onClick = { onSelect(status) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
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
        trailingIcon = {
            AnimatedVisibility(visible = value.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                TextButton(onClick = { onValueChange("") }) {
                    Text(
                        stringResource(R.string.clear_search),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
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

/** Highlights every case-insensitive occurrence of [query] in [text] — search result scanning aid. */
private fun highlightMatches(text: String, query: String, highlightColor: androidx.compose.ui.graphics.Color): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        val lowerText = text.lowercase()
        val lowerQuery = query.trim().lowercase()
        if (lowerQuery.isEmpty()) return@buildAnnotatedString
        var start = 0
        while (true) {
            val idx = lowerText.indexOf(lowerQuery, start)
            if (idx < 0) break
            addStyle(
                SpanStyle(background = highlightColor, fontWeight = FontWeight.SemiBold),
                idx,
                idx + lowerQuery.length,
            )
            start = idx + lowerQuery.length
        }
    }
}

private fun nextStatus(current: NoteStatus): NoteStatus? = when (current) {
    NoteStatus.INBOX -> NoteStatus.ACTIVE
    NoteStatus.ACTIVE -> NoteStatus.DONE
    NoteStatus.DONE -> NoteStatus.ARCHIVED
    NoteStatus.ARCHIVED -> null
}

/**
 * Signature triage swipe: real physical weight — the card rotates as it's dragged, resists
 * past the commit threshold, and throws off-screen before the status change actually lands
 * (swipe left = delete, unchanged action; swipe right = advance status, this card's new
 * motion). Everything else in the app uses the calm default spring; this is one of the two
 * deliberately expressive moments.
 */
@Composable
private fun SwipeableNote(
    note: NoteEntity,
    onDelete: () -> Unit,
    onAdvanceStatus: (from: NoteStatus, to: NoteStatus) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    justSaved: Boolean = false,
    highlightQuery: String = "",
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val offsetX = remember(note.id) { Animatable(0f) }
    val thresholdPx = with(density) { 96.dp.toPx() }
    val throwPx = with(density) { 900.dp.toPx() }
    val canAdvance = nextStatus(note.status) != null
    var deleteArmed by remember(note.id) { mutableStateOf(false) }
    var advanceArmed by remember(note.id) { mutableStateOf(false) }
    var expanded by rememberSaveable(note.id) { mutableStateOf(false) }

    fun settle() {
        scope.launch { offsetX.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) }
    }

    Box(
        modifier = modifier
            // Separate pointerInput from the drag detector below so a plain tap (no
            // horizontal movement, never consumed by the drag gesture) still toggles
            // expand — a child clickable here would lose the race to the ancestor's drag
            // detector and never fire.
            .pointerInput(note.id) {
                detectTapGestures(onTap = { expanded = !expanded })
            }
            .pointerInput(note.id, canAdvance) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    val x = offsetX.value
                    when {
                        x <= -thresholdPx -> scope.launch {
                            offsetX.animateTo(-throwPx, tween(180))
                            onDelete()
                        }
                        x >= thresholdPx && canAdvance -> scope.launch {
                            offsetX.animateTo(throwPx, tween(180))
                            nextStatus(note.status)?.let { onAdvanceStatus(note.status, it) }
                            // Unlike delete, the note stays in the dataset under its new
                            // status (same key), so this same card composable survives the
                            // status change — it must settle back to rest, or it's left
                            // visually thrown off-screen forever.
                            deleteArmed = false
                            advanceArmed = false
                            settle()
                        }
                        else -> settle()
                    }
                },
                onDragCancel = { settle() },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    val beyond = abs(offsetX.value) > thresholdPx
                    val resisted = if (beyond) dragAmount * 0.35f else dragAmount
                    val target = (offsetX.value + resisted).let {
                        if (!canAdvance) it.coerceAtMost(0f) else it
                    }
                    scope.launch { offsetX.snapTo(target) }
                    val deleteNow = target <= -thresholdPx
                    val advanceNow = target >= thresholdPx && canAdvance
                    if (deleteNow != deleteArmed || advanceNow != advanceArmed) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    deleteArmed = deleteNow
                    advanceArmed = advanceNow
                },
            )
        },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    when {
                        deleteArmed -> MaterialTheme.colorScheme.errorContainer
                        advanceArmed -> MaterialTheme.colorScheme.primaryContainer
                        offsetX.value < 0f -> MaterialTheme.colorScheme.surfaceContainer
                        offsetX.value > 0f -> MaterialTheme.colorScheme.surfaceContainer
                        else -> MaterialTheme.colorScheme.background
                    },
                    RoundedCornerShape(24.dp),
                )
                .padding(horizontal = 24.dp),
            contentAlignment = if (offsetX.value < 0f) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            if (offsetX.value < 0f) {
                Text(
                    stringResource(R.string.delete),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            } else if (offsetX.value > 0f && canAdvance) {
                Text(
                    statusLabel(nextStatus(note.status)!!),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        NoteCard(
            note,
            expanded = expanded,
            justSaved = justSaved,
            onOpen = onOpen,
            highlightQuery = highlightQuery,
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .graphicsLayer { rotationZ = (offsetX.value / 24f).coerceIn(-10f, 10f) },
        )
    }
}

/**
 * Signature "settle" moment: a freshly saved note drops in with a slight overshoot and a
 * brief warm tint that relaxes into the normal card surface — the one place motion is
 * deliberately more than the calm default spring used everywhere else.
 */
@Composable
private fun NoteCard(
    note: NoteEntity,
    expanded: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    justSaved: Boolean = false,
    highlightQuery: String = "",
) {
    val body = note.rawText ?: note.transcript ?: note.ocrText
    val title = note.title

    // Captured once at first composition: if `justSaved` flips to false mid-animation (the
    // user saved another note within the settle window), the key below must NOT include the
    // live `justSaved` value, or recomposition would cancel these animations before they reach
    // rest — leaving the card visibly scaled/tinted off until it scrolls off-screen and back.
    val playSettle = remember(note.id) { justSaved }
    val scale = remember(note.id) { Animatable(if (playSettle) 0.9f else 1f) }
    val tint = remember(note.id) { Animatable(if (playSettle) 1f else 0f) }
    LaunchedEffect(note.id) {
        if (playSettle) {
            scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
        }
    }
    LaunchedEffect(note.id) {
        if (playSettle) {
            tint.animateTo(0f, tween(durationMillis = 450))
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale.value),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = lerp(
                MaterialTheme.colorScheme.surfaceContainerLowest,
                MaterialTheme.colorScheme.primaryContainer,
                tint.value,
            ),
        ),
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
                    highlightMatches(title, highlightQuery, MaterialTheme.colorScheme.primaryContainer),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (body != null && body != title) {
                Text(
                    highlightMatches(body, highlightQuery, MaterialTheme.colorScheme.primaryContainer),
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
            AnimatedVisibility(visible = expanded) {
                TextButton(onClick = onOpen, modifier = Modifier.padding(top = 2.dp)) {
                    Text(stringResource(R.string.open_note))
                }
            }
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
private fun Composer(onSave: (title: String, body: String) -> Unit, onVoice: () -> Unit, onPhoto: () -> Unit) {
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
                    visible = !canSave,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val voiceLabel = stringResource(R.string.voice_mic_button)
                        Surface(
                            onClick = onVoice,
                            modifier = Modifier
                                .size(56.dp)
                                .semantics { contentDescription = voiceLabel },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text("🎙", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                        val photoLabel = stringResource(R.string.photo_camera_button)
                        Surface(
                            onClick = onPhoto,
                            modifier = Modifier
                                .size(56.dp)
                                .semantics { contentDescription = photoLabel },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text("📷", style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
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

/**
 * Note detail/edit. Title isn't editable (no backend command for it yet, see
 * `NoteCommandHandler` — intentionally not touched for this UI pass); body uses the existing
 * save-first `updateRawText` command. Reads from [MainViewModel.allNotes] (already observed,
 * unfiltered by status) rather than a new query.
 */
@Composable
fun NoteDetailScreen(model: MainViewModel, noteId: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    val allNotes by model.allNotes.collectAsStateWithLifecycle()
    val note = allNotes.find { it.id == noteId }

    // The note can disappear out from under this screen (deleted via triage elsewhere);
    // don't leave a dead screen open.
    LaunchedEffect(note) {
        if (note == null) onBack()
    }
    if (note == null) return

    val originalBody = note.rawText ?: note.transcript ?: note.ocrText ?: ""
    var body by rememberSaveable(note.id) { mutableStateOf(originalBody) }
    val dirty = body != originalBody

    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMsg = stringResource(R.string.note_saved)

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = {
                        model.delete(note.id)
                        onBack()
                    },
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            }

            if (note.title != null) {
                Text(note.title, style = MaterialTheme.typography.headlineLarge)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(sourceLabel(note.source), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
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

            StatusPickerRow(
                selected = note.status,
                onSelect = { model.setStatus(note.id, it) },
            )

            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.note_body_label)) },
                minLines = 6,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )

            AnimatedVisibility(visible = dirty, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                Button(
                    onClick = {
                        model.updateText(note.id, body)
                        scope.launch { snackbarHost.showSnackbar(savedMsg) }
                    },
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Text(stringResource(R.string.save), fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatusPickerRow(selected: NoteStatus, onSelect: (NoteStatus) -> Unit, modifier: Modifier = Modifier) {
    val options = listOf(
        NoteStatus.INBOX to stringResource(R.string.status_inbox),
        NoteStatus.ACTIVE to stringResource(R.string.status_active),
        NoteStatus.DONE to stringResource(R.string.status_done),
        NoteStatus.ARCHIVED to stringResource(R.string.status_archived),
    )
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (status, label) ->
            FilterChip(
                selected = selected == status,
                onClick = { onSelect(status) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

/** Opens the system share sheet on [file] via the app's FileProvider — no network involved. */
private fun shareFile(context: android.content.Context, file: File, mimeType: String) {
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

@Composable
fun SettingsScreen(model: MainViewModel, onBack: () -> Unit, onOpenTrash: () -> Unit, onOpenRules: () -> Unit) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allNotes by model.allNotes.collectAsStateWithLifecycle()
    var exporting by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }
    val exportFailedMsg = stringResource(R.string.export_failed)

    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "?"
    }

    // ponytail: every tap writes a new timestamped file into exportDir with nothing pruning
    // old ones — unbounded private-storage growth over repeated use. Add a "keep last N" sweep
    // in NoteExporter if that ever matters in practice.
    fun export(mimeType: String, write: suspend () -> File) {
        if (exporting) return
        exporting = true
        scope.launch {
            try {
                val file = write()
                shareFile(context, file, mimeType)
            } catch (e: Exception) {
                snackbarHost.showSnackbar(exportFailedMsg)
            } finally {
                exporting = false
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            }
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineLarge)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.export_section_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    pluralStringResource(R.plurals.header_subtitle, allNotes.size, allNotes.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.export_privacy_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { export("application/json") { model.exportJson() } },
                    enabled = !exporting,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text(stringResource(R.string.export_json))
                }
                Button(
                    onClick = { export("text/markdown") { model.exportMarkdown() } },
                    enabled = !exporting,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text(stringResource(R.string.export_markdown))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.trash_section_title), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onOpenTrash, modifier = Modifier.padding(start = 0.dp)) {
                    Text(stringResource(R.string.open_trash))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.rules_section_title), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onOpenRules, modifier = Modifier.padding(start = 0.dp)) {
                    Text(stringResource(R.string.open_rules))
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.settings_version, versionName),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Trash: recently soft-deleted notes with restore (D-plan screen 2). No permanent/hard delete in
 * v1 — `NoteCommandHandler` has no such command, and nothing here should add one casually.
 */
@Composable
fun TrashScreen(model: MainViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    val trash by model.trash.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val restoredMsg = stringResource(R.string.restored_snackbar)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            }
            Text(
                stringResource(R.string.trash_title),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(12.dp))
            if (trash.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        stringResource(R.string.trash_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(trash, key = { it.id }) { note ->
                        Column(modifier = Modifier.animateItem()) {
                            NoteCard(note, expanded = false, onOpen = {})
                            TextButton(
                                onClick = {
                                    model.restore(note.id)
                                    scope.launch { snackbarHost.showSnackbar(restoredMsg) }
                                },
                            ) {
                                Text(stringResource(R.string.restore))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Rules list + editor (D16, backlog screen 5). Keyword matcher → category action; enable/disable,
 * add, remove, and a "test against sample text" box that runs [RuleEngine] purely client-side
 * (no DB write) so you can check a rule before it ever touches a real note.
 */
@Composable
fun RulesScreen(model: MainViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    val rules by model.rules.collectAsStateWithLifecycle()
    var newKeywords by rememberSaveable { mutableStateOf("") }
    var newCategory by rememberSaveable { mutableStateOf(DEFAULT_CATEGORIES.first()) }
    var sampleText by rememberSaveable { mutableStateOf("") }

    val sampleMatch = remember(rules, sampleText) {
        if (sampleText.isBlank()) {
            null
        } else {
            RuleEngine.firstMatch(rules.mapNotNull { it.toRule() }, sampleText)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            }
            Text(stringResource(R.string.rules_title), style = MaterialTheme.typography.headlineLarge)

            if (rules.isEmpty()) {
                Text(
                    stringResource(R.string.rules_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    rules.forEach { entity ->
                        val rule = remember(entity) { entity.toRule() }
                        if (rule != null) {
                            RuleRow(
                                rule = rule,
                                onToggle = { model.setRuleEnabled(entity.id, it) },
                                onDelete = { model.deleteRule(entity.id) },
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.new_rule_title), style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DEFAULT_CATEGORIES.forEach { category ->
                        FilterChip(
                            selected = newCategory == category,
                            onClick = { newCategory = category },
                            label = { Text(category) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        )
                    }
                }
                OutlinedTextField(
                    value = newKeywords,
                    onValueChange = { newKeywords = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.rule_keywords_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                )
                Button(
                    onClick = {
                        val keywords = newKeywords.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        if (keywords.isNotEmpty()) {
                            model.addRule(keywords, newCategory)
                            newKeywords = ""
                        }
                    },
                    enabled = newKeywords.isNotBlank(),
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text(stringResource(R.string.add_rule))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.test_sample_title), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = sampleText,
                    onValueChange = { sampleText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.test_sample_hint)) },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(20.dp),
                )
                AnimatedVisibility(visible = sampleText.isNotBlank()) {
                    Text(
                        if (sampleMatch != null) {
                            stringResource(R.string.test_match_found, sampleMatch.action.category)
                        } else {
                            stringResource(R.string.test_no_match)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RuleRow(rule: Rule, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(rule.action.category, style = MaterialTheme.typography.titleMedium)
                    Text(
                        rule.matcher.keywords.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.Switch(checked = rule.enabled, onCheckedChange = onToggle)
            }
            TextButton(onClick = onDelete, modifier = Modifier.padding(top = 0.dp)) {
                Text(stringResource(R.string.rule_delete))
            }
        }
    }
}

/**
 * Voice capture (D15). Mic access only while this screen is open and listening; nothing is
 * recorded to disk — the transcript IS the note. One utterance per tap (start/stop), tapping
 * again appends another utterance so a longer thought can be built up before Save.
 */
@Composable
fun VoiceCaptureScreen(model: MainViewModel, onBack: () -> Unit, onSaved: () -> Unit) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    var permissionGranted by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionDenied by remember { mutableStateOf(false) }
    var listening by remember { mutableStateOf(false) }
    var transcript by rememberSaveable { mutableStateOf("") }
    var partial by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        permissionDenied = !granted
    }

    val recognizerAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val recognizer = remember(recognizerAvailable) {
        if (recognizerAvailable) SpeechRecognizer.createSpeechRecognizer(context) else null
    }
    DisposableEffect(recognizer) {
        onDispose { recognizer?.destroy() }
    }

    fun startListening() {
        if (!permissionGranted) {
            permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            return
        }
        val r = recognizer ?: return
        partial = ""
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                listening = false
                partial = ""
                scope.launch { snackbarHost.showSnackbar(context.getString(R.string.voice_error)) }
            }
            override fun onResults(results: Bundle?) {
                listening = false
                val heard = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                partial = ""
                if (heard.isNotBlank()) {
                    transcript = if (transcript.isBlank()) heard else "$transcript $heard"
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        listening = true
        r.startListening(intent)
    }

    fun stopListening() {
        recognizer?.stopListening()
        listening = false
    }

    val savedMsg = stringResource(R.string.voice_saved_snackbar)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            }
            Text(stringResource(R.string.voice_capture_title), style = MaterialTheme.typography.headlineLarge)

            when {
                !recognizerAvailable -> Text(
                    stringResource(R.string.voice_error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                permissionDenied -> Text(
                    stringResource(R.string.voice_permission_denied),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                !permissionGranted -> {
                    Text(
                        stringResource(R.string.voice_permission_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.voice_permission_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO) },
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Text(stringResource(R.string.voice_permission_grant))
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            onClick = { if (listening) stopListening() else startListening() },
                            modifier = Modifier.size(96.dp),
                            shape = CircleShape,
                            color = if (listening) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text("🎙", style = MaterialTheme.typography.displaySmall)
                            }
                        }
                    }
                    Text(
                        if (listening) stringResource(R.string.voice_listening) else stringResource(R.string.voice_tap_to_speak),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                    val shownText = listOf(transcript, partial).filter { it.isNotBlank() }.joinToString(" ")
                    Text(
                        shownText.ifBlank { stringResource(R.string.voice_transcript_hint) },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (shownText.isBlank()) {
                            MaterialTheme.colorScheme.outline
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Button(
                        onClick = {
                            saving = true
                            model.captureVoice(transcript) {
                                scope.launch { snackbarHost.showSnackbar(savedMsg) }
                                onSaved()
                            }
                        },
                        enabled = transcript.isNotBlank() && !saving,
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Text(stringResource(R.string.voice_save))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Photo capture (D17): system camera intent or the Android photo picker — neither needs a
 * runtime permission. Recognized text is reviewable/editable before the first save; after Save
 * it goes through the normal save-first path (source=PHOTO, ocrText, imagePath).
 */
@Composable
fun PhotoCaptureScreen(model: MainViewModel, onBack: () -> Unit, onSaved: () -> Unit) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    var photoFile by remember { mutableStateOf<File?>(null) }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var recognizing by remember { mutableStateOf(false) }
    var ocrText by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    fun runOcr(file: File) {
        recognizing = true
        scope.launch {
            ocrText = runCatching { OcrEngine.recognize(context, Uri.fromFile(file)) }.getOrDefault("")
            recognizing = false
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingCameraFile
        if (success && file != null) {
            photoFile = file
            runOcr(file)
        }
    }
    val pickMediaLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val file = withContext(Dispatchers.IO) {
                    val dest = File(model.photosDir, "${UUID.randomUUID()}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                    dest
                }
                photoFile = file
                runOcr(file)
            }
        }
    }

    fun launchCamera() {
        val file = File(model.photosDir, "${UUID.randomUUID()}.jpg")
        pendingCameraFile = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        // Defensive: some OEM camera apps need an explicit grant even with grantUriPermissions="true".
        context.packageManager.queryIntentActivities(Intent(MediaStore.ACTION_IMAGE_CAPTURE), 0).forEach {
            context.grantUriPermission(
                it.activityInfo.packageName,
                uri,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        cameraLauncher.launch(uri)
    }

    val savedMsg = stringResource(R.string.photo_saved_snackbar)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            }
            Text(stringResource(R.string.photo_capture_title), style = MaterialTheme.typography.headlineLarge)

            val file = photoFile
            when {
                file == null -> {
                    Text(
                        stringResource(R.string.photo_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = ::launchCamera, shape = RoundedCornerShape(20.dp)) {
                        Text(stringResource(R.string.photo_take))
                    }
                    Button(
                        onClick = {
                            pickMediaLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Text(stringResource(R.string.photo_choose_gallery))
                    }
                }
                recognizing -> {
                    CircularProgressIndicator()
                    Text(
                        stringResource(R.string.photo_reading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    val bitmap = remember(file) {
                        BitmapFactory.decodeFile(file.path)?.asImageBitmap()
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    OutlinedTextField(
                        value = ocrText,
                        onValueChange = { ocrText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.photo_ocr_hint)) },
                        minLines = 4,
                        maxLines = 10,
                        shape = RoundedCornerShape(20.dp),
                    )
                    Button(
                        onClick = {
                            saving = true
                            model.capturePhoto(file.absolutePath, ocrText) {
                                scope.launch { snackbarHost.showSnackbar(savedMsg) }
                                onSaved()
                            }
                        },
                        enabled = !saving,
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Text(stringResource(R.string.photo_save))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
