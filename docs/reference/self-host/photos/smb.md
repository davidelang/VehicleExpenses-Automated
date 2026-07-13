# Get started: SMB / CIFS photo backup

**In app:** Photo backup → **Other** → Self-hosted / protocols → **smb**

Typical for **Windows shares**, **Samba** on Linux, and many home NAS devices.

## Minimal server setup

1. Create a share (e.g. `VehicleExpenses`) with read/write for a dedicated user.  
2. Create folder `photos` under the share.  
3. Ensure the phone can reach the host (LAN or VPN). SMB is often **not** safe on the open internet without VPN.

## Values to enter in the app

| Field | Typical value |
|-------|----------------|
| host | NAS hostname or IP |
| user / pass | Share credentials |
| domain | Workgroup/domain if required (often blank or `WORKGROUP`) |
| Path prefix | `photos` or `VehicleExpenses/photos` depending on share root |

**Test connection** after save.

## Tips

- Use VPN (WireGuard/Tailscale) for remote access.  
- SMBv1 is obsolete; keep the server on modern SMB.

## Vendor docs

- rclone SMB: https://rclone.org/smb/  
- Samba: https://www.samba.org/  
