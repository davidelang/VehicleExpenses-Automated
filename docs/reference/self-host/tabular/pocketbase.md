# Get started: PocketBase (tabular)

**In app:** Spreadsheet → Other → App backends → **PocketBase**

PocketBase is a single-binary backend (SQLite + realtime + auth). Excellent multi-writer **rows**; not a spreadsheet UI.

## Minimal server setup

1. Download PocketBase for your OS: https://pocketbase.io/docs/  
2. Run `./pocketbase serve` (or systemd + reverse proxy with TLS).  
3. Open Admin UI → create collections named to match logical tabs (or map collection ids in the app):  
   - `Vehicles`, `Expenses`, `Fuel - {name}`  
4. Add text fields matching app headers (**Sync ID** first).  
5. Create an admin auth token or user with CRUD access.  
6. Note base URL e.g. `https://pb.example.com` and **collection name or id** per tab.

## Values to enter in the app

| Field | Typical value |
|-------|----------------|
| Base URL | `https://pb.example.com` |
| Admin auth token | Bearer token from PocketBase auth |
| Vehicles table id | Collection name or id |
| Expenses table id | Collection name or id |
| Fuel tab ids | `Fuel - Honda=col_id` (one per line) |

**Test connection** probe contract: write → list → read → delete best-effort.

## Vendor docs

- PocketBase docs: https://pocketbase.io/docs/