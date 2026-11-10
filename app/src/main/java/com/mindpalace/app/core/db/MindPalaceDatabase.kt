package com.mindpalace.app.core.db

import androidx.room3.Database
import androidx.room3.RoomDatabase

/**
 * The single Room database for Mind Palace. Schema v1 intentionally includes ALL §6 tables
 * (notes + FTS, derived, topics, note_links, reminders, people, places, rules, ai_index,
 * proposals, ai_runs, audit, jobs) so later phases only ALTER/append with tested migrations
 * instead of churning the schema (D6, docs/DECISIONS.md).
 *
 * Schemas are exported by the androidx.room3 Gradle plugin into app/schemas/ and committed;
 * every future version bump requires a MigrationTest (plan §14).
 */
@Database(
    entities = [
        NoteEntity::class,
        NoteFtsEntity::class,
        DerivedEntity::class,
        CategoryEntity::class,
        TopicEntity::class,
        NoteLinkEntity::class,
        ReminderEntity::class,
        PersonEntity::class,
        PlaceEntity::class,
        RuleEntity::class,
        AiIndexEntity::class,
        ProposalEntity::class,
        AiRunEntity::class,
        AuditEntity::class,
        JobEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class MindPalaceDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun derivedDao(): DerivedDao
    abstract fun categoryDao(): CategoryDao
    abstract fun auditDao(): AuditDao
    abstract fun topicDao(): TopicDao
    abstract fun noteLinkDao(): NoteLinkDao
    abstract fun reminderDao(): ReminderDao
    abstract fun personDao(): PersonDao
    abstract fun placeDao(): PlaceDao
    abstract fun ruleDao(): RuleDao
    abstract fun aiIndexDao(): AiIndexDao
    abstract fun proposalDao(): ProposalDao
    abstract fun aiRunDao(): AiRunDao
    abstract fun jobDao(): JobDao

    companion object {
        const val NAME = "mind_palace.db"
    }
}
