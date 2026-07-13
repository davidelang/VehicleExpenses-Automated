# Get started: Baserow (tabular)

**In app:** Spreadsheet → Other → Row databases → **Baserow**

Baserow is a self-hostable **row database** with multi-user editing (Airtable-like), not a classic spreadsheet grid.

## Minimal server setup

1. Follow Baserow Docker install:  
   https://baserow.io/docs/installation%2Finstall-with-docker  
2. Create a workspace and database.  
3. Create tables in the Baserow UI (schema create requires JWT — **v1 app uses pre-created tables**):  
   - `Vehicles`, `Expenses`, `Fuel - {vehicle name}` (one table per fuel tab).  
4. Add **text** fields named exactly as app headers (first field **Sync ID**). Key vehicle/expense/fuel columns:

| Tab | Required field names (subset) |
|-----|-------------------------------|
| Vehicles | Sync ID, ID, Name, Make, Model, Year, …, Landmark Text Blocks JSON, Cloud Manifest, Origin Device ID, Updated At, Deleted, Deleted At |
| Expenses | Sync ID, Vehicle Sync ID, **Vehicle Sync IDs**, Vehicle ID, Date, Amount, Currency, …, Photo URL |
| Fuel - * | Sync ID, Vehicle Sync ID, Vehicle ID, Odometer, Gallons, Cost, Currency, Timestamp, … |

See `TabularSchema` in the app for the full header list.

5. Create a **database token** with create/read/update/delete on those tables.

## Values to enter in the app

| Field | Typical value |
|-------|----------------|
| Base URL | `https://baserow.example.com` |
| API token | Database token (data API) |
| Database id | Optional reference |
| Vehicles table id | Numeric table id from Baserow URL/API |
| Expenses table id | Numeric table id |
| Fuel tab ids | `Fuel - Honda=12` (one per line) |

**Test connection** writes probe Sync ID `.ve_probe_<timestamp>` → list → read → delete best-effort on the Vehicles table.

## Tips

- Prefer HTTPS.  
- Tokens are secrets — app-private storage only.  
- Use `user_field_names` API — field **names** must match headers, not internal ids.

## Vendor docs

- Install with Docker: https://baserow.io/docs/installation%2Finstall-with-docker  
- REST API: https://baserow.io/docs/apis%2Frest-api