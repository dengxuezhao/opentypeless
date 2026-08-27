# Task Report: KBD-006 QWERTY toolbar and voice UI follow-up

## Result

DONE

## Scope

- Implemented: Route-A QWERTY toolbar hierarchy, direct clipboard/Emoji/language actions,
  function-panel entry, equal-height candidate replacement, Typeless-inspired voice hierarchy,
  exact microphone centring and portrait clipping repair.
- Not implemented: clipboard event capture policy, voice recognition/finalization performance,
  new voice actions, Rime resource packaging or Xiaomi 15 physical-device acceptance.

## Changes

- `OpenTypelessImeService.java`: QWERTY toolbar now keeps Functions on the left and Clipboard,
  Emoji and the active EN/中 engine on the right. Voice is reachable from Functions and its own
  Voice/EN/拼 segmented header.
- `KeyboardToolbarLayout.java`: adds one bounded leading placement without adding editor, storage
  or network authority.
- `KeyboardCandidateBar.java`: keeps candidate text vertically centred without platform font
  padding. The candidate and function surfaces both occupy an exact 48dp outer row with zero
  vertical toolbar padding, so composition start/finish does not move the QWERTY grid.
- `VoiceInputPanel.java`: independently centres a 168x64dp microphone capsule, keeps delete and
  punctuation in a right-side utility column, places the editor action below, and advertises the
  full 256dp portrait content height so edge-to-edge IME windows cannot clip the final row.
- Original vectors, bilingual strings, architecture gates and Test Host coverage were updated for
  the reviewed hierarchy. No Typeless/Baidu code, icon or image asset was copied.

## Architecture

- contracts: toolbar remains capability-free with one leading, two primary and one right anchor;
  voice panel exposes only delete, punctuation, editor-action and bounded keyboard-tab callbacks.
- state changes: QWERTY toolbar is visible only on the QWERTY page without candidates; the 48dp
  candidate surface replaces it in place.
- migration: none.
- feature flag: none; the existing Route-A selection is unchanged.

All text insertion, deletion, Rime selection and editor actions continue through the existing
service routes and editor transaction authority.

## Security & privacy

- data sent/stored: none.
- permissions/components: none.
- threat considerations: sensitive/no-learning clipboard suppression, Emoji recents policy,
  voice availability and lifecycle cancellation remain active. Direct toolbar actions project the
  same precomputed privacy decisions as their existing panels.

## Tests actually run

| Command | Result | Notes |
|---|---|---|
| `scripts/verify_android.sh preflight` | PASS | Final run after contract/document updates. |
| `./gradlew :app:testDebugUnitTest` | PASS | 1097/1097 JVM tests. |
| `./gradlew :app:lintRelease :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :test-host:lintDebug :test-host:assembleDebug :test-host:assembleDebugAndroidTest` | PASS | 187-task Gradle graph; dependency verification remained enabled. |
| focused app instrumentation | PASS | 20/20 on API35 ARM64 emulator: toolbar 7, candidate 9, voice 4. |
| selected-system-IME Test Host | PASS | Voice/keyboard tabs, four 48dp toolbar actions, privacy transitions, direct clipboard and Emoji scenarios. |
| dark/light portrait visual inspection | PASS | Microphone stays on the panel centreline; dark portrait clipping reproduced, fixed and rechecked. |
| Xiaomi 15/HyperOS | NOT RUN | No physical Xiaomi 15 was connected during this task. |

## Evidence

- final dark voice screenshot: `/tmp/opentypeless-kbd006-voice-dark-final-20260828.png`.
- final dark QWERTY screenshot: `/tmp/opentypeless-kbd006-qwerty-dark-final-20260828.png`.
- Debug APK: 65,933,783 bytes; SHA-256
  `cacf266ba325b27bc5267a18680135e9dc388510e66e0620853aa6b4ecb4a09c`.
- unsigned Release APK: 63,774,749 bytes; SHA-256
  `b5cd91bfb36c5865e2ace20c08625b8be26b5d6b3e13bc8dbd389d18afa97c53`.
- AndroidTest APK: 1,142,956 bytes; SHA-256
  `c220ce4bdeeac9bfa37524015405e829f32f62d5285ab49bbb1c7114dbb173d3`.

## Risks

- The emulator has no private Xiaohè package, so the visual screenshot shows `Import` rather than
  the user's installed Chinese engine. Candidate geometry is covered by direct View tests, not a
  real Xiaohè screenshot in this task.
- Clipboard currently captures only when the panel opens or Refresh is pressed. ADR-0014 explicitly
  rejects `OnPrimaryClipChangedListener`; capturing two copies before opening the panel requires a
  superseding accepted ADR and a separate KBD-011 implementation.
- The supplied voice diagnostic is a session cancelled 8ms after start, before Ready, partial,
  stop-request or raw-final timestamps. It cannot measure the reported finalization delay; a
  successful `SUCCEEDED` sample with release metrics is required for the performance task.

## Rollback

Revert this task commit. No preference, database, permission, component or persistent format was
changed.

## Follow-ups

- `KBD-011`
- `TST-010`
- voice finalization performance task to be assigned

## Git

- branch: `codex/personal-ime`
- commit: this task commit
- worktree status: task files committed separately; the pre-existing untracked session handoff is
  intentionally excluded.
