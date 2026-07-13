# Get started: SFTP photo backup

**In app:** Photo backup → **Other** → Self-hosted / protocols → **sftp**

## Minimal server setup

1. Run OpenSSH (`sshd`) on a VPS or NAS with a user that can write a directory.  
2. Create directory e.g. `/home/ve/VehicleExpenses/photos` and grant that user write access.  
3. Prefer **SSH key** auth; password auth works but is weaker on the public internet.

## Values to enter in the app

| Field | Typical value |
|-------|----------------|
| host | `nas.example.com` or IP |
| user | SSH username |
| port | `22` (default) |
| pass | Password **or** leave empty if using key |
| key_file | Path to private key **on the phone** if using key auth (advanced) |
| Path prefix | `VehicleExpenses/photos` (relative to home or configured root) |

**Test connection** after save.

## Tips

- Expose SFTP only via VPN/Tailscale when possible.  
- Key files on Android are awkward; many users use password or import a desktop-built `rclone.conf`.

## Vendor docs

- rclone SFTP: https://rclone.org/sftp/  
