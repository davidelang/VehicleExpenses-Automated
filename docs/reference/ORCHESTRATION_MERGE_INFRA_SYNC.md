# Orchestration sync: master merge infrastructure (handoff)

**Audience:** Orchestration agent (`orchestration` branch, repo root worktree).  
**Authority:** User directive after successful `full-code-review1` → `master` merge (2026-07-14).  
**Do not store this handoff only in `dev-ai-interaction/`** — that tree is not a permanent archive. This file lives under tracked `docs/reference/` and must be maintained here.

## Context

During the `full-code-review1` merge, merge-driver tooling was iterated **on `master` first** (unusual — normally orchestration is SoT). The merge succeeded via:

- `ve-special-ours` (replaces `ve-special-refuse` for TODO / project-facts)
- `merge.autostash=false`
- `merge-branch-into-master.sh` index-first fallback when `git merge` fails on `chattr +a` `ENGINEERING_LOG.md`
- `restore_special` + `verify_index_blob` for special files

**Orchestration is now behind `master` on this infra bundle.** Your job is to make `orchestration` canonical again, extend `update-rules.sh`, sync all worktrees, and **not** promote one-off debug scripts.

## Architecture reminder (user-confirmed)

| Layer | What |
|-------|------|
| **Orchestration branch** | Source of truth for shared brain / infra files listed in `update-rules.sh` `FILES`. |
| **`update-rules.sh`** | Physically copies those files into `master/` and every `agent-N/` worktree; runs `install-merge-drivers.sh`. |
| **Master branch / worktree** | Where feature branches are **merged into** via `merge-branch-into-master.sh`. |
| **Repo-wide git config** | `install-merge-drivers.sh` writes to shared `.git/config` — affects all worktrees once run. |

`merge-branch-into-master.sh` belongs on **both** `orchestration` and `master` (same bytes after sync). It is **run** from the master worktree only.

`MASTER_AGENT_MANDATE.md` belongs on **orchestration** and is synced to all worktrees; only master agents are required to read it fully (`AGENTS.md`).

## What to backport from `master` → `orchestration`

Copy the **current `master` versions** of these paths into the orchestration root worktree and commit on `orchestration`:

| Path | Action |
|------|--------|
| `MASTER_AGENT_MANDATE.md` | Replace orchestration copy (§2 merge strategy: `ve-special-ours`, index-first fallback, `merge.autostash=false`). |
| `merge-branch-into-master.sh` | Replace orchestration copy (full script including `merge_git_ort` failure → index-first path). |
| `install-merge-drivers.sh` | Replace (adds `merge.autostash=false`, `ve-special-ours`, legacy `ve-special-refuse` alias). |
| `.gitattributes` | Replace (`TODO.md` / `project-facts.md` → `merge=ve-special-ours`). |
| `git-merge-drivers/ve-special-ours` | **Add** (new file). |
| `git-merge-drivers/ve-special-refuse` | Replace (delegates to `ve-special-ours`). |
| `git-merge-drivers/ve-englog` | Replace only if `master` differs from orchestration (idempotent tail check). |
| `.grok/skills/master-merge/SKILL.md` | Replace (updated merge steps; see “Doc reference fix” below). |

**Verify before commit:**

```bash
# From orchestration root, after copying from master worktree:
git diff master -- MASTER_AGENT_MANDATE.md merge-branch-into-master.sh \
  install-merge-drivers.sh .gitattributes \
  git-merge-drivers/ .grok/skills/master-merge/SKILL.md
# Expect no diff (or only intentional orchestration-only edits — there should be none).
```

Reference merge commit on master: `fc0cdcd2` (merge `full-code-review1`). Infra commits: `67a0508c`, `8c0294d6`.

## `update-rules.sh` changes (required)

In the `FILES` array, update the special-file merge driver section:

**Current (stale):**

```text
"git-merge-drivers/ve-englog"
"git-merge-drivers/ve-special-refuse"
"install-merge-drivers.sh"
"merge-branch-into-master.sh"
```

**Required:**

```text
"git-merge-drivers/ve-englog"
"git-merge-drivers/ve-special-ours"
"git-merge-drivers/ve-special-refuse"
"install-merge-drivers.sh"
"merge-branch-into-master.sh"
```

Ensure `chmod` / ownership loops that mention `git-merge-drivers/*` include `ve-special-ours` (existing glob should cover it if present on disk).

After editing `update-rules.sh` on orchestration: commit, then run:

```bash
sudo ./update-rules.sh
```

Confirm each target worktree received the files and that `./install-merge-drivers.sh` reports `ve-special-ours` and `merge.autostash=false`.

## Do NOT promote (explicit)

### `reset-master-pre-merge.sh`

