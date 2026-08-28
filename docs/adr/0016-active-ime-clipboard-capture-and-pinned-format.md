# ADR-0016: IME-service clipboard capture, per-entry deletion and pinned v2 format

## Status

Accepted

## Background

ADR-0014 intentionally reads the Android clipboard only when the user opens the clipboard panel or
presses Refresh. That boundary is safe but cannot satisfy the reported core workflow: when two
different texts are copied before the panel opens, Android exposes only the latest primary item and
the earlier item is unrecoverable. The current format also stores only text and MRU order, so it
cannot represent a user-controlled pinned item.

Continuous process-wide monitoring, a background service, package/source metadata or reads from a
sensitive field would exceed the requested personal keyboard feature and expand the privacy
boundary. Pin metadata changes a persistent private format and therefore requires an explicit,
tested migration and rollback rule before implementation.

## Decision

- Register one `ClipboardManager.OnPrimaryClipChangedListener` for the lifetime of the existing
  OpenTypeless `InputMethodService`, including while its keyboard window is hidden. This is required
  for the common flow where selecting and copying text hides the keyboard. Do not add a separate
  background service, permission, polling loop or network path, and unregister on service
  destruction.
- Suspend reads and writes as soon as a sensitive or `NO_PERSONALIZED_LEARNING` editor starts. Keep
  that restriction latched across input-view/window/editor finish and resume only when a subsequent
  ordinary learning-allowed editor starts. This prevents a copy immediately after leaving a
  restricted field from being misclassified merely because `currentEditor` became null. Before any
  editor has established a restricted latch, service-lifetime callbacks may be captured.
- Each accepted callback immediately snapshots only item zero's already-materialized plain text.
  URI and Intent items are not coerced or resolved. The callback records no timestamp, source app,
  package, field, copy count or MIME metadata. Callback and local-I/O work carry editor epoch and
  observation generation; stale work may neither render nor paste.
- Keep explicit open/Refresh capture as a recovery path for OEMs that suppress a listener event.
  Recording remains deduplicated, so observing and then refreshing the same text does not create a
  second entry.
- Retain the existing limits of 100 distinct entries, 120,000 Unicode code points total and 40,000
  code points per entry. Pinned items sort before unpinned items. Pinning or unpinning moves an item
  to the newest position in its new section; recording a duplicate preserves its pin state and
  moves it to the newest position in that section. Capacity eviction removes the oldest unpinned
  item first, then the oldest pinned item only when every retained item is pinned. Delete removes
  exactly the selected entry. Clear-all keeps its existing second confirmation.
- Upgrade the authenticated plaintext payload to `format_version=2`. Each canonical entry line is
  a one-byte pin marker (`p` or `u`), a colon and unpadded Base64URL UTF-8 text. Decoding rejects
  malformed markers, non-canonical Base64/UTF-8, duplicate text, invalid ordering, controls and all
  count/size overflow.
- Migrate an authenticated v1 payload by decoding it with the frozen v1 grammar, assigning every
  legacy entry `pinned=false`, then atomically committing canonical v2 before returning it. A
  malformed v1 payload recovers empty as before. Any unknown newer outer or inner version is
  preserved and fails closed rather than being overwritten.
- Reuse private preference file `opentypeless_clipboard_history_v1`, AndroidKeyStore alias
  `opentypeless_clipboard_history_v1`, cipher prefix `opentypeless-encrypted-clipboard:v1:` and AAD
  `OpenTypelessClipboard:v1`. This is a payload-schema migration inside the same clipboard-only data
  and cryptographic domain, not a key/domain migration. Plaintext and the dictation-history key
  remain forbidden.
- Store mutation stays on the service's serialized local-I/O executor. The capability-free panel
  receives bounded callbacks for paste, pin/unpin and delete; it never receives a clipboard,
  persistence, editor or network capability. Paste continues through the existing single ETM
  facade.

Rejected alternatives: keep explicit-only capture, stop observing whenever the keyboard window is
hidden, add a separate Android background service, request Accessibility or broad clipboard
permissions, store source metadata, add a second plaintext pin store, use text hashes as durable
identifiers, or discard all v1 history during upgrade.

## Consequences

