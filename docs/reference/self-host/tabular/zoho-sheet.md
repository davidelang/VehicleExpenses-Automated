# Get started: Zoho Sheet (tabular)

**In app:** Spreadsheet → Other → Collaborative sheets → **Zoho Sheet**

Zoho Sheet exposes a **grid + tabular Data API** (OAuth 2). The app maps logical tabs to **worksheet names** inside one workbook.

## OAuth setup

1. Register a client at [Zoho API Console](https://accounts.zoho.com/developerconsole).  
2. For mobile sign-in, use a **client-based** app with redirect URI:  
   `vehicleexpenses://zoho/oauth`  
3. Scopes: `ZohoSheet.dataAPI.READ`, `ZohoSheet.dataAPI.UPDATE`  
4. Optional: server-based client with **offline** access to obtain a **refresh token** (store client secret only if you accept power-user risk on-device).

## Workbook layout

Create a Zoho workbook with worksheets:

| Logical tab | Worksheet name (example) |
|-------------|--------------------------|
| Vehicles | `Vehicles` |
| Expenses | `Expenses` |
| Fuel - {name} | `Fuel - Honda` |

Row 1 = header row matching `TabularSchema` headers (**Sync ID** in column A).

## Values to enter in the app

| Field | Typical value |
|-------|----------------|
| Workbook resource id | From URL `.../open/<rid>` |
| OAuth client id | From Zoho API Console |
| Client secret | Optional — enables refresh-token flow |
| Access / refresh token | Filled by **Sign in with Zoho** or pasted manually |
| Worksheet map | Vehicles / Expenses / fuel lines `Fuel - Name=SheetName` |

**Test connection** writes a probe row with Sync ID `.ve_probe_<timestamp>` → read → delete best-effort.

## Tips

- Re-sign in when the access token expires unless refresh token + secret are configured.  
- API host may differ by region (`sheet.zoho.com`, `.eu`, `.in`, …) — sign-in captures `api_domain`.  
- Per-minute rate limits apply; background sync uses the same coordinator as other providers.

## Vendor docs

- Zoho Sheet Data API v2: https://www.zoho.com/sheet/help/api/v2/