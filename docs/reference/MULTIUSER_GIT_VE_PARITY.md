# Multi-user git: VehicleExpenses is the ground truth

**Status:** normative  
**Audience:** orchestrator / human / anyone touching first-party lib repos  
**Cost:** Getting this wrong once was expensive. Re-deriving it after inventing a “lib variant” (`ai-code` on `.git`) was expensive again. **Do not re-derive. Copy VE.**

---

## Rule (non-negotiable)

When standing up or repairing git for **remotetable**, **extractmail**, or any multi-agent host next to VE:

1. **Audit VE live** (`stat`, `git config`, `getent group`).
2. **Apply the same model** to the other repo.
3. **Do not invent** a different group, mode, or “library policy.”

If something “works for ai-coder but not ai-planner,” you almost certainly **changed the group** or **dropped setgid**. That is not a new puzzle — it is a failed VE copy.

---

## VE ground truth (as of 2026-08)

| Item | Value | Why it matters |
|------|--------|----------------|
| `.git` mode | **`2770`** `drwxrws---` | Group rwx + **setgid**; **other has no access** on the directory |
| `.git` owner:group | **`dlang:ai-shared`** | Shared agent group |
| All dirs under `.git` | **setgid `2770`** | New fan-out dirs inherit **group ai-shared**, not dlang’s primary group |
| `core.sharedRepository` | **`group`** | Git creates group-friendly modes for new objects/refs |
| Who can use the repo | Members of **`ai-shared`** | See membership below |

### Group membership (load-bearing)

```text
ai-shared: ai-orchestrator, ai-planner, ai-coder, dlang
ai-code:   ai-orchestrator, dlang          ← NOT ai-planner, NOT ai-coder as only path
```

| Role | In `ai-shared`? | In `ai-code`? |
|------|-----------------|---------------|
| ai-coder | yes | yes (primary) |
| ai-planner | **yes** | **no** |
| ai-orchestrator | yes | yes |
| dlang | yes | yes |

**Therefore `.git` must be `ai-shared`, not `ai-code`.**  
Putting `.git` on `ai-code` with mode `2770` **locks out ai-planner** (and anyone not in `ai-code`). That is exactly the failure mode after a “helpful” lib-only choice.

### Object files vs object directories

| Path | Typical mode | Notes |
|------|--------------|--------|
| `.git/objects/ab/` (fan-out **dir**) | **2770 setgid** | **Must** inherit group; if not setgid and dlang creates it → **`dlang:dlang`** |
| `.git/objects/ab/cdef…` (loose **file**) | often **444** or **660** | Blobs may be world-readable; that is normal. Do not “fix” by making dirs world-writable. |
| `.git` itself | **2770** | **Not** world-executable on VE |

**Do not confuse “objects are o+r files” with “`.git` dirs should be o+rx.”** VE dirs are group-only (`o=---`).

---

## Forbidden improvisations (already paid for twice)

| Improvisation | What broke |
|---------------|------------|
| **`.git` group = `ai-code`** “because libraries are code” | **ai-planner** cannot open `.git` (`2770` + not in group) |
| **Mode `775` without setgid** | dlang commits create **`dlang:dlang`** object dirs; agents not in group `dlang` cannot use them |
| **`core.sharedRepository` unset** | New objects/refs not group-share friendly |
| **`chgrp -R` under `set -e`** | Aborts on first `ai-coder`-owned path; leaves many `dlang:dlang` paths untouched |
| **Plain `chgrp ai-code` as dlang without group in session** | No-op / EPERM even as owner if session lacks target group — use **`sudo chgrp`** |
| **`sudo ./script` without fixed `GIT_HOME`** | Script uses `/root/git/...` → MISSING |
| Treating **worktree path layout** as a substitute for fixing **group/setgid** | Layout can matter later; it does **not** replace VE git parity |
| Multiple conflicting “fix-*-git-*.sh” inventing different groups | Future agents re-break production |

---

## Required parity checklist (libs ↔ VE)

Run against **both** VE and each lib:

