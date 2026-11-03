#!/usr/bin/env python3
"""Overlay person-supplied strings onto automated packs, then prune removed keys.

English app/src/main/res/values/strings.xml is the key set.
docs/i18n/supplied/<prefTag>.xml holds only person-supplied entries.
Indonesian is values-in only. This script does not create values-id.
A stored pref pt-BR overlays values-pt. ms-MY is not a pack.

Does not translate. A new English key stays missing until an automated pack
update writes it. A removed English key is deleted from every pack and from
the supplied file.
"""
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
SUPPLIED = ROOT / "docs/i18n/supplied"

# pref tag -> resource directory. Indonesian is values-in. pt-BR is values-pt.
# Does not list ms-MY or values-id.
PACKS = {
    "en-001": "values-b+en+001",
    "en-AU": "values-en-rAU",
    "en-CA": "values-en-rCA",
    "en-GB": "values-en-rGB",
    "en-IN": "values-en-rIN",
    "af": "values-af",
    "sq": "values-sq",
    "am": "values-am",
    "hy": "values-hy",
    "as": "values-as",
    "az": "values-az",
    "eu": "values-eu",
    "be": "values-be",
    "bn": "values-bn",
    "bs": "values-bs",
    "bg": "values-bg",
    "my": "values-my",
    "ca": "values-ca",
    "ceb": "values-b+ceb",
    "zh-HK": "values-zh-rHK",
    "zh-CN": "values-zh-rCN",
    "zh-TW": "values-zh-rTW",
    "hr": "values-hr",
    "cs": "values-cs",
    "da": "values-da",
    "nl": "values-nl",
    "et": "values-et",
    "fo": "values-fo",
    "tl": "values-tl",
    "fi": "values-fi",
    "fr-CA": "values-fr-rCA",
    "fr": "values-fr",
    "fy": "values-fy",
    "gl": "values-gl",
    "gd": "values-gd",
    "ka": "values-ka",
    "de": "values-de",
    "el": "values-el",
    "gu": "values-gu",
    "ha": "values-ha",
    "hi": "values-hi",
    "hu": "values-hu",
    "is": "values-is",
    "ig": "values-ig",
    "id": "values-in",
    "ga": "values-ga",
    "it": "values-it",
    "ja": "values-ja",
    "jv": "values-jv",
    "kn": "values-kn",
    "kk": "values-kk",
    "km": "values-km",
    "rw": "values-rw",
    "ko": "values-ko",
    "ku": "values-ku",
    "ky": "values-ky",
    "lo": "values-lo",
    "lv": "values-lv",
    "lt": "values-lt",
    "lb": "values-lb",
    "mk": "values-mk",
    "mg": "values-mg",
    "ms": "values-ms",
    "ml": "values-ml",
    "mt": "values-mt",
    "mi": "values-mi",
    "mr": "values-mr",
    "mn": "values-mn",
    "ne": "values-ne",
    "nb": "values-nb",
    "nn": "values-nn",
    "or": "values-or",
    "pl": "values-pl",
    "pt-BR": "values-pt",
    "pt-PT": "values-pt-rPT",
    "pa": "values-pa",
    "qu": "values-qu",
    "ro": "values-ro",
    "rm": "values-rm",
    "ru": "values-ru",
    "sr": "values-sr",
    "sr-Latn": "values-b+sr+Latn",
    "si": "values-si",
    "sk": "values-sk",
    "sl": "values-sl",
    "so": "values-so",
    "es-419": "values-b+es+419",
    "es": "values-es",
    "es-US": "values-es-rUS",
    "su": "values-su",
    "sw": "values-sw",
    "sv": "values-sv",
    "tg": "values-tg",
    "ta": "values-ta",
    "tt": "values-tt",
    "te": "values-te",
    "th": "values-th",
    "tr": "values-tr",
    "tk": "values-tk",
    "uk": "values-uk",
    "uz": "values-uz",
    "vi": "values-vi",
    "cy": "values-cy",
    "xh": "values-xh",
    "yo": "values-yo",
    "zu": "values-zu",
}


def android_unescape(s: str) -> str:
    out = []
    i = 0
    while i < len(s):
        if s[i] == "\\" and i + 1 < len(s):
            n = s[i + 1]
            out.append({"n": "\n", "t": "\t", "'": "'", '"': '"', "\\": "\\", "@": "@", "?": "?"}.get(n, n))
            i += 2
            continue
        out.append(s[i])
        i += 1
    return "".join(out)


def load(path: Path) -> list[tuple[str, str]]:
    root = ET.parse(path).getroot()
    return [(el.attrib["name"], android_unescape(el.text or "")) for el in root.findall("string")]


def escape_android(s: str) -> str:
    s = s.replace("\\", "\\\\")
    s = s.replace("'", "\\'")
    s = s.replace('"', '\\"')
    s = s.replace("&", "&amp;")
    s = s.replace("<", "&lt;")
    s = s.replace("\n", "\\n")
    s = s.replace("\r", "")
    if s.startswith("@") or s.startswith("?"):
        s = "\\" + s
    return s


def write_resources(path: Path, rows: list[tuple[str, str]]) -> None:
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    for name, text in rows:
        lines.append(f'    <string name="{name}">{escape_android(text)}</string>')
    lines.append("</resources>")
    lines.append("")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines), encoding="utf-8")


def main() -> None:
    en_rows = load(RES / "values/strings.xml")
    en_order = [name for name, _ in en_rows]
    en_keys = set(en_order)
    for pref, folder in PACKS.items():
        pack_path = RES / folder / "strings.xml"
        if not pack_path.exists():
            raise SystemExit(f"missing pack {pack_path}")
        pack = dict(load(pack_path))
        supplied_path = SUPPLIED / f"{pref}.xml"
        supplied = dict(load(supplied_path)) if supplied_path.exists() else {}
        kept_supplied = [(name, text) for name, text in supplied.items() if name in en_keys]
        for name, text in kept_supplied:
            pack[name] = text
        missing = [name for name in en_order if name not in pack]
        if missing:
            raise SystemExit(f"{pref} missing {len(missing)} keys, first {missing[0]}")
        rows = [(name, pack[name]) for name in en_order]
        write_resources(pack_path, rows)
        write_resources(supplied_path, kept_supplied)
    if (RES / "values-id").exists():
        raise SystemExit("refusing to leave values-id; this script does not create it")
    print(f"overlay ok, {len(PACKS)} packs, values-in left as the Indonesian pack")


if __name__ == "__main__":
    main()
