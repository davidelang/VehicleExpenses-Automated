# Self-hosted sync setup docs

Operator guides for **self-hosted** tabular and photo (image) sync targets in Vehicle Expenses Automated.

| Path | Role |
|------|------|
| [INDEX.md](INDEX.md) | Hub: all self-host options + deep links into cheatsheets |
| [photos/](photos/) | Minimal cheatsheets for rclone-backed image targets |
| [tabular/](tabular/) | Minimal cheatsheets for concurrent spreadsheet/row backends |
| [vendor-links/VENDOR_LINKS.md](vendor-links/VENDOR_LINKS.md) | Canonical vendor documentation URLs (refresh when cheatsheets age) |

## Product mapping

### Photo backup (images)

| App surface | Self-host options |
|-------------|-------------------|
| **S3** (top tier) | MinIO and other S3-compatible servers via Endpoint |
| **Other → Self-hosted / protocols** | WebDAV, SFTP, FTP, SMB, Seafile |

### Spreadsheet sync (tabular)

| App surface | Self-host options |
|-------------|-------------------|
| **EtherCalc** (top tier) | Self-hosted EtherCalc |
| **Other** | Baserow, NocoDB, OnlyOffice, Collabora, PocketBase, Supabase (self-host) |

Google Sheets / Drive / OneDrive / commercial S3 / Zoho / Airtable / Firebase cloud are **not** in these cheatsheets (vendor cloud UIs).

## Recovery note

Self-hosted tabular and photo targets often **lack** Google Sheets–style **version history**. If sync is interrupted mid-write, rely on the **next successful sync** (local Room + LWW merge) or manual edits on the server. See [../SYNC_BEHAVIOR.md](../SYNC_BEHAVIOR.md).

## In-app links

Open **Help → Self-hosted sync setup** or use **Setup help** on photo/spreadsheet destination screens. Links use GitHub blob URLs under `docs/reference/self-host/`.