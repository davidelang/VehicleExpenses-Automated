#!/usr/bin/env python3
"""Pref-tag to resource-directory map, and Android string escaping.

PACKS is the only pref-tag map. The capture script imports it.
The build generator imports PACKS and escape_android. This module does not write XML.
Indonesian is values-in. pt-BR is values-pt. Does not list ms-MY or values-id.
"""
import xml.etree.ElementTree as ET
from pathlib import Path

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


if __name__ == "__main__":
    raise SystemExit(
        "scripts/i18n_apply_supplied.py does not write XML. "
        "PACKS is for the generator and the capture script."
    )
