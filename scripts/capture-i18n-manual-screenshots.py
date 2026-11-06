#!/usr/bin/env python3
"""Capture user-manual screenshots on one device, or on a phone and a tablet together.

David runs this. One --serial still requires --form phone|tablet and exits
before any shot unless `wm size` is 1440x3120 for phone or 1600x2560 for tablet
(either orientation). Two --serial values and no --form: physical `wm size`
and physical `wm density` give the diagonal in inches. The larger device is
the tablet and the smaller is the phone. A gap under 1.5 inches exits before
any shot. The two devices run at the same time. Each writes only its own form
directory and restores English when it finishes. One device's failure does not
stop the other.

--locales is all, or a comma list of pref tags. Omitted means en for one serial
and all for two. --shots is a comma list of group names and filenames. Omitted
means every shot. An unknown token exits before any shot. --replace overwrites
the selected shots. Without it, a selected JPEG of at least 1000 bytes is kept,
and a pack whose selected shots are all that size is not opened and its
language is not changed.

Setting a pack taps that pack's endonym in the Settings language menu. The menu
scrolls inside the open popup. One pack that fails the picker is recorded and
the loop continues. setPref writes app_language and recreates the activity.
The script does not call run-as and does not force-stop. An English screen that
already shows the English nav_menu content description is left alone. It does
not tap Sync now, does not change Enable background sync or Enable background
backup, does not save a fill, expense, trip, or vehicle, does not delete
anything under Pictures/VehicleExpenses, does not change Save fill and trip
photos, and does not clear Debug Fill sessions. Back is not sent while the
drawer scrim is showing. r3b, when it is taken, is a flat gallery picture
(Pictures/not-a-dash.jpg) while the name list is up, then Back. The shutter is
not used for that file.

Usage:
  python3 scripts/capture-i18n-manual-screenshots.py --form phone|tablet --serial SERIAL [--locales all|TAG,TAG] [--shots GROUP,FILE] [--replace]
  python3 scripts/capture-i18n-manual-screenshots.py --serial SERIAL --serial SERIAL [--locales all|TAG,TAG] [--shots GROUP,FILE] [--replace]
"""
from __future__ import annotations

import argparse
import math
import re
import shutil
import subprocess
import sys
import threading
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
_SCRIPTS = Path(__file__).resolve().parent
if str(_SCRIPTS) not in sys.path:
    sys.path.insert(0, str(_SCRIPTS))
from i18n_apply_supplied import PACKS  # noqa: E402

PKG = "com.davidlang.vehicleexpensesautomated"
ACTIVITY = f"{PKG}/.MainActivity"
APP_LANGUAGE_KT = (
    ROOT
    / "app/src/main/java/com/davidlang/vehicleexpensesautomated/ui/util/AppLanguage.kt"
)
RES = ROOT / "app/src/main/res"
_ZOOM_TEXT = re.compile(r"^[124]x$")
_CARD_TITLE_DATE = re.compile(r"\d{4}-\d{2}-\d{2}")
_DRAWER_LABEL_KEYS = (
    "nav_fill",
    "nav_manage_vehicles",
    "nav_new_expense",
    "nav_reports",
    "nav_start_trip",
    "nav_syncing",
    "nav_settings",
    "nav_help",
    "nav_about",
)


@dataclass(frozen=True)
class Pack:
    pref: str
    bcp47: str
    manual_segment: str


class StopRun(RuntimeError):
    """Back left the app, or Back was refused because the drawer is open."""

SHOTS = [
    "01-drawer.jpg",
    "03b-manage-vehicles-new.jpg",
    "05-expense-list.jpg",
    "06-reports.jpg",
    "07-settings.jpg",
    "07b-settings-more.jpg",
    "08-spreadsheet-sync.jpg",
    "09-spreadsheet-provider-picker.jpg",
    "10-spreadsheet-google-form.jpg",
    "11-photo-backup.jpg",
    "12-photo-provider-picker.jpg",
    "13-photo-google-form.jpg",
    "14-help.jpg",
    "15-about.jpg",
    "expense-edit.jpg",
    "fill-history.jpg",
    "fuel-edit.jpg",
    "r1-manage-vehicles-crops.jpg",
    "r2-manage-vehicles-landmarks.jpg",
    "r3-fill-odo-live.jpg",
    "r3b-fill-vehicle-not-identified.jpg",
    "r4-fill-odo-result.jpg",
    "r5-fill-pump-result.jpg",
    "r6-new-expense.jpg",
    "start-trip.jpg",
    "syncing-hub.jpg",
    "time-based-reports.jpg",
    "time-based-reports-scrolled.jpg",
    "trip-miles.jpg",
]

# Group names and filenames accepted by --shots. Every SHOTS entry is in one group.
SHOT_GROUPS: dict[str, tuple[str, ...]] = {
    "drawer": ("01-drawer.jpg",),
    "vehicles": (
        "r1-manage-vehicles-crops.jpg",
        "03b-manage-vehicles-new.jpg",
        "r2-manage-vehicles-landmarks.jpg",
    ),
    "fill": (
        "r3-fill-odo-live.jpg",
        "r3b-fill-vehicle-not-identified.jpg",
        "r4-fill-odo-result.jpg",
        "r5-fill-pump-result.jpg",
    ),
    "expense": ("r6-new-expense.jpg",),
    "reports": (
        "06-reports.jpg",
        "05-expense-list.jpg",
        "expense-edit.jpg",
        "time-based-reports.jpg",
        "time-based-reports-scrolled.jpg",
        "fill-history.jpg",
        "fuel-edit.jpg",
        "trip-miles.jpg",
    ),
    "trip": ("start-trip.jpg",),
    "sync": (
        "syncing-hub.jpg",
        "08-spreadsheet-sync.jpg",
        "09-spreadsheet-provider-picker.jpg",
        "10-spreadsheet-google-form.jpg",
        "11-photo-backup.jpg",
        "12-photo-provider-picker.jpg",
        "13-photo-google-form.jpg",
    ),
    "settings": ("07-settings.jpg", "07b-settings-more.jpg"),
    "help": ("14-help.jpg",),
    "about": ("15-about.jpg",),
}

_NOT_A_DASH_REMOTE = "/sdcard/Pictures/not-a-dash.jpg"
_DIAGONAL_GAP_IN = 1.5


def _shot_groups_cover_shots() -> None:
    names = [name for group in SHOT_GROUPS.values() for name in group]
    if len(names) != len(set(names)) or set(names) != set(SHOTS):
        raise SystemExit("SHOT_GROUPS does not match SHOTS")


def parse_shots(spec: str | None) -> set[str]:
    """Omitted means every shot. A group name or a filename. Unknown exits."""
    _shot_groups_cover_shots()
    if spec is None:
        return set(SHOTS)
    tokens = [part.strip() for part in spec.split(",") if part.strip()]
    if not tokens:
        raise SystemExit("--shots is empty")
    known = set(SHOTS)
    chosen: set[str] = set()
    for token in tokens:
        if token in SHOT_GROUPS:
            chosen.update(SHOT_GROUPS[token])
        elif token in known:
            chosen.add(token)
        else:
            raise SystemExit(f"unknown --shots token {token!r}")
    return chosen


def run(serial: str, *args: str, check: bool = True, timeout: int = 60) -> subprocess.CompletedProcess:
    return subprocess.run(
        ["adb", "-s", serial, *args],
        check=check,
        capture_output=True,
        text=True,
        timeout=timeout,
    )


def sh(serial: str, shell_cmd: str, check: bool = True) -> str:
    r = run(serial, "shell", shell_cmd, check=check)
    return (r.stdout or "") + (r.stderr or "")


def _parse_strings(path: Path) -> dict[str, str]:
    text = path.read_text(encoding="utf-8")
    out: dict[str, str] = {}
    for m in re.finditer(r'<string name="([^"]+)">(.*?)</string>', text, re.S):
        v = (
            m.group(2)
            .replace("\\n", "\n")
            .replace("\\'", "'")
            .replace('\\"', '"')
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
        )
        out[m.group(1)] = v
    return out


def load_supported() -> list[Pack]:
    """Every SupportedLocale in AppLanguage.SUPPORTED, in that order."""
    text = APP_LANGUAGE_KT.read_text(encoding="utf-8")
    block = re.search(
        r"val SUPPORTED: List<SupportedLocale> = listOf\((.*?)\n    \)",
        text,
        re.S,
    )
    if not block:
        raise SystemExit(f"AppLanguage.SUPPORTED not found in {APP_LANGUAGE_KT}")
    packs: list[Pack] = []
    for m in re.finditer(
        r'SupportedLocale\(\s*(EN|"([^"]+)")\s*,\s*"([^"]+)"\s*,\s*"([^"]*)"',
        block.group(1),
    ):
        pref = "en" if m.group(1) == "EN" else m.group(2)
        packs.append(Pack(pref, m.group(3), m.group(4)))
    if not packs or packs[0].pref != "en":
        raise SystemExit("AppLanguage.SUPPORTED did not parse")
    missing = [p.pref for p in packs if p.pref != "en" and p.pref not in PACKS]
    if missing:
        raise SystemExit(f"PACKS missing {missing[0]}")
    extra = sorted(set(PACKS) - {p.pref for p in packs})
    if extra:
        raise SystemExit(f"PACKS has {extra[0]} which is not in AppLanguage.SUPPORTED")
    return packs


def pack_by_pref() -> dict[str, Pack]:
    return {p.pref: p for p in load_supported()}


def packs_for_locales_arg(spec: str) -> list[Pack]:
    """`all`, or a comma list of pref tags. The caller passes `en` when omitted."""
    by_pref = pack_by_pref()
    if spec.strip() == "all":
        return load_supported()
    tags = [part.strip() for part in spec.split(",") if part.strip()]
    if not tags:
        raise SystemExit("--locales is empty")
    chosen: list[Pack] = []
    for tag in tags:
        pack = by_pref.get(tag)
        if pack is None:
            raise SystemExit(f"unknown pref tag {tag!r}")
        chosen.append(pack)
    return chosen


def output_dir(pack: Pack, form: str) -> Path:
    if pack.pref == "en":
        return ROOT / "docs/user-manual/images" / form
    if not pack.manual_segment:
        raise SystemExit(f"{pack.pref} has no manualPathSegment")
    return ROOT / "docs/i18n" / pack.manual_segment / "images" / form


def strings_xml(pref: str) -> Path:
    if pref == "en":
        return RES / "values/strings.xml"
    folder = PACKS.get(pref)
    if not folder:
        raise SystemExit(f"{pref} is not in PACKS")
    path = RES / folder / "strings.xml"
    if not path.is_file():
        raise SystemExit(f"missing pack strings {path}")
    return path


