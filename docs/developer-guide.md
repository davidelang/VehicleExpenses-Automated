# Developer Guide

## Data Structures
- `Vehicle` (Room entity)
- `Expense`
- `FuelFill`

## Process Flows
1. UI → ViewModel → Repository → Room
2. Sync → GoogleSheetsClient (write + read/parse)
3. Background → WorkManager → SyncWorker

Full architecture: https://github.com/davidelang/VehicleExpenses-Automated/tree/master/docs/developer-guide.md
