# Postmortem: `improve-pump-classification` merge (2026-07-16)

**Branch:** `improve-pump-classification` → `master`  
**Outcome:** Landed at `8513b66b`, build green, content correct.  
**Problem:** A fast-forwardable, 7-file feature merge required manual recovery; `./merge-branch-into-master.sh` did not complete autonomously.

This report explains what failed, why fallback ran, and why fallback also failed until hand-fixed.

---

## 1. What should have happened

`audit_merge.py` reported:

```text
SUCCESS: Feature branch is fast-forwardable or up-to-date with 'master'.
```

Delta vs `master` (`c5b474eb`):

| Path | Role |
|------|------|
| 6 Kotlin files + `PumpRoleBandClassifier.kt` (new) | Feature |
| `ENGINEERING_LOG.md` | Branch-only tail (+40 lines) |
| `TODO.md` / `project-facts.md` | Unchanged on branch |

No merge conflicts. No parallel master drift. **Ideal path:** fast-forward master to branch tip (or single `--no-ff` merge commit), append eng-log tail via `ve-englog`, stage `project-facts` tweak, `./build_app`.

---

## 2. What actually happened (timeline)

### Attempt 1 — `./merge-branch-into-master.sh` (script bug: silent false success)

```text
error: Your local changes to the following files would be overwritten by merge:
  ENGINEERING_LOG.md
Aborting
Merge with strategy ort failed.
```

Then the script printed `restore_special`, `ve-englog`, and **`Merge exit code: 0`**.

**Staged result:** nothing useful (or eng-log only). **No app files.**

**Cause:** `merge_git_ort()` on `master` (orchestration-synced copy) did **not** `return 1` when `git merge` failed. With `set +e`, the function continued into `restore_special` / `ve-englog` and returned 0. Fallback to `merge_index_first` **never ran**.

### Attempt 2 — after eng-log append in worktree

`ve-englog` had appended the 40-line branch tail to the worktree (`761` → `801` lines). Retry:

```text
ENGINEERING_LOG.md: index synced to worktree (67d0af14)
error: Your local changes ... ENGINEERING_LOG.md
error: Entry 'ENGINEERING_LOG.md' not uptodate. Cannot merge.
fatal: read-tree failed
```

Script again reported exit 0; **staged only `ENGINEERING_LOG.md`** (40 lines), not the Kotlin tree.

**Cause:** Same `return 1` bug on happy-path failure; plus `merge_index_first` ran `read-tree --reset` but **did not treat read-tree failure as fatal** (orchestration copy), so it continued and claimed success with partial index.

### Attempt 3 — manual recovery (what finally worked)

1. Patch `merge_git_ort` / `merge_index_first` to propagate failures (`return 1`).
2. `git reset HEAD` — **unstage** partial eng-log-only index (critical).
3. `git update-index --cacheinfo` — align index eng-log blob with worktree before `read-tree`.
4. `git merge-tree --write-tree` → tree `57b26697`.
5. `git read-tree --reset 57b26697` — staged all 7 app paths + eng-log.
6. `restore_special` TODO/facts, `checkout-index` for app files, `MERGE_HEAD`, `project-facts` update.
7. `./build_app` — commit `8513b66b`, build OK.

---

## 3. Root causes (layered)

### A. Environmental: `chattr +a` on `ENGINEERING_LOG.md`

The worktree file is append-only (`-----a----------------`). Git cannot `unlink` / truncate it during normal merge worktree updates. Any strategy that tries to **replace** the eng-log file in the worktree will fail unless `chattr -a` (forbidden by project policy).

This alone does **not** require a broken merge — it requires **not** using plain `git merge` worktree checkout for that path. The project chose `ve-englog` + append wrapper precisely for this.

### B. Script logic: `englog_sync_index_to_worktree` before `git merge`

At the start of both `merge_git_ort` and `merge_index_first`, the script runs:

```bash
hash=$(git hash-object ENGINEERING_LOG.md)
git update-index --cacheinfo 100644,"$hash",ENGINEERING_LOG.md
```

If the worktree log already contains the branch tail (from a prior partial run), the index blob **≠ `HEAD:ENGINEERING_LOG.md`**. Git then sees:

- index out of sync with `HEAD`, and/or  
- worktree “local changes” that would be overwritten.

So **`git merge` fails even for a trivial FF branch**, and **`read-tree --reset`** can fail with `Entry 'ENGINEERING_LOG.md' not uptodate` when the index is in a dirty/partially-staged state.

**Design tension:** syncing the index to the +a worktree is correct for *recording* the third-version eng-log, but **poisons** the standard merge path when the worktree is ahead of `HEAD`.

