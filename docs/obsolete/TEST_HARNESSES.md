# Obsolete Test Harnesses

This document archives the history of the complex multi-algorithm test harnesses that were used during the R&D phase of the `VehicleExpenses-Automated` project.

## 1. Multi-Engine Discovery Harness (`OcrHarness.kt`)
**Last Used Commit:** c51809dc
**Description:** `runDiscovery()` previously executed a list of OCR engines (ML Kit, Paddle-Lite, Paddle-ML-Hybrid) in serial sequence. It aggregated their results into a `Map<String, OcrResult>` and synchronized them into normalized JSON manifests.
**Reason for Removal:** Once ML Kit was proven to be the universally superior discovery engine, maintaining the overhead of executing and mapping multiple engines for every photo became an unnecessary performance bottleneck in the production pipeline. The code was simplified to execute a single, straight-line pass with ML Kit.

## 2. Multi-Strategy Identity & Alignment Loops (`ExperimentAlignmentScreen.kt`)
**Last Used Commit:** c51809dc
**Description:** The alignment experiment report generator originally featured massively complex, nested `forEach` loops. It exhaustively tested every vehicle against every discovery engine, every identity strategy (Feature, Arg, Embedding, Consensus, Tiered, Veto), and every alignment algorithm (ORB, Anchor-Tri). It generated massive HTML tables with dynamically injected columns (e.g., `ML Kit (Tri)`, `Paddle-Lite (Tri)`).
**Reason for Removal:** We concluded our initial phase of broad experimentation, having definitively eliminated most of the strategies we were testing. The code was simplified down to the single, proven use case: `ML Kit Discovery -> Veto Identity -> Anchor-Triangulation Alignment`.
**Future Note:** A similar, but more focused, multi-algorithm reporting structure will likely be reintroduced in the future when we begin testing new, specific variations of algorithms in parallel. The historical commits (`c51809dc` and prior) serve as a reference implementation for generating dynamic, multi-column HTML/JSON reports.
