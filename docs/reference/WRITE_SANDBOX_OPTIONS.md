# Write sandbox options: bubblewrap vs Landlock vs AppArmor

**Purpose:** Follow-up reference for restricting build/fetch-deps processes so they can **read** broadly (normal Unix perms) but **write** only under selected trees (e.g. `third_party/<lib>/src`, `third_party/<lib>/artifact`).  
**Context:** VehicleExpenses multi-agent + third_party native builds.  
**Date:** 2026-08-02  

This is **not** a full security audit. It is a practical comparison for on-the-fly agent/build wrapping.

---

## Goal

```text
Process tree P (e.g. ./build under third_party/foo):
  READ:  anything Unix allows (source, /usr, SDK, HOME read-only mounts as needed)
  WRITE: only under explicit paths (artifact/, src/build-out/, tmp)
```

Also: fail closed if the tool cannot enforce the policy.

---

## 1. bubblewrap (`bwrap`)

| | |
|--|--|
| **What** | User-namespace sandbox; new mount namespace; bind-mount view of the filesystem |
| **Typical use** | Flatpak; ad-hoc `bwrap --ro-bind /usr /usr --bind $PWD/out $PWD/out -- ./build` |

### Strengths

- **Designed for exactly this:** compose a filesystem view per invocation.
- **Children inherit** the same mount namespace → whole process tree restricted.
- **Dynamic:** no reboot; no system-wide policy load as root (user namespaces permitting).
- Can mark almost everything **read-only** and bind **read-write** only selected dirs.
- Works well with **symlinks** if you bind the real targets carefully.

### Weaknesses

- Needs **user namespaces** enabled (some distros/CI disable them).
- Awkward with tools that require **absolute host layout** (e.g. reaching into `~/Android/…` for SDKs): you must **explicitly** `--ro-bind` those paths or the build breaks (your VE→Android example).
- Nested bwrap / Docker-in-bwrap complexity.
- Not a substitute for full multi-tenant isolation (still same UID unless combined with other tech).

### Fit for VE third_party builds

**Best short-term candidate** for `sandbox-run --write PATH -- ./build`:

```bash
bwrap --die-with-parent \
  --ro-bind / / \
  --dev /dev --proc /proc \
  --tmpfs /tmp \
  --bind "$ART" "$ART" \
  --bind "$SRC_OUT" "$SRC_OUT" \
  --chdir "$WORKDIR" \
  -- ./build
```

(Exact flags need tuning; `--ro-bind / /` then re-bind writes is a common pattern.)

---

## 2. Landlock (Linux LSM)

| | |
|--|--|
| **What** | Kernel LSM: process opts into filesystem access rules (ABI evolving since ~5.13+) |
| **Typical use** | Hardened apps (e.g. some browsers/tools); libraries wrapping `landlock(2)` |

### Strengths

- **Kernel-enforced** write restrictions without a full mount namespace rewrite.
- Rules apply to the process after `landlock_restrict_self` — **children inherit**.
- Can allow read broadly and write only under path hierarchies (version-dependent API).
- Lighter than a container; good long-term fit for “wrapper around build”.

### Weaknesses

- **API surface / ABI versions** differ by kernel; need a small helper (Go/Rust/C) or recent `landlock` CLI if packaged.
- Path handling and **symlink** behavior must be tested carefully.
- Older kernels / some Android host kernels: unavailable.
- Less “turnkey” than bwrap for operators who already know Flatpak-style binds.

### Fit for VE

**Best long-term** if hosts run recent Linux and we invest in a tiny `ve-landlock-run` helper.  
Good combination: landlock for write caps + normal FS for reads.

---

## 3. AppArmor

| | |
|--|--|
| **What** | Path-based MAC profiles, usually for **named binaries**, loaded via apparmor_parser |
| **Typical use** | Distro confinement of system services |

### Strengths

- Mature, path-oriented (“deny write to everything except …”).
- Once a profile is loaded, **children of the confined binary** generally stay under the same profile (depending on profile mode and `Px`/`Ux` exec transitions).

### Weaknesses

