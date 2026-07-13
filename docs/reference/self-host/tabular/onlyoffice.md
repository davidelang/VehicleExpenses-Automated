# Get started: ONLYOFFICE (tabular)

**In app:** Spreadsheet → Other → Collaborative sheets → **OnlyOffice** *(coming soon — app sync not wired)*

ONLYOFFICE Document Server provides collaborative editing of Office documents, including spreadsheets. Heavier than EtherCalc; integration is **document/collab oriented**. There is no headless REST row API for mobile sync without a full WOPI host — use Baserow/NocoDB/Zoho Sheet for app multi-writer sync.

## Minimal server setup

1. Deploy **ONLYOFFICE Docs** (Document Server) per Help Center.  
2. Ensure JWT or integration secrets are set for any connector.  
3. Host workbooks the app can open via supported API (exact binding TBD when backend is implemented).  
4. TLS reverse proxy recommended.

## Values to enter in the app (planned)

| Field | Typical value |
|-------|----------------|
| Docs server URL | `https://onlyoffice.example.com` |
| JWT secret / integration key | From Document Server config |
| Workbook / file reference | Per integration design |

## Tips

- Confirm the app backend is **implemented** before relying on this for production sync.  
- This cheatsheet is **server install only** until TabularShare OnlyOffice backend ships.

## Vendor docs

- ONLYOFFICE Help Center: https://helpcenter.onlyoffice.com/  
