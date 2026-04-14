package com.davidlang.vehicleexpensesautomated.ui.util

/**
 * Registry for managing active image alignment engines.
 */
object AlignmentRegistry {
    private val engines = mutableListOf<AlignmentEngine>()

    init {
        setupDefaultEngines()
    }

    fun register(engine: AlignmentEngine) {
        if (engines.none { it.name == engine.name }) {
            engines.add(engine)
        }
    }

    fun getActiveEngines(): List<AlignmentEngine> = engines

    private fun setupDefaultEngines() {
        register(OrbAffineEngine())
        register(AnchorTriangulationEngine())
        register(HubEngine())
    }
}
