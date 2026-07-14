# Obsolete Identity Algorithms

This document archives the history and rationale for removing various identity algorithms that were used to match a dashboard photo to a specific vehicle's reference profile.

## 1. Feature (ORB)
**Last Used Commit:** `c51809dc` (`obsolete-IDENTITY_ALGORITHMS`)
**Description:** `FeatureIdentityEngine`. Attempted to identify the vehicle by counting the number of good ORB feature matches between the query photo and the reference photo.
**Reason for Removal:** Slow execution time (often >1000ms per vehicle) and highly inaccurate. Dashboard layouts are too similar visually (black plastic, white numbers, red needles), leading to massive false-positive matches across different vehicles.

## 2. Arg (Argument) & Embedding
**Last Used Commit:** `c51809dc` (`obsolete-IDENTITY_ALGORITHMS`)
**Description:** 
- `ArgIdentityEngine`: Calculated a simple intersection-over-union percentage of overlapping landmark words.
- `EmbeddingIdentityEngine`: Calculated the text density and similarity of the landmark sets.
**Reason for Removal:** Both achieved "fair" accuracy (around 83% in benchmarks). However, they frequently failed on subtle distinctions (e.g., distinguishing two identical Honda models where the only difference was the presence of an "ECO" light). The **Veto Algorithm** decisively outperformed them by strictly focusing on disqualifying vehicles based on unique, non-overlapping trigger words.

## 3. Consensus & Tiered
**Last Used Commit:** `c51809dc` (`obsolete-IDENTITY_ALGORITHMS`)
**Description:** 
- `ConsensusIdentityEngine`: Attempted to average the scores of Feature, Arg, and Embedding to find a "majority vote" winner.
- `TieredIdentityEngine`: A cascading fallback mechanism that tried Veto first, then Embedding, then Arg, then Feature.
**Reason for Removal:** 
- **Consensus:** Mathematically combining unreliable algorithms resulted in even higher unreliability. If two bad algorithms outvoted one good one, the match failed.
- **Tiered:** The first tier (Veto) proved so overwhelmingly accurate (approaching 100% with the 1-vs-3+ Rescue Algorithm) that falling back to subsequent tiers became unnecessary noise. If Veto failed, the other tiers were practically guaranteed to fail as well.

## 4. Hardcoded
**Last Used Commit:** `c51809dc` (`obsolete-IDENTITY_ALGORITHMS`)
**Description:** `HardcodedIdentityEngine`. A development hack that bypassed identification entirely by looking up the correct answer in `ground_truth.json`.
**Reason for Removal:** Served its purpose for testing alignment algorithms in isolation, but became obsolete once the Veto algorithm matured into a fully autonomous, production-ready identification engine.
