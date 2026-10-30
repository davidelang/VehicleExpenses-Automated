package com.davidlang.vehicleexpensesautomated.data.db

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stale generation: a finished gate must not clear the next one. No Android. */
class DatabaseHealthStateTest {

    @Test
    fun staleExportGen_doesNotClearTheNewerGate() {
        val holder = DatabaseHealthState()
        assertTrue(holder.compareAndSet(Unchecked, Ok))
        val g1 = Exporting(prior = Ok, gen = holder.nextGen())
        assertTrue(holder.compareAndSet(Ok, g1))
        assertTrue(holder.compareAndSet(g1, Ok))
        val g2 = Exporting(prior = Ok, gen = holder.nextGen())
        assertTrue(holder.compareAndSet(Ok, g2))
        assertFalse(holder.compareAndSet(g1, Ok))
        assertSame(g2, holder.states.value)
    }

    @Test
    fun staleImportGen_doesNotClearTheNewerGate() {
        val holder = DatabaseHealthState()
        assertTrue(holder.compareAndSet(Unchecked, Ok))
        val g1 = Importing(prior = Ok, gen = holder.nextGen())
        assertTrue(holder.compareAndSet(Ok, g1))
        assertTrue(holder.compareAndSet(g1, Ok))
        val g2 = Importing(prior = Ok, gen = holder.nextGen())
        assertTrue(holder.compareAndSet(Ok, g2))
        assertFalse(holder.compareAndSet(g1, Ok))
        assertSame(g2, holder.states.value)
    }
}
