# Get started: Collabora Online (tabular)

**In app:** Spreadsheet → Other → Collaborative sheets → **Collabora** *(coming soon — app sync not wired)*

Collabora Online (and **CODE** — Collabora Online Development Edition) provides LibreOffice-based collaborative editing in the browser. App tabular sync is deferred (WOPI/document model) — pair with a row DB for mobile sync.

## Minimal server setup

1. Deploy CODE or Collabora Online behind HTTPS (Docker images common).  
2. Integrate with a file host (Nextcloud is a common pairing) **or** whatever storage the future app connector expects.  
3. Configure WOPI / integration secrets per Collabora SDK docs.

## Values to enter in the app (planned)

| Field | Typical value |
|-------|----------------|
| Collabora URL | `https://collabora.example.com` |
| Integration / WOPI settings | Per deployment |
| Document reference | TBD with app backend |

## Tips

- Heavier ops than EtherCalc or Baserow.  
- Cheatsheet is **ops-oriented** until the app backend exists.

## Vendor docs

- Collabora Online SDK: https://sdk.collaboraonline.com/  
- CODE: https://www.collaboraonline.com/code/  
