#!/usr/bin/env python3
"""Capture localized user-manual screenshots on emulator-5556.

Usage:
  python3 scripts/capture-i18n-manual-screenshots.py [--serial emulator-5556] [--locales es,fr] [--phase pilot|all|west|rest|en]
"""
from __future__ import annotations

import argparse
import hashlib
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PKG = "com.davidlang.vehicleexpensesautomated"
ACTIVITY = f"{PKG}/.MainActivity"

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
    "r3-quickfill-odo-live.jpg",
    "r4-quickfill-odo-result.jpg",
    "r5-quickfill-pump-result.jpg",
    "r6-new-expense.jpg",
    "start-trip.jpg",
    "syncing-hub.jpg",
    "time-based-reports.jpg",
    "time-based-reports-scrolled.jpg",
    "trip-miles.jpg",
]

# Pref tag -> values folder
LOCALE_DIRS = {
    "en": "values",
    "es": "values-es",
    "fr": "values-fr",
    "pt-BR": "values-pt-rBR",
    "de": "values-de",
    "it": "values-it",
    "nl": "values-nl",
    "pl": "values-pl",
    "ru": "values-ru",
    "id": "values-id",
    "vi": "values-vi",
    "tr": "values-tr",
}

# BCP47 for cmd locale set-app-locales
LOCALE_BCP47 = {
    "en": "en",
    "es": "es",
    "fr": "fr",
    "pt-BR": "pt-BR",
    "de": "de",
    "it": "it",
    "nl": "nl",
    "pl": "pl",
    "ru": "ru",
    "id": "id",
    "vi": "vi",
    "tr": "tr",
}


def run(serial: str, *args: str, check: bool = True, timeout: int = 60) -> subprocess.CompletedProcess:
    cmd = ["adb", "-s", serial, *args]
    return subprocess.run(cmd, check=check, capture_output=True, text=True, timeout=timeout)


def sh(serial: str, shell_cmd: str, check: bool = True) -> str:
    r = run(serial, "shell", shell_cmd, check=check)
    return (r.stdout or "") + (r.stderr or "")


def load_strings(tag: str) -> dict[str, str]:
    folder = LOCALE_DIRS[tag]
    path = ROOT / "app/src/main/res" / folder / "strings.xml"
    if not path.is_file():
        path = ROOT / "app/src/main/res/values/strings.xml"
    text = path.read_text(encoding="utf-8")
    out = {}
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


def set_language(serial: str, tag: str) -> None:
    bcp = LOCALE_BCP47[tag]
    # System per-app locales (API 33+)
    run(serial, "shell", f"cmd locale set-app-locales {PKG} --locales {bcp}", check=False)
    # Keep app pref in sync for AppLanguage
    xml = sh(serial, f"run-as {PKG} cat shared_prefs/vehicle_settings.xml")
    if "app_language" in xml:
        xml2 = re.sub(
            r'(<string name="app_language">)[^<]*(</string>)',
            rf"\g<1>{tag}\g<2>",
            xml,
        )
    else:
        xml2 = xml.replace("</map>", f'    <string name="app_language">{tag}</string>\n</map>')
    tmp = Path("/tmp/ve-vehicle_settings.xml")
    tmp.write_text(xml2, encoding="utf-8")
    run(serial, "push", str(tmp), "/data/local/tmp/ve-vehicle_settings.xml")
    sh(serial, f"run-as {PKG} cp /data/local/tmp/ve-vehicle_settings.xml shared_prefs/vehicle_settings.xml", check=False)
    sh(serial, f"am force-stop {PKG}")
    time.sleep(0.6)
    sh(serial, f"am start -n {ACTIVITY} -a android.intent.action.MAIN -c android.intent.category.LAUNCHER")
    time.sleep(3.0)
    wait_for_menu(serial)


def dump_ui(serial: str) -> ET.Element:
    sh(serial, "uiautomator dump /sdcard/ui.xml", check=False)
    out = sh(serial, "cat /sdcard/ui.xml")
    # strip non-xml noise
    if "<?xml" in out:
        out = out[out.index("<?xml") :]
    try:
        return ET.fromstring(out)
    except ET.ParseError:
        Path("/tmp/ui-bad.xml").write_text(out, encoding="utf-8")
        raise


