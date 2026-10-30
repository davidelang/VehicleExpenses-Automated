---
type: implementation-reference
status: dynamic
ai_directive: "This is a downstream reference. It MUST be updated continuously to reflect the current state of the codebase. If you change a function or architecture described here, update this document in the same commit."
---

# Vehicle Expenses Automated — Architecture

## Synchronization

CSV zip import and export go through `CsvManager` to `TabularShareApi` (`exportCsvZip` / `importCsvZip`), implemented by `CsvZipTabularBackend`. That path and spreadsheet sync (Google Sheets via `GoogleSheetsTabularBackend`, and the other tabular backends) share `TabularSchema`: the same tab names, headers, and row encode/decode. Exported CSV rows are the canonical sheet rows for that schema. A live sheet may keep an older header order; CSV export uses the canonical header order.
