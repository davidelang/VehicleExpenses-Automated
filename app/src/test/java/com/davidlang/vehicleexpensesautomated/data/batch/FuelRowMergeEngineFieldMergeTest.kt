package com.davidlang.vehicleexpensesautomated.data.batch

import com.davidlang.vehicleexpensesautomated.data.model.FuelEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [FuelRowMergeEngine.planMerge] — app field-merge (Pass 2 after library LWW).
 *
 * High-value rules used post-sync:
 * 1) Odo-only + pump cost/vol within 15m → absorb (one update, hardDeletes loser)
 * 2) Soft-delete list non-empty on absorb
 * 3) Two complete fulls same odo → CONFLICT pending, no absorb
 * 4) Outside MERGE_WINDOW_MS → no cluster absorb
 * 5) MERGE_EXEMPT set covers cluster → no absorb
 * 6) No-op for single live row / empty list
 */
class FuelRowMergeEngineFieldMergeTest {

    private val t0 = 1_700_000_000_000L

    private fun entry(
        id: Long,
        vehicleId: Int = 1,
        odometer: Int = 0,
        gallons: Double = 0.0,
        cost: Double = 0.0,
        timestamp: Long,
        syncId: String = "s$id",
        isPartialFill: Boolean = false,
        economyIgnored: Boolean = false,
        deleted: Boolean = false,
    ): FuelEntry = FuelEntry(
        id = id,
        vehicleId = vehicleId,
        odometer = odometer,
        gallons = gallons,
        cost = cost,
        currency = "USD",
        timestamp = timestamp,
        isPartialFill = isPartialFill,
        economyIgnored = economyIgnored,
        deleted = deleted,
        syncId = syncId,
        updatedAt = timestamp,
    )

    /** S-F1: dash odo-only + pump cost/vol within window → field-complete absorb. */
    @Test
    fun absorb_partialOdoAndPumpWithinWindow() {
        val odoOnly = entry(id = 1, odometer = 10000, timestamp = t0, syncId = "dash-1")
        val pump = entry(
            id = 2,
            odometer = 0,
            gallons = 12.5,
            cost = 45.0,
            timestamp = t0 + 60_000L, // +1 min
            syncId = "pump-1",
        )
        val plan = FuelRowMergeEngine.planMerge(listOf(odoOnly, pump))
        assertFalse("expected absorb plan", plan.isEmpty())
        assertEquals(1, plan.updates.size)
        val survivor = plan.updates.single()
        assertEquals(10000, survivor.odometer)
        assertEquals(12.5, survivor.gallons, 0.001)
        assertEquals(45.0, survivor.cost, 0.001)
        assertEquals(1, plan.hardDeletes.size)
        assertTrue(plan.hardDeletes.any { it.id == 1L || it.id == 2L })
        assertTrue(plan.hardDeletes.none { it.id == survivor.id })
    }

    /** S-F2: hardDeletes carries absorbed loser (sync path soft-deletes these). */
    @Test
    fun absorb_listsLoserInHardDeletes() {
        val a = entry(id = 10, odometer = 5000, timestamp = t0, syncId = "a")
        val b = entry(id = 11, gallons = 8.0, cost = 30.0, timestamp = t0 + 120_000L, syncId = "b")
        val plan = FuelRowMergeEngine.planMerge(listOf(a, b))
        assertEquals(1, plan.hardDeletes.size)
        val loserId = plan.hardDeletes.single().id
        assertTrue(loserId == 10L || loserId == 11L)
        assertEquals(1, plan.updates.size)
        assertTrue(plan.updates.single().id != loserId)
    }

