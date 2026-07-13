# Get started: Airtable (tabular)

**In app:** Spreadsheet → Other → Row databases → **Airtable**

Airtable is a cloud row database with spreadsheet-like UI and strong multi-user editing.

## Minimal setup

1. Create an Airtable base with tables for **Vehicles**, **Expenses**, and per-vehicle **Fuel - {name}** tabs (or add fuel table ids later in the app).
2. Create fields matching app headers — first column **Sync ID** (text), then remaining columns per `TabularSchema` (see Baserow cheatsheet field list).
3. Create a **personal access token** (PAT) with `data.records:read` and `data.records:write` on the base.
4. Note your **base id** (starts with `app…`) and each **table id** or table name as used in the API.

## Values to enter in the app

| Field | Typical value |
|-------|----------------|
| API URL | Leave blank (uses `api.airtable.com`) |
| Personal access token | PAT from Airtable developer hub |
| Base id | `appXXXXXXXX` |
| Vehicles table id | Table name or id from API |
| Expenses table id | Table name or id |
| Fuel tab ids | `Fuel - Honda=tblXXXXXXXX` (one per line) |

**Test connection** writes a probe row with Sync ID `.ve_probe_<timestamp>` to the Vehicles table, lists, reads, then deletes best-effort.

## Tips

- Field names must match app headers exactly (e.g. `Sync ID`, `Vehicle Sync IDs`, `Landmark Text Blocks JSON`).
- PATs are secrets — stored app-private only; never logged.
- Prefer HTTPS (Airtable cloud is HTTPS-only).

## Vendor docs

- Personal access tokens: https://airtable.com/developers/web/api/authentication  
- API reference: https://airtable.com/developers/web/api/introduction