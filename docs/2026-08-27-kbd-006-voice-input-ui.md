# Task Report: KBD-006 Voice input UI follow-up

## Result

DONE

## Scope

- Implemented: balanced portrait and compact wide-landscape voice controls; exact centered icons;
  neutral light/dark IME palette; state-aware hints; delete, punctuation, editor action and system-
  keyboard controls; quiet idle status and bounded transient errors.
- Not implemented: new voice-provider features, translation/rewrite actions, Action protocol,
  clipboard, Emoji, Xiaomi 15 acceptance or any change to the recording/security policy.

## Changes

- `VoiceInputPanel.java`: capability-free five-control surface with 48dp minimum targets, bounded
  callbacks, portrait/wide-landscape layouts and four presentation phases.
- `OpenTypelessImeService.java`: binds those callbacks to existing service routes, follows
  `EditorInfo` action labels, projects voice state and clears non-session errors after 4.5 seconds.
- IME resources: original backspace/globe vectors, state-list button surfaces, bilingual labels and
  a neutral charcoal/blue dark palette matching the user's XIME visual reference.
- Android and architecture tests: lock source capabilities, responsive geometry, callback count,
  icon centers, enabled state and the empty-status regression.

The product hierarchy was compared with the official [Typeless App Store listing](https://apps.apple.com/us/app/typeless-ai-voice-keyboard/id6749257650),
[installation guide](https://www.typeless.com/help/installation-and-setup) and
[iOS release notes](https://www.typeless.com/help/release-notes/ios/swipe-to-type). Only the broad
interaction hierarchy was used as reference; no Typeless or XIME code, icon or bundled asset was
copied.

## Architecture

- contracts: the panel accepts one already-labelled microphone and a closed listener with delete,
  punctuation, editor action, system switch and picker callbacks.
- state changes: `IDLE`, `PREPARING`, `LISTENING` and `PROCESSING` change hint/selection only.
- migration: none.
- feature flag: none; Route-A exclusivity is unchanged.

All actual text deletion/insertion remains behind the existing service routes and
EditorTransactionManager. The panel cannot obtain `InputConnection`, editor operations, network,
storage, native or reflection capability.

## Security & privacy

- data sent/stored: none.
- permissions/components: none.
- threat considerations: recording policy, sensitive-field hiding, lifecycle cancellation and
  disclosure rules are unchanged. Transient UI errors are presentation-only and do not retain
  error bodies or text.

## Tests actually run

| Command | Result | Notes |
|---|---|---|
| `scripts/verify_android.sh preflight` | PASS | 120 repository tests, 11 Android checks, 278 architecture contracts and 10 mobile-voice tests. |
| `./gradlew :app:testDebugUnitTest` | PASS | app JVM 1085/1085; zero failures. |
| `./gradlew :app:lintRelease :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest` | PASS | 120-task invocation; strict dependency verification remained enabled. |
| focused `connectedDebugAndroidTest` | PASS | 13/13 on API35 ARM64 emulator: voice panel 4, toolbar 5 and input-mode layout 4. |
| system-selected IME visual check | PASS | light portrait, dark portrait and dark wide-landscape; no idle failure text or clipped controls. |
| Xiaomi 15/HyperOS | NOT RUN | no physical Xiaomi device was connected during this follow-up. |

## Evidence

- light portrait screenshot: `/tmp/opentypeless-voice-redesign-light-final-focused.png`.
- dark portrait screenshot: `/tmp/opentypeless-voice-redesign-dark.png`.
- dark landscape screenshot: `/tmp/opentypeless-voice-redesign-landscape.png`.
- Debug APK: 66,138,869 bytes; SHA-256
  `d7e5b15217d65dc98aa73814b99b7d54e9a4ecbe3cc6440dd7954fc38c462804`.
- unsigned Release APK: 63,631,933 bytes; SHA-256
  `0c9cb1e6a8074f111394227049ce91b334de015ab8eae535a9a374141ddec242`.
- AndroidTest APK: 1,121,899 bytes; SHA-256
  `7035ac4189681b5ec1296326a5cf88e0032a509280e91404a8091294a2b74266`.

## Risks

- The follow-up is device-tested on AOSP API35 only. Xiaomi 15 font metrics, gesture inset and
  HyperOS input-window composition still require KBD-009/TST-010 physical-device acceptance.
- Additional Typeless-like edit/translate functions require separately reviewed product and
  disclosure contracts; they are intentionally not smuggled into this UI task.

## Rollback

Revert this task commit. No preference, database, permission, component or persistent format was
changed.

## Follow-ups

- `KBD-011`
- `KBD-010`
- `KBD-009`
- `TST-010`

## Git

- branch: `codex/personal-ime`
- commit: this task commit
- worktree status: task files committed separately; the pre-existing untracked session handoff is
  intentionally excluded.
