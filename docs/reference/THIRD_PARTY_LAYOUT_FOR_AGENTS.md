# Agent handoff: `third_party/` and split libraries

**Read this first** on branch `email-connection` after the bootstrap commit that adds `third_party/` is present. Then read `docs/reference/FIRST_PARTY_LIBS.md`.

---

## What changed

VehicleExpenses is gaining a **`third_party/`** tree so the app can depend on **separate git repos** with clear pins (source SHA + committed artifacts), instead of only opaque prebuilts or VE-monorepo copies of shared logic.

| Directory | Separate repo (canonical) | Local multi-agent host |
|-----------|---------------------------|-------------------------|
| `third_party/remotetable/` | `git@github.com:davidelang/remotetable.git` | `~/git/remotetable/` |
| `third_party/extractmail/` | `git@github.com:davidelang/extractmail.git` | `~/git/extractmail/` |
| `third_party/rclone/` | (upstream + our build options; see SOURCE.md) | sandbox recipes until fully migrated |
| `third_party/paddle/` | (build meta + patched sources; see SOURCE.md) | sandbox recipes until fully migrated |

**Email work (this branch):** implement shared capability in **remotetable / extractmail**, then make the VE app a **thin consumer** via pins. Do not keep growing a parallel full implementation only inside `app/` if it belongs in a library.

---

## Hard rules for agents

1. **`third_party/<name>/src` is not VE-owned product source.**  
   - Default: **git submodule** (gitlink) of the library repo at the locked SHA.  
   - Commits **inside** `src/` belong to **that library’s** history; push to the library remote; merge to library `master` as usual.  
   - Updating the pin in VE = change `lock.yaml` + artifact (when built) + submodule gitlink in a **VE** commit. Library-only work needs **no** VE commit.

2. **Do not `git add` a full non-submodule dump of library sources into VE.**  
   If `fetch-deps` used a gitignored checkout, keep it ignored.

3. **Stable artifact paths** (e.g. `third_party/remotetable/artifact/remotetable.aar`). Version identity lives in `lock.yaml` (`git_sha`, `git_describe`).

4. **Pull model:** remotetable/extractmail do not push into VE. When the app needs a newer library, bump the pin on this branch.

5. **Reach:** Stay in the worktree root for VE helpers (`./build_app`, etc.). Library builds run via `third_party/<name>/build` or the library host’s own tooling under `~/git/<lib>/`. Prefer not to treat `../` outside the worktree as normal workspace.

6. **Sandbox:** VE still has `dev-ai-interaction/` (legacy name). **Library** repos use **`sandbox/`**. Do not put long-lived library SoT only under VE sandbox.

7. **rclone / paddle:** Boilerplate under `third_party/` is for provenance + future fetch-deps. Sandbox build trees may still exist until **after** this branch is merged everywhere; do not delete them as part of casual cleanup.

8. **OpenCV:** Not this branch.

---

## Day-to-day commands (once fetch-deps is real)

```bash
# From VE worktree root (agent-4)
./third_party/fetch-deps --all                 # fetch-only, SSH (editable)
./third_party/fetch-deps --readonly remotetable
./third_party/fetch-deps --build remotetable   # after fetch, run build script(s)
```

Until `fetch-deps` fully implements submodule update, use normal git submodule commands and edit `lock.yaml` carefully so **gitlink SHA == lock `git_sha`**.

---

## Where to implement what

| Work | Where |
|------|--------|
| Library API, conformance, extractors, backends | `~/git/remotetable` or `~/git/extractmail` (or submodule `src/` on a library feature branch) |
| Pin bump, AAR/JAR commit, VE app callers, Gradle wiring | This VE worktree (`email-connection`) |
| Process / multi-agent rules for a library | That library’s **orchestration** worktree + policy files on `master` / agent worktrees |

---

## Policy pack

Full rules: **`docs/reference/FIRST_PARTY_LIBS.md`**.  
Orientation map: **`project-facts.md`**.  
Bootstrap plan (historical): sandbox `dev-ai-interaction/plans/first-party-libs-third-party-bootstrap-20260802-0320-plan.md` (may not survive; prefer this docs path).

## fetch-deps (agents)

```bash
./third_party/fetch-deps              # ro pin (default)
./third_party/fetch-deps status -v
./third_party/fetch-deps rw           # co-edit on VE branch name — then library PR
./checkifclean -v --preset agent-4 --gate H3
```

Do not commit on **ro** trees. Do not edit `~/git/*/agent-N` from this VE agent; use `third_party/*/src` only in **rw** mode, or stay app-only.
