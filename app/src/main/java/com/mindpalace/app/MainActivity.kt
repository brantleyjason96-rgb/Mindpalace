package com.mindpalace.app

// ============================================================================
// Main screen (polished capture + browse). Backend contract used:
//   container.commands (ONLY writer of canonical note data), container.database.noteDao()
//   (Flow reads + FTS search). Delete = soft delete, Undo = restore (audited, §3.2).
// NOT fully true yet (plan 01 W1, tracked, not fixed): several vocabulary/rules/reminder writes
// in MainViewModel go straight through DAOs (addTopic/addPerson/addPlace/addLink/addReminder/
// cancelReminder/completeReminder/setRuleEnabled/addRule/deleteRule) — none of those are audited
// or undoable. Canonical NOTE fields (text/title/status/delete) do go through NoteCommandHandler.
// ============================================================================

import android.Manifest
import android.content.Context
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalClipboardManager
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mindpalace.app.core.commands.CreateNoteRequest
import com.mindpalace.app.core.commands.EmptyCaptureException
import com.mindpalace.app.core.commands.NoteCommandHandler
import com.mindpalace.app.core.commands.NoteConflictException
import com.mindpalace.app.core.commands.NoteMissingException
import com.mindpalace.app.core.db.CategoryDao
import com.mindpalace.app.core.db.CategoryEntity
import com.mindpalace.app.core.db.FtsQuery
import com.mindpalace.app.core.db.MindPalaceDatabase
import com.mindpalace.app.core.db.NoteDao
import com.mindpalace.app.core.db.NoteEntity
import com.mindpalace.app.core.db.NoteLinkDao
import com.mindpalace.app.core.db.NoteLinkEntity
import com.mindpalace.app.core.db.PersonDao
import com.mindpalace.app.core.db.PersonEntity
import com.mindpalace.app.core.db.PlaceDao
import com.mindpalace.app.core.db.PlaceEntity
import com.mindpalace.app.core.db.AiRunDao
import com.mindpalace.app.core.db.AuditDao
import com.mindpalace.app.core.db.DerivedDao
import com.mindpalace.app.core.db.AiRunEntity
import com.mindpalace.app.core.db.AuditEntity
import com.mindpalace.app.core.db.DerivedEntity
import com.mindpalace.app.core.db.FieldValueCount
import com.mindpalace.app.core.db.ProposalDao
import com.mindpalace.app.core.db.ProposalEntity
import com.mindpalace.app.core.db.ReminderDao
import com.mindpalace.app.core.db.ReminderEntity
import com.mindpalace.app.core.db.RuleDao
import com.mindpalace.app.core.db.RuleEntity
import com.mindpalace.app.core.db.TopicDao
import com.mindpalace.app.core.db.TopicEntity
import com.mindpalace.app.core.model.AUDIT_ENTITY_NOTE
import com.mindpalace.app.core.model.AuditAction
import com.mindpalace.app.core.model.AuditActor
import com.mindpalace.app.core.model.DerivedField
import com.mindpalace.app.core.model.LinkState
import com.mindpalace.app.core.model.LinkType
import com.mindpalace.app.core.model.NoteSnapshot
import com.mindpalace.app.core.model.NoteSource
import com.mindpalace.app.core.model.NoteStatus
import com.mindpalace.app.core.model.Origin
import com.mindpalace.app.core.model.ProposalKind
import com.mindpalace.app.core.model.ProposalState
import com.mindpalace.app.core.model.ReminderState
import com.mindpalace.app.core.model.ReminderType
import com.mindpalace.app.core.ink.InkRecognitionEngine
import com.mindpalace.app.core.ink.InkStroke
import com.mindpalace.app.core.ocr.OcrEngine
import com.mindpalace.app.core.reminders.AlarmScheduler
import com.mindpalace.app.core.reminders.EXTRA_OPEN_NOTE_ID
import com.mindpalace.app.core.reminders.millisToSpec
import com.mindpalace.app.core.reminders.scheduledAtMillis
import com.mindpalace.app.core.rules.Rule
import com.mindpalace.app.core.rules.RuleAction
import com.mindpalace.app.core.rules.RuleEngine
import com.mindpalace.app.core.rules.RuleMatcher
import com.mindpalace.app.core.rules.toEntity
import com.mindpalace.app.core.rules.toRule
import com.mindpalace.app.export.NoteExporter
import com.mindpalace.app.ui.AttachFileIcon
import com.mindpalace.app.ui.CameraIcon
import com.mindpalace.app.ui.CircleIconButton
import com.mindpalace.app.ui.ClockTabIcon
import com.mindpalace.app.ui.MicIcon
import com.mindpalace.app.ui.NoteTabIcon
import com.mindpalace.app.ui.PenIcon
import com.mindpalace.app.ui.ShieldTabIcon
import com.mindpalace.app.ui.VaultTabIcon
import com.mindpalace.app.ui.rememberPressScale
import com.mindpalace.app.ui.theme.MindPalaceTheme
import com.mindpalace.app.ui.theme.ThemeMode
import com.mindpalace.app.ui.theme.ThemePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** A command failure the UI should show, not crash on (plan 01 X1). */
sealed interface UiError {
    data class Conflict(val noteId: String) : UiError
    data class Missing(val noteId: String) : UiError
    data object Empty : UiError
    data class Unknown(val message: String?) : UiError
}

/** Distinguishes "the Flow hasn't emitted yet" from "it emitted null" (plan 01 N1) — collected
 *  with [Loading] as the `initialValue`, so a screen only navigates away once it actually knows
 *  the note is gone, never on the very first composition before any data has arrived. */
sealed interface NoteLookup {
    data object Loading : NoteLookup
    data object Missing : NoteLookup
    data class Found(val note: NoteEntity) : NoteLookup
}

