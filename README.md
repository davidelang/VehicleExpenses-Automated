# Vehicle Expenses Automated (Orchestration Root)

This is the **orchestration root** for a multi-agent development environment.

## 🚀 Quick Start
For instructions on how to manage agents, create branches, and merge work, see:
**[README-multi-agent.md](README-multi-agent.md)**

## 📂 Layout Overview
- **`master/`**: Main development worktree.
- **`agent-N/`**: Dynamic worktrees for feature agents.
- **`.gemini/`**: Shared brain (policies and rules).

## Third-party pins (`third_party/`)

Prebuilt libraries (OpenCV, rclone, remotetable, extractmail, …) are **pinned** under `third_party/<lib>/` with `libpin.toml` + committed `artifact/`. Rebuild with:

```bash
./third_party/fetch-deps ro <lib>
./third_party/fetch-deps build <lib>
```

Details: [`third_party/README.md`](third_party/README.md), [`docs/reference/THIRD_PARTY_PIN_BUILDS.md`](docs/reference/THIRD_PARTY_PIN_BUILDS.md), host setup: [`docs/ENVIRONMENT_SETUP.md`](docs/ENVIRONMENT_SETUP.md).

### Optional: bubblewrap write sandbox

If **`bwrap` (bubblewrap)** is installed, `fetch-deps` / `get-artifacts` confine writes automatically:

| Operation | Writable surface |
|-----------|------------------|
| materialize / status | `third_party/<lib>/` only |
| patches + build | `third_party/<lib>/src/` only |
| collect artifacts | `third_party/<lib>/artifact/` only |

This limits blast radius if a pin build or patch misbehaves (no writing into the app tree, home dir, or host SDK). **Bubblewrap is optional** — without it, the same commands work unsandboxed. Disable explicitly: `LIBPIN_NO_BWRAP=1` or `./third_party/fetch-deps --no-bwrap …`.

```bash
# Debian/Ubuntu
sudo apt install bubblewrap
```

---
## Repository History
The full source code and application history are managed via Git worktrees. Navigate to `master/` to see the primary application code.