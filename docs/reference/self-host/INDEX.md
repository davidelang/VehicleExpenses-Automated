# Self-hosted sync — setup index

Use this page from **Help** or when choosing a self-hosted endpoint. Each cheatsheet is a **minimal get-started** for Vehicle Expenses; vendor links are authoritative when our notes go stale.

## Photo backup (images)

| Target | In-app place | Get started (our cheatsheet) | Vendor docs |
|--------|--------------|------------------------------|-------------|
| **WebDAV** (Nextcloud, ownCloud, …) | Photo → Other → Self-hosted | [photos/webdav.md](photos/webdav.md) | [rclone WebDAV](https://rclone.org/webdav/) · [Nextcloud WebDAV](https://docs.nextcloud.com/server/latest/user_manual/en/files/access_webdav.html) |
| **SFTP** | Photo → Other → Self-hosted | [photos/sftp.md](photos/sftp.md) | [rclone SFTP](https://rclone.org/sftp/) |
| **FTP** | Photo → Other → Self-hosted | [photos/ftp.md](photos/ftp.md) | [rclone FTP](https://rclone.org/ftp/) |
| **SMB** (Samba / NAS share) | Photo → Other → Self-hosted | [photos/smb.md](photos/smb.md) | [rclone SMB](https://rclone.org/smb/) |
| **Seafile** | Photo → Other → Self-hosted | [photos/seafile.md](photos/seafile.md) | [rclone Seafile](https://rclone.org/seafile/) · [Seafile manual](https://manual.seafile.com/) |
| **MinIO / S3-compatible** | Photo → **S3** (endpoint field) | [photos/minio-s3-compatible.md](photos/minio-s3-compatible.md) | [MinIO docs](https://min.io/docs/minio/linux/index.html) · [rclone S3](https://rclone.org/s3/) |

Also see: [photos/README.md](photos/README.md)

## Spreadsheet sync (tabular, concurrent)

| Target | In-app place (planned) | Get started (our cheatsheet) | Vendor docs |
|--------|------------------------|------------------------------|-------------|
| **EtherCalc** | Spreadsheet → **EtherCalc** | [tabular/ethercalc.md](tabular/ethercalc.md) | [EtherCalc GitHub](https://github.com/audreyt/ethercalc) · [docs.ethercalc.net](https://docs.ethercalc.net/) |
| **Baserow** | Spreadsheet → Other → Row databases | [tabular/baserow.md](tabular/baserow.md) | [Baserow self-host](https://baserow.io/docs/installation%2Finstall-with-docker) · [API](https://baserow.io/docs/apis%2Frest-api) |
| **NocoDB** | Spreadsheet → Other → Row databases | [tabular/nocodb.md](tabular/nocodb.md) | [NocoDB self-host](https://nocodb.com/docs/self-hosting) · [Getting started](https://nocodb.com/docs/product-docs/getting-started) |
| **Firebase / Firestore** | Spreadsheet → Other → App backends | [tabular/firebase.md](tabular/firebase.md) | [Firestore REST](https://firebase.google.com/docs/firestore/use-rest-api) |
| **Zoho Sheet** | Spreadsheet → Other → Collaborative | [tabular/zoho-sheet.md](tabular/zoho-sheet.md) | [Zoho Sheet API v2](https://www.zoho.com/sheet/help/api/v2/) |
| **OnlyOffice** | Spreadsheet → Other → Collaborative (deferred) | [tabular/onlyoffice.md](tabular/onlyoffice.md) | [ONLYOFFICE Docs](https://helpcenter.onlyoffice.com/) |
| **Collabora Online** | Spreadsheet → Other → Collaborative (deferred) | [tabular/collabora.md](tabular/collabora.md) | [Collabora Online](https://sdk.collaboraonline.com/) · [CODE](https://www.collaboraonline.com/code/) |
| **PocketBase** | Spreadsheet → Other → App backends | [tabular/pocketbase.md](tabular/pocketbase.md) | [PocketBase docs](https://pocketbase.io/docs/) |
| **Supabase (self-host)** | Spreadsheet → Other → App backends | [tabular/supabase-selfhost.md](tabular/supabase-selfhost.md) | [Self-hosting](https://supabase.com/docs/guides/self-hosting) |

Also see: [tabular/README.md](tabular/README.md)

## App usage notes

1. Photos use **binary files** under a path prefix (e.g. `VehicleExpenses/photos`). Tabular uses **row/cell** APIs or collaborative sheets — not CSV file drop as live multi-writer.  
2. After server setup, use **Test connection** in the app (write → list → read).  
3. Prefer HTTPS and app passwords / API tokens; never reuse primary account passwords when the product supports tokens.  
4. Full vendor link table: [vendor-links/VENDOR_LINKS.md](vendor-links/VENDOR_LINKS.md)
