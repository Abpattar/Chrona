package com.chrona.app.di

import android.content.Context
import androidx.room.Room
import com.chrona.app.data.local.ChronaDatabase
import com.chrona.app.data.repository.EventRepository
import com.chrona.app.data.repository.RoomEventRepository
import com.chrona.app.data.repository.RoomRelationshipRepository
import com.chrona.app.data.repository.RoomTaskRepository
import com.chrona.app.data.repository.TaskRepository
import com.chrona.app.domain.relation.RelationGraph
import com.chrona.app.usecase.CreateTaskUseCase
import com.chrona.app.usecase.EventUseCases
import com.chrona.app.usecase.IdGenerator
import com.chrona.app.usecase.ListTasksUseCase
import com.chrona.app.usecase.TaskLifecycleUseCases
import com.chrona.app.usecase.TimeSource
import java.util.UUID

/**
 * Manual composition root (MASTER_ARCHITECTURE.md §3.8).
 *
 * No DI framework: with one module and a handful of collaborators, generated graphs would add
 * a build step and indirection without removing any real complexity. Wiring is explicit so a
 * reader can see the whole object graph in one file.
 *
 * Clocks and ID generation are parameters, not hard-coded calls, so tests can substitute
 * deterministic versions (CODING_CONVENTIONS.md).
 */
class AppContainer(
    context: Context,
    val database: ChronaDatabase = buildDatabase(context),
    val timeSource: TimeSource = SystemTimeSource(),
    val idGenerator: IdGenerator = UuidGenerator(),
) {
    val taskRepository: TaskRepository = RoomTaskRepository(database)
    val eventRepository: EventRepository = RoomEventRepository(database)
    val relationshipRepository = RoomRelationshipRepository(database)

    val createTask = CreateTaskUseCase(taskRepository, idGenerator, timeSource)
    val listTasks = ListTasksUseCase(taskRepository)
    val lifecycle = TaskLifecycleUseCases(taskRepository, timeSource)
    val events = EventUseCases(eventRepository, idGenerator, timeSource)

    /** Placeholder for P05: builds the dependency graph from stored relationships. */
    fun relationGraph(): RelationGraph = RelationGraph()

    private companion object {
        fun buildDatabase(context: Context): ChronaDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                ChronaDatabase::class.java,
                ChronaDatabase.FILE_NAME,
            )
                // No fallbackToDestructiveMigration(): losing real task data silently is worse
                // than failing loudly. Every version bump needs an explicit migration + test.
                .build()
    }
}

/** Wall-clock reference time. The only place production code reads "now". */
class SystemTimeSource(
    private val zoneProvider: () -> java.time.ZoneId = { java.time.ZoneId.systemDefault() },
) : TimeSource {
    override fun now(): Instant = Instant.now()
    override fun zone(): java.time.ZoneId = zoneProvider()
}

/** Random opaque IDs. Stable identity does not require the ID to be guessable or ordered. */
class UuidGenerator : IdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}

private typealias Instant = java.time.Instant