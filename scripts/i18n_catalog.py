"""Shared catalog scan for the resource generator and i18n-outstanding.

Definition sites are S() / P() string literals and the English endonym on
AppLanguage.SupportedLocale (passed to S at the settings row).
"""
import json
import re
from pathlib import Path

from i18n_apply_supplied import PACKS, escape_android

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src"
RES = ROOT / "app/src/main/res"
CATALOG = ROOT / "app/src/main/i18n"
MANUAL = ROOT / "docs/user-manual/manual.md"
I18N_DOCS = ROOT / "docs/i18n"
GEN_KT = (
    ROOT
    / "build/generated/i18n/kotlin/com/davidlang/vehicleexpensesautomated/ui/util/I18nGenerated.kt"
)

FORMAT_RE = re.compile(r"%%|%(?:\d+\$)[a-zA-Z]")
OMIT_KEYS = {
    "settings_gallons",
    "settings_deprecation",
    "experiment_unused_parameter",
    "experiment_unused_variable",
    "lang_name_ms_my",
}


def format_tokens(text: str) -> list[str]:
    return sorted(FORMAT_RE.findall(text))


def kotlin_quote(text: str) -> str:
    out = ['"']
    for ch in text:
        if ch == "\\":
            out.append("\\\\")
        elif ch == '"':
            out.append('\\"')
        elif ch == "$":
            out.append("\\$")
        elif ch == "\n":
            out.append("\\n")
        elif ch == "\r":
            out.append("\\r")
        elif ch == "\t":
            out.append("\\t")
        else:
            out.append(ch)
    out.append('"')
    return "".join(out)


def parse_kotlin_string(text: str, i: int) -> tuple[str, int]:
    if text.startswith('"""', i):
        j = text.find('"""', i + 3)
        if j < 0:
            raise ValueError("unterminated triple string")
        return text[i + 3 : j], j + 3
    if text[i] != '"':
        raise ValueError("expected string")
    i += 1
    chars: list[str] = []
    while i < len(text):
        ch = text[i]
        if ch == '"':
            return "".join(chars), i + 1
        if ch == "\\":
            i += 1
            if i >= len(text):
                raise ValueError("truncated escape")
            n = text[i]
            chars.append({"n": "\n", "r": "\r", "t": "\t", "$": "$", '"': '"', "\\": "\\", "'": "'"}.get(n, n))
            i += 1
            continue
        chars.append(ch)
        i += 1
    raise ValueError("unterminated string")


def _skip_char(text: str, i: int) -> int:
    # i points at the opening single quote of a character literal.
    i += 1
    if i < len(text) and text[i] == "\\":
        i += 2
    elif i < len(text):
        i += 1
    if i < len(text) and text[i] == "'":
        i += 1
    return i


def _skip_ws(text: str, i: int) -> int:
    while i < len(text) and text[i].isspace():
        i += 1
    return i


def _skip_arg(text: str, i: int) -> int:
    i = _skip_ws(text, i)
    if i >= len(text):
        return i
    if text.startswith('"""', i) or text[i] == '"':
        _, i = parse_kotlin_string(text, i)
        return i
    if text[i] == "(":
        depth = 0
        while i < len(text):
            if text[i] == "(":
                depth += 1
            elif text[i] == ")":
                depth -= 1
                if depth == 0:
                    return i + 1
            i += 1
        return i
    depth = 0
    while i < len(text):
        ch = text[i]
        if ch == "(":
            depth += 1
        elif ch == ")":
            if depth == 0:
                return i
            depth -= 1
        elif ch == "," and depth == 0:
            return i
        elif ch == '"':
            _, i = parse_kotlin_string(text, i)
            continue
        i += 1
    return i


def iter_kotlin_files() -> list[Path]:
    files = []
    for path in APP.rglob("*.kt"):
        if "build" in path.parts:
            continue
        files.append(path)
    return files


def scan_sources() -> list[tuple[str, str]]:
    """Return (kind, sentence) in source order. kind is 's' or 'p' or 'endonym'."""
    found: list[tuple[str, str]] = []
    for path in iter_kotlin_files():
        text = path.read_text(encoding="utf-8")
        found.extend(_scan_text(text))
    return found


def _scan_text(text: str) -> list[tuple[str, str]]:
    found: list[tuple[str, str]] = []
    i = 0
    n = len(text)
    while i < n:
        if text.startswith("//", i):
            j = text.find("\n", i)
            i = n if j < 0 else j + 1
            continue
        if text.startswith("/*", i):
            j = text.find("*/", i + 2)
            i = n if j < 0 else j + 2
            continue
        if text.startswith('"""', i) or (text[i] == '"' and not text.startswith('"""', i)):
            _, i = parse_kotlin_string(text, i)
            continue
        if text[i] == "'":
            i = _skip_char(text, i)
            continue
        if text.startswith("SupportedLocale(", i) and _call_name(text, i, "SupportedLocale"):
            found.extend(_endonym(text, i))
            i += len("SupportedLocale")
            continue
        if text.startswith("S(", i) and _call_name(text, i, "S"):
            sentence, nxt = _first_string_arg(text, i + 1)
            if sentence is not None:
                found.append(("s", sentence))
            i = nxt
            continue
        if text.startswith("P(", i) and _call_name(text, i, "P"):
            found.extend(_plural_strings(text, i + 1))
            i += 2
            continue
        i += 1
    return found


def _call_name(text: str, i: int, name: str) -> bool:
    if i >= 4 and text[i - 4 : i] == "fun ":
        return False
    if i > 0 and (text[i - 1].isalnum() or text[i - 1] == "_"):
        return False
    return text.startswith(name + "(", i)


