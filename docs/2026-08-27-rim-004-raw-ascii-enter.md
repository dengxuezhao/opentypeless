# Task Report: RIM-004 raw ASCII Enter follow-up

## Result

DONE

## Scope

- Implemented: when a Chinese/Rime composition contains ASCII preedit, Enter accepts and commits
  that exact raw ASCII instead of rejecting the key or selecting a Chinese candidate.
- Preserved: Enter with no Rime composition still follows the existing semantic editor-action or
  newline route; candidate click/auto-commit and backspace behavior remain unchanged.
- Not implemented: symbol-width switching, keyboard geometry, voice UI, clipboard history/search,
  or Emoji catalog changes. Those remain separate task IDs and rollback commits.

## Changes

- `NativeRimeInputEngine`: route non-empty `ENTER` through the same bounded composition-completion
  result used by candidate and fixed-length commits; close/checkpoint the exact native lease first.
- `NativeRimeInputEngineTest`: verify exact `ni` commit, zero candidate selection, empty preedit and
  one-shot session/checkpoint/lease closure; keep empty Enter fail-closed.
- Rime architecture gate: reject removal of the raw-ASCII Enter path and require its regression
  test.
- `TestHostInstrumentedTest`: select QWERTY deterministically, tolerate the remembered Latin/Rime
  state, target the stable two-line-key accessibility descriptions, and assert Enter removes the
  composing span while preserving `ni`.
- KSP-012 policy: add exact reviewed incremental/device-evidence and final clean APK hashes; no Rime
  data allowlist was broadened.

## Architecture

- contracts: `ENTER` returns `CommitReady` only when bounded `asciiInput` is non-empty; the commit
  retains editor generation, coordination generation and monotonically increasing revision.
- state changes: accepted raw ASCII clears preedit/candidates and closes the current native/UserDB
  lease exactly once, matching existing candidate commits.
- migration: none.
- feature flag: none.

## Security & privacy

- data sent/stored: no network and no new persistent format. The synthetic test schema stayed in
  app-private no-backup storage only for the device test and was removed afterward.
- permissions/components: none added or changed.
- threat considerations: the engine still has no `InputConnection` or editor-writer authority;
  the service continues through CompositionCoordinator and the single EditorTransactionManager.
  Empty Enter, stale generation and post-commit replay remain fail-closed.

## Tests actually run

| Command | Result | Notes |
|---|---|---|
| focused architecture contracts | PASS | 41/41; Rime, candidate bar and Latin keyboard contracts. |
| focused JVM + host compile | PASS | `NativeRimeInputEngineTest` 15/15; Android test-host compiled. |
| `scripts/test_verify_rime_resource_policy.py` | PASS | 37/37 hostile policy tests. |
| `scripts/verify_android.sh all` | PASS | 120 script tests, 272 architecture tests, 191 Gradle tasks, Release Lint, five APKs and 1199 XML tests. |
| actual librime on API 35 arm64 emulator | PASS | final clean APKs; synthetic import and native preedit/candidate/backspace 1/1, cleanup rerun 1/1. |
| system-selected IME on API 35 arm64 emulator | PASS | final clean APKs; `n -> ni -> backspace -> n -> ni -> Enter`, exact `ni`, composing span removed, 1/1. |
| Xiaomi device | NOT RUN | only `emulator-5554` was connected for this task; no Xiaomi result is claimed. |

## Evidence

- Debug APK: 65,523,463 bytes, SHA-256
  `65e6e1bc2b8337511b4f3733a7b4cb2117ab1df951d5ea9e92b0fa7b596aa68f`.
- unsigned Release APK: 63,628,769 bytes, SHA-256
  `c4d08980dc9f1009346b0ab1ce2128bca5a1e8e7d0ac3711ffb1910ce99b2d30`.
- app AndroidTest APK: 1,103,906 bytes, SHA-256
  `50af81d31ee32e73b0f4d8877349d79f387bd495967dadf2dc663a6e1607b16b`.
- test-host / test-host AndroidTest: 13,085 / 1,698,068 bytes, SHA-256
  `908d7582c6c668466311aeb2c124167eb5985900abcece78e499bc6970e1a4e3` /
  `7bcd86c64aab8abee8540d212569182d2338ee59dcbf5c41edb7992f3f8d6215`.
- resource policy canonical SHA-256:
  `ab5d0c0ae5f8683187be7709eb4dfac6b8ede8d3c695569463de858cdc39a804`;
  all five APKs reported real Xiaohè resources 0 and violations 0.
- synthetic device package: 2,191 bytes, SHA-256
  `7f5c927e9f1803c4f2f962a45b4878a05e11be1781627283f6ae2bdde2d0049c`;
  exact app-private copy, UserDB directory and `/data/local/tmp` copy were removed, and LatinIME was
  restored.

## Risks

- This accepts only the literal ASCII preedit. It intentionally does not reinterpret case,
  punctuation or candidate text.
- Device evidence covers the API 35 arm64 emulator, not the user's Xiaomi hardware.

## Rollback

Revert this task's single commit. There is no data migration; the prior behavior will again reject
Enter during non-empty Rime composition.

## Follow-ups

- `KBD-015`
- `KBD-009`
- `KBD-006`
- `KBD-011`
- `KBD-010`

## Git

- branch: `codex/personal-ime`
- commit: this report is included in the task's dedicated rollback commit
- worktree status: user-owned `docs/2026-08-19-session-handoff.md` remains untracked and excluded
