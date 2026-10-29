package com.davidlang.vehicleexpensesautomated.data.db

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

sealed interface DbState

data object Unchecked : DbState
data object Ok : DbState

data class Failed(
    val fromVersion: Int?,
    val toVersion: Int,
    val error: Throwable,
) : DbState

data class Exporting(val prior: DbState, val gen: Long) : DbState

data class Importing(val prior: DbState, val gen: Long) : DbState


/**
 * Process-wide database and transfer state. Compare-and-set only.
 * No Context, SQLite, Room, files, or Android log.
 * Callers keep the instance a winning take returned and pass that instance back.
 */
class DatabaseHealthState {
    val states = MutableStateFlow<DbState>(Unchecked)
    val state: StateFlow<DbState> = states.asStateFlow()

    private val genCounter = AtomicLong(0)

    fun nextGen(): Long = genCounter.incrementAndGet()

    /** One-shot export failure reasons. A value sent with no collector is kept. */
    val exportFailures = Channel<String>(Channel.BUFFERED)

    val progress = MutableStateFlow("")

    fun compareAndSet(expect: DbState, update: DbState): Boolean = states.compareAndSet(expect, update)



}