def node_center(node: ET.Element) -> tuple[int, int] | None:
    b = node.attrib.get("bounds") or ""
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b)
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def find_nodes(root: ET.Element, *, text: str | None = None, desc: str | None = None, partial: bool = False) -> list[ET.Element]:
    hits = []
    for n in root.iter():
        t = n.attrib.get("text") or ""
        d = n.attrib.get("content-desc") or ""
        if text is not None:
            if partial:
                if text in t:
                    hits.append(n)
            elif t == text:
                hits.append(n)
        if desc is not None:
            if partial:
                if desc in d:
                    hits.append(n)
            elif d == desc:
                hits.append(n)
    return hits


def tap(serial: str, x: int, y: int) -> None:
    sh(serial, f"input tap {x} {y}")
    time.sleep(0.7)


def swipe(serial: str, x1: int, y1: int, x2: int, y2: int, dur_ms: int = 400) -> None:
    sh(serial, f"input swipe {x1} {y1} {x2} {y2} {dur_ms}")
    time.sleep(0.6)


def press_back(serial: str) -> None:
    sh(serial, "input keyevent 4")
    time.sleep(0.5)


def click_text(serial: str, text: str, *, partial: bool = False, required: bool = True) -> bool:
    root = dump_ui(serial)
    nodes = find_nodes(root, text=text, partial=partial)
    for n in nodes:
        c = node_center(n)
        if c:
            tap(serial, *c)
            return True
    if required:
        print(f"  WARN: click_text not found: {text!r}", flush=True)
    return False


def click_desc(serial: str, desc: str, *, partial: bool = False) -> bool:
    root = dump_ui(serial)
    nodes = find_nodes(root, desc=desc, partial=partial)
    for n in nodes:
        c = node_center(n)
        if c:
            tap(serial, *c)
            return True
    print(f"  WARN: click_desc not found: {desc!r}", flush=True)
    return False


def screen_size(serial: str) -> tuple[int, int]:
    out = sh(serial, "wm size")
    m = re.search(r"(\d+)x(\d+)", out)
    if m:
        return int(m.group(1)), int(m.group(2))
    return 1344, 2992