```bash
# Group and mode of .git (must match VE group name and 2770 setgid)
stat -c '%a %U:%G %A %n' \
  /home/dlang/git/VehicleExpenses-automated/.git \
  /home/dlang/git/remotetable/.git \
  /home/dlang/git/extractmail/.git

# Must be group
git -C /home/dlang/git/VehicleExpenses-automated config --get core.sharedRepository
git -C /home/dlang/git/remotetable config --get core.sharedRepository
git -C /home/dlang/git/extractmail config --get core.sharedRepository

# Must be 0
find /home/dlang/git/remotetable/.git /home/dlang/git/extractmail/.git -group dlang | wc -l
find /home/dlang/git/remotetable/.git /home/dlang/git/extractmail/.git -type d ! -perm -2000 | wc -l

# Must be 0 (everything under .git should be ai-shared)
find /home/dlang/git/remotetable/.git ! -group ai-shared | wc -l
find /home/dlang/git/extractmail/.git ! -group ai-shared | wc -l
```

**Pass criterion:** lib `.git` shows the **same group name as VE** (`ai-shared`), **same directory mode class** (`2770` + setgid), **`sharedRepository=group`**, zero `group dlang`, zero dirs without setgid.

---

## Repair command (canonical)

```bash
# as dlang — script uses sudo for chgrp/chmod; does NOT invent ai-code
bash /home/dlang/git/VehicleExpenses-automated/fix-lib-git-match-ve.sh
```

If you must run under root:

```bash
sudo GIT_HOME=/home/dlang/git /home/dlang/git/VehicleExpenses-automated/fix-lib-git-match-ve.sh
```

Manual equivalent (duplicate of script body):

```bash
for g in /home/dlang/git/remotetable/.git /home/dlang/git/extractmail/.git; do
  sudo chgrp -R ai-shared "$g"
  sudo find "$g" -type d -exec chmod 2770 {} +
  git --git-dir="$g" config core.sharedRepository group
done
```

Also ensure agent users have `safe.directory` for lib paths (script adds these). Planner/coder globals historically only listed VE.

---

## Pack launchers (related deploy)

Multi-agent **launchers** (thin `run-grok*` + `.grok/lib/grok-launch-common.sh` + packs) must also stay in sync from the VE orchestration SoT:

```bash
bash /home/dlang/git/VehicleExpenses-automated/deploy-pack-launchers.sh
# optional: --commit --push   --ve-only | --libs-only
```

That deploys to every VE worktree and `~/git/{remotetable,extractmail}` (orch + master + other lib worktrees). **Separate** from git ownership/`ai-shared` above — run both when standing up a host.

## Related layout note (secondary — not a substitute for the table above)

Consumer co-dev worktrees may live under  
`VehicleExpenses-…/agent-N/third_party/<lib>/src`  
while the object DB is `~/git/<lib>/.git`. That is awkward, but **permission group must still be `ai-shared`** so every agent role can use that object DB. Fixing layout is optional follow-on; **wrong group is not optional**.

---

## Deprecated scripts / wrong advice

These earlier helpers **taught the wrong group** or aborted early. Prefer **`fix-lib-git-match-ve.sh`** only:

| Script | Problem |
|--------|---------|
| `fix-lib-git-perms.sh` | Documented / applied **`ai-code`** for `.git` |
| `fix-extractmail-git-objects.sh` | Same **`ai-code`** mistake; useful mechanics but wrong group |
| `fix-git-ownership-multiagent.sh` | Mixed guidance; do not use as policy source |

If those scripts remain on disk, they should **redirect or die** with a pointer here — never re-apply `ai-code` to `.git`.

---

## Message to future agents (including future Grok)

- You were told to **audit VE carefully** and **duplicate** it.  
- **Details matter:** group name, setgid, `sharedRepository`, who is in which Unix group.  
- **ai-code ≠ multi-agent git share group.** **ai-shared** is.  
- Do not “make a call” that VE already made.  
- If you change this document, re-verify against **live** `stat`/`getent` on VE first.