def _first_string_arg(text: str, paren: int) -> tuple[str | None, int]:
    # paren points at '('.
    i = _skip_ws(text, paren + 1)
    if i < len(text) and (text[i] == '"' or text.startswith('"""', i)):
        sentence, nxt = parse_kotlin_string(text, i)
        return sentence, nxt
    return None, paren + 1


def _plural_strings(text: str, paren: int) -> list[tuple[str, str]]:
    i = _skip_arg(text, paren + 1)
    i = _skip_ws(text, i)
    out: list[tuple[str, str]] = []
    if i < len(text) and text[i] == ",":
        i += 1
    i = _skip_ws(text, i)
    if i < len(text) and text[i] == '"':
        one, i = parse_kotlin_string(text, i)
        out.append(("p", one))
    i = _skip_ws(text, i)
    if i < len(text) and text[i] == ",":
        i += 1
    i = _skip_ws(text, i)
    if i < len(text) and text[i] == '"':
        other, _i = parse_kotlin_string(text, i)
        out.append(("p", other))
    return out


def _endonym(text: str, start: int) -> list[tuple[str, str]]:
    # SupportedLocale(pref, bcp47, segment, "English")
    i = start + len("SupportedLocale(")
    for _ in range(3):
        i = _skip_arg(text, i)
        i = _skip_ws(text, i)
        if i < len(text) and text[i] == ",":
            i += 1
    i = _skip_ws(text, i)
    if i < len(text) and text[i] == '"':
        sentence, _nxt = parse_kotlin_string(text, i)
        return [("endonym", sentence)]
    return []


def definition_sentences() -> list[str]:
    seen: set[str] = set()
    ordered: list[str] = []
    for _kind, sentence in scan_sources():
        if sentence not in seen:
            seen.add(sentence)
            ordered.append(sentence)
    return ordered


def read_keep_terms() -> list[str]:
    path = CATALOG / "keep-terms.txt"
    if not path.is_file():
        return []
    return [line.strip() for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def worth_sending(sentence: str, terms: list[str]) -> bool:
    stripped = FORMAT_RE.sub(" ", sentence)
    for term in sorted(terms, key=len, reverse=True):
        stripped = stripped.replace(term, " ")
    return any(ch.isalnum() for ch in stripped)


def load_catalog() -> dict[str, list[dict[str, str]]]:
    """pref -> entries in file order. Skips en and override files."""
    out: dict[str, list[dict[str, str]]] = {}
    if not CATALOG.is_dir():
        return out
    for path in sorted(CATALOG.glob("*.json")):
        if path.name.endswith(".override.json"):
            continue
        pref = path.stem
        if pref == "en":
            continue
        data = json.loads(path.read_text(encoding="utf-8"))
        if not isinstance(data, list):
            raise SystemExit(f"{path} is not a list")
        entries = []
        for item in data:
            entries.append({"key": item["key"], "source": item["source"], "text": item["text"]})
        out[pref] = entries
    return out


def load_overrides(pref: str) -> list[tuple[str, str]]:
    path = CATALOG / f"{pref}.override.json"
    if not path.is_file():
        return []
    data = json.loads(path.read_text(encoding="utf-8"))
    items = data if isinstance(data, list) else data.get("entries", [])
    ordered: list[tuple[str, str]] = []
    for item in items:
        if "key" in item and "text" in item:
            ordered.append((item["key"], item["text"]))
    return ordered


def catalog_sources(catalog: dict[str, list[dict[str, str]]]) -> list[str]:
    seen: set[str] = set()
    ordered: list[str] = []
    for entries in catalog.values():
        for entry in entries:
            if entry["source"] not in seen:
                seen.add(entry["source"])
                ordered.append(entry["source"])
    return ordered


def choose_text(english: str, catalog_text: str | None, overrides: list[tuple[str, str]], key: str) -> tuple[str, bool]:
    """Return (text, fell_back_to_english). Later matching override wins."""
    chosen: str | None = None
    for okey, otext in overrides:
        if okey == key and format_tokens(otext) == format_tokens(english):
            chosen = otext
    if chosen is not None:
        return chosen, False
    if catalog_text is not None:
        return catalog_text, False
    return english, True


def write_resources(path: Path, rows: list[tuple[str, str]]) -> None:
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    for name, text in rows:
        lines.append(f'    <string name="{name}">{escape_android(text)}</string>')
    lines.append("</resources>")
    lines.append("")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines), encoding="utf-8")


def manual_html_segments() -> list[str]:
    if not I18N_DOCS.is_dir():
        return []
    found = []
    for path in sorted(I18N_DOCS.glob("*/user-manual.html")):
        if path.is_file():
            found.append(path.parent.name)
    return found


def manual_paragraphs() -> list[str]:
    if not MANUAL.is_file():
        return []
    paras: list[str] = []
    buf: list[str] = []
    for line in MANUAL.read_text(encoding="utf-8").splitlines():
        if line.strip() == "":
            if buf:
                paras.append("\n".join(buf).strip())
                buf = []
        else:
            buf.append(line)
    if buf:
        paras.append("\n".join(buf).strip())
    out = []
    for para in paras:
        if para.startswith("![") and "\n" not in para:
            continue
        out.append(para)
    return out


def alloc_key(sentence: str, used: set[str]) -> str:
    slug = re.sub(r"[^a-z0-9]+", "_", sentence.lower()).strip("_")[:50] or "sentence"
    if not slug[0].isalpha():
        slug = "s_" + slug
    base = slug
    n = 2
    while slug in used:
        slug = f"{base}_{n}"
        n += 1
    used.add(slug)
    return slug
