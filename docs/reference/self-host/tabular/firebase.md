# Get started: Firebase / Firestore (tabular)

**In app:** Spreadsheet → Other → App backends → **Firebase**

Firebase (Firestore) is a **document row store**, not a spreadsheet grid. The app maps each logical tab to a Firestore **collection** and upserts rows by **Sync ID**.

## Auth model (safe default)

| Approach | Use when |
|----------|----------|
| **Short-lived ID token** | User signs in with Firebase Auth (email/Google) and pastes a fresh ID token for testing or power users. |
| **Custom security rules** | Production: rules restrict reads/writes to the signed-in user's uid — **never** ship unrestricted service-account JSON in the APK. |

**Warning:** A service account key embedded in the app grants broad access to anyone who extracts the APK. Prefer user-scoped tokens + rules.

## Minimal setup

1. Create a Firebase project and enable **Cloud Firestore**.  
2. Deploy security rules that allow your auth model (e.g. `request.auth != null`).  
3. Create collections (or let the app create documents on first sync):  
   - `Vehicles`, `Expenses`, `Fuel - {vehicle name}` (one collection per fuel tab).  
4. Documents use **string fields** named exactly as app headers (**Sync ID** first). See `TabularSchema` in the app for the full list.

## Values to enter in the app

| Field | Typical value |
|-------|----------------|
| Project id | `my-vehicle-expenses` |
| ID token / access token | Bearer token from Firebase Auth (short-lived) |
| Vehicles collection | `Vehicles` |
| Expenses collection | `Expenses` |
| Fuel collections | `Fuel - Honda=honda_fuel` (one per line) |

**Test connection** writes probe Sync ID `.ve_probe_<timestamp>` → list → read → delete best-effort on the Vehicles collection.

## Tips

- Tokens expire — refresh before sync or re-paste.  
- Prefer HTTPS-only Firestore REST (`firestore.googleapis.com`).  
- Field names with spaces (e.g. **Sync ID**) are supported via Firestore REST.

## Vendor docs

- Firestore REST: https://firebase.google.com/docs/firestore/use-rest-api  
- Security rules: https://firebase.google.com/docs/firestore/security/get-started