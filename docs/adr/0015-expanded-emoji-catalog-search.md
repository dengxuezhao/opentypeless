# ADR-0015: Expanded Emoji catalog, virtualized grid and in-memory search

## Status

Accepted

## Background

The first `KBD-010` slice intentionally shipped only 168 Emoji, 21 per category. That proved the
editor, privacy and recent-history boundaries, but it is too sparse for daily use. Rendering a
complete catalog as eager `Button` objects would also make opening the input method slow and would
consume unnecessary memory. Search must not introduce a nested `EditText`, a second editor
authority, runtime network access or persistence of user queries.

Popular open-source Emoji projects were reviewed for product structure. `iamcal/emoji-data` uses a
stable category/order-oriented metadata model, while `muan/emojilib` demonstrates keyword-based
lookup. OpenTypeless adopts those interaction patterns only. It does not copy their code, JSON,
sprites, fonts or artwork; the shipped sequences and annotations remain sourced from Unicode.

## Decision

- Generate 1,898 distinct fully-qualified Unicode Emoji 15.1 sequences in CLDR order. Include nine
  browse categories: smileys, people, animals, food, activities, travel, objects, symbols and
  flags. Omit skin-tone variants and standalone components in this slice to avoid a 3,773-entry
  variant explosion; base multi-code-point, ZWJ, keycap and flag sequences remain available.
- Generate English/Chinese search names and keywords from CLDR 45 `annotations` and
  `annotationsDerived`. The maintenance generator verifies exact SHA-256 identities for all five
  Unicode inputs, rejects controls, oversize metadata, missing names, duplicate glyphs and any
  count other than 1,898. The generated Java source is checked in; builds do not download or parse
  catalog assets.
- Add no runtime dependency, network request, font, sprite or image. The generated catalog is not
  initialized on ordinary QWERTY/Rime creation; the first explicit Emoji render activates it.
- Render categories with a recycling `GridView`, a bottom horizontal category rail and a compact
  top back/search bar. Category lists may contain hundreds of rows, but only visible cells become
  `Button` views. Every action remains at least 48dp and has a localized accessibility label.
- Search is a bounded in-memory projection: at most 32 Unicode code points in the query and 240
  deterministic CLDR-order results. English, Chinese annotations and broad pinyin category aliases
  are indexed. While editing, visible QWERTY callbacks update only the panel query before Rime or
  the editor; Enter/back finishes search. Closing or leaving the editor destroys the query and
  bound row references.
- ADR-0013's `format_version=1`, 21-item recent MRU and hard-safety policy remain unchanged. Newly
  cataloged values become valid v1 entries without migration; unknown/malformed values still fail
  closed. Static Emoji and in-memory search remain available in sensitive fields, while Recent is
  neither read, shown nor written when learning is denied.

Rejected alternatives: importing a third-party picker/runtime, bundling emoji artwork, parsing a
large JSON file during IME startup, eagerly creating every cell, saving search queries, showing all
skin-tone variants as independent rows, or using an internal `EditText` that could acquire editor
authority.

## Consequences

The picker covers practical Unicode 15.1 input with over eleven times the previous inventory and a
searchable, familiar category layout. The checked-in generated source adds about 467 KB before
Android compilation, but no startup asset parsing and no network/dependency surface. Individual
skin-tone selection and newer Unicode versions remain explicit future catalog decisions.

The search result cap can omit very broad tail matches; a more specific query restores precision.
Pinyin support is category-level, while individual entries are searchable through English or
Chinese CLDR terms. Search text is transient and is never included in recents, preferences,
diagnostics, export or network data.

## Validation

Accepted on 2026-08-27 with the following evidence:

- The five downloaded Unicode Emoji 15.1 / CLDR 45 files matched the SHA-256 values pinned in
  `scripts/generate_emoji_catalog.py`; generation and `--check` both reported exactly 1,898 entries.
  Generated-source SHA-256 is
  `9dd48a5e04ad1a67b016bbdbf89fe869a331aaddfd4fcff436b610506846c998`.
- Focused Emoji JVM tests: PASS 11/11. They cover exact category/count uniqueness, English/Chinese/
  pinyin-category search, 240-result and 32-code-point bounds, v1 codec/MRU and hard-safety policy.
- Focused API35 ARM64 instrumentation: PASS 6/6 for the private recent store and panel. It covers
  recycled-grid rendering, bottom category placement, search callbacks, multi-code-point insertion,
  48dp controls and sensitive Recent suppression.
- Strict offline Debug Java and AndroidTest compilation both pass with dependency verification
  enabled. No manifest, permission, component, backup rule or dependency changed.
- Emoji architecture contract: PASS 12/12. It rejects a shrunken/generated catalog, non-virtual
  grid, full-width overlapping cells, API34-only `Stream.toList()`, search reaching Rime/editor,
  sensitive recents reads and an unaccepted persistence/catalog decision.
- System-selected IME on the API35 ARM64 emulator: PASS 1/1 (6.16s). QWERTY search for `dog` left
  the host editor empty, `🐕` committed exactly once, the password field exposed no Recent node and
  accepted static `😄`; LatinIME was restored afterward.
- Complete offline verification: PASS. It covers 121 repository tests, 11 Android checks, 291
  source architecture tests, 10 voice tests, 191 Gradle tasks, app JVM 1097/1097, compiled
  architecture 114/114, Release lint and five exact APK resource scans with zero violations.

## Rollback

Revert the `KBD-010` expansion commit. The prior 168-entry UI resumes and continues to decode the
same `format_version=1`; recents containing a newly added Emoji are simply filtered as unknown by
the old catalog. No editor, Rime, voice or clipboard format depends on the expanded data.

## References

- Task: `KBD-010`
- Persistence/privacy decision: [ADR-0013](0013-emoji-recents-private-format.md)
- Unicode Emoji 15.1: https://www.unicode.org/Public/emoji/15.1/emoji-test.txt
- Unicode CLDR release 45: https://github.com/unicode-org/cldr/tree/release-45
- Interaction references: https://github.com/iamcal/emoji-data,
  https://github.com/muan/emojilib
