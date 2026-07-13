# Get started: WebDAV photo backup

**In app:** Photo backup → **Other** → Self-hosted / protocols → **webdav**

WebDAV is the usual way to use **Nextcloud**, **ownCloud**, or many NAS web interfaces as photo storage.

## Minimal server setup (Nextcloud example)

1. Install Nextcloud (vendor docs below).  
2. Create a user dedicated to the app (recommended).  
3. Create folder e.g. `VehicleExpenses/photos` in Files.  
4. Create an **app password** (Settings → Security) — do not use the login password if app passwords exist.

## Values to enter in the app (rclone webdav)

| Field | Typical value |
|-------|----------------|
| URL | `https://cloud.example.com/remote.php/dav/files/USERNAME/` (Nextcloud) |
| Vendor | `nextcloud` if listed; else `other` |
| User | Nextcloud username |
| Pass | **App password** |
| Path prefix | `VehicleExpenses/photos` (or folder under the DAV root) |

Then **Test connection** (write → list → read).

## Tips

- Prefer HTTPS.  
- Reverse proxies must allow WebDAV methods (`PROPFIND`, `PUT`, `GET`, `DELETE`, …).  
- Path prefix is relative to the WebDAV root you configured.

## Vendor docs (authoritative)

- rclone WebDAV: https://rclone.org/webdav/  
- Nextcloud WebDAV access: https://docs.nextcloud.com/server/latest/user_manual/en/files/access_webdav.html  
