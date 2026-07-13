# Get started: FTP photo backup

**In app:** Photo backup → **Other** → Self-hosted / protocols → **ftp**

Prefer **SFTP** or **WebDAV over HTTPS** when you can. FTP is offered for legacy NAS devices.

## Minimal server setup

1. Enable FTP (or FTPS) on the NAS/server.  
2. Create a user limited to a folder e.g. `VehicleExpenses/photos`.  
3. Prefer **FTPS** (TLS) if the server supports it.

## Values to enter in the app

| Field | Typical value |
|-------|----------------|
| host | FTP hostname |
| user / pass | FTP credentials |
| Path prefix | `VehicleExpenses/photos` |

**Test connection** after save.

## Tips

- Plain FTP sends passwords in clear text — avoid on untrusted networks.  
- If FTPS fails, check passive mode / firewall (vendor FTP docs).

## Vendor docs

- rclone FTP: https://rclone.org/ftp/  
