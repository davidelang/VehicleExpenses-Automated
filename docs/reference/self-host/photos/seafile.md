# Get started: Seafile photo backup

**In app:** Photo backup → **Other** → Self-hosted / protocols → **seafile**

## Minimal server setup

1. Install Seafile Community/Professional per vendor manual.  
2. Create a library e.g. `VehicleExpenses`.  
3. Create folder `photos` inside the library.  
4. Note the **server URL**, account email, and password (or library token if you use encrypted libraries — see rclone Seafile docs).

## Values to enter in the app

| Field | Typical value |
|-------|----------------|
| url | `https://seafile.example.com` |
| user | email |
| pass | password |
| Path / library | Follow rclone Seafile modes: root-of-server vs fixed library (see vendor docs) |
| Path prefix | `photos` or `VehicleExpenses/photos` |

**Test connection** after save.

## Tips

- rclone documents two modes (server root vs specific library); encrypted libraries usually need the library-bound mode.  
- Prefer HTTPS.

## Vendor docs

- rclone Seafile: https://rclone.org/seafile/  
- Seafile manual: https://manual.seafile.com/  
