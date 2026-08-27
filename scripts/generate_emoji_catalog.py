#!/usr/bin/env python3
"""Generate the checked-in Emoji 15.1 catalog from pinned Unicode/CLDR inputs.

This maintenance tool never runs during an Android build. Download the five exact source files
listed in ``SOURCES`` into a temporary directory, then pass that directory with ``--source-dir``.
Every byte is verified before parsing and the Java output is deterministic.
"""

from __future__ import annotations

import argparse
from collections import defaultdict
from dataclasses import dataclass
import hashlib
from pathlib import Path
import re
import sys
from xml.etree import ElementTree


SOURCES = {
    "emoji-test.txt": "d876ee249aa28eaa76cfa6dfaa702847a8d13b062aa488d465d0395ee8137ed9",
    "en.xml": "cd86c3f805d7ee7cedd690cdb0523bdba279a89ba91aeb7982016571e77e61cb",
    "en-derived.xml": "fefbd0d2b52ba50bf46ecf91586726ccd0ebbe74616f3d5c3dc6ab2f9317dc0d",
    "zh.xml": "a09285afe873592b9eeeb1fa0de34a0bab79fce85351155d3427c6ec65c9b2ba",
    "zh-derived.xml": "30b66c9c20ede0c8919588b78edb5a4f173bb2c3c69776ec59b77eb2faf9670c",
}
GROUPS = {
    "Smileys & Emotion": "SMILEYS",
    "People & Body": "PEOPLE",
    "Animals & Nature": "ANIMALS",
    "Food & Drink": "FOOD",
    "Activities": "ACTIVITIES",
    "Travel & Places": "TRAVEL",
    "Objects": "OBJECTS",
    "Symbols": "SYMBOLS",
    "Flags": "FLAGS",
}
SKIN_TONES = range(0x1F3FB, 0x1F400)
CONTROL_OR_BIDI = re.compile(r"[\x00-\x1f\x7f\u202a-\u202e\u2066-\u2069]")


@dataclass(frozen=True)
class Annotation:
    name: str
    keywords: tuple[str, ...]


@dataclass(frozen=True)
class Entry:
    category: str
    emoji: str
    english_name: str
    chinese_name: str
    keywords: str


def verify_sources(source_dir: Path) -> None:
    for name, expected in SOURCES.items():
        path = source_dir / name
        if not path.is_file() or path.is_symlink():
            raise ValueError(f"missing regular source: {name}")
        actual = hashlib.sha256(path.read_bytes()).hexdigest()
        if actual != expected:
            raise ValueError(f"source digest mismatch for {name}: {actual}")


def normalized_key(value: str) -> str:
    return value.replace("\ufe0f", "")


def clean_term(value: str) -> str:
    value = " ".join(value.split()).strip()
    if CONTROL_OR_BIDI.search(value):
        raise ValueError("annotation contains a control or bidi override")
    if len(value) > 96:
        raise ValueError("annotation term exceeds 96 UTF-16 units")
    return value


def read_annotations(paths: tuple[Path, Path]) -> dict[str, Annotation]:
    names: dict[str, str] = {}
    words: dict[str, set[str]] = defaultdict(set)
    for path in paths:
        root = ElementTree.parse(path).getroot()
        for node in root.findall(".//annotation"):
            key = normalized_key(node.attrib.get("cp", ""))
            raw_text = "".join(node.itertext())
            if not key or not raw_text.strip():
                continue
            if node.attrib.get("type") == "tts":
                names.setdefault(key, clean_term(raw_text))
            else:
                words[key].update(
                    cleaned
                    for part in raw_text.split("|")
                    if (cleaned := clean_term(part))
                )
    return {
        key: Annotation(names.get(key, ""), tuple(sorted(words.get(key, ()))))
        for key in names.keys() | words.keys()
    }


