# Get started: Supabase (self-hosted tabular)

**In app:** Spreadsheet → Other → App backends → **Supabase**

Supabase self-host gives Postgres + Auth + REST/Realtime. Strong concurrency; weak “open in Excel” story unless you add a separate grid.

## Minimal server setup

1. Follow official self-hosting guide:  
   https://supabase.com/docs/guides/self-hosting  
2. Bring up stack (Docker Compose typically).  
3. Create Postgres tables `vehicles`, `expenses`, `fuel_*` with columns named as app headers (quote mixed-case: `"Sync ID"`, `"Vehicle Sync IDs"`, …).  
4. Enable Row Level Security policies for the app role.  
5. Create an **anon** or restricted **service** API key (prefer least privilege for mobile).  
6. Note project **API URL** (e.g. `https://supabase.example.com`).

## Values to enter in the app

| Field | Typical value |
|-------|----------------|
| Base URL | `https://supabase.example.com` (PostgREST root) |
| API key | anon or service role key |
| Vehicles table id | Postgres table name (e.g. `vehicles`) |
| Expenses table id | Table name |
| Fuel tab ids | `Fuel - Honda=fuel_honda` (one per line) |

**Test connection** probe contract: write → list → read → delete best-effort.

## Tips

- Self-host is ops-heavy vs hosted Supabase.  
- Do not embed unrestricted service_role keys in mobile clients without a BFF.

## Vendor docs

- Self-hosting: https://supabase.com/docs/guides/self-hosting