Two copy events observed while the OpenTypeless IME service is alive can be retained before the
panel opens even if the keyboard window is hidden. Copies made while the service process is absent,
the OEM suppresses callbacks or the restricted latch is active are intentionally not promised;
Refresh can only recover Android's current primary item.

Pinning and deletion survive process restart without exposing plaintext. The v2 payload is not
readable by an older v1 build, but that build recognizes it as a future version and leaves it
unchanged. Listener lifecycle and migration add contract and device-test obligations. No new
permission, exported component, backup surface, sync, diagnostic body or network behavior is added.

ADR-0016 supersedes ADR-0014's explicit-only capture rule and v1 payload schema. ADR-0014's bounds,
plain-text-only read, encryption domain, sensitive/no-learning policy, search behavior, ETM paste
route and no-export/no-network decisions remain in force.

## Validation

Acceptance evidence recorded before implementation on 2026-08-28:

- `./gradlew testDebugUnitTest --tests 'com.opentypeless.android.keyboard.clipboard.*' --tests
  'com.opentypeless.android.security.LocalClipboardCipherTest'`: PASS on the v1 baseline.
- `python3 architecture-tests/clipboard_panel_contract.py`: PASS on the v1 baseline and confirms the
  shipped source forbids any listener, matching the reported consecutive-copy root cause.
- Source audit of `OpenTypelessImeService`, `ClipboardHistoryStore`, `ClipboardHistoryCodec` and
  `SystemClipboardReader` confirms one serialized local-I/O owner, explicit lifecycle clearing, one
  distinct AES-GCM domain and no permission/background component requirement.

The implementation gate covers reproducible tests for two ordered listener
callbacks while the window is hidden, restricted-latch privacy lifecycle, service unregister,
duplicate callback deduplication, per-entry delete, pin/unpin ordering and eviction, strict v2
codec, encrypted v1-to-v2 migration, unknown
future preservation, stale panel callbacks, ETM-only paste and API 35 system-selected IME behavior.

Implementation evidence recorded on 2026-08-28:

- Focused JVM clipboard/cipher tests: PASS 17/17. Coverage includes pin/unpin/delete ordering,
  pinned-first eviction including the all-pinned boundary, duplicate pin preservation, strict v2,
  frozen v1 migration, malformed text and authenticated cipher rejection.
- `python3 -m unittest test_clipboard_panel_contract.py`: PASS 16/16. The source contract requires
  the single service-owned listener, restricted latch, hidden-window continuity, destroy-time
  unregister, v2 migration, capability-free panel and ETM-only paste.
- API 35 ARM64 focused instrumentation: PASS 11/11 for real AndroidKeyStore v1-to-v2 migration,
  unknown future preservation, ciphertext-at-rest, restart pin/delete state, exact/stale UI actions,
  48dp controls and plain-text-only Android clipboard reads.
- System-selected OpenTypeless Test Host: PASS 1/1. With the keyboard window hidden, two consecutive
  copies were retained; the first was pinned, the second deleted and the first pasted. OTP and
  no-learning fields hid the action and latched capture off; a later ordinary editor resumed it.
  The emulator default IME was restored to LatinIME after the run.
- `scripts/verify_android.sh all` with pinned JDK 17 and Android SDK 35: PASS. The clean graph ran
  121 script tests, 294 source architecture tests and 191 Gradle tasks; Release Lint, Debug/Release
  assembly, five exact APK scans and engineering metrics completed with zero resource violations.
- Manifest/source review: no new permission, exported component, separate service, source metadata,
  plaintext fallback, diagnostic body, backup, network or dependency was added.

## Rollback

Reverting to the v1 implementation leaves an existing outer `format_version=2` payload untouched
as `FUTURE_VERSION`; history is temporarily unavailable but not reinterpreted or silently erased.
Re-applying the v2 implementation restores it. A deliberate downgrade tool may decode v2 and write
v1 after dropping pin markers, but is not part of this task. App-data clear remains the destructive
user recovery. The listener can be removed independently without changing stored v2 data.

## References

- Task: `KBD-011`
- Design: `docs/opentypeless_specs/02_ARCHITECTURE_DEVELOPMENT.md`,
  `06_SECURITY_PRIVACY.md`, `07_IMPLEMENTATION_BACKLOG.md`, `08_TEST_VALIDATION.md`
- Supersedes: [ADR-0014](0014-clipboard-history-encrypted-format.md)