### C. Script bugs (regression on `master` after orchestration sync)

| Bug | Symptom |
|-----|---------|
| `merge_git_ort`: no `return 1` on `git merge` failure | Fallback never invoked; false “success” |
| `merge_index_first`: no `return 1` on `read-tree` failure | Partial index; false “success” |
| Exit code 0 while only eng-log staged | Master agent proceeds; app code missing from index |
| Recovery `checkout-index` from `HEAD` | Reverted in-progress `merge-branch-into-master.sh` patches when resetting worktree |

Orchestration had the `return 1` fix from the `full-code-review1` iteration; **`update-rules.sh` sync restored an older script on `master`**, and the fix only re-landed in commit `8513b66b`.

### D. Operational: retry without full reset

After attempt 1, the worktree eng-log already had the branch tail. Retries compounded index/worktree/`HEAD` skew. A clean retry needs **`git reset HEAD`** (unstage) before `read-tree`, not only `read-tree --reset HEAD` with eng-log left modified.

---

## 4. Why fallback should not have been *necessary* for this branch

Content-wise, fallback exists for **conflicted or +a-blocked ort merges**. This branch was:

- FFable (`audit_merge.py` clean)
- No TODO/facts edits on branch
- Eng-log handled by append-only driver (third version = master worktree + tail)

**A sufficient FF path** (not implemented today):

1. Detect FF (`merge-base == HEAD`).
2. Stage feature tree via `read-tree` / `checkout-index` for **non-special** paths only (no eng-log worktree replace).
3. Run `ve-englog` once (idempotent if tail already present).
4. `restore_special` TODO/facts (no-ops if unchanged).
5. `./build_app`.

That avoids `git merge` touching `ENGINEERING_LOG.md` entirely. Fallback (`merge-tree` + index-first) is the right *family* of solution but should run **once**, reliably, without the happy-path false-success bugs.

---

## 5. Fixes applied in `8513b66b` (master only until next orchestration sync)

```diff
 merge_git_ort:
+  if ! git merge ...; then return 1; fi

 merge_index_first:
+  if ! git read-tree --reset "$tree"; then return 1; fi
```

These prevent false success. They do **not** yet add an FF fast path or fix eng-log index sync ordering.

---

## 6. Recommended script changes (orchestration SoT)

| Priority | Change |
|----------|--------|
| **P0** | Keep `return 1` fixes; sync via `update-rules.sh` (already on `master`, not yet on `orchestration`). |
| **P1** | **FF fast path:** if `merge-base HEAD branch == HEAD`, skip `git merge`; `read-tree` branch tree into index for non-special paths only; then `ve-englog` + `restore_special`. |
| **P1** | Before `read-tree`, always `git reset HEAD` if index has staged partial merge from a prior attempt (or detect dirty index and abort with clear message). |
| **P2** | Do **not** `englog_sync_index_to_worktree` before `git merge` on happy path; keep `skip-worktree` on eng-log until after merge attempt fails, or only sync after `ve-englog`. |
| **P2** | On failure, print explicit **`merge-branch-into-master: FAILED`** when staged app file count is 0. |
| **P3** | Exclude `merge-branch-into-master.sh` from blind `checkout-index` recovery loops (or re-apply fixes from a known good ref). |

---

## 7. Verification checklist for next merge

After `./merge-branch-into-master.sh <branch>`:

```bash
git diff --cached --name-only | grep -E '\.kt$|PumpRoleBand' | wc -l   # expect > 0 for app merges
git rev-parse :TODO.md HEAD:TODO.md                                    # must match (master)
test -f "$(git rev-parse --git-path MERGE_HEAD)" && echo MERGE_HEAD OK
```

If only `ENGINEERING_LOG.md` is staged, **stop** — do not run `./build_app`.

---

## 8. Summary

| Question | Answer |
|----------|--------|
| Was the branch hard to merge? | **No** — FFable, 7 files, no special-file conflicts. |
| Why did `git merge` fail? | **`+a` eng-log** + index synced to worktree ahead of `HEAD`. |
| Why did fallback not save us? | **Script bugs** masked failure (exit 0, no `return 1`); partial staging blocked `read-tree`. |
| Why manual steps? | Unstage partial index, patch script, manual `read-tree` of `merge-tree` output. |
| Is content on `master` correct? | **Yes** — `8513b66b`, `builds` updated, classifier present. |
| What prevents recurrence? | Sync script fixes to orchestration + FF fast path + post-merge staged-file guard. |

**Bottom line:** The failure was **infrastructure and script regression**, not branch complexity. A simple FF merge should not require master-agent surgery once P0–P1 items land in orchestration and propagate via `update-rules.sh`.