def open_drawer(serial: str, s: dict[str, str]) -> None:
    """Open nav drawer; Menu content-desc or top-left fallback for this density."""
    for attempt in range(3):
        root = dump_ui(serial)
        nodes = find_nodes(root, desc="Menu", partial=True)
        if not nodes:
            # localized content-desc variants / empty
            for n in root.iter():
                d = n.attrib.get("content-desc") or ""
                b = n.attrib.get("bounds") or ""
                m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", b)
                if not m:
                    continue
                x1, y1, x2, y2 = map(int, m.groups())
                # top-left icon button cluster
                if y2 < 400 and x2 < 200 and (d in ("Menu", "Navigate up", "") or "Menu" in d):
                    # prefer actual Menu
                    if d == "Menu" or (x1 < 100 and y1 < 300):
                        nodes = [n]
                        if d == "Menu":
                            break
        if nodes:
            c = node_center(nodes[0])
            if c:
                tap(serial, *c)
                time.sleep(0.6)
                # verify drawer opened (look for settings/help or app title)
                root2 = dump_ui(serial)
                texts = " ".join((n.attrib.get("text") or "") for n in root2.iter())
                if any(
                    k in texts
                    for k in (
                        s.get("nav_settings", "Settings"),
                        s.get("nav_help", "Help"),
                        s.get("nav_about", "About"),
                        s.get("nav_drawer_title", "Vehicle"),
                        "Settings",
                        "Help",
                        "Ajustes",
                        "Paramètres",
                    )
                ):
                    return
        # coordinate fallback for 1344x2992 / density 480 (Menu ~84,255)
        w, h = screen_size(serial)
        tap(serial, max(48, w // 16), max(200, h // 12))
        time.sleep(0.6)
    print("  WARN: open_drawer may have failed", flush=True)


def dismiss_onboarding(serial: str, s: dict[str, str]) -> None:
    root = dump_ui(serial)
    texts = " ".join((n.attrib.get("text") or "") for n in root.iter())
    skip = s.get("onboarding_skip_for_now", "Skip for now")
    if skip in texts or "Skip" in texts or "Saltar" in texts or "Ignorer" in texts:
        if not click_text(serial, skip, required=False):
            # partial skip patterns
            for cand in [skip, "Skip", "Saltar", "Ignorer", "Überspringen", "Salta", "Overslaan", "Pomiń", "Пропуст", "Lewati", "Bỏ qua", "Atla"]:
                if click_text(serial, cand, partial=True, required=False):
                    break
        time.sleep(1.0)


def wait_for_menu(serial: str, tries: int = 8) -> None:
    for _ in range(tries):
        root = dump_ui(serial)
        if find_nodes(root, desc="Menu", partial=True):
            return
        texts = " ".join((n.attrib.get("text") or "") for n in root.iter())
        # still onboarding?
        if any(x in texts for x in ("Skip", "Saltar", "Ignorer", "vehicle yet", "vehículo", "Welcome", "Bienvenid")):
            return
        time.sleep(0.5)


def go_quick_fill(serial: str, s: dict[str, str]) -> None:
    dismiss_onboarding(serial, s)
    wait_for_menu(serial)
    open_drawer(serial, s)
    label = s.get("nav_quick_fill", "Quick Fill-up")
    if not click_text(serial, label, partial=True, required=False):
        click_text(serial, "Quick Fill", partial=True, required=False)
    time.sleep(0.8)


def drawer_go(serial: str, s: dict[str, str], *labels: str) -> bool:
    open_drawer(serial, s)
    for lab in labels:
        if lab and click_text(serial, lab, partial=True, required=False):
            time.sleep(0.9)
            return True
    return False


def capture(serial: str, out: Path) -> None:
    out.parent.mkdir(parents=True, exist_ok=True)
    helper = ROOT / "scripts/capture-manual-shot.sh"
    subprocess.run([str(helper), serial, str(out)], check=True)
    time.sleep(0.2)


def navigate_and_capture(serial: str, tag: str, out_dir: Path) -> dict[str, str]:
    """Return notes per missing/fallback shot."""
    notes: dict[str, str] = {}
    s = load_strings(tag)
    print(f"=== locale {tag} → {out_dir}", flush=True)
    set_language(serial, tag)
    dismiss_onboarding(serial, s)
    go_quick_fill(serial, s)

    def shot(name: str) -> Path:
        return out_dir / name

    # --- 01 drawer ---
    open_drawer(serial, s)
    capture(serial, shot("01-drawer.jpg"))
    # close drawer by tapping outside / back
    press_back(serial)
    time.sleep(0.3)

    # --- r3 quick fill live ---
    go_quick_fill(serial, s)
    capture(serial, shot("r3-quickfill-odo-live.jpg"))

    # --- r4 odo result: best effort shutter center-bottom ---
    # RoundCaptureButton often near bottom center
    swipe(serial, 672, 2200, 672, 1600)  # ensure controls visible
    # try tap shutter area (portrait: lower third center)
    tap(serial, 672, 2400)
    time.sleep(1.5)
    capture(serial, shot("r4-quickfill-odo-result.jpg"))
    notes.setdefault("r4-quickfill-odo-result.jpg", "best-effort shutter/ocr state")

    # --- r5 pump mode ---
    # Toggle pump often via icon; try text or middle control
    go_quick_fill(serial, s)
    # second capture button row / pump toggle — try swipe+tap
    tap(serial, 900, 2400)
    time.sleep(0.8)
    capture(serial, shot("r5-quickfill-pump-result.jpg"))
    notes.setdefault("r5-quickfill-pump-result.jpg", "best-effort pump toggle")

    # --- Manage vehicles ---
    open_drawer(serial, s)
    if not click_text(serial, s.get("nav_manage_vehicles", "Manage Vehicles"), partial=True, required=False):
        click_text(serial, "Manage", partial=True, required=False)
    time.sleep(1.0)
    capture(serial, shot("r1-manage-vehicles-crops.jpg"))

    # Add new vehicle
    add = s.get("vehicle_add_new_vehicle", "Add New Vehicle")
    if not click_text(serial, add, partial=True, required=False):
        click_text(serial, "Add", partial=True, required=False)
    time.sleep(0.8)
    capture(serial, shot("03b-manage-vehicles-new.jpg"))
    press_back(serial)

    # Landmarks — best effort: stay on manage vehicles list
    open_drawer(serial, s)
    click_text(serial, s.get("nav_manage_vehicles", "Manage"), partial=True, required=False)
    time.sleep(0.8)
    # try open first vehicle card
    root = dump_ui(serial)
    tapped = False
    for n in root.iter():
        t = n.attrib.get("text") or ""
        if t and t not in ("",) and any(k in t for k in ("Honda", "Ford", "Van", "vehicle", "Vehicle")):
            c = node_center(n)
            if c and c[1] > 400:
                tap(serial, *c)
                tapped = True
                break
    time.sleep(1.0)
    capture(serial, shot("r2-manage-vehicles-landmarks.jpg"))
    if not tapped:
        notes["r2-manage-vehicles-landmarks.jpg"] = "could not open vehicle landmarks; captured manage screen"
    press_back(serial)

    # --- New expense ---
    open_drawer(serial, s)
    click_text(serial, s.get("nav_new_expense", "New expense"), partial=True, required=False)
    time.sleep(1.0)
    capture(serial, shot("r6-new-expense.jpg"))
    press_back(serial)

    # --- Reports hub ---
    open_drawer(serial, s)
    click_text(serial, s.get("nav_reports", "Reports"), partial=True, required=False)
    time.sleep(1.0)
    capture(serial, shot("06-reports.jpg"))

    # expense list via card
    exp_list = s.get("nav_expense_list", "Expense list")
    if not click_text(serial, exp_list, partial=True, required=False):
        click_text(serial, s.get("reports_expenses_by_category", "Expenses"), partial=True, required=False)
        press_back(serial)
        open_drawer(serial, s)
        click_text(serial, s.get("nav_reports", "Reports"), partial=True, required=False)
        click_text(serial, "Expense", partial=True, required=False)
    time.sleep(0.8)
    capture(serial, shot("05-expense-list.jpg"))
    # open first expense if any
    root = dump_ui(serial)
    opened = False
    for n in root.iter():
        t = n.attrib.get("text") or ""
        if t and len(t) > 3 and "Expense" not in t and n.attrib.get("clickable") == "true":
            c = node_center(n)
            if c and 500 < c[1] < 2500:
                tap(serial, *c)
                opened = True
                break
    time.sleep(0.8)
    capture(serial, shot("expense-edit.jpg"))
    if not opened:
        notes["expense-edit.jpg"] = "no expense row; captured list/edit-ish state"
    press_back(serial)

    # back to reports hub
    open_drawer(serial, s)
    click_text(serial, s.get("nav_reports", "Reports"), partial=True, required=False)
    time.sleep(0.8)

    # time based reports
    tbr = s.get("reports_time_based_reports", "Time based reports")
    if not click_text(serial, tbr, partial=True, required=False):
        click_text(serial, "Time", partial=True, required=False)
    time.sleep(1.0)
    capture(serial, shot("time-based-reports.jpg"))
    swipe(serial, 672, 2200, 672, 800)
    capture(serial, shot("time-based-reports-scrolled.jpg"))
    press_back(serial)

    # fill history
    open_drawer(serial, s)
    click_text(serial, s.get("nav_reports", "Reports"), partial=True, required=False)
    time.sleep(0.5)
    fh = s.get("reports_fill_history", "Fill history")
    if not click_text(serial, fh, partial=True, required=False):
        click_text(serial, "Fill", partial=True, required=False)
    time.sleep(0.8)
    capture(serial, shot("fill-history.jpg"))
    # fuel edit from first fill
    root = dump_ui(serial)
    opened = False
    for n in root.iter():
        t = n.attrib.get("text") or ""
        if t and re.search(r"\d{4}|\d+/\d+|odo|Odo|mi", t, re.I):
            c = node_center(n)
            if c and c[1] > 600:
                tap(serial, *c)
                opened = True
                break
    time.sleep(0.8)
    capture(serial, shot("fuel-edit.jpg"))
    if not opened:
        notes["fuel-edit.jpg"] = "no fill row; captured fill history"
    press_back(serial)

    # trip miles
    open_drawer(serial, s)
    click_text(serial, s.get("nav_reports", "Reports"), partial=True, required=False)
    time.sleep(0.5)
    tm = s.get("reports_trip_miles", "Trip miles")
    if not click_text(serial, tm, partial=True, required=False):
        click_text(serial, "Trip", partial=True, required=False)
    time.sleep(0.8)
    capture(serial, shot("trip-miles.jpg"))
    press_back(serial)

    # start trip
    open_drawer(serial, s)
    click_text(serial, s.get("nav_start_trip", "Start trip"), partial=True, required=False)
    time.sleep(1.0)
    capture(serial, shot("start-trip.jpg"))
    press_back(serial)

    # syncing
    open_drawer(serial, s)
    click_text(serial, s.get("nav_syncing", "Syncing"), partial=True, required=False)
    time.sleep(1.0)
    capture(serial, shot("syncing-hub.jpg"))

    # spreadsheet sync
    ss = s.get("nav_spreadsheet_sync", "Spreadsheet Sync")
    if not click_text(serial, ss, partial=True, required=False):
        click_text(serial, "Spreadsheet", partial=True, required=False)
    time.sleep(1.0)
    capture(serial, shot("08-spreadsheet-sync.jpg"))

    # add destination / provider picker
    add_ss = s.get("settings_add_spreadsheet_destination", "Add spreadsheet destination")
    if not click_text(serial, add_ss, partial=True, required=False):
        click_text(serial, "Add", partial=True, required=False)
    time.sleep(0.8)
    capture(serial, shot("09-spreadsheet-provider-picker.jpg"))
    # pick Google if shown
    if not click_text(serial, "Google", partial=True, required=False):
        press_back(serial)
    else:
        time.sleep(1.0)
        capture(serial, shot("10-spreadsheet-google-form.jpg"))
        press_back(serial)
    # if form not captured, capture list again
    if not shot("10-spreadsheet-google-form.jpg").exists():
        # try open existing dest
        root = dump_ui(serial)
        for n in root.iter():
            t = n.attrib.get("text") or ""
            if "Vehicle" in t or "Sheet" in t or "Google" in t:
                c = node_center(n)
                if c and c[1] > 500:
                    tap(serial, *c)
                    time.sleep(1.0)
                    break
        capture(serial, shot("10-spreadsheet-google-form.jpg"))
        notes.setdefault("10-spreadsheet-google-form.jpg", "opened existing or form best-effort")
        press_back(serial)
    press_back(serial)

    # photo backup
    open_drawer(serial, s)
    click_text(serial, s.get("nav_syncing", "Syncing"), partial=True, required=False)
    time.sleep(0.5)
    pb = s.get("nav_photo_backup", "Photo Backup")
    if not click_text(serial, pb, partial=True, required=False):
        click_text(serial, "Photo", partial=True, required=False)
    time.sleep(1.0)
    capture(serial, shot("11-photo-backup.jpg"))
    add_ph = s.get("settings_add_photo_destination", "Add photo destination")
    if not click_text(serial, add_ph, partial=True, required=False):
        click_text(serial, "Add", partial=True, required=False)
    time.sleep(0.8)
    capture(serial, shot("12-photo-provider-picker.jpg"))
    if click_text(serial, "Google", partial=True, required=False) or click_text(serial, "Drive", partial=True, required=False):
        time.sleep(1.0)
        capture(serial, shot("13-photo-google-form.jpg"))
        press_back(serial)
    else:
        # open existing dest
        root = dump_ui(serial)
        for n in root.iter():
            t = n.attrib.get("text") or ""
            if t and ("Drive" in t or "Photo" in t or "folder" in t.lower() or "Google" in t):
                c = node_center(n)
                if c and c[1] > 500:
                    tap(serial, *c)
                    time.sleep(1.0)
                    break
        capture(serial, shot("13-photo-google-form.jpg"))
        notes.setdefault("13-photo-google-form.jpg", "best-effort photo form")
        press_back(serial)
    press_back(serial)

    # settings
    open_drawer(serial, s)
    click_text(serial, s.get("nav_settings", "Settings"), partial=True, required=False)
    time.sleep(1.0)
    capture(serial, shot("07-settings.jpg"))
    swipe(serial, 672, 2200, 672, 700)
    capture(serial, shot("07b-settings-more.jpg"))
    press_back(serial)

    # help / about
    open_drawer(serial, s)
    click_text(serial, s.get("nav_help", "Help"), partial=True, required=False)
    time.sleep(1.0)
    capture(serial, shot("14-help.jpg"))
    press_back(serial)

    open_drawer(serial, s)
    click_text(serial, s.get("nav_about", "About"), partial=True, required=False)
    time.sleep(1.0)
    capture(serial, shot("15-about.jpg"))
    press_back(serial)

    # ensure all 28 exist — fill from previous EN if automation failed a file
    en_dir = ROOT / "docs/user-manual/images"
    for name in SHOTS:
        p = shot(name)
        if not p.is_file() or p.stat().st_size < 1000:
            src = en_dir / name
            if src.is_file():
                p.write_bytes(src.read_bytes())
                notes[name] = notes.get(name, "") + " FALLBACK_EN_COPY"
                print(f"  FALLBACK EN copy for {name}", flush=True)

    return notes


def hash_compare(tag: str, out_dir: Path) -> int:
    en_dir = ROOT / "docs/user-manual/images"

    def md5s(d: Path) -> dict[str, str]:
        return {
            f.name: hashlib.md5(f.read_bytes()).hexdigest()
            for f in d.glob("*.jpg")
        }

    en = md5s(en_dir)
    loc = md5s(out_dir)
    same = sum(1 for f in loc if f in en and loc[f] == en[f])
    print(f"  identical_to_en {same} of {len(loc)}", flush=True)
    return same


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--serial", default="emulator-5556")
    ap.add_argument("--locales", default="")
    ap.add_argument("--phase", default="all", choices=["pilot", "west", "rest", "all", "en"])
    args = ap.parse_args()
    serial = args.serial

    # device check
    devs = subprocess.run(["adb", "devices"], capture_output=True, text=True).stdout
    if serial not in devs:
        print(f"ERROR: {serial} not in adb devices", file=sys.stderr)
        return 1
    print(f"device OK: {serial}", flush=True)

    west = ["fr", "de", "it", "nl", "pt-BR"]
    rest = ["pl", "ru", "id", "vi", "tr"]
    if args.locales:
        locales = [x.strip() for x in args.locales.split(",") if x.strip()]
    elif args.phase == "pilot":
        locales = ["es"]
    elif args.phase == "west":
        locales = west
    elif args.phase == "rest":
        locales = rest
    elif args.phase == "en":
        locales = ["en"]
    else:
        locales = ["es"] + west + rest

    all_notes: dict[str, dict[str, str]] = {}
    for tag in locales:
        if tag == "en":
            out_dir = ROOT / "docs/user-manual/images"
        else:
            out_dir = ROOT / "docs/i18n" / tag / "images"
        notes = navigate_and_capture(serial, tag, out_dir)
        all_notes[tag] = notes
        same = hash_compare(tag, out_dir)
        if tag != "en" and same > 8:
            print(f"  WARN: many identical images for {tag} (same={same})", flush=True)

    # reset language to English for user
    set_language(serial, "en")
    dismiss_onboarding(serial, load_strings("en"))
    print("reset language to en", flush=True)

    note_path = Path("/tmp/i18n-capture-notes.txt")
    lines = []
    for tag, notes in all_notes.items():
        if notes:
            lines.append(f"## {tag}")
            for k, v in notes.items():
                lines.append(f"- {k}: {v}")
    note_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"notes → {note_path}", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
