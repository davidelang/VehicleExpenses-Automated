# First-party libraries and `third_party/` (VehicleExpenses)

**Status:** Authoritative policy (2026-08-03).  
**Audience:** developers working on libraries this project maintains (remotetable, extractmail) and multi-worktree hosts.  
**Pin/build/audit contract (all third_party libs, including pure upstream):** start with **`third_party/README.md`** and **`docs/reference/THIRD_PARTY_PIN_BUILDS.md`**.  
**Supersedes:** Sandbox-only notes under `dev-ai-interaction/research/first-party-libs-*` and staging `subprojects/DECISIONS.md` process sections (product milestones in those dirs may still apply until moved into the library repos).

> **Layout is not ownership.** remotetable, extractmail, opencv, rclone, and paddle all use the same `third_party/<name>/` pin shape. “First-party” here means we also host multi-agent development of those repos — not different pin rules.

---

## 1. Problem

- Prebuilts (rclone, paddle) in VE without clear source + recipe provenance fail third-party review.
- **remotetable** and **extractmail** will change APIs often; historical VE builds need **source pins** (full git SHA + `git describe`) plus **committed artifacts** the app actually links.
- Libraries are **independent products**. Other consumers (including extractmail) are **equal** to VE. A library commit does **not** imply a VE commit.
- Development uses **multi-worktree / multi-role** hosts per library, not long-term storage under VE’s cluttered sandbox.

---

## 2. GitHub and host layout

| Repo | SSH | HTTPS |
|------|-----|--------|
| remotetable | `git@github.com:davidelang/remotetable.git` | `https://github.com/davidelang/remotetable.git` |
| extractmail | `git@github.com:davidelang/extractmail.git` | `https://github.com/davidelang/extractmail.git` |
| VehicleExpenses | `git@github.com:davidelang/VehicleExpenses-Automated.git` | `https://github.com/davidelang/VehicleExpenses-Automated.git` |

**Visibility:** public. **License:** MIT.

**Library multi-agent host (final locations):**

```text
~/git/remotetable/              # orchestration worktree + .git
~/git/remotetable/master/       # product (branch master)
~/git/remotetable/agent-N/      # feature worktrees
~/git/remotetable/sandbox/      # per-repo sandbox (name: sandbox, not dev-ai-interaction)

~/git/extractmail/              # same pattern
```

- Prefer branch name **`master`** (not `main`) for product.
- **`orchestration`** and **`master`** are same repo, different roles (brain vs product). Both carry policy files (`AGENTS.md`, mandates, launchers) so agents can start in any worktree.
- Remotes match VE style: SSH `origin` (and optional `push` alias to the same URL).

**Contribution model (preferred for others):** fork → work on fork → PR into upstream. Maintainer may use dual remotes; locks always pin **canonical** `davidelang/*` + SHA.

---

## 3. VE `third_party/` contract

Per-library grouping (not artifacts/ vs locks/ split):

```text
third_party/
  fetch-deps                 # CLI; long options; no file extension in the name
  remotetable/
    SOURCE.md                # human audit narrative
    lock.yaml                # structured pin
    build                    # or build-*; shebang; exec'd by fetch-deps
    artifact/                # STABLE filenames (e.g. remotetable.aar); version in lock only
    src/                     # submodule checkout (default) or fetch-deps materialization
  extractmail/
  rclone/
  paddle/
  # opencv/ — later branch
```

| Rule | Detail |
|------|--------|
| Stable artifact path | e.g. `artifact/remotetable.aar` — do not rename per describe |
| Track artifacts | Once present, commit them |
| Ignore | Non-submodule `src/` trees if ever used; submodule is a **gitlink**, not ignored |
| Pull model | Library never updates VE; human/VE agent bumps pin when needed |
| M1 artifact (remotetable) | **AAR**; later JAR/CLI/etc. via additional `build-*` + lock `artifacts[]` |
| extractmail | Equal consumer of remotetable (will have its own `third_party/remotetable` inside the extractmail repo over time) |

**email-connection / agent-4:** Standing up `third_party` and the two lib repos **is** the near-term email work (split common code; app becomes thin consumer). rclone + paddle enter `third_party` on this branch; sandbox copies remain until all worktrees are past agent-4 merge. OpenCV later.

