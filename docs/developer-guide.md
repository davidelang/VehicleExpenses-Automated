# Developer Guide

## Models
- FuelEntry (replaces FuelFill/FuelFillup)
- ExpenseEntry (replaces Expense)

## DAOs
- FuelEntryDao
- ExpenseEntryDao
- VehicleDao

## Repositories
- FuelEntryRepository
- ExpenseEntryRepository
- VehicleRepository

## DatabaseModule
Provides all DAOs from AppDatabase.

## SyncWorker
Uses GoogleSheetsClient to push FuelEntry and ExpenseEntry to Sheets.

Run `./gradlew clean build` after changes.