_STRINGS: dict[str, dict[str, str]] = {}


def load_strings(pref: str) -> dict[str, str]:
    """English is values/strings.xml. Any other pack overlays that file via PACKS.

    A key missing from the pack is the English string, which is what the app shows.
    """
    cached = _STRINGS.get(pref)
    if cached is not None:
        return cached
    out = _parse_strings(RES / "values/strings.xml")
    if pref != "en":
        out.update(_parse_strings(strings_xml(pref)))
    _STRINGS[pref] = out
    return out


def need(s: dict[str, str], key: str) -> str:
    if key not in s or not s[key]:
        raise SystemExit(f"missing string {key}")
    return s[key]


def _endonym_key(pref: str) -> str:
    return "lang_name_" + pref.lower().replace("-", "_")


def endonym_for(pack: Pack) -> str:
    """Settings row label. The same bytes in every pack. en and en-001 are both English."""
    return need(load_strings("en"), _endonym_key(pack.pref))


def _all_endonyms() -> set[str]:
    return {endonym_for(p) for p in load_supported()}


def _visible_strings(root: ET.Element) -> set[str]:
    vals: set[str] = set()
    for n in root.iter():
        t = (n.attrib.get("text") or "").strip()
        d = (n.attrib.get("content-desc") or "").strip()
        if t:
            vals.add(t)
        if d:
            vals.add(d)
    return vals


def _detect_packs(root: ET.Element) -> list[Pack]:
    """Packs whose drawer strings are on screen. A tie shares those visible strings."""
    visible = _visible_strings(root)
    best: list[Pack] = []
    best_score = 0
    keys = ("nav_menu",) + _DRAWER_LABEL_KEYS
    for pack in load_supported():
        s = load_strings(pack.pref)
        score = sum(1 for key in keys if (s.get(key) or "") in visible)
        if score > best_score:
            best_score = score
            best = [pack]
        elif score == best_score and score > 0:
            best.append(pack)
    return best


def _desc_on_screen(serial: str, desc: str) -> bool:
    try:
        root = dump_ui(serial)
    except ET.ParseError:
        return False
    return bool(find_nodes(root, desc=desc))


def _bring_app_forward(serial: str) -> None:
    if resumed_package(serial) == PKG:
        return
    sh(
        serial,
        f"am start -n {ACTIVITY} -a android.intent.action.MAIN -c android.intent.category.LAUNCHER",
    )
    time.sleep(2.0)


def _dismiss_any_onboarding(serial: str) -> None:
    try:
        root = dump_ui(serial)
    except ET.ParseError:
        return
    blocked: set[str] = set()
    for pack in load_supported():
        skip = load_strings(pack.pref).get("onboarding_skip_for_now") or ""
        if skip and find_nodes(root, text=skip):
            click_text(serial, skip, blocked)
            time.sleep(0.8)
            return


def _tap_visible_menu(serial: str, root: ET.Element, blocked: set[str]) -> bool:
    visible = {
        (n.attrib.get("content-desc") or "").strip()
        for n in root.iter()
        if (n.attrib.get("content-desc") or "").strip()
    }
    seen: set[str] = set()
    for pack in load_supported():
        menu = load_strings(pack.pref).get("nav_menu") or ""
        if not menu or menu in seen or menu not in visible:
            continue
        seen.add(menu)
        if click_desc(serial, menu, blocked):
            return True
    return False


def _open_settings(serial: str) -> list[Pack]:
    """Open the drawer unless it is already open, then tap Settings. Returns tied packs."""
    en_blocked = blocked_labels(load_strings("en"))
    root = dump_ui(serial)
    candidates = _detect_packs(root)
    scrim = None
    for cand in candidates:
        scrim = drawer_scrim_node(root, load_strings(cand.pref))
        if scrim is not None:
            break
    if scrim is None:
        if not _tap_visible_menu(serial, root, en_blocked):
            raise SystemExit("language picker: Menu content description not found")
        time.sleep(0.5)
        root = dump_ui(serial)
        candidates = _detect_packs(root)
    if not candidates:
        raise SystemExit("language picker: could not tell the current pack from the drawer")
    blocked: set[str] = set()
    labels: list[str] = []
    for pack in candidates:
        s = load_strings(pack.pref)
        blocked |= blocked_labels(s)
        label = need(s, "nav_settings")
        if label not in labels:
            labels.append(label)
    opened = False
    for label in labels:
        if click_text(serial, label, blocked):
            opened = True
            break
    if not opened:
        for label in labels:
            if click_text_fling(serial, label, blocked):
                opened = True
                break
    if not opened:
        raise SystemExit("language picker: Settings row not found")
    time.sleep(0.9)
    return candidates


def _fling(serial: str, *, down: bool) -> bool:
    """Fast shorter swipe than fling_page, so a single language row is not skipped.

    Settings-page scroll only. The open language menu uses _fling_menu so the
    gesture stays inside the popup and does not hit the scrim.
    """
    size = screen_size(serial)
    if size is None:
        return False
    w, h = size
    x = w // 2
    y_hi = int(h * 0.72)
    y_lo = int(h * 0.50)
    y1, y2 = (y_hi, y_lo) if down else (y_lo, y_hi)
    sh(serial, f"input swipe {x} {y1} {x} {y2} 70")
    time.sleep(0.45)
    return True


def _menu_entry_nodes(
    root: ET.Element,
    endonyms: set[str],
    system_texts: set[str],
    boxes: list[tuple[int, int, int, int]],
) -> list[ET.Element]:
    """Endonym and System default rows that are not the closed field value."""
    nodes: list[ET.Element] = []
    for n in root.iter():
        t = n.attrib.get("text") or ""
        if t in endonyms or t in system_texts:
            if not _inside(n, boxes):
                nodes.append(n)
    return nodes


def _menu_row_signature(nodes: list[ET.Element]) -> tuple[str, ...]:
    return tuple((n.attrib.get("text") or "") for n in _row_clusters(nodes))


def _popup_swipe_box(nodes: list[ET.Element]) -> tuple[int, int, int, int] | None:
    """Bounds of the visible menu rows, inset so the swipe cannot hit the scrim."""
    rects = [b for b in (bounds(n) for n in nodes) if b]
    if not rects:
        return None
    x1 = min(b[0] for b in rects) + 12
    y1 = min(b[1] for b in rects) + 12
    x2 = max(b[2] for b in rects) - 12
    y2 = max(b[3] for b in rects) - 12
    if x2 - x1 < 24 or y2 - y1 < 36:
        return None
    return (x1, y1, x2, y2)


def _fling_menu(serial: str, box: tuple[int, int, int, int], *, down: bool) -> bool:
    """Scroll the open language popup. The swipe stays inside `box`."""
    x1, y1, x2, y2 = box
    x = (x1 + x2) // 2
    y_lo = y1 + max(8, int((y2 - y1) * 0.20))
    y_hi = y1 + int((y2 - y1) * 0.80)
    y_lo = min(max(y_lo, y1), y2)
    y_hi = min(max(y_hi, y1), y2)
    if y_hi <= y_lo:
        return False
    y_start, y_end = (y_hi, y_lo) if down else (y_lo, y_hi)
    sh(serial, f"input swipe {x} {y_start} {x} {y_end} 70")
    time.sleep(0.45)
    return True


def _field_node(
    root: ET.Element,
    label_texts: set[str],
    value_texts: set[str],
) -> ET.Element | None:
    """The language field value sitting under a Language label. Not a menu row."""
    labels: list[tuple[int, int, int, int]] = []
    for text in label_texts:
        for n in find_nodes(root, text=text):
            b = bounds(n)
            if b:
                labels.append(b)
    if not labels:
        return None
    best: ET.Element | None = None
    best_dist = 10**9
    for n in root.iter():
        t = n.attrib.get("text") or ""
        if t not in value_texts or t in label_texts:
            continue
        b = bounds(n)
        if not b:
            continue
        cy = (b[1] + b[3]) // 2
        for lb in labels:
            if cy < lb[1] - 80 or cy > lb[3] + 700:
                continue
            dist = abs(cy - lb[3])
            if dist < best_dist:
                best_dist = dist
                best = n
    return best


def _find_language_field(
    serial: str,
    label_texts: set[str],
    value_texts: set[str],
) -> ET.Element:
    def search(down: bool) -> ET.Element | None:
        prev: tuple[str, ...] | None = None
        root = dump_ui(serial)
        for _ in range(48):
            node = _field_node(root, label_texts, value_texts)
            if node is not None:
                return node
            labels = bottom_labels(root)
            if prev is not None and labels == prev:
                return None
            prev = labels
            if not _fling(serial, down=down):
                return None
            root = dump_ui(serial)
        return _field_node(root, label_texts, value_texts)

    node = search(True)
    if node is None:
        node = search(False)
    if node is None:
        raise SystemExit("language picker: language field not found")
    return node


def _inside(node: ET.Element, boxes: list[tuple[int, int, int, int]]) -> bool:
    if not boxes:
        return False
    b = bounds(node)
    if not b:
        return False
    cx = (b[0] + b[2]) // 2
    cy = (b[1] + b[3]) // 2
    for x1, y1, x2, y2 in boxes:
        if x1 <= cx <= x2 and y1 <= cy <= y2:
            return True
    return False


