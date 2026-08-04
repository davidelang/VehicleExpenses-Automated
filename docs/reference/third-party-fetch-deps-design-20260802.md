# third_party / fetch-deps design notes (2026-08-02)

Working design after multi-turn planning. Not yet fully implemented.

## Jobs

| Job | Mode | Location |
|-----|------|----------|
| Consume pin | **ro** (default) | `third_party/<lib>/src` detached @ lock sha, patches, then chmod a-w |
| Tight coupling one VE agent | **rw** | same `src`, **branch = VE branch name** (default), base = **lock sha** |
| Library-only | n/a | `~/git/<lib>/…` only — never VE |
| Deliberate upgrade/rebuild | upgrade / refresh | ref + build + artifact + lock update |

## Files

- `lock.yaml` — pin contract only: urls, **git_sha**, describe, optional `track_branch`, artifacts (`path` + `from_src`), patches, build. **No mode.**
- `status.local` (gitignored) — mode ro/rw, branch, head, dirty, worktree_of, …
- `patches/` — applied after clean checkout, **before** chmod -w
- `artifact/` — tracked consumer binaries; `src/` empty in git
- `build` — produces paths listed in `from_src`

## fetch-deps UX (target)

```text
fetch-deps [name]           # ro pin (default)
fetch-deps status [-v|-vv]
fetch-deps rw [name]        # branch=VE branch, base=lock sha; optional --base
fetch-deps ro [name]        # back to pin if clean enough per policy
fetch-deps refresh          # human: tip of track_branch/masterish → new pin (explicit)
fetch-deps upgrade name --ref REF
fetch-deps build [name]
```

Rare: `--allow-ssh`, `--branch`, `--depth`, `--patch-strategy=am`.

Transport: local host → HTTPS; SSH only `--allow-ssh`. Agents have no SSH keys.

## checkifclean

Exit: clean / not-clean. Verbosity:

1. which repos dirty (VE, remotetable, extractmail, …)
2. how (unadded, uncommitted, third_party not merged, patch drift, lock≠artifact, lock≠src HEAD, …)
3. full git status-equivalent per repo + third_party detail

Top-level dirty is **reportable**; fatality is **policy** (project/branch).

## Pin / branch retarget offer (not only “advance tip”)

Lock may record optional `track_branch` / last-known branch for human context.

On **fetch-deps** (including plain **ro** pin sync, not only refresh):

1. Resolve pin `git_sha`.
2. Discover remote/local branches that **contain** that SHA.
3. Classify branches matching **case-insensitive glob** `*main*` or `*master*` (covers `master`, `main`, `Maslow-Main`, `bar-main-baz`, …). Configurable later.
4. If lock still names a **feature** branch `foo` but SHA is reachable from a **main-ish** branch `bar-main-baz` (and not only from foo):
   - **Offer** (not automatic): retarget lock’s branch field + use `bar-main-baz` as tracking branch (“feature foo looks merged into bar-main-baz”).
5. This is **retarget after merge**, distinct from **refresh** (move pin SHA forward to tip).

## checkifclean: H × D matrix (not one dial)

**History points (when to enforce):**

| H | When check runs / what commits must satisfy policy |
|---|------------------------------------------------------|
| **H1** | Every intermediate commit (strict history) |
| **H2** | Every commit **kept in final PR** (after rewrite) |
| **H3** | Only **final tip** of PR / current tree for daily work |

**Depth (what “clean” means):**

| D | Check |
|---|--------|
| **D−** | No check |
| **D0** | Metadata: repos dirty? third_party mode/HEAD/lock/artifact/patch skew? |
| **D1** | D0 + build/verify |
| **D2** | D1 + broader automated tests |
| **D3** | D2 + full regression (unattended capable) |

Policy is **three settings**: depth required at H1, at H2, at H3 (not a single global level).

**Verbosity of report** (independent of H/D):

| -v | Content |
|----|---------|
| default | clean \| not-clean; which repos |
| -v | + how (categories) |
| -vv | + git status-class detail per repo |

Dirty is always **reportable**; fatality depends on which (H,D) gate is running.

### Project presets (locked intent 2026-08-02)

**Shared baseline (all projects):**

| Gate | Depth | Meaning |
|------|-------|---------|
| **H1** (every intermediate / “can I even try to build?”) | **D0 only** | Metadata clean required before build is attempted. **H1 is never D1+** (build is not the H1 gate). |
| **H2** (each idealized commit kept in final PR) | **D1** | Must **build** (verify). |
| **H3** (tip of PR / release tip) | **project-specific** | See below. |

**H3 by project:**

| Context | H3 depth | Why |
|---------|----------|-----|
| **VE agent-4** | **D1** | Must build at tip; full automated test/regress not available |
| **extractmail** | **D3** | Full regression can run unattended |

Example agent-4 tuple: `(H1→D0, H2→D1, H3→D1)`.  
Example extractmail: `(H1→D0, H2→D1, H3→D3)`.

Bisect: prioritize commits that change **lock + artifacts + patches**. rw experiments in ignored `src` need not each be VE commits.

### Future todo: separate H/D for main vs third_party?

**Not required for v1.** One matrix per project is enough.

**Possible later split** if main app and third_party want different paranoia:

| Axis | Example |
|------|---------|
| `policy.main` | H1/D0, H2/D1, H3/D1 |
| `policy.third_party` | H1/D0, H2/D1, H3/D0 (pin dirty only warn) |

Use when: pin/ro third_party noise blocks app-only iteration, or lib promotion needs stricter tip checks than app. Track as future todo only.

## Nested same-lib (v1)

- Prefer **one physical** `third_party/<lib>` tree (whole-dir symlink).
- Discovery order: **deepest-first**, then same-level siblings (foo and bar both pull baz).
- Canonical: first materialize wins **or** prefer top-level VE path when both VE and nested need same lib—document chosen rule; **warn and ask** if ambiguous/dirty.
- Dual divergent HEADs → checkifclean **error**.

## Shallow clones

- **No shallow by default** (exact pin SHA must resolve).
- Optional **`--depth N`** for experiments / tip/tag tryouts; deepen if object missing.

## remove_agent

Empty/remove `src` (worktree remove or rm clone) before `rm -rf` agent dir.

## TODO

- [ ] Git LFS on GitHub for VE (large third_party artifacts)
- [ ] Build policy: if artifact missing/unreadable → rebuild from source
- [ ] Implement ro/rw/status/refresh|upgrade + checkifclean (H×D policy)
- [ ] bwrap prototype for third_party build write sandbox
- [ ] Nested lib whole-dir symlink + deepest-first discovery
- [ ] Main-ish branch glob + merge-retarget **offer** on fetch

## Refs

- `docs/reference/WRITE_SANDBOX_OPTIONS.md`
- `docs/reference/FIRST_PARTY_LIBS.md` (on email-connection)
- `docs/reference/INFRASTRUCTURE_GAP_AUDIT_20260802.md`
