# Task Report: KBD-015 punctuation mode follow-up

## Result

DONE

## Scope

- Implemented: `GENERAL` text fields now derive visible quick symbols and emitted text from the
  active internal engine. Latin/EN uses the original ASCII inventory; Rime/中 uses a fixed
  full-width punctuation inventory.
- Covered: letter hints, long press, downward flick, both symbol pages, and bottom comma/period.
- Preserved: digits and existing non-ASCII symbols; numeric, date, phone, email, URI and password
  fields continue using exact ASCII so structured values are not corrupted.
- Not implemented: QWERTY geometry/bottom inset, Voice UI, clipboard history/search, or Emoji
  catalog work. Those remain separate task IDs and rollback commits.

## Changes

- `PendingRimeSymbols`: expand the bounded punctuation normalization table while retaining the
  one-safe-scalar validation and maximum pending suffix.
- `LatinKeyboardLayout`: keep one active-engine presentation state and use it for key labels,
  accessibility text and emitted callbacks.
- JVM, Android View and hostile architecture tests: lock Chinese/English transitions and preserve
  single-dispatch/editor-authority boundaries.
- Architecture, backlog and validation specifications: record the mode-derived behavior and
  structured-field exception.

## Architecture

- contracts: every transformed value remains one safe Unicode scalar; digits and unknown
  non-ASCII symbols are identity mappings.
- state changes: `setEngineSelection` updates the active presentation engine before rebuilding
  the current letter layer, so visible labels and emitted callbacks share one state.
- migration: none.
- feature flag: none.

## Security & privacy

- data sent/stored: none; conversion is local and stateless.
- permissions/components: none added or changed.
- threat considerations: no new `InputConnection` owner or editor write path. All output still
  enters the existing keyboard callback, Rime composition lease and `EditorTransactionManager`.

## Tests actually run

| Command | Result | Notes |
|---|---|---|
| focused `PendingRimeSymbolsTest` | PASS | Fixed ASCII/full-width mapping and invalid input. |
| `scripts/verify_android.sh preflight` | PASS | 120 script tests, 272 architecture tests and all static policy gates. |
| full Python architecture suite | PASS | 272/272. |
| `:app:testDebugUnitTest` | PASS | 1085/1085 XML test cases. |
| `:app:connectedDebugAndroidTest` focused layout class | PASS | 17/17 on API35 arm64 emulator. |
| `:app:lintRelease` plus Debug/Release/AndroidTest assembly | PASS | 120 Gradle tasks in the invoked graph. |
| Xiaomi device | NOT RUN | Only `emulator-5554` was connected. |

## Evidence

- The Android View test reads `＠\na`, `？\nm`, `，` and `。` in Rime mode; emits full-width values
  through long press, downward flick, bottom punctuation and the symbol page; then reads ASCII
  `@\na`, `,` and `.` after switching back to Latin.
- Generated APKs: Debug 65,571,205 bytes; unsigned Release 63,628,769 bytes; AndroidTest
  1,121,344 bytes.
- APK SHA-256: Debug
  `8f106b8f5de481c9b3f5b89da0206217def97ee59d903b8f99ecb4fb3b3acf62`; unsigned Release
  `ef23a309c3e6600ee510960f9ea6d23578d57a218e61da51c1dddaf7c8214122`; AndroidTest
  `2eca793c58b51f4ffd15f748cdde0400bc374fa6427cb14cb46cfc48f3643e19`.

## Risks

- Full-width straight quote forms are deterministic rather than context-paired Chinese opening
  and closing quotes. Contextual quote pairing would need a separate bounded state design.
- OEM geometry and touch behavior on Xiaomi hardware remain unverified for this commit.

## Rollback

Revert this task's dedicated commit. There is no persistence or data migration; rollback restores
ASCII quick-symbol presentation/output in both internal engines.

## Follow-ups

- `KBD-009`
- `KBD-006`
- `KBD-011`
- `KBD-010`

## Git

- branch: `codex/personal-ime`
- commit: this report is included in the task's dedicated rollback commit
- worktree status: user-owned `docs/2026-08-19-session-handoff.md` remains untracked and excluded
