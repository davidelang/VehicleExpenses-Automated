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

### Optional: write sandbox (bubblewrap and/or Landlock)

Linux only; **optional**. `libpin-sandbox` confines **mutation** of pin steps (read/exec stay open for toolchains):

| Operation | Writable surface |
|-----------|------------------|
| materialize / status | `third_party/<lib>/` |
| patches + build | `third_party/<lib>/src/` |
| collect artifacts | `third_party/<lib>/artifact/` |

- **bubblewrap** (if installed): outer RO root + RW hole  
- **Landlock** (if kernel supports): LSM write scope; stacks under bwrap  
- **Neither:** commands still work  

```bash
sudo apt install bubblewrap   # optional
./third_party/libpin-landlock --status
```

Disable: `LIBPIN_NO_BWRAP=1`, `LIBPIN_NO_LANDLOCK=1`, or `--no-bwrap` / `--no-landlock`.

---
## Repository History
The full source code and application history are managed via Git worktrees. Navigate to `master/` to see the primary application code.