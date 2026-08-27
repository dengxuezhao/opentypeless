# Task Report: KBD-011 Clipboard history and search follow-up

## Result

DONE

## Scope

- Implemented: encrypted 100-entry MRU clipboard history; bounded text/number/link categories;
  in-panel search, refresh and confirmed clear; revised compact card UI; process-restart recovery;
  sensitive and no-learning field exclusion.
- Not implemented: background clipboard monitoring, URI/Intent coercion, cross-device sync, export,
  cloud backup, source-app metadata or Xiaomi physical-device acceptance.

## Changes

- `ClipboardHistory.java` and `ClipboardHistoryCodec.java`: immutable bounded MRU model and strict
  canonical format-v1 codec.
- `ClipboardHistoryStore.java` and `LocalClipboardCipher.java`: serialized private persistence with
  a clipboard-only AndroidKeyStore AES-256-GCM alias and no plaintext fallback.
- `KeyboardClipboardPanel.java`: searchable categories, multi-card list, count, refresh, back/close
  controls and double-confirm clear with generation-safe callbacks.
- `OpenTypelessImeService.java`: asynchronous epoch/generation-checked loading; explicit capture;
  in-memory search-key routing; destructive lifecycle cleanup; sensitive/no-learning gates; ETM-only
  paste.
- IME resources: bilingual labels plus dedicated search, refresh, clear, close and back vectors and
  clipboard surfaces.
- Tests and policy: JVM/instrumentation/architecture coverage, accepted ADR-0014, compatibility
  authority and exact reviewed resource-policy identities.

## Architecture

- contracts: the panel receives immutable snapshots and bounded callbacks; it has no
  `ClipboardManager`, `InputConnection`, editor operation, storage, network, native or reflection
  capability.
- state changes: each load carries editor epoch plus clipboard request generation; only the active
  unrestricted field may render, query or paste the result.
- migration: new private `android-clipboard-history` format version 1; absent or corrupt v1 recovers
  empty, while an unknown future version is preserved and fails closed.
- feature flag: none; this replaces the previous current-item-only KBD-011 panel.

## Security & privacy

- data sent/stored: up to 100 explicitly observed plain-text clipboard bodies are stored locally as
  one authenticated ciphertext. Nothing is sent, backed up, transferred, exported or diagnosed.
- permissions/components: none added.
- threat considerations: no listener or polling; no URI/Intent parsing; no metadata/search-query
  persistence; a distinct key/AAD domain; strict size/count/UTF-8 bounds; stale generations fail
  closed; sensitive and no-learning fields cannot read, decrypt, render, capture or paste history.

## Tests actually run

| Command | Result | Notes |
|---|---|---|
| focused clipboard JVM tests | PASS | 14/14: MRU model, format-v1 codec, snapshots and AES-GCM cipher. |
| focused `connectedDebugAndroidTest` | PASS | 9/9 on API35 ARM64 emulator: store 3, panel 4 and reader 2. |
| system-selected Test Host clipboard method | PASS | 1/1; ETM paste plus OTP and no-learning exclusion. |
| `scripts/verify_android.sh preflight` | PASS | 121 repository tests, 11 Android checks, 285 architecture contracts and 10 mobile-voice tests. |
| clean Gradle verification graph | PASS | 191 tasks; app JVM 1095/1095 and compiled architecture gate 114/114. |
| exact product/test APK resource scans | PASS | Zero violations and zero bundled Xiaohè resources. |
| Xiaomi 10 Ultra / Xiaomi 15 | NOT RUN | No physical Xiaomi device was connected. |

## Evidence

- two-entry panel screenshot: `/tmp/opentypeless-clipboard-two-final.png`.
- post-restart persistence screenshot: `/tmp/opentypeless-clipboard-persisted.png`.
- final search UI screenshot: `/tmp/opentypeless-clipboard-search-final.png`.
- Debug APK: 65,552,076 bytes; SHA-256
  `c49d3640032729990e3cc7da3b9810f2f92657d801543b0468c9eaf3e0fddd25`.
- unsigned Release APK: 63,639,409 bytes; SHA-256
  `cf587203d70e2fbc4f8daa5834f1e40597868caa5e465b54b5e5833acbbcf483`.
- AndroidTest APK: 1,112,662 bytes; SHA-256
  `14780efd611662ecb437944ed0295c30a0f6a61d1335ab7ea13141408bbf6110`.

## Risks

- History observes clipboard text only when the user opens or refreshes the panel; copies never
  observed by those actions are intentionally absent.
- AndroidKeyStore invalidation or authenticated-payload corruption recovers an empty history rather
  than guessing or exposing data.
- HyperOS clipboard notices, font metrics and gesture-inset behavior remain unverified without a
  connected Xiaomi device.

## Rollback

Revert this task commit. Older builds ignore the private preference file and key alias and resume
the original current-item panel. No other format or domain depends on this data.

## Follow-ups

- `KBD-010`
- `KBD-009`
- `TST-010`

## Git

- branch: `codex/personal-ime`
- commit: this task commit
- worktree status: task files committed separately; the pre-existing untracked session handoff is
  intentionally excluded.