def _row_clusters(nodes: list[ET.Element]) -> list[ET.Element]:
    """One node per horizontal row. Parent and child text share a row."""
    ranked: list[tuple[int, int, ET.Element]] = []
    for n in nodes:
        b = bounds(n)
        if not b:
            continue
        ranked.append((((b[1] + b[3]) // 2), (b[0] + b[2]) // 2, n))
    ranked.sort(key=lambda item: (item[0], item[1]))
    rows: list[tuple[int, ET.Element]] = []
    for cy, _cx, n in ranked:
        if rows and abs(cy - rows[-1][0]) < 36:
            continue
        rows.append((cy, n))
    return [n for _cy, n in rows]


def _menu_open(
    root: ET.Element,
    endonyms: set[str],
    system_texts: set[str],
    boxes: list[tuple[int, int, int, int]],
) -> bool:
    """True when an endonym or System default row is on screen and is not the field."""
    for n in root.iter():
        t = n.attrib.get("text") or ""
        if t in endonyms or t in system_texts:
            if not _inside(n, boxes):
                return True
    return False


def _menu_rows(
    root: ET.Element,
    endonym: str,
    boxes: list[tuple[int, int, int, int]],
) -> list[ET.Element]:
    nodes = [n for n in find_nodes(root, text=endonym) if not _inside(n, boxes)]
    return _row_clusters(nodes)


def _tap_node(serial: str, node: ET.Element) -> None:
    c = node_center(node)
    if not c:
        raise SystemExit("language picker: node has no center")
    tap(serial, *c)


def _boxes_around(root: ET.Element, field: ET.Element) -> list[tuple[int, int, int, int]]:
    """Field text plus every node that contains that text's center (the outlined field)."""
    fb = bounds(field)
    if not fb:
        return []
    fcx = (fb[0] + fb[2]) // 2
    fcy = (fb[1] + fb[3]) // 2
    fw = fb[2] - fb[0]
    fh = fb[3] - fb[1]
    boxes = [fb]
    for n in root.iter():
        b = bounds(n)
        if not b or b == fb:
            continue
        if not (b[0] <= fcx <= b[2] and b[1] <= fcy <= b[3]):
            continue
        bw = b[2] - b[0]
        bh = b[3] - b[1]
        # Skip the screen and the open menu. Keep the outlined field around the value.
        if bh > max(fh * 3, 280) or bw > max(fw * 8, 1600):
            continue
        boxes.append(b)
    return boxes


def _popup_still_open(root: ET.Element, endonyms: set[str], system_texts: set[str]) -> bool:
    """Menu rows, not the single field left on Settings after the menu closes."""
    endonym_nodes: list[ET.Element] = []
    system_nodes: list[ET.Element] = []
    for n in root.iter():
        t = n.attrib.get("text") or ""
        if t in endonyms:
            endonym_nodes.append(n)
        elif t in system_texts:
            system_nodes.append(n)
    if len(_row_clusters(endonym_nodes)) >= 3:
        return True
    if len(_row_clusters(system_nodes)) >= 2:
        return True
    if endonym_nodes and system_nodes:
        return True
    return False


def _open_language_menu(
    serial: str,
    field: ET.Element,
    endonyms: set[str],
    system_texts: set[str],
    boxes: list[tuple[int, int, int, int]],
) -> ET.Element | None:
    """Tap the field. Returns the dump once the popup is open."""
    _tap_node(serial, field)
    for _ in range(6):
        root = dump_ui(serial)
        if _menu_open(root, endonyms, system_texts, boxes):
            return root
        time.sleep(0.3)
    return None


def _select_endonym(
    serial: str,
    pack: Pack,
    field: ET.Element,
    endonyms: set[str],
    system_texts: set[str],
    boxes: list[tuple[int, int, int, int]],
) -> float:
    """Tap the menu row. en is the first English row; en-001 is the second.

    Scrolling stays inside the open popup. If the popup closes, it is opened
    again. Movement is the menu rows, not the screen behind the popup.
    """
    endonym = endonym_for(pack)
    if endonym in system_texts:
        raise SystemExit(f"language picker: refusing System default for {pack.pref}")
    index = 1 if pack.pref == "en-001" else 0
    for _attempt in range(4):
        root = _open_language_menu(serial, field, endonyms, system_texts, boxes)
        if root is None:
            continue
        prev_rows: tuple[str, ...] | None = None
        for _step in range(45):
            if not _menu_open(root, endonyms, system_texts, boxes):
                root = _open_language_menu(serial, field, endonyms, system_texts, boxes)
                if root is None:
                    break
                prev_rows = None
                continue
            rows = _menu_rows(root, endonym, boxes)
            if len(rows) > index:
                _tap_node(serial, rows[index])
                return time.time()
            entries = _menu_entry_nodes(root, endonyms, system_texts, boxes)
            signature = _menu_row_signature(entries)
            if prev_rows is not None and signature == prev_rows:
                break
            prev_rows = signature
            box = _popup_swipe_box(entries)
            if box is None or not _fling_menu(serial, box, down=True):
                break
            root = dump_ui(serial)
    raise SystemExit(
        f"language picker: {endonym!r} row {index} for {pack.pref} was not tapped"
    )


def _wait_for_pack_menu(
    serial: str,
    pack: Pack,
    endonyms: set[str],
    system_texts: set[str],
    tapped_at: float,
    already_showing: bool,
) -> None:
    """Activity recreate finished and this pack's nav_menu content description is up."""
    desc = need(load_strings(pack.pref), "nav_menu")
    deadline = time.time() + 30
    stable = 0
    while time.time() < deadline:
        try:
            root = dump_ui(serial)
        except ET.ParseError:
            stable = 0
            time.sleep(0.4)
            continue
        pkg = resumed_package(serial)
        menu_ok = bool(find_nodes(root, desc=desc))
        popup = _popup_still_open(root, endonyms, system_texts)
        back = pkg in (None, PKG)
        ready = menu_ok and back and not popup
        # The same content description was already up, so wait out recreate.
        if already_showing and time.time() - tapped_at < 3.0:
            ready = False
        if ready:
            stable += 1
            if stable >= 2:
                return
        else:
            stable = 0
        time.sleep(0.4)
    raise SystemExit(f"language picker: nav_menu {desc!r} not showing for {pack.pref}")


def set_language(serial: str, pack: Pack) -> None:
    """Tap the Settings language menu. setPref recreates the activity. No run-as."""
    en_menu = need(load_strings("en"), "nav_menu")
    if pack.pref == "en" and _desc_on_screen(serial, en_menu):
        print("  locale en already showing; language menu not opened", flush=True)
        return
    _bring_app_forward(serial)
    if pack.pref == "en" and _desc_on_screen(serial, en_menu):
        print("  locale en already showing; language menu not opened", flush=True)
        return
    _dismiss_any_onboarding(serial)
    if pack.pref == "en" and _desc_on_screen(serial, en_menu):
        print("  locale en already showing; language menu not opened", flush=True)
        return
    candidates = _open_settings(serial)
    label_texts = {need(load_strings(p.pref), "settings_language") for p in candidates}
    system_texts = {need(load_strings(p.pref), "settings_language_system") for p in candidates}
    endonyms = _all_endonyms()
    field = _find_language_field(serial, label_texts, endonyms | system_texts)
    boxes = _boxes_around(dump_ui(serial), field)
    already = _desc_on_screen(serial, need(load_strings(pack.pref), "nav_menu"))
    tapped_at = _select_endonym(serial, pack, field, endonyms, system_texts, boxes)
    _wait_for_pack_menu(serial, pack, endonyms, system_texts, tapped_at, already)
    print(f"  locale {pack.pref} via language menu", flush=True)


def _tmp_token(serial: str) -> str:
    return re.sub(r"[^A-Za-z0-9._-]+", "_", serial)[:80]


def dump_ui(serial: str) -> ET.Element:
    sh(serial, "uiautomator dump /sdcard/ui.xml", check=False)
    out = sh(serial, "cat /sdcard/ui.xml", check=False)
    if "<?xml" in out:
        out = out[out.index("<?xml") :]
    try:
        return ET.fromstring(out)
    except ET.ParseError:
        Path(f"/tmp/ui-bad-{_tmp_token(serial)}.xml").write_text(out, encoding="utf-8")
        raise


def bounds(node: ET.Element) -> tuple[int, int, int, int] | None:
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds") or "")
    if not m:
        return None
    return tuple(map(int, m.groups()))  # type: ignore[return-value]


def node_center(node: ET.Element) -> tuple[int, int] | None:
    b = bounds(node)
    if not b:
        return None
    x1, y1, x2, y2 = b
    return (x1 + x2) // 2, (y1 + y2) // 2


def find_nodes(
    root: ET.Element,
    *,
    text: str | None = None,
    desc: str | None = None,
) -> list[ET.Element]:
    hits: list[ET.Element] = []
    for n in root.iter():
        t = n.attrib.get("text") or ""
        d = n.attrib.get("content-desc") or ""
        if text is not None and t == text:
            hits.append(n)
        elif desc is not None and d == desc:
            hits.append(n)
    return hits


def tap(serial: str, x: int, y: int) -> None:
    sh(serial, f"input tap {x} {y}")
    time.sleep(0.7)


def screen_size(serial: str) -> tuple[int, int] | None:
    """Last WxH from `wm size` (override wins over physical)."""
    matches = re.findall(r"(\d+)x(\d+)", sh(serial, "wm size", check=False))
    if not matches:
        print("  WARN: wm size failed; not swiping", flush=True)
        return None
    w, h = matches[-1]
    return int(w), int(h)


def parse_physical_size(text: str) -> tuple[int, int] | None:
    """Physical panel size. Override size is not the panel."""
    m = re.search(r"Physical size:\s*(\d+)x(\d+)", text)
    if not m:
        return None
    return int(m.group(1)), int(m.group(2))


def parse_physical_density(text: str) -> int | None:
    """Physical panel density. Override density is not the panel."""
    m = re.search(r"Physical density:\s*(\d+)", text)
    if not m:
        return None
    dpi = int(m.group(1))
    if dpi <= 0:
        return None
    return dpi


def diagonal_inches(width: int, height: int, density: int) -> float:
    return math.hypot(width, height) / density


def device_diagonal_inches(serial: str) -> float | None:
    size = parse_physical_size(sh(serial, "wm size", check=False))
    dpi = parse_physical_density(sh(serial, "wm density", check=False))
    if size is None or dpi is None:
        return None
    return diagonal_inches(size[0], size[1], dpi)


def swipe_page(serial: str) -> bool:
    size = screen_size(serial)
    if size is None:
        return False
    w, h = size
    x = w // 2
    y1 = int(h * 0.75)
    y2 = int(h * 0.30)
    sh(serial, f"input swipe {x} {y1} {x} {y2} 400")
    time.sleep(0.6)
    return True


def fling_page(serial: str) -> bool:
    """Fast swipe. A slow swipe stays on the corrupt-incoming rows."""
    size = screen_size(serial)
    if size is None:
        return False
    w, h = size
    x = w // 2
    y1 = int(h * 0.82)
    y2 = int(h * 0.18)
    sh(serial, f"input swipe {x} {y1} {x} {y2} 70")
    time.sleep(0.55)
    return True


def bottom_labels(root: ET.Element) -> tuple[str, ...]:
    """Texts in the bottom band, top-to-bottom. Used to see whether a fling moved."""
    texts: list[tuple[int, int, str]] = []
    max_y2 = 0
    for n in root.iter():
        b = bounds(n)
        if not b:
            continue
        max_y2 = max(max_y2, b[3])
        t = (n.attrib.get("text") or "").strip()
        if t:
            texts.append((b[3], b[0], t))
    if max_y2 <= 0:
        return ()
    cutoff = int(max_y2 * 0.75)
    band = [item for item in texts if item[0] >= cutoff]
    if not band and texts:
        texts.sort()
        band = texts[-4:]
    band.sort()
    return tuple(t for _, _, t in band)


def _record_packages(text: str, label: str) -> list[str]:
    return re.findall(
        rf"{label}(?:=|:)\s*ActivityRecord\{{[^\n]* ([A-Za-z][\w.]*)/",
        text,
    )


def resumed_package(serial: str) -> str | None:
    """Package of the resumed activity, or None when dumpsys does not say."""
    out = sh(serial, "dumpsys activity activities", check=False)
    top = _record_packages(out, "topResumedActivity")
    if top:
        return top[0]
    win = sh(serial, "dumpsys window", check=False)
    focused = re.findall(
        r"mCurrentFocus=Window\{[^\n]* ([A-Za-z][\w.]*)/",
        win,
    ) or re.findall(
        r"mFocusedApp=ActivityRecord\{[^\n]* ([A-Za-z][\w.]*)/",
        win,
    )
    if focused:
        return focused[0]
    resumed = _record_packages(out, "mResumedActivity") or _record_packages(out, "ResumedActivity")
    if resumed:
        return resumed[-1]
    return None


def press_back(serial: str, s: dict[str, str]) -> None:
    """Pop one destination. Never inject Back while the drawer scrim is up."""
    if drawer_scrim_node(dump_ui(serial), s) is not None:
        print("  STOP: refused Back while the drawer scrim is showing", flush=True)
        raise StopRun("refused input keyevent 4 while the drawer scrim is showing")
    sh(serial, "input keyevent 4")
    time.sleep(0.5)
    pkg = resumed_package(serial)
    if pkg != PKG:
        print(f"  STOP: resumed activity is {pkg!r}, not {PKG}", flush=True)
        raise StopRun(f"resumed activity {pkg!r} is not {PKG}")


def blocked_labels(s: dict[str, str]) -> set[str]:
    keys = (
        "settings_sync_now_all_configured",
        "settings_sync_now_this_destination",
        "settings_sync",
        "settings_keep_local_and_write_it_back",
        "settings_keep_local_and_leave_the_sheet",
        "settings_accept_remote_field_cleared",
        "settings_enable_background_sync",
        "settings_enable_background_backup",
        "settings_save_fill_and_trip_photos",
        "fuel_save",
        "vehicle_save_changes",
        "help_create_vehicle",
        "settings_clear",
        "settings_clear_debug_data",
        "settings_fill_sessions",
        "vehicle_show_all_vehicles",
        "vehicle_clear_odo",
        "vehicle_run_discovery",
        "vehicle_gallery",
        "vehicle_camera",
    )
    return {need(s, k) for k in keys}


def node_blocked(node: ET.Element, blocked: set[str]) -> bool:
    t = node.attrib.get("text") or ""
    d = node.attrib.get("content-desc") or ""
    for value in (t, d):
        if not value:
            continue
        if value in blocked or value.startswith("Sync now"):
            return True
    return False


def click_nodes(serial: str, nodes: list[ET.Element], blocked: set[str], label: str) -> bool:
    for n in nodes:
        if node_blocked(n, blocked):
            print(f"  WARN: refused {label!r}", flush=True)
            return False
        c = node_center(n)
        if c:
            tap(serial, *c)
            return True
    print(f"  WARN: not found: {label!r}", flush=True)
    return False


def click_text(serial: str, text: str, blocked: set[str]) -> bool:
    if text in blocked or text.startswith("Sync now"):
        print(f"  WARN: refused text {text!r}", flush=True)
        return False
    return click_nodes(serial, find_nodes(dump_ui(serial), text=text), blocked, text)


def click_desc(serial: str, desc: str, blocked: set[str]) -> bool:
    if desc in blocked or desc.startswith("Sync now"):
        print(f"  WARN: refused desc {desc!r}", flush=True)
        return False
    return click_nodes(serial, find_nodes(dump_ui(serial), desc=desc), blocked, desc)


def click_text_scrolled(serial: str, text: str, blocked: set[str]) -> bool:
    if click_text(serial, text, blocked):
        return True
    if not swipe_page(serial):
        return False
    return click_text(serial, text, blocked)


def click_text_fling(serial: str, text: str, blocked: set[str]) -> bool:
    """Fling until a node whose text is exactly `text`, then tap that node.

    Stops when a fling does not change the bottom labels. Does not tap Sync,
    Sync now, or a corrupt-incoming action (those strings are in `blocked`).
    """
    if text in blocked or text.startswith("Sync now"):
        print(f"  WARN: refused text {text!r}", flush=True)
        return False
    prev: tuple[str, ...] | None = None
    for _ in range(8):
        root = dump_ui(serial)
        nodes = find_nodes(root, text=text)
        if nodes:
            return click_nodes(serial, nodes, blocked, text)
        labels = bottom_labels(root)
        if prev is not None and labels == prev:
            print(f"  WARN: fling stopped; bottom labels unchanged ({text!r})", flush=True)
            return False
        prev = labels
        if not fling_page(serial):
            return False
    print(f"  WARN: not found after flings: {text!r}", flush=True)
    return False


def is_zoom_control(node: ET.Element) -> bool:
    for n in node.iter():
        t = (n.attrib.get("text") or "").strip()
        d = (n.attrib.get("content-desc") or "").strip()
        if _ZOOM_TEXT.fullmatch(t) or _ZOOM_TEXT.fullmatch(d):
            return True
    return False


def control_between(
    root: ET.Element,
    desc_a: str,
    desc_b: str,
    blocked: set[str],
) -> ET.Element | None:
    """Clickable strictly between two content-desc anchors, on their row or column.

    Landscape Fill stacks mode, shutter, gallery, save, so the shutter is not
    between Save and Pick. The nearest clickable on that column, on the side of
    Pick opposite Save, is that shutter. 1x / 2x / 4x are not candidates.
    """
    nodes_a = find_nodes(root, desc=desc_a)
    nodes_b = find_nodes(root, desc=desc_b)
    if not nodes_a or not nodes_b:
        return None
    a_n, b_n = nodes_a[0], nodes_b[0]
    a_c, b_c = node_center(a_n), node_center(b_n)
    a_b, b_b = bounds(a_n), bounds(b_n)
    if not a_c or not b_c or not a_b or not b_b:
        return None
    horizontal = abs(a_c[0] - b_c[0]) >= abs(a_c[1] - b_c[1])
    if horizontal:
        top = min(a_b[1], b_b[1]) - 40
        bot = max(a_b[3], b_b[3]) + 40
        limit = abs(a_c[0] - b_c[0]) + 80
    else:
        left = min(a_b[0], b_b[0]) - 40
        right = max(a_b[2], b_b[2]) + 40
        limit = abs(a_c[1] - b_c[1]) + 80
    lo_x, hi_x = sorted((a_c[0], b_c[0]))
    lo_y, hi_y = sorted((a_c[1], b_c[1]))
    best: ET.Element | None = None
    best_dist = 10**9
    mid = ((a_c[0] + b_c[0]) / 2, (a_c[1] + b_c[1]) / 2)
    for n in root.iter():
        if n.attrib.get("clickable") != "true" or node_blocked(n, blocked) or is_zoom_control(n):
            continue
        d = n.attrib.get("content-desc") or ""
        t = n.attrib.get("text") or ""
        if d in (desc_a, desc_b) or t in (desc_a, desc_b):
            continue
        c = node_center(n)
        b = bounds(n)
        if not c or not b:
            continue
        if horizontal:
            if not (lo_x < c[0] < hi_x and top <= c[1] <= bot):
                continue
            if (b[2] - b[0]) > limit:
                continue
        else:
            if not (lo_y < c[1] < hi_y and left <= c[0] <= right):
                continue
            if (b[3] - b[1]) > limit:
                continue
        dist = abs(c[0] - mid[0]) + abs(c[1] - mid[1])
        if dist < best_dist:
            best_dist = dist
            best = n
    if best is not None or horizontal:
        return best
    # Stacked column: shutter sits just past Pick, away from Save.
    vx, vy = b_c[0] - a_c[0], b_c[1] - a_c[1]
    best_dist = 10**9
    for n in root.iter():
        if n.attrib.get("clickable") != "true" or node_blocked(n, blocked) or is_zoom_control(n):
            continue
        d = n.attrib.get("content-desc") or ""
        t = n.attrib.get("text") or ""
        if d in (desc_a, desc_b) or t in (desc_a, desc_b):
            continue
        c = node_center(n)
        if not c:
            continue
        if abs(c[0] - b_c[0]) > 160:
            continue
        dx, dy = c[0] - b_c[0], c[1] - b_c[1]
        if dx * vx + dy * vy <= 0:
            continue
        dist = abs(dx) + abs(dy)
        if dist > 700 or dist >= best_dist:
            continue
        best_dist = dist
        best = n
    return best


def _screen_extent(root: ET.Element) -> tuple[int, int] | None:
    max_x = 0
    max_y = 0
    for n in root.iter():
        b = bounds(n)
        if not b:
            continue
        max_x = max(max_x, b[2])
        max_y = max(max_y, b[3])
    if max_x < 100 or max_y < 100:
        return None
    return max_x, max_y


def drawer_scrim_node(root: ET.Element, s: dict[str, str]) -> ET.Element | None:
    """Full-height node beside the open drawer, not the sheet that holds the labels.

    The sheet is full height and contains the drawer labels. The scrim is the
    other full-height node (the narrow strip on a phone, the wider remainder on
    a tablet). It is not looked up by a content description.
    """
    label_text = {need(s, key) for key in _DRAWER_LABEL_KEYS}
    label_bounds: list[tuple[int, int, int, int]] = []
    seen: set[str] = set()
    for n in root.iter():
        t = (n.attrib.get("text") or "").strip()
        if t not in label_text:
            continue
        b = bounds(n)
        if not b:
            continue
        seen.add(t)
        label_bounds.append(b)
    if len(seen) < 2:
        return None
    extent = _screen_extent(root)
    if extent is None:
        return None
    max_x, max_y = extent
    label_left = min(b[0] for b in label_bounds)
    label_right = max(b[2] for b in label_bounds)

    def contains_labels(b: tuple[int, int, int, int]) -> int:
        count = 0
        for lb in label_bounds:
            cx = (lb[0] + lb[2]) // 2
            cy = (lb[1] + lb[3]) // 2
            if b[0] <= cx <= b[2] and b[1] <= cy <= b[3]:
                count += 1
        return count

    candidates: list[tuple[int, int, ET.Element]] = []
    for n in root.iter():
        b = bounds(n)
        if not b:
            continue
        w = b[2] - b[0]
        h = b[3] - b[1]
        if h < int(max_y * 0.90):
            continue
        # Not the full-screen root, and not a hairline. The phone scrim is the
        # narrow remainder; a tablet scrim is wider than the sheet.
        if w >= int(max_x * 0.92) or w < 16:
            continue
        if contains_labels(b) >= 2:
            continue
        cx = (b[0] + b[2]) // 2
        if cx < label_left:
            gap = max(0, label_left - b[2])
        elif cx > label_right:
            gap = max(0, b[0] - label_right)
        else:
            continue
        clickable = 0 if n.attrib.get("clickable") == "true" else 1
        candidates.append((clickable, gap, n))
    if not candidates:
        return None
    clickable_only = [item for item in candidates if item[0] == 0]
    pool = clickable_only or candidates
    def width(item: tuple[int, int, ET.Element]) -> int:
        b = bounds(item[2])
        return (b[2] - b[0]) if b else 10**9

    pool.sort(key=lambda item: (item[1], -width(item)))
    return pool[0][2]


def open_drawer(serial: str, s: dict[str, str], blocked: set[str]) -> None:
    """Open the drawer. The scrim means it is already open. Never Back to recover."""
    menu = need(s, "nav_menu")
    root = dump_ui(serial)
    if drawer_scrim_node(root, s) is not None:
        return
    if not find_nodes(root, desc=menu):
        print("  WARN: open_drawer stopped: Menu absent and drawer closed", flush=True)
        return
    for _ in range(3):
        if not click_desc(serial, menu, blocked):
            root = dump_ui(serial)
            if drawer_scrim_node(root, s) is not None:
                return
            if not find_nodes(root, desc=menu):
                print("  WARN: open_drawer stopped: Menu absent and drawer closed", flush=True)
                return
            break
        time.sleep(0.4)
        root = dump_ui(serial)
        if drawer_scrim_node(root, s) is not None:
            return
        texts = " ".join((n.attrib.get("text") or "") for n in root.iter())
        if need(s, "nav_settings") in texts or need(s, "nav_help") in texts:
            return
    print("  WARN: open_drawer may have failed", flush=True)


def tap_drawer_scrim(serial: str, s: dict[str, str], blocked: set[str]) -> bool:
    """Close the open drawer by tapping the scrim, not Back."""
    root = dump_ui(serial)
    node = drawer_scrim_node(root, s)
    if node is None:
        print("  WARN: drawer scrim not found", flush=True)
        return False
    if node_blocked(node, blocked):
        print("  WARN: refused drawer scrim", flush=True)
        return False
    c = node_center(node)
    if not c:
        return False
    tap(serial, *c)
    return True


def click_between(
    serial: str,
    desc_a: str,
    desc_b: str,
    blocked: set[str],
    label: str,
) -> bool:
    node = control_between(dump_ui(serial), desc_a, desc_b, blocked)
    if node is None:
        print(f"  WARN: not found: {label!r}", flush=True)
        return False
    c = node_center(node)
    if not c:
        return False
    tap(serial, *c)
    return True


def dismiss_onboarding(serial: str, s: dict[str, str], blocked: set[str]) -> None:
    skip = need(s, "onboarding_skip_for_now")
    root = dump_ui(serial)
    if find_nodes(root, text=skip):
        click_text(serial, skip, blocked)
        time.sleep(0.8)


def drawer_go(serial: str, s: dict[str, str], blocked: set[str], label: str) -> bool:
    open_drawer(serial, s, blocked)
    use = blocked
    syncing = need(s, "nav_syncing")
    # af, az, and be translate settings_sync to the same text as nav_syncing.
    # That row is Syncing. Sync now and the corrupt-incoming actions stay blocked.
    if label == syncing and syncing == s.get("settings_sync"):
        use = set(blocked)
        use.discard(syncing)
    if click_text(serial, label, use):
        time.sleep(0.9)
        return True
    return False


def capture(serial: str, out: Path) -> None:
    out.parent.mkdir(parents=True, exist_ok=True)
    r = subprocess.run(
        ["adb", "-s", serial, "exec-out", "screencap", "-p"],
        check=False,
        capture_output=True,
        timeout=60,
    )
    if r.returncode != 0 or len(r.stdout) < 1000:
        err = (r.stderr or b"").decode("utf-8", "replace")
        print(f"  WARN: screencap failed for {out.name}: {err.strip()}", flush=True)
        return
    png = Path(f"/tmp/ve-cap-{_tmp_token(serial)}-{out.name}.png")
    png.write_bytes(r.stdout)
    try:
        if shutil.which("convert"):
            subprocess.run(["convert", str(png), "-quality", "90", str(out)], check=True)
        else:
            from PIL import Image

            Image.open(png).convert("RGB").save(out, "JPEG", quality=90)
    finally:
        png.unlink(missing_ok=True)
    print(f"  shot {out}", flush=True)


def window_has(window: ET.Element, *, text: str | None = None, desc: str | None = None) -> bool:
    for n in window.iter():
        if text is not None and (n.attrib.get("text") or "") == text:
            return True
        if desc is not None and (n.attrib.get("content-desc") or "") == desc:
            return True
    return False


def _parent_map(root: ET.Element) -> dict[ET.Element, ET.Element]:
    parents: dict[ET.Element, ET.Element] = {}
    for parent in root.iter():
        for child in list(parent):
            parents[child] = parent
    return parents


def _name_list_up(root: ET.Element, s: dict[str, str]) -> bool:
    """Title plus a vehicle-name row, even when the Fill controls are in the same dump.

    The instruction line is the title alone. A name row is clickable text that
    is not pack chrome, under a title node. Show all vehicles is not a name.
    """
    title = need(s, "vehicle_vehicle_not_identified")
    show_all = need(s, "vehicle_show_all_vehicles")
    chrome = set(s.values())
    parents = _parent_map(root)
    title_boxes = [b for b in (bounds(n) for n in find_nodes(root, text=title)) if b]
    if not title_boxes:
        return False
    for n in root.iter():
        text = (n.attrib.get("text") or "").strip()
        if not text or text == title or text == show_all or text in chrome or _ZOOM_TEXT.match(text):
            continue
        clickable = n.attrib.get("clickable") == "true"
        if not clickable:
            parent = parents.get(n)
            clickable = parent is not None and parent.attrib.get("clickable") == "true"
        if not clickable:
            continue
        b = bounds(n)
        if not b:
            continue
        for tb in title_boxes:
            if b[1] + 8 < tb[1]:
                continue
            if b[2] < tb[0] or b[0] > tb[2]:
                continue
            if b[1] - tb[3] > 800:
                continue
            return True
    return False


def classify_title(root: ET.Element, s: dict[str, str], blocked: set[str]) -> tuple[bool, bool]:
    """Name list is the title plus a vehicle name. A Toast is not the list.

    The Fill instruction line uses the same title and shares the activity window
    with the shutter, so the shutter is not what decides. Pack chrome, not
    `blocked`, is what a vehicle name must not be.
    """
    del blocked  # signature kept for callers; name-list chrome is the pack strings
    title = need(s, "vehicle_vehicle_not_identified")
    toast = False
    windows = list(root) or [root]
    for w in windows:
        cls = w.attrib.get("class") or ""
        if "Toast" in cls and window_has(w, text=title):
            toast = True
            continue
        if not window_has(w, text=title):
            for n in w.iter():
                if "Toast" in (n.attrib.get("class") or "") and window_has(n, text=title):
                    toast = True
    return _name_list_up(root, s), toast


def live_shutter_present(root: ET.Element, s: dict[str, str], blocked: set[str], settle: str) -> bool:
    if settle == "fill":
        return control_between(
            root,
            need(s, "fuel_save"),
            need(s, "fill_pick_an_existing_image"),
            blocked,
        ) is not None
    return control_between(
        root,
        need(s, "nav_start_trip"),
        need(s, "trip_personal_now_at_this_location"),
        blocked,
    ) is not None


def wait_after_shutter(serial: str, s: dict[str, str], blocked: set[str], *, settle: str) -> str:
    """One shutter already happened. 'dialog' only when the list dialog is up.

    Retry (Fill) or the live shutter again (Start trip), with no dialog, stops
    the vehicle-list shot. An empty-list toast is not that shot.
    """
    retry = need(s, "settings_retry")
    cancel = need(s, "ui_cancel_processing")
    deadline = time.time() + 20
    settled_at: float | None = None
    saw_toast = False
    saw_retry = False
    while time.time() < deadline:
        root = dump_ui(serial)
        dialog, toast = classify_title(root, s, blocked)
        if toast:
            saw_toast = True
        if dialog:
            return "dialog"
        has_retry = bool(find_nodes(root, desc=retry) or find_nodes(root, text=retry))
        processing = bool(find_nodes(root, desc=cancel) or find_nodes(root, text=cancel))
        has_live = live_shutter_present(root, s, blocked, settle)
        if has_retry:
            saw_retry = True
        settled = (not processing) and (has_retry if settle == "fill" else has_live)
        if settled:
            if settled_at is None:
                settled_at = time.time()
            elif time.time() - settled_at >= 2.5:
                if saw_toast:
                    return "toast"
                if saw_retry or settle == "fill":
                    return "retry"
                return "live"
        elif processing:
            settled_at = None
        time.sleep(0.4)
    if saw_toast:
        return "toast"
    if saw_retry:
        return "retry"
    return "timeout"


def dismiss_vehicle_dialog(serial: str, s: dict[str, str], blocked: set[str]) -> None:
    """Back dismisses the dialog. Do not tap a name or Show all vehicles."""
    for _ in range(2):
        dialog, _toast = classify_title(dump_ui(serial), s, blocked)
        if not dialog:
            return
        press_back(serial, s)


def click_pump_mode(serial: str, s: dict[str, str], blocked: set[str]) -> bool:
    """Tap the ↕ control. Portrait: on the shutter row, right of Pick. Landscape: on the shutter column."""
    root = dump_ui(serial)
    save_desc = need(s, "fuel_save")
    pick_desc = need(s, "fill_pick_an_existing_image")
    shutter = control_between(root, save_desc, pick_desc, blocked)
    picks = find_nodes(root, desc=pick_desc)
    saves = find_nodes(root, desc=save_desc)
    if not picks or not saves:
        print("  WARN: pump mode switch not found (no Save / Pick row)", flush=True)
        return False
    size = screen_size(serial)
    landscape = bool(size and size[0] > size[1])
    skip_desc = {
        save_desc,
        pick_desc,
        need(s, "settings_retry"),
        need(s, "ui_cancel_processing"),
        need(s, "nav_start_trip"),
    }
    best: tuple[int, int] | None = None
    if landscape:
        anchor = node_center(shutter) if shutter is not None else None
        if anchor is None:
            print("  WARN: pump mode switch not found (no shutter anchor)", flush=True)
            return False
        ax, ay = anchor
        best_dist = 10**9
        for n in root.iter():
            if n.attrib.get("clickable") != "true" or node_blocked(n, blocked) or is_zoom_control(n):
                continue
            if n is shutter:
                continue
            d = n.attrib.get("content-desc") or ""
            t = n.attrib.get("text") or ""
            if d in skip_desc or t in skip_desc:
                continue
            c = node_center(n)
            if c is None:
                continue
            x, y = c
            if not (y < ay - 20 and abs(x - ax) < 160):
                continue
            dist = abs(x - ax) + abs(y - ay)
            if dist < best_dist:
                best_dist = dist
                best = c
    else:
        pb = bounds(picks[0])
        sb = bounds(saves[0])
        pc = node_center(picks[0])
        if not pb or not sb or not pc:
            return False
        top = min(pb[1], sb[1]) - 40
        bot = max(pb[3], sb[3]) + 40
        pick_w = pb[2] - pb[0]
        best_x = 10**9
        for n in root.iter():
            if n.attrib.get("clickable") != "true" or node_blocked(n, blocked) or is_zoom_control(n):
                continue
            d = n.attrib.get("content-desc") or ""
            t = n.attrib.get("text") or ""
            if d in skip_desc or t in skip_desc:
                continue
            c = node_center(n)
            b = bounds(n)
            if c is None or not b:
                continue
            if b[0] <= pc[0] <= b[2] and b[1] <= pc[1] <= b[3]:
                continue
            if not (top <= c[1] <= bot):
                continue
            if c[0] <= pb[2]:
                continue
            if (b[2] - b[0]) > max(pick_w * 2, 400):
                continue
            if c[0] < best_x:
                best_x = c[0]
                best = c
    if best is None:
        print("  WARN: pump mode switch not found beside shutter", flush=True)
        return False
    tap(serial, *best)
    return True


def click_labeled_field(serial: str, label: str, blocked: set[str]) -> bool:
    root = dump_ui(serial)
    labels = find_nodes(root, text=label)
    if not labels:
        print(f"  WARN: field label not found: {label!r}", flush=True)
        return False
    lb = bounds(labels[0])
    if lb is None:
        return False
    lx1, ly1, lx2, ly2 = lb
    for n in root.iter():
        if n.attrib.get("clickable") != "true" or node_blocked(n, blocked):
            continue
        b = bounds(n)
        if not b:
            continue
        x1, y1, x2, y2 = b
        if x1 <= lx2 and x2 >= lx1 and y1 <= ly2 and y2 >= ly1:
            c = node_center(n)
            if c:
                tap(serial, *c)
                return True
    c = node_center(labels[0])
    if c:
        tap(serial, *c)
        return True
    return False


def screen_has_text(root: ET.Element, text: str) -> bool:
    if not text:
        return False
    for n in root.iter():
        for value in ((n.attrib.get("text") or ""), (n.attrib.get("content-desc") or "")):
            if value == text or text in value:
                return True
    return False


def is_fills_count_line(text: str, template: str) -> bool:
    """True for the formatted `fill_s_1_s_fills` line, such as `194 fills`."""
    parts = re.split(r"%(?:\d+\$)?[sd]", template)
    if len(parts) == 1:
        return text.strip() == template.strip()
    pat = r"\d+".join(re.escape(p) for p in parts)
    return re.fullmatch(pat, text.strip()) is not None


def is_card_title(text: str) -> bool:
    """Expense and fill cards title the row as `name · yyyy-MM-dd`."""
    return "\u00b7" in text and _CARD_TITLE_DATE.search(text) is not None


def tap_card_title(
    serial: str,
    blocked: set[str],
    fills_count_template: str | None = None,
) -> bool:
    """Tap the card title text. That node is not clickable. Not the fills-count line."""
    root = dump_ui(serial)
    for n in root.iter():
        if node_blocked(n, blocked):
            continue
        t = (n.attrib.get("text") or "").strip()
        if not t:
            continue
        if fills_count_template and is_fills_count_line(t, fills_count_template):
            continue
        if not is_card_title(t):
            continue
        c = node_center(n)
        if not c:
            continue
        tap(serial, *c)
        return True
    return False


def _jpeg_kept(path: Path) -> bool:
    return path.is_file() and path.stat().st_size >= 1000


def _shots_kept(out_dir: Path, selected: set[str]) -> bool:
    return all(_jpeg_kept(out_dir / name) for name in selected)


def _write_flat_jpeg(path: Path) -> bool:
    """Solid gray, not a dashboard. The name list stays up on a flat picture."""
    try:
        from PIL import Image

        Image.new("RGB", (320, 240), (190, 190, 190)).save(path, "JPEG", quality=85)
        return _jpeg_kept(path) or (path.is_file() and path.stat().st_size >= 100)
    except Exception:
        pass
    if shutil.which("convert"):
        r = subprocess.run(
            ["convert", "-size", "320x240", "xc:#bebebe", str(path)],
            check=False,
        )
        return r.returncode == 0 and path.is_file() and path.stat().st_size >= 100
    return False


def _ensure_not_a_dash(serial: str) -> bool:
    """Push a flat JPEG only when Pictures/not-a-dash.jpg is absent. Never delete."""
    if run(serial, "shell", "test", "-f", _NOT_A_DASH_REMOTE, check=False).returncode == 0:
        return True
    run(serial, "shell", "mkdir", "-p", "/sdcard/Pictures", check=False)
    local = Path(f"/tmp/ve-not-a-dash-{_tmp_token(serial)}.jpg")
    if not _write_flat_jpeg(local):
        return False
    pushed = run(serial, "push", str(local), _NOT_A_DASH_REMOTE, check=False)
    if pushed.returncode != 0:
        return False
    run(
        serial,
        "shell",
        "am",
        "broadcast",
        "-a",
        "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
        "-d",
        "file:///sdcard/Pictures/not-a-dash.jpg",
        check=False,
    )
    run(serial, "shell", "cmd", "media", "scan", _NOT_A_DASH_REMOTE, check=False)
    time.sleep(1.0)
    return run(serial, "shell", "test", "-f", _NOT_A_DASH_REMOTE, check=False).returncode == 0


def _tap_matching(
    serial: str,
    match,
    blocked: set[str],
) -> bool:
    root = dump_ui(serial)
    parents = _parent_map(root)
    extent = _screen_extent(root)
    max_w = extent[0] if extent else 10**9
    for n in root.iter():
        if node_blocked(n, blocked):
            continue
        text = n.attrib.get("text") or ""
        desc = n.attrib.get("content-desc") or ""
        if not match(text, desc):
            continue
        target = n
        if target.attrib.get("clickable") != "true":
            parent = parents.get(n)
            climbed: ET.Element | None = None
            while parent is not None:
                if parent.attrib.get("clickable") == "true":
                    climbed = parent
                    break
                parent = parents.get(parent)
            if climbed is not None:
                b = bounds(climbed)
                if b and (b[2] - b[0]) <= int(max_w * 0.92):
                    target = climbed
        if node_blocked(target, blocked):
            continue
        c = node_center(target)
        if not c:
            continue
        tap(serial, *c)
        return True
    return False


def _tap_matching_scrolled(serial: str, match, blocked: set[str], tries: int = 6) -> bool:
    for i in range(tries):
        if _tap_matching(serial, match, blocked):
            return True
        if i + 1 == tries or not swipe_page(serial):
            return False
    return False


def _is_pictures_label(text: str, desc: str) -> bool:
    for raw in (text, desc):
        if raw.strip().rstrip("/").lower() == "pictures":
            return True
    return False


def _is_albums_tab(text: str, desc: str) -> bool:
    for raw in (text, desc):
        if raw.strip().lower() in {"albums", "folders", "collections"}:
            return True
    return False


def _is_not_a_dash_label(text: str, desc: str) -> bool:
    return "not-a-dash" in f"{text}\n{desc}".lower()


def _open_pictures_album(serial: str, blocked: set[str]) -> bool:
    if _tap_matching_scrolled(serial, _is_pictures_label, blocked):
        return True
    if not _tap_matching(serial, _is_albums_tab, blocked):
        return False
    time.sleep(0.7)
    return _tap_matching_scrolled(serial, _is_pictures_label, blocked)


def _back_to_app(serial: str, s: dict[str, str]) -> None:
    """Leave a system picker. Back while the drawer scrim is up is still refused."""
    for _ in range(5):
        if resumed_package(serial) == PKG:
            return
        if drawer_scrim_node(dump_ui(serial), s) is not None:
            print("  STOP: refused Back while the drawer scrim is showing", flush=True)
            raise StopRun("refused input keyevent 4 while the drawer scrim is showing")
        sh(serial, "input keyevent 4")
        time.sleep(0.45)
    pkg = resumed_package(serial)
    if pkg != PKG:
        print(f"  STOP: resumed activity is {pkg!r}, not {PKG}", flush=True)
        raise StopRun(f"resumed activity {pkg!r} is not {PKG}")


def _leave_picker(serial: str, s: dict[str, str], blocked: set[str]) -> None:
    if resumed_package(serial) != PKG:
        _back_to_app(serial, s)
    dismiss_vehicle_dialog(serial, s, blocked)


def _wait_name_list(serial: str, s: dict[str, str], blocked: set[str]) -> bool:
    deadline = time.time() + 25
    while time.time() < deadline:
        try:
            root = dump_ui(serial)
        except ET.ParseError:
            time.sleep(0.4)
            continue
        dialog, _toast = classify_title(root, s, blocked)
        if dialog:
            return True
        time.sleep(0.4)
    return False


def _capture_r3b_from_flat_picture(
    serial: str,
    s: dict[str, str],
    blocked: set[str],
    notes: dict[str, str],
    do_capture,
) -> None:
    """Name list from Pictures/not-a-dash.jpg. No shutter, no name tap, no Save."""
    name = "r3b-fill-vehicle-not-identified.jpg"
    if not _ensure_not_a_dash(serial):
        notes[name] = "Pictures/not-a-dash.jpg was absent and could not be pushed"
        return
    if not click_desc(serial, need(s, "fill_pick_an_existing_image"), blocked):
        notes[name] = "Pick an existing image not found"
        return
    time.sleep(1.0)
    if not _open_pictures_album(serial, blocked):
        notes[name] = "Pictures was not opened"
        _leave_picker(serial, s, blocked)
        return
    time.sleep(0.8)
    if not _tap_matching_scrolled(serial, _is_not_a_dash_label, blocked):
        notes[name] = "not-a-dash.jpg was not tapped"
        _leave_picker(serial, s, blocked)
        return
    if not _wait_name_list(serial, s, blocked):
        notes[name] = "name list was not up after not-a-dash.jpg"
        _leave_picker(serial, s, blocked)
        return
    do_capture(name)
    dismiss_vehicle_dialog(serial, s, blocked)


def navigate_and_capture(
    serial: str,
    out_dir: Path,
    pack: Pack,
    selected: set[str],
    replace: bool,
) -> dict[str, str]:
    notes: dict[str, str] = {}
    s = load_strings(pack.pref)
    blocked = blocked_labels(s)
    print(f"=== {pack.pref} → {out_dir}", flush=True)
    sh(serial, f"am start -n {ACTIVITY} -a android.intent.action.MAIN -c android.intent.category.LAUNCHER")
    time.sleep(2.5)
    dismiss_onboarding(serial, s, blocked)

    def shot(name: str) -> Path:
        return out_dir / name

    def needs_capture(name: str) -> bool:
        if name not in selected:
            return False
        if replace:
            return True
        return not _jpeg_kept(shot(name))

    def do_capture(name: str) -> None:
        if name not in selected:
            return
        dest = shot(name)
        if not replace and _jpeg_kept(dest):
            print(f"  keep existing {dest.name}", flush=True)
            return
        capture(serial, dest)

    try:
        _navigate(serial, s, blocked, notes, do_capture, needs_capture, selected, replace)
    except StopRun as e:
        print(f"  STOP: {e}", flush=True)
        notes["stopped"] = str(e)

    missing = [
        name
        for name in SHOTS
        if name in selected and not _jpeg_kept(shot(name))
    ]
    for name in missing:
        notes.setdefault(name, "missing or too small")
        print(f"  MISSING {name}", flush=True)
    return notes


def _navigate(
    serial: str,
    s: dict[str, str],
    blocked: set[str],
    notes: dict[str, str],
    do_capture,
    needs_capture,
    selected: set[str],
    replace: bool,
) -> None:
    del replace  # do_capture and needs_capture already apply it

    def want(name: str) -> bool:
        return name in selected

    def needs_any(names: tuple[str, ...]) -> bool:
        return any(needs_capture(name) for name in names)

    # Drawer shot, then the scrim. Later sections may open the drawer to navigate.
    if needs_capture("01-drawer.jpg"):
        open_drawer(serial, s, blocked)
        do_capture("01-drawer.jpg")
        if not tap_drawer_scrim(serial, s, blocked):
            notes["01-drawer.jpg"] = "drawer scrim was not tapped after the drawer shot"
            raise StopRun("drawer scrim missing after the drawer shot")

    # Fill. r3b is the flat gallery picture. r4 and r5 stay on the shutter.
    if needs_any(SHOT_GROUPS["fill"]):
        if not drawer_go(serial, s, blocked, need(s, "nav_fill")):
            for name in SHOT_GROUPS["fill"]:
                if needs_capture(name):
                    notes.setdefault(name, "Fill drawer item not found")
        if want("r3-fill-odo-live.jpg"):
            do_capture("r3-fill-odo-live.jpg")
        r3b = "r3b-fill-vehicle-not-identified.jpg"
        if want(r3b) and needs_capture(r3b):
            _capture_r3b_from_flat_picture(serial, s, blocked, notes, do_capture)
        elif want(r3b):
            do_capture(r3b)
        r4 = "r4-fill-odo-result.jpg"
        r5 = "r5-fill-pump-result.jpg"
        if want(r4) or want(r5):
            if click_between(
                serial,
                need(s, "fuel_save"),
                need(s, "fill_pick_an_existing_image"),
                blocked,
                "fill shutter",
            ):
                wait_after_shutter(serial, s, blocked, settle="fill")
                dismiss_vehicle_dialog(serial, s, blocked)
            else:
                shutter_missing = (
                    "fill shutter between Save and Pick an existing image not found"
                )
                if needs_capture(r4):
                    notes.setdefault(r4, shutter_missing)
                if needs_capture(r5):
                    notes.setdefault(r5, shutter_missing)
            failed = need(s, "fill_photo_save_failed_entry_will_have_no_photo")
            unidentified = need(s, "vehicle_vehicle_not_identified")

            def fill_result_blocked(root: ET.Element) -> str | None:
                if screen_has_text(root, failed):
                    return "photo save failed is showing; not written"
                if screen_has_text(root, unidentified):
                    return "vehicle not identified is showing; not written"
                return None

            def capture_fill_result(name: str) -> None:
                if not want(name):
                    return
                if not needs_capture(name):
                    do_capture(name)
                    return
                why = fill_result_blocked(dump_ui(serial))
                if why:
                    notes[name] = why
                    print(f"  WARN: {name} not written; {why}", flush=True)
                    return
                do_capture(name)

            if want(r4):
                capture_fill_result(r4)
            if needs_capture(r5):
                why_r5 = fill_result_blocked(dump_ui(serial))
                if why_r5:
                    notes[r5] = why_r5
                    print(f"  WARN: {r5} not written; {why_r5}", flush=True)
                else:
                    if not click_pump_mode(serial, s, blocked):
                        notes.setdefault(
                            r5,
                            "pump mode control not found; captured current Fill",
                        )
                    else:
                        time.sleep(0.6)
                    capture_fill_result(r5)
            elif want(r5):
                do_capture(r5)

    # Manage Vehicles. Do not save, clear, or run discovery.
    if needs_any(SHOT_GROUPS["vehicles"]):
        if needs_capture("r1-manage-vehicles-crops.jpg") or needs_capture("03b-manage-vehicles-new.jpg"):
            drawer_go(serial, s, blocked, need(s, "nav_manage_vehicles"))
            if want("r1-manage-vehicles-crops.jpg"):
                do_capture("r1-manage-vehicles-crops.jpg")
            if needs_capture("03b-manage-vehicles-new.jpg"):
                if click_labeled_field(serial, need(s, "fuel_vehicle"), blocked):
                    time.sleep(0.4)
                if click_text(serial, need(s, "vehicle_add_new_vehicle"), blocked):
                    time.sleep(0.6)
                do_capture("03b-manage-vehicles-new.jpg")
                press_back(serial, s)
        if needs_capture("r2-manage-vehicles-landmarks.jpg"):
            drawer_go(serial, s, blocked, need(s, "nav_manage_vehicles"))
            if not click_text_scrolled(serial, need(s, "vehicle_show_landmarks"), blocked):
                notes["r2-manage-vehicles-landmarks.jpg"] = (
                    "Show Landmarks not found; captured Manage Vehicles"
                )
            else:
                time.sleep(0.6)
            do_capture("r2-manage-vehicles-landmarks.jpg")
            press_back(serial, s)

    # New expense. Do not shutter (that would write a receipt) and do not save.
    if needs_capture("r6-new-expense.jpg"):
        drawer_go(serial, s, blocked, need(s, "nav_new_expense"))
        do_capture("r6-new-expense.jpg")
        press_back(serial, s)

    # Reports. A sub-screen whose files were not selected is not opened.
    if needs_any(SHOT_GROUPS["reports"]):
        if (
            needs_capture("06-reports.jpg")
            or needs_capture("05-expense-list.jpg")
            or needs_capture("expense-edit.jpg")
        ):
            drawer_go(serial, s, blocked, need(s, "nav_reports"))
            if want("06-reports.jpg"):
                do_capture("06-reports.jpg")
            if needs_capture("05-expense-list.jpg") or needs_capture("expense-edit.jpg"):
                if not click_text_scrolled(serial, need(s, "reports_expenses_list"), blocked):
                    click_text_scrolled(serial, need(s, "nav_expense_list"), blocked)
                time.sleep(0.6)
                if want("05-expense-list.jpg"):
                    do_capture("05-expense-list.jpg")
                if needs_capture("expense-edit.jpg"):
                    opened = tap_card_title(serial, blocked)
                    time.sleep(0.6)
                    do_capture("expense-edit.jpg")
                    if not opened:
                        notes["expense-edit.jpg"] = "no expense row; captured the list"
                elif want("expense-edit.jpg"):
                    do_capture("expense-edit.jpg")
                press_back(serial, s)
            elif want("06-reports.jpg"):
                press_back(serial, s)

        if needs_capture("time-based-reports.jpg") or needs_capture(
            "time-based-reports-scrolled.jpg"
        ):
            drawer_go(serial, s, blocked, need(s, "nav_reports"))
            if click_text_scrolled(serial, need(s, "reports_time_based_reports"), blocked):
                time.sleep(0.8)
            if want("time-based-reports.jpg"):
                do_capture("time-based-reports.jpg")
            if needs_capture("time-based-reports-scrolled.jpg"):
                swipe_page(serial)
                do_capture("time-based-reports-scrolled.jpg")
            elif want("time-based-reports-scrolled.jpg"):
                do_capture("time-based-reports-scrolled.jpg")
            press_back(serial, s)

        if needs_capture("fill-history.jpg") or needs_capture("fuel-edit.jpg"):
            drawer_go(serial, s, blocked, need(s, "nav_reports"))
            if click_text_scrolled(serial, need(s, "reports_fill_history"), blocked):
                time.sleep(0.6)
            if want("fill-history.jpg"):
                do_capture("fill-history.jpg")
            if needs_capture("fuel-edit.jpg"):
                opened = tap_card_title(serial, blocked, need(s, "fill_s_1_s_fills"))
                time.sleep(0.6)
                do_capture("fuel-edit.jpg")
                if not opened:
                    notes["fuel-edit.jpg"] = "no fill row; captured fill history"
            press_back(serial, s)

        if needs_capture("trip-miles.jpg"):
            drawer_go(serial, s, blocked, need(s, "nav_reports"))
            if click_text_scrolled(serial, need(s, "reports_trip_miles"), blocked):
                time.sleep(0.6)
            do_capture("trip-miles.jpg")
            press_back(serial, s)

    # Start trip: live shot, then the shutter between the two labeled controls. Do not tap either.
    if needs_capture("start-trip.jpg"):
        drawer_go(serial, s, blocked, need(s, "nav_start_trip"))
        do_capture("start-trip.jpg")
        if click_between(
            serial,
            need(s, "nav_start_trip"),
            need(s, "trip_personal_now_at_this_location"),
            blocked,
            "start trip shutter",
        ):
            wait_after_shutter(serial, s, blocked, settle="trip")
            dismiss_vehicle_dialog(serial, s, blocked)
        else:
            notes["start-trip.jpg"] = (
                "shutter between Start trip and Personal now at this location not found"
            )
        press_back(serial, s)

    # Sync screens. Exact card titles only. Do not tap Sync now or the background switches.
    sheet_shots = (
        "syncing-hub.jpg",
        "08-spreadsheet-sync.jpg",
        "09-spreadsheet-provider-picker.jpg",
        "10-spreadsheet-google-form.jpg",
    )
    photo_shots = (
        "11-photo-backup.jpg",
        "12-photo-provider-picker.jpg",
        "13-photo-google-form.jpg",
    )
    if needs_any(sheet_shots):
        drawer_go(serial, s, blocked, need(s, "nav_syncing"))
        if want("syncing-hub.jpg"):
            do_capture("syncing-hub.jpg")
        if needs_any(sheet_shots[1:]):
            opened_card = click_text_fling(serial, need(s, "settings_spreadsheet_sync"), blocked)
            if opened_card:
                time.sleep(0.8)
            elif want("08-spreadsheet-sync.jpg"):
                notes["08-spreadsheet-sync.jpg"] = "Spreadsheet sync card not found"
            if want("08-spreadsheet-sync.jpg"):
                do_capture("08-spreadsheet-sync.jpg")
            opened_picker = False
            opened_form = False
            if needs_capture("09-spreadsheet-provider-picker.jpg") or needs_capture(
                "10-spreadsheet-google-form.jpg"
            ):
                opened_picker = click_text_scrolled(
                    serial, need(s, "settings_add_spreadsheet_destination"), blocked
                )
                if opened_picker:
                    time.sleep(0.6)
                if want("09-spreadsheet-provider-picker.jpg"):
                    do_capture("09-spreadsheet-provider-picker.jpg")
                if needs_capture("10-spreadsheet-google-form.jpg"):
                    opened_form = click_text(serial, need(s, "settings_google_sheets"), blocked)
                    if opened_form:
                        time.sleep(0.8)
                    do_capture("10-spreadsheet-google-form.jpg")
                elif want("10-spreadsheet-google-form.jpg"):
                    do_capture("10-spreadsheet-google-form.jpg")
            # Full path backs twice from the Google form and lands on the card.
            if opened_form:
                press_back(serial, s)
                press_back(serial, s)
            elif opened_picker:
                press_back(serial, s)

    if needs_any(photo_shots):
        drawer_go(serial, s, blocked, need(s, "nav_syncing"))
        opened_card = click_text_fling(serial, need(s, "settings_photo_backup"), blocked)
        if opened_card:
            time.sleep(0.8)
        elif want("11-photo-backup.jpg"):
            notes["11-photo-backup.jpg"] = "Photo backup card not found"
        if want("11-photo-backup.jpg"):
            do_capture("11-photo-backup.jpg")
        opened_picker = False
        opened_form = False
        if needs_capture("12-photo-provider-picker.jpg") or needs_capture(
            "13-photo-google-form.jpg"
        ):
            opened_picker = click_text_scrolled(
                serial, need(s, "settings_add_photo_destination"), blocked
            )
            if opened_picker:
                time.sleep(0.6)
            if want("12-photo-provider-picker.jpg"):
                do_capture("12-photo-provider-picker.jpg")
            if needs_capture("13-photo-google-form.jpg"):
                opened_form = click_text(serial, need(s, "settings_google_drive"), blocked)
                if opened_form:
                    time.sleep(0.8)
                do_capture("13-photo-google-form.jpg")
            elif want("13-photo-google-form.jpg"):
                do_capture("13-photo-google-form.jpg")
        if opened_form:
            press_back(serial, s)
            press_back(serial, s)
        elif opened_picker:
            press_back(serial, s)

    if needs_capture("07-settings.jpg") or needs_capture("07b-settings-more.jpg"):
        drawer_go(serial, s, blocked, need(s, "nav_settings"))
        if want("07-settings.jpg"):
            do_capture("07-settings.jpg")
        if needs_capture("07b-settings-more.jpg"):
            swipe_page(serial)
            do_capture("07b-settings-more.jpg")
        elif want("07b-settings-more.jpg"):
            do_capture("07b-settings-more.jpg")
        press_back(serial, s)

    if needs_capture("14-help.jpg"):
        drawer_go(serial, s, blocked, need(s, "nav_help"))
        do_capture("14-help.jpg")
        press_back(serial, s)
    if needs_capture("15-about.jpg"):
        drawer_go(serial, s, blocked, need(s, "nav_about"))
        do_capture("15-about.jpg")
        press_back(serial, s)


def _system_exit_message(exc: SystemExit) -> str:
    return exc.code if isinstance(exc.code, str) else str(exc)


def _write_notes(form: str, all_notes: dict[str, dict[str, str]]) -> None:
    note_path = Path(f"/tmp/manual-capture-notes-{form}.txt")
    lines: list[str] = []
    if len(all_notes) == 1:
        only = next(iter(all_notes.values()))
        lines = [f"{k}: {v}" for k, v in only.items()]
    else:
        for tag, notes in all_notes.items():
            lines.append(f"## {tag}")
            lines.extend(f"{k}: {v}" for k, v in notes.items())
    note_path.write_text("\n".join(lines) + ("\n" if lines else ""), encoding="utf-8")
    print(f"notes → {note_path}", flush=True)


def run_device(
    serial: str,
    form: str,
    packs: list[Pack],
    selected: set[str],
    replace: bool,
) -> bool:
    """One device. Restores English if a pack was opened. Returns True when stopped."""
    en = pack_by_pref()["en"]
    changed = False
    all_notes: dict[str, dict[str, str]] = {}
    stopped = False
    try:
        for pack in packs:
            out_dir = output_dir(pack, form)
            if not replace and _shots_kept(out_dir, selected):
                print(f"=== {pack.pref} kept; {form} not opened", flush=True)
                continue
            changed = True
            try:
                set_language(serial, pack)
            except SystemExit as e:
                msg = _system_exit_message(e)
                print(f"  WARN: {msg}; continuing", flush=True)
                all_notes[pack.pref] = {"language": msg}
                continue
            notes = navigate_and_capture(serial, out_dir, pack, selected, replace)
            all_notes[pack.pref] = notes
            if notes.get("stopped"):
                stopped = True
    except SystemExit as e:
        stopped = True
        msg = _system_exit_message(e)
        print(f"  STOP: {serial} {form}: {msg}", flush=True)
        all_notes.setdefault("run", {})["stopped"] = msg
    except Exception as e:
        stopped = True
        print(f"  STOP: {serial} {form}: {e}", flush=True)
        all_notes.setdefault("run", {})["stopped"] = str(e)
    finally:
        if changed:
            try:
                set_language(serial, en)
            except SystemExit as e:
                msg = _system_exit_message(e)
                print(f"  WARN: restore en failed on {serial}: {msg}", flush=True)
                all_notes.setdefault("en", {})["language"] = msg
                stopped = True
    _write_notes(form, all_notes)
    return stopped


def _assign_two_forms(serials: list[str]) -> list[tuple[str, str]] | None:
    """Larger physical diagonal is the tablet. Gap under 1.5 inches is an error."""
    measured: list[tuple[float, str]] = []
    for serial in serials:
        diag = device_diagonal_inches(serial)
        if diag is None:
            print(
                f"ERROR: physical wm size or density unavailable for {serial}",
                file=sys.stderr,
            )
            return None
        measured.append((diag, serial))
    measured.sort(key=lambda item: (item[0], item[1]))
    gap = measured[1][0] - measured[0][0]
    if gap < _DIAGONAL_GAP_IN:
        print(
            f"ERROR: physical diagonal gap {gap:.2f} in is under {_DIAGONAL_GAP_IN:.1f} "
            f"({measured[0][1]} {measured[0][0]:.2f} in, "
            f"{measured[1][1]} {measured[1][0]:.2f} in)",
            file=sys.stderr,
        )
        return None
    phone, tablet = measured[0], measured[1]
    print(
        f"phone {phone[1]} {phone[0]:.2f} in; tablet {tablet[1]} {tablet[0]:.2f} in",
        flush=True,
    )
    return [(phone[1], "phone"), (tablet[1], "tablet")]


def _single_form_size_ok(serial: str, form: str) -> bool:
    """Today's check: last `wm size` is 1440x3120 or 1600x2560, either orientation."""
    expect_w, expect_h = (1440, 3120) if form == "phone" else (1600, 2560)
    got = screen_size(serial)
    if got not in ((expect_w, expect_h), (expect_h, expect_w)):
        shown = f"{got[0]}x{got[1]}" if got else "unavailable"
        print(
            f"ERROR: wm size {shown} is not {expect_w}x{expect_h} for --form {form}",
            file=sys.stderr,
        )
        return False
    return True


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--form", choices=["phone", "tablet"])
    ap.add_argument("--serial", action="append", required=True, dest="serials")
    ap.add_argument(
        "--locales",
        default=None,
        help="all, or comma-separated AppLanguage pref tags (default: en, or all for two serials)",
    )
    ap.add_argument(
        "--shots",
        default=None,
        help="comma list of group names or filenames; omitted means every shot",
    )
    ap.add_argument(
        "--replace",
        action="store_true",
        help="overwrite the selected shots",
    )
    args = ap.parse_args()
    serials = list(args.serials)
    if len(serials) == 1:
        if args.form is None:
            print("ERROR: one --serial requires --form phone|tablet", file=sys.stderr)
            return 1
    elif len(serials) == 2:
        if args.form is not None:
            print("ERROR: two --serial values do not take --form", file=sys.stderr)
            return 1
    else:
        print("ERROR: pass one or two --serial values", file=sys.stderr)
        return 1
    if len(set(serials)) != len(serials):
        print("ERROR: --serial values must differ", file=sys.stderr)
        return 1
    # Unknown shots and locales exit before any device read.
    selected = parse_shots(args.shots)
    locale_spec = args.locales
    if locale_spec is None:
        locale_spec = "all" if len(serials) == 2 else "en"
    packs = packs_for_locales_arg(locale_spec)
    for pack in packs:
        load_strings(pack.pref)

    devs = subprocess.run(["adb", "devices"], capture_output=True, text=True).stdout
    for serial in serials:
        if serial not in devs:
            print(f"ERROR: {serial} not in adb devices", file=sys.stderr)
            return 1

    if len(serials) == 1:
        if not _single_form_size_ok(serials[0], args.form):
            return 1
        assigned = [(serials[0], args.form)]
    else:
        assigned = _assign_two_forms(serials)
        if assigned is None:
            return 1

    if len(assigned) == 1:
        serial, form = assigned[0]
        return 1 if run_device(serial, form, packs, selected, args.replace) else 0

    stopped = {"flag": False}
    lock = threading.Lock()

    def worker(serial: str, form: str) -> None:
        try:
            device_stopped = run_device(serial, form, packs, selected, args.replace)
        except Exception as e:
            print(f"  STOP: {serial} {form}: {e}", flush=True)
            device_stopped = True
        if device_stopped:
            with lock:
                stopped["flag"] = True

    threads = [
        threading.Thread(target=worker, args=(serial, form), name=form)
        for serial, form in assigned
    ]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()
    return 1 if stopped["flag"] else 0


if __name__ == "__main__":
    raise SystemExit(main())
