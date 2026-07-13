# Get started: EtherCalc (tabular)

**In app (planned):** Spreadsheet sync → **EtherCalc**

EtherCalc is a real-time multi-user web spreadsheet — a self-host peer to “edit in the browser while apps sync.”

## Minimal server setup

**Docker Compose (trusted LAN / VPN) — from upstream:**

```bash
git clone https://github.com/audreyt/ethercalc
cd ethercalc
docker compose up -d
```

Default often listens on `http://localhost:8000` (confirm in current upstream README). Persist data as documented by the project (e.g. local data volume).

For internet exposure: put TLS reverse proxy in front; do not leave open anonymous edit without a trust model.

## Values to enter in the app (planned fields)

| Field | Typical value |
|-------|----------------|
| Server base URL | `https://calc.example.com` |
| Room / sheet id(s) | One room per logical tab **or** naming scheme agreed with app (e.g. `ve-vehicles`, `ve-expenses`, `ve-fuel-Honda`) |
| Auth token | If your deployment requires it; often open on LAN |

**Test connection** after bind.

## Tips

- Create rooms once in the browser, then point the app at the same room IDs.  
- Upstream has evolved (Cloudflare Workers vs classic Node); use the install path matching the version you deploy.  
- Not a file/CSV drop — the app will use EtherCalc’s HTTP/API surface when implemented.

## Vendor docs

- GitHub / self-host: https://github.com/audreyt/ethercalc  
- Project docs: https://docs.ethercalc.net/  
