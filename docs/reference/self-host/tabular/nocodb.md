# Get started: NocoDB (tabular)

**In app:** Spreadsheet → Other → Row databases → **NocoDB**

NocoDB turns a database into a smart spreadsheet UI; multi-user row editing.

## Minimal server setup

1. Self-host per NocoDB docs (Docker / auto-upstall):  
   https://nocodb.com/docs/self-hosting  
2. Create project + tables for **Vehicles**, **Expenses**, and **Fuel - {name}** (or map fuel tables later).  
3. Add columns matching app headers — first column **Sync ID** (SingleLineText). Include **Vehicle Sync IDs** on Expenses.  
4. Create an **API token** with appropriate scopes.  
5. Note base URL e.g. `https://nocodb.example.com` and **table ids** from the API or table settings.

## Values to enter in the app

| Field | Typical value |
|-------|----------------|
| Base URL | `https://nocodb.example.com` |
| API token | xc-token from NocoDB |
| Project / base id | Optional reference |
| Vehicles table id | From NocoDB table settings |
| Expenses table id | Table id |
| Fuel tab ids | `Fuel - Honda=<tableId>` (one per line) |

**Test connection** writes probe Sync ID `.ve_probe_<timestamp>` → list → read → delete best-effort.

## Vendor docs

- Self-hosting: https://nocodb.com/docs/self-hosting  
- Getting started: https://nocodb.com/docs/product-docs/getting-started  
- GitHub: https://github.com/nocodb/nocodb