class MainViewModel(
    database: MindPalaceDatabase,
    private val commands: NoteCommandHandler,
    private val exporter: NoteExporter,
    private val exportDir: File,
    val photosDir: File,
    val attachmentsDir: File,
) : ViewModel() {

    private val noteDao: NoteDao = database.noteDao()
    private val ruleDao: RuleDao = database.ruleDao()
    private val categoryDao: CategoryDao = database.categoryDao()
    private val topicDao: TopicDao = database.topicDao()
    private val personDao: PersonDao = database.personDao()
    private val placeDao: PlaceDao = database.placeDao()
    private val noteLinkDao: NoteLinkDao = database.noteLinkDao()
    private val reminderDao: ReminderDao = database.reminderDao()
    private val proposalDao: ProposalDao = database.proposalDao()
    private val aiRunDao: AiRunDao = database.aiRunDao()
    private val auditDao: AuditDao = database.auditDao()
    private val derivedDao: DerivedDao = database.derivedDao()

    /**
     * Every write funnels through [launchCommand] (plan 01 X1) instead of a bare
     * `viewModelScope.launch { ... }` — none of those used to have a try/catch, so any command
     * exception (a stale revision, a note deleted out from under the screen, an empty capture)
     * crashed the whole app instead of showing an error. [errors] is a hot, buffered stream the
     * UI can collect to show a snackbar; nothing observing it yet is not a reason to drop an
     * error silently.
     */
    private val _errors = MutableSharedFlow<UiError>(extraBufferCapacity = 4)
    val errors: SharedFlow<UiError> = _errors.asSharedFlow()

    private fun launchCommand(onError: (UiError) -> Unit = ::reportError, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: NoteConflictException) {
                onError(UiError.Conflict(e.noteId))
            } catch (e: NoteMissingException) {
                onError(UiError.Missing(e.noteId))
            } catch (e: EmptyCaptureException) {
                onError(UiError.Empty)
            } catch (e: Exception) {
                onError(UiError.Unknown(e.message))
            }
        }
    }

    private fun reportError(error: UiError) {
        viewModelScope.launch { _errors.emit(error) }
    }

    /** Revision history for the Thought Detail screen — newest first, same order the audit
     *  table is already queried in. */
    fun auditForNote(noteId: String): Flow<List<AuditEntity>> =
        auditDao.observeRecentFor(AUDIT_ENTITY_NOTE, noteId, limit = 50)

    /** Live lookup for the Thought Detail screen (plan 01 N1) — see [NoteLookup]. */
    fun noteLookup(noteId: String): Flow<NoteLookup> =
        noteDao.observeById(noteId).map { if (it == null) NoteLookup.Missing else NoteLookup.Found(it) }

    /** Current derived facts (category/tag/person/place/topic) for the Thought Detail screen's
     *  "why was this filed here" explanation. */
    fun derivedForNote(noteId: String): Flow<List<DerivedEntity>> = derivedDao.observeForNote(noteId)

    /** AI run history (CLAUDE.md backlog) — most recent night-loop runs, newest first. */
    val aiRuns: StateFlow<List<AiRunEntity>> =
        aiRunDao.observeRecent(50).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingProposals: StateFlow<List<ProposalEntity>> =
        proposalDao.observeByState(ProposalState.PENDING)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Review (CLAUDE.md "AI: Proposals review") — applies through the command handler, same
     *  save-first contract as every other mutation; never writes the note directly. */
    fun approveProposal(id: String) {
        launchCommand { commands.approveProposal(id) }
    }

    fun rejectProposal(id: String) {
        launchCommand { commands.rejectProposal(id) }
    }

    val rules: StateFlow<List<RuleEntity>> =
        ruleDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<CategoryEntity>> =
        categoryDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addCategory(name: String) {
        launchCommand {
            val position = categoryDao.maxPosition() + 1
            categoryDao.insert(CategoryEntity(name = name, position = position, createdAt = System.currentTimeMillis()))
        }
    }

    val topics: StateFlow<List<TopicEntity>> =
        topicDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val people: StateFlow<List<PersonEntity>> =
        personDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val places: StateFlow<List<PlaceEntity>> =
        placeDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Vault per-notebook/entity note counts (Phase 2), keyed by name/value. Only non-zero once
     *  a rule (categories) or an approved AI proposal (topics/people/places) has actually filed
     *  a note under that value — an honest reflection of what's wired today, not a guess. */
    val categoryNoteCounts: StateFlow<Map<String, Int>> =
        derivedDao.observeCountsByField(DerivedField.CATEGORY)
            .map { counts -> counts.associate { it.value to it.count } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val topicNoteCounts: StateFlow<Map<String, Int>> =
        derivedDao.observeCountsByField(DerivedField.TOPIC)
            .map { counts -> counts.associate { it.value to it.count } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val personNoteCounts: StateFlow<Map<String, Int>> =
        derivedDao.observeCountsByField(DerivedField.PERSON)
            .map { counts -> counts.associate { it.value to it.count } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val placeNoteCounts: StateFlow<Map<String, Int>> =
        derivedDao.observeCountsByField(DerivedField.PLACE)
            .map { counts -> counts.associate { it.value to it.count } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun addTopic(name: String) {
        launchCommand {
            topicDao.insertIfAbsent(
                TopicEntity(id = UUID.randomUUID().toString(), name = name, createdAt = System.currentTimeMillis(), createdBy = "user"),
            )
        }
    }

    fun addPerson(name: String) {
        launchCommand {
            personDao.insertIfAbsent(
                PersonEntity(id = UUID.randomUUID().toString(), name = name, createdAt = System.currentTimeMillis()),
            )
        }
    }

    fun addPlace(name: String) {
        launchCommand {
            placeDao.insertIfAbsent(
                PlaceEntity(id = UUID.randomUUID().toString(), name = name, createdAt = System.currentTimeMillis()),
            )
        }
    }

    /** Links touching [noteId] in either direction (explicit, rule-made, or AI-suggested). */
    fun linksForNote(noteId: String): Flow<List<NoteLinkEntity>> = noteLinkDao.observeTouching(noteId)

    /** User-made explicit link between two notes (§11) — confirmed immediately, no review step. */
    fun addLink(fromId: String, toId: String) {
        launchCommand {
            noteLinkDao.insert(
                NoteLinkEntity(
                    fromId = fromId,
                    toId = toId,
                    type = LinkType.EXPLICIT,
                    reason = "user link",
                    state = LinkState.CONFIRMED,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    val activeReminders: StateFlow<List<ReminderEntity>> =
        reminderDao.observeByState(ReminderState.ACTIVE)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun remindersForNote(noteId: String): Flow<List<ReminderEntity>> = reminderDao.observeForNote(noteId)

    /** Creates the reminder row and schedules the exact alarm in the same call (D19). */
    fun addReminder(context: Context, noteId: String, atMillis: Long) {
        launchCommand {
            val reminder = ReminderEntity(
                id = UUID.randomUUID().toString(),
                noteId = noteId,
                type = ReminderType.TIME,
                spec = millisToSpec(atMillis),
                state = ReminderState.ACTIVE,
                createdAt = System.currentTimeMillis(),
            )
            reminderDao.insert(reminder)
            AlarmScheduler.schedule(context, reminder)
        }
    }

    fun cancelReminder(context: Context, reminder: ReminderEntity) {
        launchCommand {
            reminderDao.setState(reminder.id, ReminderState.CANCELLED, null)
            AlarmScheduler.cancel(context, reminder)
        }
    }

    fun completeReminder(context: Context, reminder: ReminderEntity) {
        launchCommand {
            reminderDao.setState(reminder.id, ReminderState.DONE, null)
            AlarmScheduler.cancel(context, reminder)
        }
    }

    fun setRuleEnabled(id: String, enabled: Boolean) {
        launchCommand { ruleDao.setEnabled(id, enabled) }
    }

    fun addRule(keywords: List<String>, category: String) {
        launchCommand {
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
        launchCommand { ruleDao.delete(id) }
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
        launchCommand {
            commands.setStatus(noteId, status)
            refresh.value++
        }
    }

    /** Detail/edit screen save. Title isn't editable — the backend has no command for it yet. */
    fun updateText(noteId: String, newText: String) {
        launchCommand {
            commands.updateRawText(noteId, newText)
            refresh.value++
        }
    }

    suspend fun exportJson(): File = exporter.writeJson(exportDir)

    suspend fun exportMarkdown(): File = exporter.writeMarkdown(exportDir)

    /** Save-first: returns as soon as the row is durable — the whole capture path. */
    fun capture(title: String, body: String, onSaved: (noteId: String) -> Unit) {
        launchCommand {
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
        launchCommand {
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
        launchCommand {
            val note = commands.create(
                CreateNoteRequest(source = NoteSource.PHOTO, imagePath = imagePath, ocrText = ocrText.ifBlank { null }),
            )
            justSavedId.value = note.id
            onSaved(note.id)
            delay(SETTLE_ANIMATION_MS)
            if (justSavedId.value == note.id) justSavedId.value = null
        }
    }

    /** Same save-first contract, source=INK (D26): reuses PHOTO's exact shape — the rendered
     *  stroke drawing via [imagePath], recognized text via [ocrText] — rather than new columns,
     *  since it's the same "an image + its recognized text" data. Review step (recognize → edit)
     *  happens in HandwritingCaptureScreen, same as Photo's OCR review. */
    fun captureInk(imagePath: String, recognizedText: String, onSaved: (noteId: String) -> Unit) {
        launchCommand {
            val note = commands.create(
                CreateNoteRequest(source = NoteSource.INK, imagePath = imagePath, ocrText = recognizedText.ifBlank { null }),
            )
            justSavedId.value = note.id
            onSaved(note.id)
            delay(SETTLE_ANIMATION_MS)
            if (justSavedId.value == note.id) justSavedId.value = null
        }
    }

    /** Same save-first contract, source=ATTACHMENT (D25): no review step — a copied file has
     *  nothing to correct before saving, unlike Voice/Photo's transcript/OCR text. */
    fun captureAttachment(path: String, fileName: String, onSaved: (noteId: String) -> Unit) {
        launchCommand {
            val note = commands.create(
                CreateNoteRequest(source = NoteSource.ATTACHMENT, attachmentPath = path, attachmentFileName = fileName),
            )
            justSavedId.value = note.id
            onSaved(note.id)
            delay(SETTLE_ANIMATION_MS)
            if (justSavedId.value == note.id) justSavedId.value = null
        }
    }

    fun delete(noteId: String) {
        launchCommand {
            commands.softDelete(noteId)
            refresh.value++
        }
    }

    fun restore(noteId: String) {
        launchCommand {
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
                        container.attachmentsDir,
                    ) as T
            }
    }
}

/** Every top-level destination this Activity can show, used only to drive [AnimatedContent]'s
 *  screen-transition slide/fade — a plain `when` over mutable booleans would re-render the
 *  "outgoing" slot as the new screen mid-transition instead of animating it out (D21 polish). */
private sealed interface AppScreen {
    data object Main : AppScreen
    data object VoiceCapture : AppScreen
    data object PhotoCapture : AppScreen
    data object HandwritingCapture : AppScreen
    data object Trash : AppScreen
    data object Rules : AppScreen
    data object Categories : AppScreen
    data object Topics : AppScreen
    data object People : AppScreen
    data object Places : AppScreen
    data object Reminders : AppScreen
    data object AiProposals : AppScreen
    data object AiSetup : AppScreen
    data object AiRunHistory : AppScreen
    data object Settings : AppScreen
    data class NoteDetail(val noteId: String) : AppScreen
}

class MainActivity : ComponentActivity() {

    private val container: AppContainer
        get() = (application as MindPalaceApplication).container

    /** Set by a reminder notification tap (onCreate or onNewIntent); consumed once by the
     *  LaunchedEffect in setContent, which opens that note. */
    private val pendingOpenNoteId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingOpenNoteId.value = intent?.getStringExtra(EXTRA_OPEN_NOTE_ID)
        // Consume the extra once (plan 01 N1): a rotation recreates this Activity, re-running
        // onCreate against the SAME Intent object (getIntent() returns it unchanged) — without
        // this, every rotation re-set pendingOpenNoteId and yanked the user back to the notified
        // note even after they had already navigated elsewhere.
        intent?.removeExtra(EXTRA_OPEN_NOTE_ID)
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
            var showHandwritingCapture by rememberSaveable { mutableStateOf(false) }
            var showTopics by rememberSaveable { mutableStateOf(false) }
            var showPeople by rememberSaveable { mutableStateOf(false) }
            var showPlaces by rememberSaveable { mutableStateOf(false) }
            var showReminders by rememberSaveable { mutableStateOf(false) }
            var showCategories by rememberSaveable { mutableStateOf(false) }
            var showAiProposals by rememberSaveable { mutableStateOf(false) }
            var showAiSetup by rememberSaveable { mutableStateOf(false) }
            var showAiRunHistory by rememberSaveable { mutableStateOf(false) }

            val pendingNoteId by pendingOpenNoteId
            LaunchedEffect(pendingNoteId) {
                pendingNoteId?.let {
                    openNoteId = it
                    // Clear EVERY screen flag (plan 01 N1), not just Reminders/Settings — whatever
                    // screen the user had open when the notification arrived used to take
                    // precedence in the `screen` when-expression below, so the note opened hidden
                    // behind Trash, Rules, or any other still-true flag.
                    showSettings = false
                    showVoiceCapture = false
                    showTrash = false
                    showRules = false
                    showPhotoCapture = false
                    showHandwritingCapture = false
                    showTopics = false
                    showPeople = false
                    showPlaces = false
                    showReminders = false
                    showCategories = false
                    showAiProposals = false
                    showAiSetup = false
                    showAiRunHistory = false
                    pendingOpenNoteId.value = null
                }
            }

            val currentOpenId = openNoteId
            val screen: AppScreen = when {
                showVoiceCapture -> AppScreen.VoiceCapture
                showPhotoCapture -> AppScreen.PhotoCapture
                showHandwritingCapture -> AppScreen.HandwritingCapture
                showTrash -> AppScreen.Trash
                showRules -> AppScreen.Rules
                showCategories -> AppScreen.Categories
                showTopics -> AppScreen.Topics
                showPeople -> AppScreen.People
                showPlaces -> AppScreen.Places
                showReminders -> AppScreen.Reminders
                showAiProposals -> AppScreen.AiProposals
                showAiSetup -> AppScreen.AiSetup
                showAiRunHistory -> AppScreen.AiRunHistory
                showSettings -> AppScreen.Settings
                currentOpenId != null -> AppScreen.NoteDetail(currentOpenId)
                else -> AppScreen.Main
            }

            MindPalaceTheme(darkTheme = darkTheme) {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 10 })
                            .togetherWith(fadeOut(tween(140)) + slideOutHorizontally(tween(140)) { -it / 10 })
                    },
                    label = "screenNav",
                ) { target ->
                    when (target) {
                        AppScreen.VoiceCapture -> VoiceCaptureScreen(
                            model = model,
                            onBack = { showVoiceCapture = false },
                            onSaved = { showVoiceCapture = false },
                        )
                        AppScreen.PhotoCapture -> PhotoCaptureScreen(
                            model = model,
                            onBack = { showPhotoCapture = false },
                            onSaved = { showPhotoCapture = false },
                        )
                        AppScreen.HandwritingCapture -> HandwritingCaptureScreen(
                            model = model,
                            onBack = { showHandwritingCapture = false },
                            onSaved = { showHandwritingCapture = false },
                        )
                        AppScreen.Trash -> TrashScreen(model = model, onBack = { showTrash = false })
                        AppScreen.Rules -> RulesScreen(model = model, onBack = { showRules = false })
                        AppScreen.Categories -> CategoriesScreen(model = model, onBack = { showCategories = false })
                        AppScreen.Topics -> TopicsScreen(model = model, onBack = { showTopics = false })
                        AppScreen.People -> PeopleScreen(model = model, onBack = { showPeople = false })
                        AppScreen.Places -> PlacesScreen(model = model, onBack = { showPlaces = false })
                        AppScreen.Reminders -> RemindersScreen(
                            model = model,
                            onBack = { showReminders = false },
                            onOpenNote = {
                                showReminders = false
                                openNoteId = it
                            },
                        )
                        AppScreen.AiProposals -> AiProposalsScreen(model = model, onBack = { showAiProposals = false })
                        AppScreen.AiSetup -> AiSetupScreen(onBack = { showAiSetup = false })
                        AppScreen.AiRunHistory -> AiRunHistoryScreen(model = model, onBack = { showAiRunHistory = false })
                        AppScreen.Settings -> SettingsScreen(
                            model = model,
                            onBack = { showSettings = false },
                            onOpenTrash = { showTrash = true },
                            onOpenRules = { showRules = true },
                            onOpenCategories = { showCategories = true },
                            onOpenTopics = { showTopics = true },
                            onOpenPeople = { showPeople = true },
                            onOpenPlaces = { showPlaces = true },
                            onOpenReminders = { showReminders = true },
                            onOpenAiSetup = { showAiSetup = true },
                            onOpenAiRunHistory = { showAiRunHistory = true },
                        )
                        is AppScreen.NoteDetail -> NoteDetailScreen(
                            model = model,
                            noteId = target.noteId,
                            onBack = { openNoteId = null },
                            onOpenNote = { openNoteId = it },
                        )
                        AppScreen.Main -> MainTabsScreen(
                            model = model,
                            themeMode = themeMode,
                            onThemeModeChange = { mode ->
                                themeMode = mode
                                ThemePrefs.save(this@MainActivity, mode)
                            },
                            onOpenNote = { openNoteId = it },
                            onOpenSettings = { showSettings = true },
                            onOpenVoiceCapture = { showVoiceCapture = true },
                            onOpenPhotoCapture = { showPhotoCapture = true },
                            onOpenHandwritingCapture = { showHandwritingCapture = true },
                            onOpenRules = { showRules = true },
                            onOpenCategories = { showCategories = true },
                            onOpenTopics = { showTopics = true },
                            onOpenPeople = { showPeople = true },
                            onOpenPlaces = { showPlaces = true },
                            onOpenTrash = { showTrash = true },
                            onOpenReminders = { showReminders = true },
                            onOpenAiProposals = { showAiProposals = true },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingOpenNoteId.value = intent.getStringExtra(EXTRA_OPEN_NOTE_ID)
    }
}

@Composable
private fun TimelineTab(
    model: MainViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenSettings: () -> Unit,
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

/** The four persistent destinations; ordinal drives slide direction in [MainTabsScreen]. */
private enum class MainTab { CAPTURE, TIMELINE, VAULT, PRIVACY }

/**
 * Bottom-tab shell replacing the old single Main screen (design pass matching the Stitch
 * mockup). Capture/Timeline/Vault/Privacy are real tabs over the same [MainViewModel] — no new
 * data layer, just a navigation shell around what was already there (Settings/Rules/Categories/
 * Topics/People/Places/Trash/Reminders stay as pushed screens, reached from Vault/Privacy now
 * instead of only from Settings).
 */
@Composable
private fun MainTabsScreen(
    model: MainViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVoiceCapture: () -> Unit,
    onOpenPhotoCapture: () -> Unit,
    onOpenHandwritingCapture: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenTopics: () -> Unit,
    onOpenPeople: () -> Unit,
    onOpenPlaces: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenAiProposals: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(MainTab.CAPTURE) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == MainTab.CAPTURE,
                    onClick = { tab = MainTab.CAPTURE },
                    icon = { NoteTabIcon() },
                    label = { Text(stringResource(R.string.tab_capture)) },
                )
                NavigationBarItem(
                    selected = tab == MainTab.TIMELINE,
                    onClick = { tab = MainTab.TIMELINE },
                    icon = { ClockTabIcon() },
                    label = { Text(stringResource(R.string.tab_timeline)) },
                )
                NavigationBarItem(
                    selected = tab == MainTab.VAULT,
                    onClick = { tab = MainTab.VAULT },
                    icon = { VaultTabIcon() },
                    label = { Text(stringResource(R.string.tab_vault)) },
                )
                NavigationBarItem(
                    selected = tab == MainTab.PRIVACY,
                    onClick = { tab = MainTab.PRIVACY },
                    icon = { ShieldTabIcon() },
                    label = { Text(stringResource(R.string.tab_privacy)) },
                )
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = tab,
            modifier = Modifier.padding(padding),
            transitionSpec = {
                val forward = targetState.ordinal >= initialState.ordinal
                val sign = if (forward) 1 else -1
                (fadeIn(tween(200)) + slideInHorizontally(tween(200)) { it / 10 * sign })
                    .togetherWith(fadeOut(tween(140)) + slideOutHorizontally(tween(140)) { -it / 10 * sign })
            },
            label = "tabNav",
        ) { target ->
            when (target) {
                MainTab.CAPTURE -> CaptureTab(
                    model = model,
                    onVoice = onOpenVoiceCapture,
                    onPhoto = onOpenPhotoCapture,
                    onWrite = onOpenHandwritingCapture,
                    onOpenNote = onOpenNote,
                    onReviewInbox = {
                        model.setStatusFilter(NoteStatus.INBOX)
                        tab = MainTab.TIMELINE
                    },
                )
                MainTab.TIMELINE -> TimelineTab(
                    model = model,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    onOpenNote = onOpenNote,
                    onOpenSettings = onOpenSettings,
                )
                MainTab.VAULT -> VaultTab(
                    model = model,
                    onOpenCategories = onOpenCategories,
                    onOpenTopics = onOpenTopics,
                    onOpenPeople = onOpenPeople,
                    onOpenPlaces = onOpenPlaces,
                )
                MainTab.PRIVACY -> PrivacyRulesTab(
                    model = model,
                    onOpenRules = onOpenRules,
                    onOpenTrash = onOpenTrash,
                    onOpenReminders = onOpenReminders,
                    onOpenSettings = onOpenSettings,
                    onOpenAiProposals = onOpenAiProposals,
                )
            }
        }
    }
}

/** Scratchpad + multi-modal capture triggers (mockup: "Capture"). Reuses the existing
 *  [Composer] and the real save-first [MainViewModel.capture] — nothing new is persisted here. */
@Composable
private fun CaptureTab(
    model: MainViewModel,
    onVoice: () -> Unit,
    onPhoto: () -> Unit,
    onWrite: () -> Unit,
    onOpenNote: (String) -> Unit,
    onReviewInbox: () -> Unit,
) {
    val context = LocalContext.current
    val allNotes by model.allNotes.collectAsStateWithLifecycle()
    val ruleEntities by model.rules.collectAsStateWithLifecycle()
    val rules = remember(ruleEntities) { ruleEntities.mapNotNull { it.toRule() } }
    val inboxNotes = remember(allNotes) {
        allNotes.filter { it.status == NoteStatus.INBOX }.sortedByDescending { it.createdAt }
    }
    val previewInboxNotes = remember(inboxNotes) { inboxNotes.take(3) }
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val savedMsg = stringResource(R.string.saved_snackbar)
    val deletedMsg = stringResource(R.string.deleted_snackbar)
    val undoLabel = stringResource(R.string.undo)
    val movedMsgs = mapOf(
        NoteStatus.INBOX to stringResource(R.string.moved_inbox),
        NoteStatus.ACTIVE to stringResource(R.string.moved_active),
        NoteStatus.DONE to stringResource(R.string.moved_done),
        NoteStatus.ARCHIVED to stringResource(R.string.moved_archived),
    )

    fun showUndo(message: String, label: String, onUndo: () -> Unit) {
        scope.launch {
            snackbarHost.currentSnackbarData?.dismiss()
            val result = snackbarHost.showSnackbar(message = message, actionLabel = label)
            if (result == SnackbarResult.ActionPerformed) onUndo()
        }
    }

    // D25: any file type, no preview — just copied into app-private storage and attached to a
    // new note. Unlike Voice/Photo there's no transcript/OCR text to review before saving.
    val pickAttachmentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val fileName = withContext(Dispatchers.IO) {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
                }
            } ?: uri.lastPathSegment ?: "attachment"
            val dest = withContext(Dispatchers.IO) {
                val file = File(model.attachmentsDir, "${UUID.randomUUID()}-$fileName")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                file
            }
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            model.captureAttachment(dest.path, fileName) { id ->
                showUndo(savedMsg, undoLabel) { model.delete(id) }
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            Composer(
                rules = rules,
                onSave = { title, body ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    model.capture(title, body) { id ->
                        showUndo(savedMsg, undoLabel) { model.delete(id) }
                    }
                },
                onVoice = onVoice,
                onPhoto = onPhoto,
                onAttach = { pickAttachmentLauncher.launch(arrayOf("*/*")) },
                onWrite = onWrite,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.tab_capture), style = MaterialTheme.typography.headlineLarge)
            Text(
                stringResource(R.string.capture_tab_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                onClick = onWrite,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shadowElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(14.dp)) {
                        Box(modifier = Modifier.padding(10.dp)) {
                            PenIcon(modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.handwriting_promo_title), style = MaterialTheme.typography.titleLarge)
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape) {
                                Text(
                                    stringResource(R.string.handwriting_promo_badge),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                        Text(
                            stringResource(R.string.handwriting_promo_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (previewInboxNotes.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.awaiting_triage_title), style = MaterialTheme.typography.titleMedium)
                    if (inboxNotes.size > previewInboxNotes.size) {
                        TextButton(onClick = onReviewInbox) {
                            Text(pluralStringResource(R.plurals.inbox_waiting, inboxNotes.size, inboxNotes.size))
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    previewInboxNotes.forEach { note ->
                        SwipeableNote(
                            note = note,
                            modifier = Modifier.animateContentSize(),
                            onDelete = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                model.delete(note.id)
                                showUndo(deletedMsg, undoLabel) { model.restore(note.id) }
                            },
                            onAdvanceStatus = { from, to ->
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                model.setStatus(note.id, to)
                                showUndo(movedMsgs.getValue(to), undoLabel) { model.setStatus(note.id, from) }
                            },
                            onOpen = { onOpenNote(note.id) },
                        )
                    }
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
            ) {
                Text(
                    stringResource(R.string.capture_footer_quote),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

/** Notebooks & entities (mockup: "Vault") — summary tiles over the real Categories/Topics/
 *  People/Places vocabularies, each opening its existing CRUD screen. */
@Composable
private fun VaultTab(
    model: MainViewModel,
    onOpenCategories: () -> Unit,
    onOpenTopics: () -> Unit,
    onOpenPeople: () -> Unit,
    onOpenPlaces: () -> Unit,
) {
    val categories by model.categories.collectAsStateWithLifecycle()
    val topics by model.topics.collectAsStateWithLifecycle()
    val people by model.people.collectAsStateWithLifecycle()
    val places by model.places.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.tab_vault), style = MaterialTheme.typography.headlineLarge)
        Text(
            stringResource(R.string.vault_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VaultTile(stringResource(R.string.categories_title), categories.size, onOpenCategories)
        VaultTile(stringResource(R.string.topics_title), topics.size, onOpenTopics)
        VaultTile(stringResource(R.string.people_title), people.size, onOpenPeople)
        VaultTile(stringResource(R.string.places_title), places.size, onOpenPlaces)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun VaultTile(title: String, count: Int, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text("$count", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Privacy assurance + the real deterministic rules engine (mockup: "Privacy & Rules"). Rule
 *  toggles write through [MainViewModel.setRuleEnabled] immediately; add/edit/delete stays on
 *  the existing [RulesScreen] rather than duplicating that editor here. */
@Composable
private fun PrivacyRulesTab(
    model: MainViewModel,
    onOpenRules: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAiProposals: () -> Unit,
) {
    val rules by model.rules.collectAsStateWithLifecycle()
    val pendingProposals by model.pendingProposals.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.tab_privacy), style = MaterialTheme.typography.headlineLarge)

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.privacy_assurance_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    stringResource(R.string.privacy_assurance_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.rules_section_title), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onOpenRules) { Text(stringResource(R.string.open_rules)) }
            }
            if (rules.isEmpty()) {
                Text(
                    stringResource(R.string.rules_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
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

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.ai_proposals_section_title), style = MaterialTheme.typography.titleMedium)
                if (pendingProposals.isNotEmpty()) {
                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = CircleShape) {
                        Text(
                            "${pendingProposals.size}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
            TextButton(onClick = onOpenAiProposals) { Text(stringResource(R.string.open_ai_proposals)) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.trash_section_title), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onOpenTrash) { Text(stringResource(R.string.open_trash)) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.reminders_section_title), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onOpenReminders) { Text(stringResource(R.string.open_reminders)) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.settings)) }
        }
        Spacer(Modifier.height(24.dp))
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
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (status, label) ->
            FilterChip(
                selected = selected == status,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSelect(status)
                },
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

    // The gesture block below only restarts when (note.id, canAdvance) changes. canAdvance stays
    // true across an INBOX -> ACTIVE -> DONE sequence (every status but the last has a next one),
    // so after the first swipe the block keeps running with `note` captured at its OLD status —
    // the second swipe would then recompute nextStatus from stale state and repeat the first
    // transition (plan 01 T1). rememberUpdatedState always reads the latest note instead.
    val currentNote by rememberUpdatedState(note)

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
                    val advanceableNow = nextStatus(currentNote.status) != null
                    when {
                        x <= -thresholdPx -> scope.launch {
                            offsetX.animateTo(-throwPx, tween(180))
                            onDelete()
                        }
                        x >= thresholdPx && advanceableNow -> scope.launch {
                            offsetX.animateTo(throwPx, tween(180))
                            nextStatus(currentNote.status)?.let { onAdvanceStatus(currentNote.status, it) }
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
                        if (nextStatus(currentNote.status) == null) it.coerceAtMost(0f) else it
                    }
                    scope.launch { offsetX.snapTo(target) }
                    val deleteNow = target <= -thresholdPx
                    val advanceNow = target >= thresholdPx && nextStatus(currentNote.status) != null
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
    val attachmentFileName = note.attachmentFileName
    val body = note.rawText ?: note.transcript ?: note.ocrText
        ?: attachmentFileName?.let { stringResource(R.string.attachment_body_label, it) }
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

    val cardShape = RoundedCornerShape(24.dp)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale.value)
            .shadow(
                elevation = 3.dp,
                shape = cardShape,
                ambientColor = MaterialTheme.colorScheme.primary,
                spotColor = MaterialTheme.colorScheme.primary,
            ),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = lerp(
                MaterialTheme.colorScheme.surfaceContainerLowest,
                MaterialTheme.colorScheme.primaryContainer,
                tint.value,
            ),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
        NoteSource.ATTACHMENT -> R.string.source_attachment
        NoteSource.INK -> R.string.source_ink
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
private fun Composer(
    rules: List<Rule>,
    onSave: (title: String, body: String) -> Unit,
    onVoice: () -> Unit,
    onPhoto: () -> Unit,
    onAttach: () -> Unit,
    onWrite: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var titleFocused by remember { mutableStateOf(false) }
    var bodyFocused by remember { mutableStateOf(false) }

    val canSave = title.isNotBlank() || body.isNotBlank()
    val expanded = titleFocused || bodyFocused || canSave

    // Mirrors NoteCommandHandler.applyRules exactly (D16) — a live preview of the same
    // first-match rule the backend will actually run right after save, not a guess.
    val previewMatch = remember(rules, title, body) {
        val text = "$title $body".trim()
        if (text.isBlank()) null else RuleEngine.firstMatch(rules, text)
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
    )

    val composerShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = composerShape,
                ambientColor = MaterialTheme.colorScheme.primary,
                spotColor = MaterialTheme.colorScheme.primary,
            )
            .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = composerShape,
    ) {
        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PulsingDraftDot()
                    Text(
                        stringResource(R.string.draft_active_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                val words = body.trim().let { if (it.isEmpty()) 0 else it.split("\\s+".toRegex()).size }
                Text(
                    stringResource(R.string.draft_word_count, words, body.length),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { bodyFocused = it.isFocused },
                placeholder = { Text(stringResource(R.string.capture_hint_text)) },
                minLines = 3,
                maxLines = 8,
                shape = RoundedCornerShape(20.dp),
                colors = fieldColors,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    ToolbarIconButton(onClick = onVoice, contentDescription = stringResource(R.string.voice_mic_button)) {
                        MicIcon(modifier = Modifier.size(20.dp))
                    }
                    ToolbarIconButton(onClick = onPhoto, contentDescription = stringResource(R.string.photo_camera_button)) {
                        CameraIcon(modifier = Modifier.size(20.dp))
                    }
                    ToolbarIconButton(onClick = onAttach, contentDescription = stringResource(R.string.mode_attach)) {
                        AttachFileIcon(modifier = Modifier.size(20.dp))
                    }
                    ToolbarIconButton(onClick = onWrite, contentDescription = stringResource(R.string.mode_write)) {
                        PenIcon(modifier = Modifier.size(20.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnimatedVisibility(visible = title.isNotBlank() || body.isNotBlank()) {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape) {
                            Text(
                                previewMatch?.action?.category ?: stringResource(R.string.status_inbox),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
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
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Text(stringResource(R.string.capture_save), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolbarIconButton(onClick: () -> Unit, contentDescription: String, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(36.dp),
        shape = RoundedCornerShape(10.dp),
        color = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.semantics { this.contentDescription = contentDescription }) {
            content()
        }
    }
}

@Composable
private fun PulsingDraftDot() {
    val infinite = rememberInfiniteTransition(label = "draftPulse")
    val alpha by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(animation = tween(700), repeatMode = RepeatMode.Reverse),
        label = "draftPulseAlpha",
    )
    Box(
        Modifier
            .size(7.dp)
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = alpha), CircleShape),
    )
}

/**
 * Note detail/edit. Title isn't editable (no backend command for it yet, see
 * `NoteCommandHandler` — intentionally not touched for this UI pass); body uses the existing
 * save-first `updateRawText` command. The note itself comes from [MainViewModel.noteLookup] (a
 * live single-note query that distinguishes loading from missing, plan 01 N1);
 * [MainViewModel.allNotes] is still used for the link picker below.
 */
@Composable
fun NoteDetailScreen(model: MainViewModel, noteId: String, onBack: () -> Unit, onOpenNote: (String) -> Unit) {
    BackHandler(onBack = onBack)

    val allNotes by model.allNotes.collectAsStateWithLifecycle()
    // Loading vs. missing (plan 01 N1): the old code used allNotes directly, which starts as an
    // empty list on first composition — indistinguishable from "this note was deleted" — and
    // closed the screen immediately on every fresh open (including from a notification tap).
    val lookup by remember(noteId) { model.noteLookup(noteId) }.collectAsStateWithLifecycle(initialValue = NoteLookup.Loading)

    // The note can disappear out from under this screen (deleted via triage elsewhere); don't
    // leave a dead screen open — but only once we genuinely know it's gone, not before the first
    // emission has even arrived.
    LaunchedEffect(lookup) {
        if (lookup is NoteLookup.Missing) onBack()
    }
    val note = (lookup as? NoteLookup.Found)?.note ?: return

    val originalBody = note.rawText ?: note.transcript ?: note.ocrText ?: ""
    var body by rememberSaveable(note.id) { mutableStateOf(originalBody) }
    val dirty = body != originalBody

    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMsg = stringResource(R.string.note_saved)

    val errorConflictMsg = stringResource(R.string.error_conflict)
    val errorMissingMsg = stringResource(R.string.error_missing)
    val errorEmptyMsg = stringResource(R.string.error_empty)
    val errorUnknownMsg = stringResource(R.string.error_unknown)
    LaunchedEffect(Unit) {
        model.errors.collect { error ->
            val message = when (error) {
                is UiError.Conflict -> errorConflictMsg
                is UiError.Missing -> errorMissingMsg
                UiError.Empty -> errorEmptyMsg
                is UiError.Unknown -> errorUnknownMsg
            }
            snackbarHost.showSnackbar(message)
        }
    }

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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                val clipboard = LocalClipboardManager.current
                TextButton(onClick = { clipboard.setText(AnnotatedString(body)) }) {
                    Text(stringResource(R.string.copy_note_text))
                }
            }

            PendingAiSuggestionsSection(model = model, noteId = note.id)

            DerivedFactsSection(model = model, noteId = note.id)

            RevisionHistorySection(
                model = model,
                noteId = note.id,
                currentText = originalBody,
                isEditorDirty = dirty,
                onRevertedText = { body = it },
            )

            NoteLinksSection(model = model, note = note, allNotes = allNotes, onOpenNote = onOpenNote)

            ReminderSection(model = model, noteId = note.id)

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Any AI proposals still PENDING for this specific note, same approve/reject commands the
 *  dedicated AI Proposals screen uses, surfaced in context instead of only in that separate list. */
@Composable
private fun PendingAiSuggestionsSection(model: MainViewModel, noteId: String) {
    val pending by model.pendingProposals.collectAsStateWithLifecycle()
    val forNote = remember(pending, noteId) { pending.filter { it.noteId == noteId } }
    if (forNote.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.pending_ai_suggestions_title), style = MaterialTheme.typography.titleMedium)
        forNote.forEach { proposal ->
            ProposalRow(
                proposal = proposal,
                noteSnippet = null,
                onApprove = { model.approveProposal(proposal.id) },
                onReject = { model.rejectProposal(proposal.id) },
            )
        }
    }
}

/** Current derived facts (category/tag/person/place/topic) for this note — the "why was this
 *  filed here" explanation, decoding each fact's [Origin] into a human-readable source. */
@Composable
private fun DerivedFactsSection(model: MainViewModel, noteId: String) {
    val facts by remember(noteId) { model.derivedForNote(noteId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    if (facts.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.derived_facts_title), style = MaterialTheme.typography.titleMedium)
        facts.forEach { fact ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(derivedFieldLabel(fact.field), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape) {
                    Text(
                        fact.value,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Text(originLabel(fact.origin), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun derivedFieldLabel(field: DerivedField): String = stringResource(
    when (field) {
        DerivedField.CATEGORY -> R.string.derived_field_category
        DerivedField.TAG -> R.string.derived_field_tag
        DerivedField.DUE_AT -> R.string.derived_field_due_at
        DerivedField.PERSON -> R.string.derived_field_person
        DerivedField.PLACE -> R.string.derived_field_place
        DerivedField.TOPIC -> R.string.derived_field_topic
    },
)

@Composable
private fun originLabel(raw: String): String = when (val origin = Origin.parse(raw)) {
    Origin.SystemOrigin -> stringResource(R.string.origin_system)
    Origin.UserOrigin -> stringResource(R.string.origin_user)
    is Origin.RuleOrigin -> stringResource(R.string.origin_rule, origin.ruleId)
    is Origin.AiOrigin -> stringResource(R.string.origin_ai, origin.runId.take(8))
    null -> stringResource(R.string.origin_unknown)
}

/** Full audit trail for this note, newest first (already the DAO's order) — actor, action, a
 *  word-count delta instead of a full word-level diff (ponytail: real LCS diffing is a bigger
 *  lift than the value justifies for v1), and Revert on any past text revision. Revert goes
 *  through the normal [MainViewModel.updateText] command, so it's itself a new audited
 *  revision — append-only stays true automatically, no new command needed. */
@Composable
private fun RevisionHistorySection(
    model: MainViewModel,
    noteId: String,
    currentText: String,
    isEditorDirty: Boolean,
    onRevertedText: (String) -> Unit,
) {
    val entries by remember(noteId) { model.auditForNote(noteId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    if (entries.isEmpty()) return
    val json = remember { Json { ignoreUnknownKeys = true } }
    fun decode(raw: String?): NoteSnapshot? = raw?.let { runCatching { json.decodeFromString(NoteSnapshot.serializer(), it) }.getOrNull() }
    fun textOf(snapshot: NoteSnapshot?): String? = snapshot?.rawText ?: snapshot?.transcript ?: snapshot?.ocrText

    // "Current" (plan 01 V1) = the newest entry whose resulting text actually matches the note's
    // saved text right now — not just entries[0] (the newest audit row of ANY kind), which could
    // be a status change or an undo that didn't touch the text at all.
    val currentIndex = entries.indexOfFirst { textOf(decode(it.after)) == currentText }
    var pendingRevertText by remember(noteId) { mutableStateOf<String?>(null) }

    fun revert(text: String) {
        model.updateText(noteId, text)
        onRevertedText(text)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.revision_history_title), style = MaterialTheme.typography.titleMedium)
        entries.forEachIndexed { index, entry ->
            val before = decode(entry.before)
            val after = decode(entry.after)
            // If the snapshot (or its text) is missing, there's nothing safe to revert to — a
            // bare "" fallback here used to silently blank the note out (plan 01 V1).
            val revertText = textOf(before)
            RevisionRow(
                entry = entry,
                before = before,
                after = after,
                isCurrent = index == currentIndex,
                canRevert = revertText != null,
                onRevert = {
                    if (isEditorDirty) pendingRevertText = revertText else revertText?.let(::revert)
                },
            )
        }
    }

    val confirmText = pendingRevertText
    if (confirmText != null) {
        AlertDialog(
            onDismissRequest = { pendingRevertText = null },
            title = { Text(stringResource(R.string.revert_confirm_title)) },
            text = { Text(stringResource(R.string.revert_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    revert(confirmText)
                    pendingRevertText = null
                }) { Text(stringResource(R.string.revert_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRevertText = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun RevisionRow(
    entry: AuditEntity,
    before: NoteSnapshot?,
    after: NoteSnapshot?,
    isCurrent: Boolean,
    canRevert: Boolean,
    onRevert: () -> Unit,
) {
    val rowShape = RoundedCornerShape(16.dp)
    Card(
        shape = rowShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(auditActionLabel(entry.action), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    if (isCurrent) {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape) {
                            Text(
                                stringResource(R.string.revision_current),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
                Text(
                    DateUtils.getRelativeTimeSpanString(entry.at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Text(auditActorLabel(entry.actor), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (entry.action == AuditAction.UPDATE_RAW_TEXT || entry.action == AuditAction.CREATE) {
                val beforeWords = wordCount(before?.rawText ?: before?.transcript ?: before?.ocrText)
                val afterWords = wordCount(after?.rawText ?: after?.transcript ?: after?.ocrText)
                val delta = afterWords - beforeWords
                if (delta != 0) {
                    Text(
                        if (delta > 0) stringResource(R.string.revision_words_added, delta) else stringResource(R.string.revision_words_removed, -delta),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            if (!isCurrent && entry.action == AuditAction.UPDATE_RAW_TEXT && canRevert) {
                TextButton(onClick = onRevert, modifier = Modifier.padding(start = 0.dp)) {
                    Text(stringResource(R.string.revision_revert))
                }
            }
        }
    }
}

@Composable
private fun auditActionLabel(action: AuditAction): String = stringResource(
    when (action) {
        AuditAction.CREATE -> R.string.revision_action_create
        AuditAction.UPDATE_RAW_TEXT -> R.string.revision_action_update
        AuditAction.SET_STATUS -> R.string.revision_action_status
        AuditAction.SOFT_DELETE -> R.string.revision_action_delete
        AuditAction.RESTORE -> R.string.revision_action_restore
        AuditAction.UNDO -> R.string.revision_action_undo
        AuditAction.UPDATE_TITLE -> R.string.revision_action_update_title
    },
)

@Composable
private fun auditActorLabel(actor: AuditActor): String = stringResource(
    when (actor) {
        AuditActor.USER -> R.string.revision_actor_user
        AuditActor.RULE -> R.string.revision_actor_rule
        AuditActor.AI -> R.string.revision_actor_ai
        AuditActor.SYSTEM -> R.string.revision_actor_system
    },
)

private fun wordCount(text: String?): Int =
    text?.trim()?.let { if (it.isEmpty()) 0 else it.split("\\s+".toRegex()).size } ?: 0

/** Existing links (tap to open the other note) + an inline filtered picker to add one (D18). */
@Composable
private fun NoteLinksSection(
    model: MainViewModel,
    note: NoteEntity,
    allNotes: List<NoteEntity>,
    onOpenNote: (String) -> Unit,
) {
    val links by remember(note.id) { model.linksForNote(note.id) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var addingLink by rememberSaveable(note.id) { mutableStateOf(false) }
    var linkQuery by rememberSaveable(note.id) { mutableStateOf("") }

    val linkedIds = remember(links) { links.map { if (it.fromId == note.id) it.toId else it.fromId }.toSet() }
    val haptics = LocalHapticFeedback.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.links_section_title), style = MaterialTheme.typography.titleMedium)

        if (links.isEmpty()) {
            Text(
                stringResource(R.string.links_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            links.forEach { link ->
                val otherId = if (link.fromId == note.id) link.toId else link.fromId
                val other = allNotes.find { it.id == otherId } ?: return@forEach
                val label = other.title ?: other.rawText ?: other.transcript ?: other.ocrText
                    ?: stringResource(R.string.untitled)
                val linkRowShape = RoundedCornerShape(16.dp)
                Surface(
                    onClick = { onOpenNote(otherId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation = 2.dp, shape = linkRowShape, ambientColor = MaterialTheme.colorScheme.primary, spotColor = MaterialTheme.colorScheme.primary),
                    shape = linkRowShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                ) {
                    Text(
                        label,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (!addingLink) {
            TextButton(onClick = { addingLink = true }, modifier = Modifier.padding(start = 0.dp)) {
                Text(stringResource(R.string.add_link))
            }
        } else {
            OutlinedTextField(
                value = linkQuery,
                onValueChange = { linkQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.link_search_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
            )
            val results = remember(linkQuery, allNotes, linkedIds) {
                val q = linkQuery.trim().lowercase()
                if (q.isEmpty()) {
                    emptyList()
                } else {
                    allNotes.filter { candidate ->
                        candidate.id != note.id &&
                            candidate.id !in linkedIds &&
                            listOfNotNull(candidate.title, candidate.rawText, candidate.transcript, candidate.ocrText)
                                .any { it.lowercase().contains(q) }
                    }.take(10)
                }
            }
            results.forEach { candidate ->
                val label = candidate.title ?: candidate.rawText ?: candidate.transcript ?: candidate.ocrText
                    ?: stringResource(R.string.untitled)
                Surface(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        model.addLink(note.id, candidate.id)
                        addingLink = false
                        linkQuery = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Text(
                        label,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Time reminders for one note (D19): existing reminders with Mark done / Cancel, and a
 * "Remind me" flow — permission checks, then date picker, then time picker, then schedule.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderSection(model: MainViewModel, noteId: String) {
    val context = LocalContext.current
    val reminders by remember(noteId) { model.remindersForNote(noteId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val active = remember(reminders) { reminders.filter { it.state == ReminderState.ACTIVE } }

    var showExactAlarmExplainer by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pickedDateMillis by remember { mutableStateOf<Long?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* proceed regardless — the reminder itself still saves and schedules */
        showDatePicker = true
    }

    fun startAddReminder() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        if (!AlarmScheduler.canScheduleExact(context)) {
            showExactAlarmExplainer = true
            return
        }
        showDatePicker = true
    }

    val dateFormat = remember { java.text.SimpleDateFormat("EEE, MMM d, h:mm a", java.util.Locale.getDefault()) }
    val haptics = LocalHapticFeedback.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.reminders_section_title), style = MaterialTheme.typography.titleMedium)

        if (active.isEmpty()) {
            Text(
                stringResource(R.string.reminder_note_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            active.forEach { reminder ->
                val atMillis = reminder.scheduledAtMillis()
                val rowShape = RoundedCornerShape(16.dp)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation = 2.dp, shape = rowShape, ambientColor = MaterialTheme.colorScheme.primary, spotColor = MaterialTheme.colorScheme.primary),
                    shape = rowShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (atMillis != null) dateFormat.format(java.util.Date(atMillis)) else reminder.spec,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                model.completeReminder(context, reminder)
                            },
                        ) {
                            Text(stringResource(R.string.reminder_mark_done))
                        }
                        TextButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                model.cancelReminder(context, reminder)
                            },
                        ) {
                            Text(stringResource(R.string.reminder_cancel), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        TextButton(onClick = ::startAddReminder, modifier = Modifier.padding(start = 0.dp)) {
            Text(stringResource(R.string.add_reminder))
        }
    }

    if (showExactAlarmExplainer) {
        AlertDialog(
            onDismissRequest = { showExactAlarmExplainer = false },
            title = { Text(stringResource(R.string.reminder_exact_alarm_title)) },
            text = { Text(stringResource(R.string.reminder_exact_alarm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExactAlarmExplainer = false
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:${context.packageName}")
                            },
                        )
                    },
                ) { Text(stringResource(R.string.reminder_open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showExactAlarmExplainer = false }) {
                    Text(stringResource(R.string.reminder_dialog_cancel))
                }
            },
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickedDateMillis = datePickerState.selectedDateMillis
                        showDatePicker = false
                        if (pickedDateMillis != null) showTimePicker = true
                    },
                ) { Text(stringResource(R.string.reminder_pick_time)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.reminder_dialog_cancel)) }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val dateMillis = pickedDateMillis
        if (dateMillis == null) {
            showTimePicker = false
        } else {
            val timePickerState = rememberTimePickerState()
            AlertDialog(
                onDismissRequest = { showTimePicker = false },
                title = { Text(stringResource(R.string.reminder_pick_time)) },
                text = { TimePicker(state = timePickerState) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val date = java.time.Instant.ofEpochMilli(dateMillis)
                                .atZone(java.time.ZoneOffset.UTC)
                                .toLocalDate()
                            val atMillis = java.time.LocalDateTime
                                .of(date, java.time.LocalTime.of(timePickerState.hour, timePickerState.minute))
                                .atZone(java.time.ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()
                            model.addReminder(context, noteId, atMillis)
                            showTimePicker = false
                            pickedDateMillis = null
                        },
                    ) { Text(stringResource(R.string.reminder_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.reminder_dialog_cancel)) }
                },
            )
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
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (status, label) ->
            FilterChip(
                selected = selected == status,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSelect(status)
                },
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
fun SettingsScreen(
    model: MainViewModel,
    onBack: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenTopics: () -> Unit,
    onOpenPeople: () -> Unit,
    onOpenPlaces: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenAiSetup: () -> Unit,
    onOpenAiRunHistory: () -> Unit,
) {
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

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.categories_section_title), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onOpenCategories, modifier = Modifier.padding(start = 0.dp)) {
                    Text(stringResource(R.string.open_categories))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.vocab_section_title), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onOpenTopics, modifier = Modifier.padding(start = 0.dp)) {
                    Text(stringResource(R.string.open_topics))
                }
                TextButton(onClick = onOpenPeople, modifier = Modifier.padding(start = 0.dp)) {
                    Text(stringResource(R.string.open_people))
                }
                TextButton(onClick = onOpenPlaces, modifier = Modifier.padding(start = 0.dp)) {
                    Text(stringResource(R.string.open_places))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.reminders_section_title), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onOpenReminders, modifier = Modifier.padding(start = 0.dp)) {
                    Text(stringResource(R.string.open_reminders))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.ai_setup_section_title), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onOpenAiSetup, modifier = Modifier.padding(start = 0.dp)) {
                    Text(stringResource(R.string.open_ai_setup))
                }
                TextButton(onClick = onOpenAiRunHistory, modifier = Modifier.padding(start = 0.dp)) {
                    Text(stringResource(R.string.open_ai_run_history))
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

private fun modelFileStatus(context: Context): Pair<Boolean, Long> {
    val file = File(com.mindpalace.app.ai.AiModelConfig.modelPath(context))
    return file.exists() to file.length()
}

/**
 * AI setup (CLAUDE.md backlog): pick the GGUF model file instead of requiring `adb push` to the
 * hardcoded path, see whether one's installed, and trigger an immediate run. Scheduling itself
 * (daily, charging + idle + battery not low) isn't user-configurable yet — [AiNightWorker] has
 * the only copy of those constraints; this screen just reports them.
 */
@Composable
fun AiSetupScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var modelStatus by remember { mutableStateOf(modelFileStatus(context)) }
    val (modelExists, modelBytes) = modelStatus
    var copying by remember { mutableStateOf(false) }
    var runStarted by remember { mutableStateOf(false) }

    val pickModelLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        copying = true
        scope.launch {
            withContext(Dispatchers.IO) {
                val dest = File(com.mindpalace.app.ai.AiModelConfig.modelPath(context))
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            }
            modelStatus = modelFileStatus(context)
            copying = false
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
            Text(stringResource(R.string.ai_setup_title), style = MaterialTheme.typography.headlineLarge)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.ai_setup_model_section), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (copying) {
                        stringResource(R.string.ai_setup_copying)
                    } else if (modelExists) {
                        stringResource(R.string.ai_setup_model_installed, modelBytes / (1024 * 1024))
                    } else {
                        stringResource(R.string.ai_setup_no_model)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { pickModelLauncher.launch(arrayOf("*/*")) },
                    enabled = !copying,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Text(stringResource(R.string.ai_setup_choose_model))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.ai_setup_schedule_section), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.ai_setup_schedule_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = {
                        com.mindpalace.app.ai.AiNightWorker.runOnce(context)
                        runStarted = true
                    },
                    enabled = modelExists,
                    modifier = Modifier.padding(start = 0.dp),
                ) {
                    Text(stringResource(R.string.ai_setup_run_now))
                }
                AnimatedVisibility(visible = runStarted) {
                    Text(
                        stringResource(R.string.ai_setup_run_started),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * AI run history (CLAUDE.md backlog): every night-loop run so far, newest first. The data was
 * already recorded by [com.mindpalace.app.ai.AiNightRunner] into `ai_runs` — this just surfaces it.
 */
@Composable
fun AiRunHistoryScreen(model: MainViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val runs by model.aiRuns.collectAsStateWithLifecycle()

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
            Text(stringResource(R.string.ai_run_history_title), style = MaterialTheme.typography.headlineLarge)

            if (runs.isEmpty()) {
                Text(
                    stringResource(R.string.ai_run_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    runs.forEach { run -> AiRunRow(run) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AiRunRow(run: AiRunEntity) {
    val rowShape = RoundedCornerShape(20.dp)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = rowShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(run.model, style = MaterialTheme.typography.titleMedium)
                val outcomeColor = if (run.outcome == "SUCCEEDED") {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.error
                }
                Surface(color = outcomeColor.copy(alpha = 0.15f), shape = CircleShape) {
                    Text(
                        run.outcome,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = outcomeColor,
                    )
                }
            }
            Text(
                DateUtils.getRelativeTimeSpanString(run.started, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val finished = run.finished
            if (finished != null) {
                Text(
                    stringResource(R.string.ai_run_duration, (finished - run.started) / 1000),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(R.string.ai_run_notes_processed, run.notesProcessed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * AI proposals review (CLAUDE.md backlog, "AI: Proposals review"): the only place a night-loop
 * suggestion can ever reach a note — approve applies it through [MainViewModel.approveProposal]
 * (which goes through [com.mindpalace.app.core.commands.NoteCommandHandler], never the DAOs
 * directly); reject just retires it. The model never gets a second vote.
 */
@Composable
fun AiProposalsScreen(model: MainViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    val proposals by model.pendingProposals.collectAsStateWithLifecycle()
    val allNotes by model.allNotes.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

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
            Text(stringResource(R.string.ai_proposals_title), style = MaterialTheme.typography.headlineLarge)

            if (proposals.isEmpty()) {
                Text(
                    stringResource(R.string.ai_proposals_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    proposals.forEach { proposal ->
                        val note = allNotes.find { it.id == proposal.noteId }
                        val snippet = note?.let { it.title ?: it.rawText ?: it.transcript ?: it.ocrText }
                        ProposalRow(
                            proposal = proposal,
                            noteSnippet = snippet,
                            onApprove = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                model.approveProposal(proposal.id)
                            },
                            onReject = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                model.rejectProposal(proposal.id)
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProposalRow(proposal: ProposalEntity, noteSnippet: String?, onApprove: () -> Unit, onReject: () -> Unit) {
    val rowShape = RoundedCornerShape(20.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 2.dp, shape = rowShape, ambientColor = MaterialTheme.colorScheme.primary, spotColor = MaterialTheme.colorScheme.primary),
        shape = rowShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape) {
                    Text(
                        proposal.kind.name.lowercase().replaceFirstChar { it.uppercase() },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Text(
                    stringResource(R.string.ai_proposal_confidence, (proposal.confidence * 100).toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(proposal.newValue, style = MaterialTheme.typography.titleMedium)
            if (snippetIsUsable(noteSnippet)) {
                Text(
                    noteSnippet!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(
                "“${proposal.evidenceQuote}”",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 2,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onApprove, shape = RoundedCornerShape(16.dp)) {
                    Text(stringResource(R.string.ai_proposal_approve))
                }
                TextButton(onClick = onReject) {
                    Text(stringResource(R.string.ai_proposal_reject))
                }
            }
        }
    }
}

private fun snippetIsUsable(snippet: String?): Boolean = !snippet.isNullOrBlank()

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
    val haptics = LocalHapticFeedback.current

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
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
    val categories by model.categories.collectAsStateWithLifecycle()
    var newKeywords by rememberSaveable { mutableStateOf("") }
    var newCategory by rememberSaveable { mutableStateOf("") }
    var sampleText by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(categories) {
        if (newCategory.isBlank() && categories.isNotEmpty()) newCategory = categories.first().name
    }

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
                    categories.forEach { category ->
                        FilterChip(
                            selected = newCategory == category.name,
                            onClick = { newCategory = category.name },
                            label = { Text(category.name) },
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
                val (addRuleSource, addRuleScale) = rememberPressScale()
                val addRuleHaptics = LocalHapticFeedback.current
                Button(
                    onClick = {
                        val keywords = newKeywords.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        if (keywords.isNotEmpty()) {
                            addRuleHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            model.addRule(keywords, newCategory)
                            newKeywords = ""
                        }
                    },
                    modifier = addRuleScale,
                    enabled = newKeywords.isNotBlank(),
                    shape = RoundedCornerShape(20.dp),
                    interactionSource = addRuleSource,
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
    val haptics = LocalHapticFeedback.current
    val rowShape = RoundedCornerShape(20.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 2.dp, shape = rowShape, ambientColor = MaterialTheme.colorScheme.primary, spotColor = MaterialTheme.colorScheme.primary),
        shape = rowShape,
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
                androidx.compose.material3.Switch(
                    checked = rule.enabled,
                    onCheckedChange = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggle(it)
                    },
                )
            }
            TextButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDelete()
                },
                modifier = Modifier.padding(top = 0.dp),
            ) {
                Text(stringResource(R.string.rule_delete))
            }
        }
    }
}

/** One row in a [VocabScreen] list — note count is 0 until a rule (categories) or an approved
 *  AI proposal (topics/people/places) has actually filed a note under this value. */
private data class VocabItem(val name: String, val createdAt: Long, val noteCount: Int)

private enum class VocabSort { RECENT, ALPHABETICAL, MOST_NOTES }

@Composable
fun CategoriesScreen(model: MainViewModel, onBack: () -> Unit) {
    val categories by model.categories.collectAsStateWithLifecycle()
    val counts by model.categoryNoteCounts.collectAsStateWithLifecycle()
    val items = remember(categories, counts) {
        categories.map { VocabItem(it.name, it.createdAt, counts[it.name] ?: 0) }
    }
    VocabScreen(
        title = stringResource(R.string.categories_title),
        items = items,
        addHint = stringResource(R.string.add_category_hint),
        emptyText = stringResource(R.string.categories_empty),
        onAdd = model::addCategory,
        onBack = onBack,
    )
}

@Composable
fun TopicsScreen(model: MainViewModel, onBack: () -> Unit) {
    val topics by model.topics.collectAsStateWithLifecycle()
    val counts by model.topicNoteCounts.collectAsStateWithLifecycle()
    val items = remember(topics, counts) {
        topics.map { VocabItem(it.name, it.createdAt, counts[it.name] ?: 0) }
    }
    VocabScreen(
        title = stringResource(R.string.topics_title),
        items = items,
        addHint = stringResource(R.string.add_topic_hint),
        emptyText = stringResource(R.string.topics_empty),
        onAdd = model::addTopic,
        onBack = onBack,
    )
}

@Composable
fun PeopleScreen(model: MainViewModel, onBack: () -> Unit) {
    val people by model.people.collectAsStateWithLifecycle()
    val counts by model.personNoteCounts.collectAsStateWithLifecycle()
    val items = remember(people, counts) {
        people.map { VocabItem(it.name, it.createdAt, counts[it.name] ?: 0) }
    }
    VocabScreen(
        title = stringResource(R.string.people_title),
        items = items,
        addHint = stringResource(R.string.add_person_hint),
        emptyText = stringResource(R.string.people_empty),
        onAdd = model::addPerson,
        onBack = onBack,
    )
}

@Composable
fun PlacesScreen(model: MainViewModel, onBack: () -> Unit) {
    val places by model.places.collectAsStateWithLifecycle()
    val counts by model.placeNoteCounts.collectAsStateWithLifecycle()
    val items = remember(places, counts) {
        places.map { VocabItem(it.name, it.createdAt, counts[it.name] ?: 0) }
    }
    VocabScreen(
        title = stringResource(R.string.places_title),
        items = items,
        addHint = stringResource(R.string.add_place_hint),
        emptyText = stringResource(R.string.places_empty),
        onAdd = model::addPlace,
        onBack = onBack,
    )
}

/**
 * Shared shell for the three append-only vocabularies (Topics, People, Places — D18), plus
 * Categories. There's no delete because nothing yet references these by id in a way that needs
 * cleanup, and no merge/edit because §7 says two plausible matches must ask, not guess — safer
 * to leave that for when AI-assisted matching actually exists.
 */
@Composable
private fun VocabScreen(
    title: String,
    items: List<VocabItem>,
    addHint: String,
    emptyText: String,
    onAdd: (String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var newName by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(VocabSort.RECENT) }
    val haptics = LocalHapticFeedback.current

    val visible = remember(items, query, sort) {
        items
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
            .let { list ->
                when (sort) {
                    VocabSort.RECENT -> list.sortedByDescending { it.createdAt }
                    VocabSort.ALPHABETICAL -> list.sortedBy { it.name.lowercase() }
                    VocabSort.MOST_NOTES -> list.sortedByDescending { it.noteCount }
                }
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
            Text(title, style = MaterialTheme.typography.headlineLarge)

            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(addHint) },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                )
                val (addSource, addScale) = rememberPressScale()
                Button(
                    onClick = {
                        val trimmed = newName.trim()
                        if (trimmed.isNotBlank()) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAdd(trimmed)
                            newName = ""
                        }
                    },
                    modifier = addScale,
                    enabled = newName.isNotBlank(),
                    shape = RoundedCornerShape(20.dp),
                    interactionSource = addSource,
                ) {
                    Text(stringResource(R.string.add))
                }
            }

            if (items.isNotEmpty()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.vocab_search_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = sort == VocabSort.RECENT, onClick = { sort = VocabSort.RECENT }, label = { Text(stringResource(R.string.vocab_sort_recent)) })
                    FilterChip(selected = sort == VocabSort.ALPHABETICAL, onClick = { sort = VocabSort.ALPHABETICAL }, label = { Text(stringResource(R.string.vocab_sort_alphabetical)) })
                    FilterChip(selected = sort == VocabSort.MOST_NOTES, onClick = { sort = VocabSort.MOST_NOTES }, label = { Text(stringResource(R.string.vocab_sort_most_notes)) })
                }
            }

            if (items.isEmpty()) {
                Text(
                    emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (visible.isEmpty()) {
                Text(
                    stringResource(R.string.vocab_search_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    visible.forEach { item ->
                        val rowShape = RoundedCornerShape(16.dp)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(
                                    elevation = 2.dp,
                                    shape = rowShape,
                                    ambientColor = MaterialTheme.colorScheme.primary,
                                    spotColor = MaterialTheme.colorScheme.primary,
                                ),
                            shape = rowShape,
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(item.name, style = MaterialTheme.typography.bodyLarge)
                                if (item.noteCount > 0) {
                                    Text(
                                        pluralStringResource(R.plurals.vocab_note_count, item.noteCount, item.noteCount),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
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
    var micLevel by remember { mutableFloatStateOf(0f) }

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
            override fun onRmsChanged(rmsdB: Float) {
                micLevel = (rmsdB / 10f).coerceIn(0f, 1f)
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                listening = false
                partial = ""
                micLevel = 0f
                scope.launch { snackbarHost.showSnackbar(context.getString(R.string.voice_error)) }
            }
            override fun onResults(results: Bundle?) {
                listening = false
                micLevel = 0f
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
        micLevel = 0f
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
                    val (grantSource, grantScale) = rememberPressScale()
                    Button(
                        onClick = { permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO) },
                        modifier = grantScale,
                        shape = RoundedCornerShape(20.dp),
                        interactionSource = grantSource,
                    ) {
                        Text(stringResource(R.string.voice_permission_grant))
                    }
                }
                else -> {
                    val micColor = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    val ambientPulse by rememberInfiniteTransition(label = "micAmbient").animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                        label = "micAmbientPulse",
                    )
                    val ringLevel by animateFloatAsState(
                        targetValue = if (listening) maxOf(micLevel, ambientPulse * 0.3f) else 0f,
                        animationSpec = tween(150),
                        label = "micRingLevel",
                    )
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(elevation = 3.dp, shape = RoundedCornerShape(20.dp), ambientColor = MaterialTheme.colorScheme.primary, spotColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Canvas(modifier = Modifier.size(180.dp)) {
                                    if (listening) {
                                        val baseRadius = size.minDimension / 2f * 0.33f
                                        val maxExtra = size.minDimension / 2f * 0.67f
                                        for (ring in 1..2) {
                                            val t = (ringLevel * ring / 2f).coerceIn(0f, 1f)
                                            drawCircle(
                                                color = micColor.copy(alpha = (0.28f - ring * 0.08f) * (0.5f + ringLevel)),
                                                radius = baseRadius + maxExtra * t,
                                            )
                                        }
                                    }
                                }
                                CircleIconButton(
                                    onClick = { if (listening) stopListening() else startListening() },
                                    contentDescription = stringResource(R.string.voice_mic_button),
                                    containerColor = if (listening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = if (listening) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimaryContainer,
                                    size = 96.dp,
                                    hapticType = HapticFeedbackType.LongPress,
                                ) {
                                    MicIcon(modifier = Modifier.size(40.dp))
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
                        }
                    }
                    val (saveSource, saveScale) = rememberPressScale()
                    Button(
                        onClick = {
                            saving = true
                            model.captureVoice(transcript) {
                                scope.launch { snackbarHost.showSnackbar(savedMsg) }
                                onSaved()
                            }
                        },
                        modifier = saveScale,
                        enabled = transcript.isNotBlank() && !saving,
                        shape = RoundedCornerShape(20.dp),
                        interactionSource = saveSource,
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
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(elevation = 3.dp, shape = RoundedCornerShape(20.dp), ambientColor = MaterialTheme.colorScheme.primary, spotColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                stringResource(R.string.photo_intro),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val (takeSource, takeScale) = rememberPressScale()
                            Button(
                                onClick = ::launchCamera,
                                modifier = takeScale.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                interactionSource = takeSource,
                            ) {
                                CameraIcon(modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimary)
                                Spacer(Modifier.width(10.dp))
                                Text(stringResource(R.string.photo_take), fontWeight = FontWeight.SemiBold)
                            }
                            val (galleryInteractionSource, galleryScale) = rememberPressScale()
                            OutlinedButton(
                                onClick = {
                                    pickMediaLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                },
                                modifier = galleryScale.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                interactionSource = galleryInteractionSource,
                            ) {
                                Text(stringResource(R.string.photo_choose_gallery))
                            }
                        }
                    }
                }
                recognizing -> {
                    val pulse by rememberInfiniteTransition(label = "ocrPulse").animateFloat(
                        initialValue = 0.9f,
                        targetValue = 1.08f,
                        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                        label = "ocrPulseScale",
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            modifier = Modifier
                                .size(64.dp)
                                .scale(pulse),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                CameraIcon(tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.photo_reading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
                else -> {
                    val bitmap = remember(file) {
                        BitmapFactory.decodeFile(file.path)?.asImageBitmap()
                    }
                    if (bitmap != null) {
                        val frameShape = RoundedCornerShape(20.dp)
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .shadow(
                                    elevation = 10.dp,
                                    shape = frameShape,
                                    ambientColor = MaterialTheme.colorScheme.primary,
                                    spotColor = MaterialTheme.colorScheme.primary,
                                )
                                .clip(frameShape),
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
                    val (photoSaveSource, photoSaveScale) = rememberPressScale()
                    Button(
                        onClick = {
                            saving = true
                            model.capturePhoto(file.absolutePath, ocrText) {
                                scope.launch { snackbarHost.showSnackbar(savedMsg) }
                                onSaved()
                            }
                        },
                        modifier = photoSaveScale,
                        enabled = !saving,
                        shape = RoundedCornerShape(20.dp),
                        interactionSource = photoSaveSource,
                    ) {
                        Text(stringResource(R.string.photo_save))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private val InkColors = listOf(
    Color.Black,
    Color(0xFFD32F2F),
    Color(0xFF1976D2),
    Color(0xFF388E3C),
    Color(0xFFF57C00),
)

/** Object-eraser hit radius — removes a whole stroke if any of its points falls this close to
 *  the drag, not a pixel-level eraser (simplest fit for stroke-based recognition input). */
private const val ERASER_RADIUS_PX = 40f

private val StrokeWidths = listOf(3f, 6f, 10f)

private enum class InkTool { PEN, HIGHLIGHTER, ERASER }

/**
 * Handwriting capture (D26): draw → Recognize (downloads the ink model on first use, then
 * recognizes fully on-device) → editable text, mirroring Voice/Photo's review-before-save shape.
 * Saves via [MainViewModel.captureInk], which reuses PHOTO's exact column shape — no new schema.
 */
@Composable
fun HandwritingCaptureScreen(model: MainViewModel, onBack: () -> Unit, onSaved: () -> Unit) {
    BackHandler(onBack = onBack)

    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    var strokes by remember { mutableStateOf(listOf<InkStroke>()) }
    var selectedColor by remember { mutableStateOf(InkColors.first()) }
    var tool by remember { mutableStateOf(InkTool.PEN) }
    var strokeWidth by remember { mutableFloatStateOf(StrokeWidths[1]) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var renderedFile by remember { mutableStateOf<File?>(null) }
    var recognizedText by rememberSaveable { mutableStateOf("") }
    var recognizing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun eraseNear(offset: Offset) {
        strokes = strokes.filterNot { it.isNear(offset, ERASER_RADIUS_PX) }
    }
    fun newStroke() = when (tool) {
        InkTool.HIGHLIGHTER -> InkStroke(selectedColor, widthPx = strokeWidth * 3f, alpha = 0.35f)
        else -> InkStroke(selectedColor, widthPx = strokeWidth)
    }
    fun startStroke(offset: Offset) {
        if (tool == InkTool.ERASER) {
            eraseNear(offset)
            return
        }
        strokes = strokes + newStroke().withPoint(offset, System.currentTimeMillis())
    }
    fun addToStroke(offset: Offset) {
        if (tool == InkTool.ERASER) {
            eraseNear(offset)
            return
        }
        if (strokes.isEmpty()) return
        strokes = strokes.toMutableList().also {
            it[it.lastIndex] = it.last().withPoint(offset, System.currentTimeMillis())
        }
    }
    fun clearInk() {
        strokes = emptyList()
    }
    fun undoStroke() {
        strokes = strokes.dropLast(1)
    }

    val savedMsg = stringResource(R.string.handwriting_saved_snackbar)
    val defaultErrorMsg = stringResource(R.string.handwriting_error)

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
            Text(stringResource(R.string.handwriting_capture_title), style = MaterialTheme.typography.headlineLarge)

            val file = renderedFile
            when {
                file == null -> {
                    Text(
                        stringResource(R.string.handwriting_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .onSizeChanged { canvasSize = it },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shadowElevation = 3.dp,
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    // S Pen only (D27): a finger-down is never even consumed, so
                                    // it falls through as a no-op touch instead of drawing or
                                    // palm-smudging a stroke — the canvas is deaf to anything
                                    // that isn't the stylus.
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        if (down.type != PointerType.Stylus) return@awaitEachGesture
                                        down.consume()
                                        startStroke(down.position)
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (change.changedToUpIgnoreConsumed()) {
                                                change.consume()
                                                break
                                            }
                                            if (change.positionChanged()) {
                                                addToStroke(change.position)
                                                change.consume()
                                            }
                                        }
                                    }
                                },
                        ) {
                            strokes.forEach { stroke ->
                                val points = stroke.points
                                if (points.size < 2) return@forEach
                                val path = Path().apply {
                                    moveTo(points.first().x, points.first().y)
                                    for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
                                }
                                drawPath(
                                    path,
                                    color = stroke.color.copy(alpha = stroke.alpha),
                                    style = Stroke(width = stroke.widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = tool == InkTool.PEN,
                            onClick = { tool = InkTool.PEN },
                            label = { Text(stringResource(R.string.handwriting_tool_pen)) },
                        )
                        FilterChip(
                            selected = tool == InkTool.HIGHLIGHTER,
                            onClick = { tool = InkTool.HIGHLIGHTER },
                            label = { Text(stringResource(R.string.handwriting_tool_highlighter)) },
                        )
                        FilterChip(
                            selected = tool == InkTool.ERASER,
                            onClick = { tool = InkTool.ERASER },
                            label = { Text(stringResource(R.string.handwriting_eraser)) },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        InkColors.forEach { swatch ->
                            val isSelected = tool != InkTool.ERASER && selectedColor == swatch
                            Surface(
                                onClick = {
                                    selectedColor = swatch
                                    if (tool == InkTool.ERASER) tool = InkTool.PEN
                                },
                                modifier = Modifier.size(32.dp),
                                shape = CircleShape,
                                color = swatch,
                                border = if (isSelected) {
                                    BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface)
                                } else {
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                },
                            ) {}
                        }
                        Spacer(Modifier.weight(1f))
                        Row(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(10.dp))
                                .padding(2.dp),
                        ) {
                            StrokeWidths.forEach { width ->
                                val isSelected = strokeWidth == width
                                Surface(
                                    onClick = { strokeWidth = width },
                                    color = if (isSelected) MaterialTheme.colorScheme.surfaceContainerLowest else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                                        Box(
                                            Modifier
                                                .size((width).dp.coerceAtMost(14.dp))
                                                .background(MaterialTheme.colorScheme.onSurface, CircleShape),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    errorMessage?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = ::undoStroke, enabled = strokes.isNotEmpty()) {
                            Text(stringResource(R.string.handwriting_undo))
                        }
                        OutlinedButton(onClick = ::clearInk, shape = RoundedCornerShape(20.dp)) {
                            Text(stringResource(R.string.handwriting_clear))
                        }
                        val (recognizeSource, recognizeScale) = rememberPressScale()
                        Button(
                            onClick = {
                                if (strokes.isEmpty() || recognizing) return@Button
                                recognizing = true
                                errorMessage = null
                                scope.launch {
                                    runCatching {
                                        InkRecognitionEngine.ensureModelReady()
                                        val text = InkRecognitionEngine.recognize(strokes)
                                        val rendered = withContext(Dispatchers.IO) {
                                            InkRecognitionEngine.renderToFile(strokes, canvasSize.width, canvasSize.height, model.photosDir)
                                        }
                                        text to rendered
                                    }.onSuccess { (text, rendered) ->
                                        recognizedText = text
                                        renderedFile = rendered
                                    }.onFailure {
                                        errorMessage = it.message ?: defaultErrorMsg
                                    }
                                    recognizing = false
                                }
                            },
                            enabled = !recognizing && strokes.isNotEmpty(),
                            modifier = recognizeScale,
                            shape = RoundedCornerShape(20.dp),
                            interactionSource = recognizeSource,
                        ) {
                            Text(
                                if (recognizing) {
                                    stringResource(R.string.handwriting_recognizing)
                                } else {
                                    stringResource(R.string.handwriting_recognize)
                                },
                            )
                        }
                    }
                }
                else -> {
                    val bitmap = remember(file) { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }
                    if (bitmap != null) {
                        val frameShape = RoundedCornerShape(20.dp)
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .shadow(
                                    elevation = 10.dp,
                                    shape = frameShape,
                                    ambientColor = MaterialTheme.colorScheme.primary,
                                    spotColor = MaterialTheme.colorScheme.primary,
                                )
                                .clip(frameShape),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    OutlinedTextField(
                        value = recognizedText,
                        onValueChange = { recognizedText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.handwriting_text_hint)) },
                        minLines = 4,
                        maxLines = 10,
                        shape = RoundedCornerShape(20.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                renderedFile = null
                                clearInk()
                                recognizedText = ""
                            },
                        ) {
                            Text(stringResource(R.string.handwriting_try_again))
                        }
                        val (saveSource, saveScale) = rememberPressScale()
                        Button(
                            onClick = {
                                saving = true
                                model.captureInk(file.absolutePath, recognizedText) {
                                    scope.launch { snackbarHost.showSnackbar(savedMsg) }
                                    onSaved()
                                }
                            },
                            modifier = saveScale,
                            enabled = !saving,
                            shape = RoundedCornerShape(20.dp),
                            interactionSource = saveSource,
                        ) {
                            Text(stringResource(R.string.handwriting_save))
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Every ACTIVE reminder across all notes (D19), reachable from Settings. */
@Composable
fun RemindersScreen(model: MainViewModel, onBack: () -> Unit, onOpenNote: (String) -> Unit) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val reminders by model.activeReminders.collectAsStateWithLifecycle()
    val allNotes by model.allNotes.collectAsStateWithLifecycle()
    val dateFormat = remember { java.text.SimpleDateFormat("EEE, MMM d, h:mm a", java.util.Locale.getDefault()) }
    val haptics = LocalHapticFeedback.current

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
            Text(stringResource(R.string.reminders_title), style = MaterialTheme.typography.headlineLarge)

            if (reminders.isEmpty()) {
                Text(
                    stringResource(R.string.reminders_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    reminders.forEach { reminder ->
                        val note = allNotes.find { it.id == reminder.noteId }
                        val label = note?.title ?: note?.rawText ?: note?.transcript ?: note?.ocrText
                            ?: stringResource(R.string.untitled)
                        val atMillis = reminder.scheduledAtMillis()
                        val cardShape = RoundedCornerShape(20.dp)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(elevation = 3.dp, shape = cardShape, ambientColor = MaterialTheme.colorScheme.primary, spotColor = MaterialTheme.colorScheme.primary),
                            shape = cardShape,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    if (atMillis != null) dateFormat.format(java.util.Date(atMillis)) else reminder.spec,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Surface(
                                    onClick = { onOpenNote(reminder.noteId) },
                                    color = androidx.compose.ui.graphics.Color.Transparent,
                                ) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Row {
                                    TextButton(
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            model.completeReminder(context, reminder)
                                        },
                                    ) {
                                        Text(stringResource(R.string.reminder_mark_done))
                                    }
                                    TextButton(
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            model.cancelReminder(context, reminder)
                                        },
                                    ) {
                                        Text(stringResource(R.string.reminder_cancel), color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
