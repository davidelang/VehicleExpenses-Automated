---
type: implementation-reference
status: dynamic
ai_directive: "This is a downstream reference. It MUST be updated continuously to reflect the current state of the codebase. If you change a function or architecture described here, update this document in the same commit."
---

# Pump cost and volume classification

Live Quick Fill and the pump experiment publish `classifyWeighted` from their own copies (`QuickFillPumpCostVolChooser` and `ExperimentPumpCostVolChooser`). This object does not call the other copy. `classify` is the side record: Quick Fill records `costLinear` / `volumeLinear`, and the pump experiment records `costVolLinear_Paddle`. Neither copy reads the price-range settings.

The Y-band steps are the old path. They stay described in [../obsolete/PUMP_COST_VOLUME_CLASSIFIER_SPEC.md](../obsolete/PUMP_COST_VOLUME_CLASSIFIER_SPEC.md).
