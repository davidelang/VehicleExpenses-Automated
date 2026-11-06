#!/usr/bin/env python3
"""Write gitignored strings.xml files and the S() lookup from the catalog.

Does not translate, does not use the network, and does not edit the catalog
or a call-site sentence.
"""
import sys
from pathlib import Path

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parent))

from i18n_apply_supplied import PACKS
from i18n_catalog import (
    GEN_KT,
    RES,
    alloc_key,
    choose_text,
    definition_sentences,
    kotlin_quote,
    load_catalog,
    load_overrides,
    manual_html_segments,
    write_resources,
)


def main() -> None:
    order = definition_sentences()
    sentences = set(order)
    catalog = load_catalog()
    missing = [pref for pref in PACKS if pref not in catalog]
    extra = [pref for pref in catalog if pref not in PACKS]
    if missing or extra:
        raise SystemExit(f"catalog pref mismatch missing={missing[:3]} extra={extra[:3]}")
    base_pref = next(iter(PACKS))
    base = catalog[base_pref]
    base_keys = [entry["key"] for entry in base]
    sources = {entry["key"]: entry["source"] for entry in base}
    text_by_pref = {}
    for pref, entries in catalog.items():
        if [entry["key"] for entry in entries] != base_keys:
            raise SystemExit(f"{pref} catalog key order does not match {base_pref}")
        for entry in entries:
            if entry["source"] != sources[entry["key"]]:
                raise SystemExit(f"{pref} source differs for {entry['key']}")
        text_by_pref[pref] = {entry["key"]: entry["text"] for entry in entries}

    used = set(base_keys)
    emitted: list[tuple[str, str, bool]] = []
    covered: set[str] = set()
    for entry in base:
        if entry["source"] in sentences:
            emitted.append((entry["key"], entry["source"], True))
            covered.add(entry["source"])
    for sentence in order:
        if sentence in covered:
            continue
        emitted.append((alloc_key(sentence, used), sentence, False))
        covered.add(sentence)

    write_resources(RES / "values/strings.xml", [(key, english) for key, english, _in in emitted])
    fallback: set[str] = set()
    for pref, folder in PACKS.items():
        overrides = load_overrides(pref)
        rows = []
        for key, english, in_catalog in emitted:
            catalog_text = text_by_pref[pref].get(key) if in_catalog else None
            text, fell = choose_text(english, catalog_text, overrides, key)
            if fell:
                fallback.add(english)
            rows.append((key, text))
        write_resources(RES / folder / "strings.xml", rows)
    _write_kotlin(emitted)
    print(f"i18n english fallback: {len(fallback)}")


def _write_kotlin(emitted: list[tuple[str, str, bool]]) -> None:
    sentence_to_key: dict[str, str] = {}
    for key, english, _in in emitted:
        sentence_to_key.setdefault(english, key)
    chosen = set(sentence_to_key.values())
    also = [key for key, _english, _in in emitted if key not in chosen]
    items = list(sentence_to_key.items())
    chunks: list[list[tuple[str, str]]] = []
    step = 200
    for start in range(0, len(items), step):
        chunks.append(items[start : start + step])
    if not chunks:
        chunks = [[]]
    lines = [
        "package com.davidlang.vehicleexpensesautomated.ui.util",
        "",
        "import com.davidlang.vehicleexpensesautomated.R",
        "",
        "internal object I18nSentences {",
        "    fun id(sentence: String): Int {",
        "        val hit = map[sentence]",
        "        if (hit == null) {",
        "            return if (sentence.isEmpty()) also.size else 0",
        "        }",
        "        return hit",
        "    }",
        "",
        f"    private val map: Map<String, Int> = HashMap<String, Int>({max(len(items), 1)}).also {{ into ->",
    ]
    for index in range(len(chunks)):
        lines.append(f"        fill{index}(into)")
    lines.append("    }")
    lines.append("")
    if also:
        lines.append("    private val also: IntArray = intArrayOf(")
        for key in also:
            lines.append(f"        R.string.{key},")
        lines.append("    )")
    else:
        lines.append("    private val also: IntArray = intArrayOf()")
    lines.append("")
    for index, chunk in enumerate(chunks):
        lines.append(f"    private fun fill{index}(into: MutableMap<String, Int>) {{")
        for english, key in chunk:
            lines.append(f"        into[{kotlin_quote(english)}] = R.string.{key}")
        lines.append("    }")
        lines.append("")
    lines.append("}")
    lines.append("")
    segments = manual_html_segments()
    lines.append("internal object ManualHtmlSegments {")
    if segments:
        quoted = ", ".join(kotlin_quote(seg) for seg in segments)
        lines.append(f"    val present: Set<String> = setOf({quoted})")
    else:
        lines.append("    val present: Set<String> = emptySet()")
    lines.append("}")
    lines.append("")
    GEN_KT.parent.mkdir(parents=True, exist_ok=True)
    GEN_KT.write_text("\n".join(lines), encoding="utf-8")


if __name__ == "__main__":
    main()
