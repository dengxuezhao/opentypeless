# Task Report: KBD-010 Expanded Emoji catalog and search

## Result

DONE

## Scope

- Implemented: 1,898 Unicode Emoji 15.1 base sequences in nine categories; recycling grid; bottom
  category rail; bounded English/pinyin UI search backed by English/Chinese CLDR 45 metadata;
  localized names; unchanged 21-item Recent; sensitive/no-learning Recent suppression.
- Not implemented: independent skin-tone variant selection, Unicode versions after 15.1, direct
  Chinese query composition inside the in-panel QWERTY search, or physical Xiaomi acceptance.

## Changes

- `EmojiCatalogData.java` and `scripts/generate_emoji_catalog.py`: deterministic checked-in catalog
  from five exact-hash Unicode/CLDR inputs; exact 1,898-entry and metadata bounds.
- `EmojiCatalog.java`: immutable nine-category inventory, redacted entries, 32-code-point query and
  240-result bounds, English/Chinese metadata lookup and category-level pinyin aliases.
- `KeyboardEmojiPanel.java`: recycling 48dp grid cells, compact top navigation/search, count label,
  horizontal bottom category rail, result projection and lifecycle cleanup.
- `OpenTypelessImeService.java`: search-key interception before Rime/editor routes, generation-bound
  panel callbacks, QWERTY overlay restoration and existing ETM-only Emoji insertion.
- Resources: bilingual labels and a dedicated cell surface; no font, sprite or image bundle.
- Governance: Accepted ADR-0015, Unicode/CLDR compatibility authority, notices/provenance, tests and
  exact APK resource-policy identities.

## Architecture

- contracts: catalog/domain classes remain Android-free; the panel owns only Views and bounded
  callbacks and has no editor, Rime native, storage or network capability.
- state changes: search is an active-panel in-memory projection; QWERTY text/delete/Enter is consumed
  before the normal typing route and never reaches the editor while search editing is active.
- migration: none. ADR-0013 Recent `format_version=1` and 21-entry bound remain unchanged; old values
  continue to decode, while newly cataloged values are admitted by the same catalog-membership rule.
- feature flag: none; this replaces the original 168-entry KBD-010 catalog/UI.

## Security & privacy

- data sent/stored: no network data. Search queries are not stored, diagnosed, exported or added to
  Recent. Recent still stores only bounded catalog code points in backup-excluded private storage.
- permissions/components: none added; manifest, backup rules and dependency graph are unchanged.
- threat considerations: exact input hashes and generated-source identity fail closed; controls,
  bidi overrides, duplicates, missing names and oversized rows are rejected; broad search is capped;
  sensitive/no-learning fields cannot read, show or write Recent; all insertion uses the ETM façade.

## Tests actually run

| Command | Result | Notes |
|---|---|---|
| catalog generator `--check` | PASS | Exact five source hashes and exactly 1,898 entries. |
| focused Emoji JVM tests | PASS | 11/11: catalog/count, English/Chinese/domain search, pinyin categories, bounds, codec and MRU. |
| focused Emoji architecture contract | PASS | 12/12, including virtual grid, 48dp cells, API26 collection code and editor/Rime isolation. |
| focused API35 ARM64 instrumentation | PASS | 6/6: panel 4 and private Recent store 2. |
| system-selected Test Host Emoji method | PASS | 1/1 in 6.16s: `dog` search, exact `🐕`, password Recent suppression and static `😄`. |
| `scripts/verify_android.sh all` | PASS | 121 repository tests, 11 Android checks, 291 architecture tests, 10 voice tests and 191 Gradle tasks. |
| app JVM / compiled architecture / Release lint | PASS | 1097/1097, 114/114 and lintRelease; the gate caught and removed an API34-only collection call. |
| exact product/test APK resource scans | PASS | Five APKs, zero violations and zero bundled real Xiaohè resources. |
| Xiaomi 10 Ultra / Xiaomi 15 | NOT RUN | No physical Xiaomi device was connected. |

## Evidence

- recent panel screenshot: `/tmp/opentypeless-emoji-final.png`.
- 168-item smileys category screenshot: `/tmp/opentypeless-emoji-category-final.png`.
- `dog` result screenshot: `/tmp/opentypeless-emoji-search-results-final.png`.
- Debug APK: 65,685,084 bytes; SHA-256
  `e26689650c8fd346e505b1ea73134e73544fa5a13d11b8b7e73f2486a4c7b0cf`.
- unsigned Release APK: 63,772,165 bytes; SHA-256
  `3b35c3dfb3ea6d23ddef0a84f251a8878f385789c0c5bee67229c4d4411aeff0`.
- AndroidTest APK: 1,113,594 bytes; SHA-256
  `aae577f3f03dc3d3f127c46c3efdce0458e78dcea22dd404ba1fd99330bb052b`.
- emulator installed base APK: 65,685,084 bytes and the same Debug SHA-256. The default IME was
  restored to `com.android.inputmethod.latin/.LatinIME` after validation.

## Risks

- Skin-tone variants are intentionally omitted as independent rows; base people/body Emoji remain.
- The soft-keyboard search entry accepts Latin letters, so practical UI lookup is English or broad
  category pinyin. Chinese CLDR terms are retained and domain-tested, but direct Chinese composition
  inside this search surface is not claimed.
- Emoji glyph rendering, bottom insets and category-rail scrolling remain unverified on Xiaomi OEM
  builds because no physical Xiaomi device was connected.

## Rollback

Revert this task commit. The prior 168-entry picker resumes and continues to read Recent v1. Recent
values that only exist in the expanded catalog are filtered as unknown by the old catalog; no other
format, editor, Rime, voice or clipboard domain depends on this change.

## Follow-ups

- `KBD-009`
- `TST-010`

## Git

- branch: `codex/personal-ime`
- commit: this task commit
- worktree status: task files committed separately; the pre-existing untracked session handoff is
  intentionally excluded.
