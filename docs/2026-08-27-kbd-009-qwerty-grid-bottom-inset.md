# Task Report: KBD-009 QWERTY grid and bottom inset follow-up

## Result

PARTIAL

## Scope

- Implemented: align all three QWERTY letter rows to one deterministic 20-unit grid.
- Implemented: raise the portrait panel base bottom inset from 10dp to 16dp, keep landscape at
  8dp, and continue adding the platform navigation-bar inset.
- Preserved: 50dp key height, 22sp letters, 48dp minimum touch target, four contiguous rows,
  keyboard callbacks and the sole editor transaction path.
- Not completed: Xiaomi 15 portrait/landscape acceptance because no Xiaomi 15 is connected.

## Changes

- `LatinKeyboardLayout`: replace row-specific `1 / 0.5 / 1.45` proportions with
  `10×2 / 1+9×2+1 / 3+7×2+3`; account for fixed margins so each row closes on the same grid.
- `OpenTypelessImeService`: use explicit portrait/landscape base bottom padding before adding the
  navigation-bar inset.
- Android View tests: assert exact cross-row centers at both 1080px and 2400px widths.
- Architecture tests/specifications: make grid and safe-area drift fail closed.

## Architecture

- contracts: key rows remain capability-free Views and use one bounded callback surface.
- state changes: none; geometry is derived from current width and orientation.
- migration: none.
- feature flag: none.

## Security & privacy

- data sent/stored: none.
- permissions/components: none added or changed.
- threat considerations: no new editor authority, `InputConnection`, network, clipboard or
  persistence path.

## Tests actually run

| Command | Result | Notes |
|---|---|---|
| focused KBD-002/KBD-009 architecture contract | PASS | 12/12 hostile tests. |
| `scripts/verify_android.sh preflight` | PASS | 120 script tests, 274 architecture tests and all static policy gates. |
| full Python architecture suite | PASS | 274/274. |
| `:app:testDebugUnitTest` | PASS | 1085/1085 XML test cases. |
| focused Android layout class | PASS | 18/18 on API35 arm64 emulator. |
| `:app:lintRelease` plus Debug/Release/AndroidTest assembly | PASS | 120-task invoked graph. |
| system-selected IME portrait smoke | PASS | 1080×2400, four complete rows and raised bottom inset. |
| system-selected IME landscape smoke | PASS | 2400×1080, four complete rows and no clipping. |
| Xiaomi 15 | NOT RUN | No Xiaomi 15 is connected. |

## Evidence

- Before screenshot: `/tmp/opentypeless-qwerty-before.png`.
- Updated portrait screenshot: `/tmp/opentypeless-qwerty-grid.png`.
- Updated landscape screenshot: `/tmp/opentypeless-qwerty-landscape.png`.
- Android assertions cover center alignment rather than relying only on screenshot appearance.
- APK SHA-256: Debug
  `bd5d69af215a483d7d4c93bd6d7e2cd17d3a90deec7f6747ba3fab2a9f1310f9`; unsigned Release
  `42128c501b99847e8fc52db32679a62842837221c508717056e8d6998acd9136`; AndroidTest
  `aae93560236455d3342e64b64042dfe0e5dc1da91a03c5bddb0e5b07cc7c9b6c`.

## Risks

- Pixel rounding can leave Shift and Delete widths two physical pixels apart at some widths; their
  occupied grid spans remain equal and the test bounds this deterministic remainder.
- Xiaomi/HyperOS window inset behavior remains unverified for this commit.

## Rollback

Revert this task's dedicated commit. There is no data migration; rollback restores the previous
row weights and 10dp portrait base bottom padding.

## Follow-ups

- `KBD-009` (Xiaomi 15 device acceptance only)
- `KBD-006`
- `KBD-011`
- `KBD-010`

## Git

- branch: `codex/personal-ime`
- commit: this report is included in the task's dedicated rollback commit
- worktree status: user-owned `docs/2026-08-19-session-handoff.md` remains untracked and excluded