def read_entries(source_dir: Path) -> tuple[Entry, ...]:
    english = read_annotations((source_dir / "en.xml", source_dir / "en-derived.xml"))
    chinese = read_annotations((source_dir / "zh.xml", source_dir / "zh-derived.xml"))
    entries: list[Entry] = []
    group = ""
    for raw in (source_dir / "emoji-test.txt").read_text(encoding="utf-8").splitlines():
        if raw.startswith("# group: "):
            group = raw.removeprefix("# group: ")
            continue
        if "; fully-qualified" not in raw:
            continue
        category = GROUPS.get(group)
        if category is None:
            raise ValueError(f"unexpected Emoji group: {group}")
        sequence, comment = raw.split(";", 1)[0], raw.split("#", 1)[1]
        code_points = tuple(int(value, 16) for value in sequence.split())
        if any(value in SKIN_TONES for value in code_points):
            continue
        emoji = "".join(chr(value) for value in code_points)
        comment_parts = comment.strip().split(maxsplit=2)
        if len(comment_parts) != 3 or comment_parts[0] != emoji:
            raise ValueError(f"malformed Emoji comment: {comment}")
        english_name = clean_term(comment_parts[2])
        key = normalized_key(emoji)
        en = english.get(key, Annotation("", ()))
        zh = chinese.get(key, Annotation("", ()))
        if not en.name or not zh.name:
            raise ValueError(f"missing CLDR name for {emoji}")
        terms = sorted({english_name, en.name, zh.name, *en.keywords, *zh.keywords})
        keyword_text = " ".join(terms)
        if len(keyword_text) > 768:
            raise ValueError(f"keyword index exceeds 768 UTF-16 units for {emoji}")
        entries.append(Entry(category, emoji, english_name, zh.name, keyword_text))
    glyphs = {entry.emoji for entry in entries}
    if len(entries) != 1_898 or len(glyphs) != len(entries):
        raise ValueError(f"expected 1898 unique entries, got {len(entries)}/{len(glyphs)}")
    return tuple(entries)


def java_string(value: str) -> str:
    escaped = (
        value.replace("\\", "\\\\")
        .replace('"', '\\"')
        .replace("\r", "\\r")
        .replace("\n", "\\n")
        .replace("\t", "\\t")
    )
    return f'"{escaped}"'


def render(entries: tuple[Entry, ...]) -> str:
    by_category: dict[str, list[Entry]] = defaultdict(list)
    for entry in entries:
        by_category[entry.category].append(entry)
    lines = [
        "// Generated by scripts/generate_emoji_catalog.py. Do not edit by hand.",
        "package com.opentypeless.android.keyboard.emoji;",
        "",
        "import java.util.EnumMap;",
        "import java.util.List;",
        "import java.util.Map;",
        "",
        "/** Unicode Emoji 15.1 order with CLDR 45 English/Chinese search annotations. */",
        "final class EmojiCatalogData {",
        '    static final String UNICODE_VERSION = "15.1";',
        '    static final String CLDR_VERSION = "45";',
        "    static final int ENTRY_COUNT = 1_898;",
        "",
        "    private EmojiCatalogData() {}",
        "",
        "    static Map<EmojiCatalog.Category, List<EmojiCatalog.Entry>> inventory() {",
        "        EnumMap<EmojiCatalog.Category, List<EmojiCatalog.Entry>> values =",
        "                new EnumMap<>(EmojiCatalog.Category.class);",
    ]
    for category in GROUPS.values():
        lines.append(
            f"        values.put(EmojiCatalog.Category.{category}, {category.lower()}());"
        )
    lines.extend(("        return Map.copyOf(values);", "    }", ""))
    for category in GROUPS.values():
        lines.extend((
            f"    private static List<EmojiCatalog.Entry> {category.lower()}() {{",
            "        return List.of(",
        ))
        category_entries = by_category[category]
        for index, entry in enumerate(category_entries):
            suffix = "," if index + 1 < len(category_entries) else ""
            lines.extend((
                "                new EmojiCatalog.Entry(",
                f"                        {java_string(entry.emoji)},",
                f"                        {java_string(entry.english_name)},",
                f"                        {java_string(entry.chinese_name)},",
                f"                        {java_string(entry.keywords)}){suffix}",
            ))
        lines.extend(("        );", "    }", ""))
    lines.extend(("}", ""))
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-dir", required=True, type=Path)
    parser.add_argument(
        "--output",
        type=Path,
        default=Path(__file__).resolve().parents[1]
        / "android/app/src/main/java/com/opentypeless/android/keyboard/emoji/EmojiCatalogData.java",
    )
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    try:
        verify_sources(args.source_dir)
        generated = render(read_entries(args.source_dir))
    except (OSError, UnicodeError, ValueError, ElementTree.ParseError) as error:
        print(f"emoji catalog generation failed: {error}", file=sys.stderr)
        return 1
    if args.check:
        if not args.output.is_file() or args.output.read_text(encoding="utf-8") != generated:
            print("checked-in Emoji catalog is stale", file=sys.stderr)
            return 1
        print("Emoji catalog is current: 1898 entries")
        return 0
    args.output.write_text(generated, encoding="utf-8")
    print(f"generated {args.output}: 1898 entries")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