- Exists on **`master` only** (commit `67a0508c`) as a **one-off debug / retry** tool while merge drivers were in flux.
- **Do not** add to `update-rules.sh` `FILES`.
- **Do not** copy to `orchestration` as canonical infra.
- **Long-term:** normal merges should not need it. Happy path:

  ```bash
  ./merge-branch-into-master.sh <branch>
  # todo-close / todo-append / project-facts review (always)
  ./run-as-primary ./build_app @<merge-summary.txt>
  ```

### `dev-ai-interaction/` as permanent documentation

- **Do not** rely on `dev-ai-interaction/MASTER_MERGE_RESET.md` or similar sandbox notes as the lasting spec.
- Permanent operational docs belong under **`docs/`** (this file and cross-links from `MASTER_AGENT_MANDATE.md` / skills).
- Optional: delete or stop referencing sandbox-only merge-reset notes after this sync.

### Optional cleanup on `master` (separate turn, user approval)

If the user wants a leaner master branch later:

- Remove `reset-master-pre-merge.sh` from `master` in a dedicated chore commit (not part of this orchestration sync unless requested).
- Update `.grok/skills/master-merge/SKILL.md` to drop “Pre-merge reset” pointing at sandbox paths.

## Doc reference fix (while syncing)

Update `.grok/skills/master-merge/SKILL.md` on orchestration so **Pre-merge reset / failed merge** points to tracked docs, not sandbox:

**Remove or replace:**

```markdown
See `dev-ai-interaction/MASTER_MERGE_RESET.md` if redoing a merge after a failed attempt.
```

**Replace with:**

```markdown
See `docs/reference/ORCHESTRATION_MERGE_INFRA_SYNC.md` § "Failed merge recovery" and `MASTER_AGENT_MANDATE.md` §2.
```

Add a short § to this file (below) so the skill citation is accurate.

## Failed merge recovery (permanent — no reset script)

Use this instead of `reset-master-pre-merge.sh` for routine retries:

1. **Abort in-progress merge** (if any): `git merge --abort` (may fail on `ENGINEERING_LOG.md` if `+a` — see step 3).
2. **Drop stale autostash:** `git stash drop` while list head is autostash (merge script does this).
3. **Re-align index to `HEAD`** without unlinking eng log:
   - `git read-tree --reset HEAD`
   - `git checkout-index -f` for all paths from `HEAD` **except** `ENGINEERING_LOG.md`.
4. **Re-run:** `./install-merge-drivers.sh` then `./merge-branch-into-master.sh <branch>`.
5. If worktree is corrupted (orphan staged merge, worktree ≠ index): same as (3), then merge script again.

Only if eng log worktree line count is wrong **and** `sudo chattr -a` is available: restore eng log from `HEAD` blob, then `sudo ./fix-engineering-log-perms`. That is a permissions emergency, not standard merge flow.

## Orchestration agent checklist

- [ ] Checkout `orchestration` at repo root (`/home/dlang/git/VehicleExpenses-automated`).
- [ ] Copy/backport files listed in § “What to backport” from `master` worktree (or `git show master:<path>`).
- [ ] Update `update-rules.sh` `FILES` to include `git-merge-drivers/ve-special-ours`.
- [ ] Fix `master-merge/SKILL.md` sandbox doc reference → `docs/reference/`.
- [ ] Commit on `orchestration` with message e.g. `chore: sync merge infra from master (ve-special-ours, +a-safe merge)`.
- [ ] Run `sudo ./update-rules.sh`; verify `master/` and agent worktrees match orchestration bytes.
- [ ] Spot-check: `git check-attr merge -- TODO.md project-facts.md ENGINEERING_LOG.md` in `master/` worktree.
- [ ] Spot-check: `./install-merge-drivers.sh` in one agent worktree (shared config updated).
- [ ] Do **not** add `reset-master-pre-merge.sh` to sync list.
- [ ] Report completion to user (orchestration commit hash + `update-rules.sh` result).

## Cross-links to update after sync

When touching related files, ensure they agree with this handoff:

| File | Note |
|------|------|
| `MASTER_AGENT_MANDATE.md` | §2 merge strategy (already on backport list). |
| `project-facts.md` (orchestration orientation) | Update merge driver line: `ve-special-ours` not `ve-special-refuse`; mention `merge-branch-into-master.sh` index-first fallback. |
| `AGENT_MANDATES.md` | Only if merge/sync prose is duplicated and stale. |
| `docs/specs/OPERATIONAL_HANDBOOK.md` | Optional one-line pointer to this doc for merge infra ownership. |

## Success criteria

- `git diff orchestration master --` on the infra file set is **empty** (after orchestration commit and master receives `update-rules.sh` sync).
- `update-rules.sh` distributes `ve-special-ours` to all worktrees.
- No operational merge documentation lives only under `dev-ai-interaction/`.
- `reset-master-pre-merge.sh` is not part of orchestration SoT or `FILES` sync.