---

## 4. `lock.yaml` and builds

Lock holds **identity and integrity**, not machine-specific layout:

- `git_ssh`, `git_https`, full `git_sha`, `git_describe`
- `artifacts[]`: `path`, optional `sha256`, `build` script name
- Notes / consumer blurb

**Artifact sha256** without reproducible builds mainly detects tampering/change, not “correct rebuild.” Still record when useful; say so in SOURCE.md.

**Build scripts** live beside the lock (`./build`, `./build-aar`, …). `fetch-deps --build` **exec**s them (shebang) with cwd = `third_party/<name>/`. Do not embed full recipes only in YAML.

**Future:** constrain builds so they can read/execute above the name dir but not **write** above it.

---

## 5. `fetch-deps` CLI

Coding standard for new CLIs: **long options** (`getopt_long` or equivalent).

| Option | Meaning |
|--------|---------|
| *(default)* | Fetch-only (materialize sources; no build) |
| `--build` | Run build script(s) from lock / convention |
| `--all` | All libs under `third_party/` |
| names / `*` | Specific lib dirs |
| `--editable` | **Default.** Use **SSH** URLs (pushable dev) |
| `--readonly` | Use **HTTPS** URLs (audit/CI without SSH keys) |

**Source materialization:**

- **Default:** **submodule** at `third_party/<name>/src` (committed gitlink matches lock sha when pin is bumped).
- **Optional CLI:** gitignored plain checkout (audit / no gitlink).
- **Local co-dev:** submodule path **is** a normal working tree of the library repo; other worktrees live under `~/git/<lib>/…`. Optional `--worktree-search PATH:PATH` to attach/find local clones. Do **not** store `src_mode: worktree` in the lock (machine-specific).

**Committed `.gitmodules` URL form:** **SSH**.

---

## 6. Tags and version identity

- Pins: **full SHA** + **`git describe`** (human). Not floating `latest`.
- Do **not** tag every experiment. Tags at meaningful cleaned milestones; **published tags stay**.
- Tracks (automation TODO: VE + both libs): nightly describe-like, `vX.Y.Z-rcN`, release `vX.Y.Z`.
- VE **release** should force library pins onto release-track tags.

---

## 7. Eng-log

- Early dual-log OK.
- Steady: library eng-log = detail; VE eng-log = pin bumps + API consumption only.

---

## 8. Profiles

| Profile | Examples | Notes |
|---------|----------|--------|
| Churning first-party | remotetable, extractmail | Full multi-agent host + third_party pin |
| Options-only rebuild | rclone, (opencv later) | build encodes flags; artifact in VE |
| Paddle special | paddle | Docker **build instructions** in meta; **no** image blobs in git; source via submodule/checkout of patched tree |

---

## 9. Agent orientation

After bootstrap on `email-connection`, agents working on email / libs must read:

1. **`docs/reference/THIRD_PARTY_LAYOUT_FOR_AGENTS.md`** (short handoff)
2. **This file** (full policy)
3. **`project-facts.md`** pointers

Library-only agents start under `~/git/<lib>/` with that repo’s AGENTS pack.

## fetch-deps modes (implementation)

| Command | Behavior |
|---------|----------|
| `fetch-deps` / `fetch-deps ro` | Readonly pin @ lock sha; patches; chmod a-w; `status.local` |
| `fetch-deps rw` | Branch = VE branch name; base = lock sha; writable |
| `fetch-deps status [-v\|-vv]` | Checkout state |
| `fetch-deps refresh` | Advance pin to library master tip (explicit human) |
| `fetch-deps upgrade NAME --ref REF` | Try ref + build + artifact + lock |
| `fetch-deps build` | Run `./build`, copy `from_src` → `artifact/` |

Lock file has **no mode**. Mode is `third_party/<lib>/status.local` (gitignored).

Agents: no SSH; use `$GIT_HOME` (default monorepo sibling `…/git`) or HTTPS.

See design: `docs/reference/third-party-fetch-deps-design-20260802.md` (if present) or orchestration research copy.