- **Not really on-the-fly per agent worktree** without root (or admin) loading profiles.
- Profiles are usually **per executable path**, not “this one PID tree for this one job”.
- Dynamic paths like `/home/dlang/git/VehicleExpenses-automated/agent-4/third_party/foo` mean either:
  - very broad profiles (`/home/**/third_party/**` w), or  
  - regenerating/loading profiles on every `setup_agent` / `fetch-deps` (admin friction).
- Your sketch (`VE tree rw`, rest of disk ro) is closer to a **session profile** than classic AppArmor service profiles; possible but operationally heavy.
- **Does not** easily express “PID 12345 and descendants only” without tying to a binary wrapper always used to start builds.

### “Do restrictions apply to all children of a target path?”

- AppArmor restricts **processes** (by profile), not “everything under a directory” as a first-class object.
- A profile can say: allow write only under `/home/.../third_party/foo/**`.
- **Children** of a confined process keep the profile unless they execute into another profile.
- So: **yes for children of the confined process**; **not** “any process that touches this directory.”

### Fit for VE

Possible for a **stable wrapper binary** (`/usr/local/bin/ve-build-sandbox`) with a profile allowing writes under `**/third_party/**/artifact` and `**/third_party/**/src` — but **less flexible** than bwrap for multi-worktree paths and worse for “load policy as the agent runs” without root.

---

## 4. Docker / Podman (brief)

| | |
|--|--|
| **Strengths** | Strong isolation; clear RW mounts |
| **Weaknesses** | Layout assumptions (`~/Android`, host SDKs, device nodes, nested docker); slower; agents + docker permissions; bind-mount explosion for a real Android build |

**Fit:** good for **paddle docker builds** (already container-native); poor default for **host Gradle/Android** that expects a full home layout unless you invest in a complete image + mounts.

---

## 5. Comparison matrix

| Criterion | bubblewrap | Landlock | AppArmor |
|-----------|------------|----------|----------|
| On-the-fly without reboot | Yes | Yes (if kernel supports) | Profile load often needs admin |
| Write-only under paths | Excellent | Good | Good if profile lists paths |
| Read rest of system | Yes (ro binds) | Yes (default allow read) | Yes if profile allows |
| Children covered | Yes (namespace) | Yes (inherit) | Yes (same profile) |
| Android SDK / weird absolute paths | Painful (must bind) | Easier (read open) | Profile must allow those paths |
| Ops complexity | Medium | Medium–high (helper) | High for dynamic trees |
| VE third_party `./build` | **Best first try** | **Best hardening** | Optional wrapper profile later |

---

## 6. Recommendation for this project

1. **Short term:** prototype **`bwrap`** wrapper around `third_party/<lib>/build` with explicit `--bind` for write paths and `--ro-bind` for toolchain paths discovered from env (`ANDROID_HOME`, etc.).  
2. **Medium term:** evaluate **Landlock** helper for less mount thrash and better “read host, write only X”.  
3. **AppArmor:** only if you want a **distro-installed** always-on wrapper binary; not the first tool for dynamic agent worktrees.  
4. **Docker:** keep for paddle-style image builds; don’t force all third_party builds into docker.

---

## 7. Follow-up experiments (for you)

```bash
# 1) Is userns available?
sysctl kernel.unprivileged_userns_clone 2>/dev/null; cat /proc/sys/kernel/unprivileged_userns_clone 2>/dev/null

# 2) Minimal bwrap write test
mkdir -p /tmp/bw/{ro,rw}
echo x > /tmp/bw/ro/f
bwrap --ro-bind /tmp/bw/ro /tmp/bw/ro --bind /tmp/bw/rw /tmp/bw/rw \
  bash -c 'echo y > /tmp/bw/rw/out; echo z > /tmp/bw/ro/out'  # last should fail

# 3) Landlock kernel
grep -i landlock /proc/config.gz 2>/dev/null || zgrep LANDLOCK /proc/config.gz 2>/dev/null
uname -r   # need recent enough kernel + CONFIG_SECURITY_LANDLOCK
```

Document results next to this file when known.

---

## 8. Related project notes

- third_party **pin ro** + **rw** modes, artifacts, patches: multi-agent / FIRST_PARTY design discussion (2026-08).  
- Long-term: `fetch-deps build` / lib `./build` should run under the chosen sandbox wrapper when available, with policy fallback “warn if sandbox unavailable.”