    /** S-F3: two complete fulls same odo → conflict pending, no silent absorb. */
    @Test
    fun twoCompleteFullsSameOdo_conflictNoAbsorb() {
        val f1 = entry(
            id = 20,
            odometer = 1000,
            gallons = 10.0,
            cost = 40.0,
            timestamp = t0,
            syncId = "full-1",
        )
        val f2 = entry(
            id = 21,
            odometer = 1000,
            gallons = 10.5,
            cost = 41.0,
            timestamp = t0 + 30_000L,
            syncId = "full-2",
        )
        val plan = FuelRowMergeEngine.planMerge(listOf(f1, f2))
        assertTrue(
            "two complete fulls must not hard-delete either",
            plan.hardDeletes.isEmpty(),
        )
        assertTrue("expect CONFLICT_ODO pending", plan.newPending.isNotEmpty())
        assertTrue(plan.newPending.any { it.kind == BatchPendingKind.CONFLICT_ODO })
        assertTrue(plan.hardDeletes.isEmpty())
    }

    /** S-F4: rows outside 15-minute window do not field-merge. */
    @Test
    fun outsideMergeWindow_noAbsorb() {
        val gap = FuelRowMergeEngine.MERGE_WINDOW_MS + 60_000L
        val odoOnly = entry(id = 30, odometer = 2000, timestamp = t0, syncId = "far-dash")
        val pump = entry(
            id = 31,
            gallons = 9.0,
            cost = 35.0,
            timestamp = t0 + gap,
            syncId = "far-pump",
        )
        val plan = FuelRowMergeEngine.planMerge(listOf(odoOnly, pump))
        assertTrue(
            "outside window should not absorb",
            plan.hardDeletes.isEmpty() && plan.updates.isEmpty(),
        )
    }

    /** S-F5: MERGE_EXEMPT member set covering cluster → leave as-is. */
    @Test
    fun mergeExempt_suppressesAbsorb() {
        val odoOnly = entry(id = 40, odometer = 3000, timestamp = t0, syncId = "ex-dash")
        val pump = entry(
            id = 41,
            gallons = 7.0,
            cost = 28.0,
            timestamp = t0 + 45_000L,
            syncId = "ex-pump",
        )
        val plan = FuelRowMergeEngine.planMerge(
            listOf(odoOnly, pump),
            mergeExemptSets = listOf(setOf("ex-dash", "ex-pump")),
        )
        assertTrue(
            "exempt cluster must not absorb",
            plan.hardDeletes.isEmpty(),
        )
    }

    /** S-F6: empty / single row → empty plan. */
    @Test
    fun emptyOrSingle_noOp() {
        assertTrue(FuelRowMergeEngine.planMerge(emptyList()).isEmpty())
        val one = entry(id = 50, odometer = 100, gallons = 1.0, cost = 5.0, timestamp = t0)
        assertTrue(FuelRowMergeEngine.planMerge(listOf(one)).isEmpty())
    }

    /** S-F7: isClusterMergeExempt helper. */
    @Test
    fun isClusterMergeExempt_subsetMatch() {
        val cluster = setOf("a", "b", "c")
        assertTrue(
            FuelRowMergeEngine.isClusterMergeExempt(
                cluster,
                listOf(setOf("a", "b")),
            ),
        )
        assertFalse(
            FuelRowMergeEngine.isClusterMergeExempt(
                cluster,
                listOf(setOf("a", "z")),
            ),
        )
        assertFalse(
            FuelRowMergeEngine.isClusterMergeExempt(
                emptySet(),
                listOf(setOf("a")),
            ),
        )
    }

    /** S-F8: mergeFields fills odo from one side and cost/vol from the other. */
    @Test
    fun mergeFields_combinesPartials() {
        val odo = entry(id = 60, odometer = 9999, timestamp = t0, syncId = "m1")
        val pump = entry(
            id = 61,
            gallons = 11.0,
            cost = 50.0,
            timestamp = t0 + 10_000L,
            syncId = "m2",
        )
        val merged = FuelRowMergeEngine.mergeFields(odo, pump, preferLatestTs = true)
        assertEquals(9999, merged.odometer)
        assertEquals(11.0, merged.gallons, 0.001)
        assertEquals(50.0, merged.cost, 0.001)
        assertEquals(t0 + 10_000L, merged.timestamp)
    }
}
