package com.chrona.app.di

import com.chrona.app.domain.model.Outcome
import com.chrona.app.domain.model.Task
import com.chrona.app.domain.model.TaskQuery

/**
 * Loads every task Chrona holds, closed states included.
 *
 * Extracted so the Schedule screen, the task browser and the simulation all read the task pool
 * through one definition. Closed states are included on purpose: the browser's "Completed" filter
 * needs them, and the engine decides what is today's work from `requestedDate`, not from the
 * query — filtering them out here would make a finished task disappear from History.
 */
fun AppContainer.allTaskLoader(): suspend (java.time.LocalDate) -> Outcome<List<Task>> = { _ ->
    taskRepository.list(TaskQuery.EVERYTHING)
}

/**
 * The user's own open work, for the surfaces that plan or show *their* day.
 *
 * Deliberately excludes simulation rows: a 142-task simulated fortnight whose dates sit in the
 * past would otherwise all be carried forward into the real day, and the user would open their
 * own schedule to find a simulation's breakfast scheduled four times.
 */
fun AppContainer.userTaskLoader(): suspend (java.time.LocalDate) -> Outcome<List<Task>> = { _ ->
    taskRepository.list(TaskQuery.ACTIVE)
}
