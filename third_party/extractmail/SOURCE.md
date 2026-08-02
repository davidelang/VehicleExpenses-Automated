# extractmail — third_party consumer pin (VehicleExpenses)

## Modes (`fetch-deps`)

| Command | Meaning |
|---------|---------|
| `fetch-deps extractmail` or `fetch-deps ro` | **ro** pin @ lock `git_sha`: patches, then **chmod a-w**. Detached. Do **not** commit here. |
| `fetch-deps rw extractmail` | **rw** branch default = **current VE branch**, base = lock sha. Writable; library PR then pin promote. |
| `fetch-deps status` | mode / HEAD / lock / dirty |
| `fetch-deps refresh` | Explicitly advance pin to library master tip (human) |
| `fetch-deps upgrade extractmail --ref TAG` | Try ref, build, update artifact + lock |

**Develop library-only** under `~/git/extractmail/` — not from a different project's agent-N into VE.

**Agents have no GitHub SSH keys** — fetch uses local `GIT_HOME` or HTTPS.

Policy: `docs/reference/FIRST_PARTY_LIBS.md`, design research note under `dev-ai-interaction/research/third-party-fetch-deps-design-*`.
