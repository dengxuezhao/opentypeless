# ADR-0014: Clipboard history encrypted format and explicit-capture boundary

## Status

Superseded

Replaced by [ADR-0016](0016-active-ime-clipboard-capture-and-pinned-format.md).

## Background

The first `KBD-011` slice deliberately renders only the current Android clipboard item and retains
no history. The requested multi-item list and search require a persistent format that can contain
private user text. Plain SharedPreferences, an unbounded database, background clipboard listeners,
or reuse of the dictation-history encryption domain would create avoidable privacy, migration and
latency risks. Sensitive and no-learning fields must not cause a clipboard read, history decrypt,
search, display or write.

Android clipboard access also varies by OS and OEM. The feature therefore needs a useful history
without claiming that it captures every copy event or adding a service that monitors the clipboard
while the keyboard is not being used.

## Decision

- Capture only the already-materialized primary plain-text item when the user explicitly opens the
  clipboard panel or presses Refresh. Do not register `OnPrimaryClipChangedListener`, poll, coerce a
  URI/Intent, or read from a sensitive field.
- Keep a most-recent-first list of at most 100 distinct entries and at most 120,000 Unicode code
  points in total. Each entry still obeys the existing 40,000-code-point editor bound. Recording a
  duplicate moves it to the front; the oldest entries are removed to satisfy both bounds.
- Persist in private SharedPreferences `opentypeless_clipboard_history_v1` with
  `format_version=1` and one authenticated ciphertext payload. The canonical plaintext codec has a
  fixed magic line, version line and one unpadded Base64URL UTF-8 entry per line. Unknown versions,
  duplicate/non-canonical encodings, malformed UTF-8/UTF-16, controls and all size/count overflow
  fail closed.
- Encrypt the complete payload with AES-256-GCM under a new AndroidKeyStore alias
  `opentypeless_clipboard_history_v1`, prefix `opentypeless-encrypted-clipboard:v1:` and domain AAD
  `OpenTypelessClipboard:v1`. Clipboard data must not use the dictation-history key or accept a
  plaintext legacy fallback.
- Keystore and SharedPreferences work runs only on the service's serialized local-I/O executor.
  Every request carries both editor epoch and clipboard request generation; a stale completion may
  not render or paste. A failed commit may return the bounded in-memory result for the active panel
  but must report that persistence failed.
- Persist only entry bodies and MRU order: no timestamp, count, package, field identity, type,
  surrounding text or search query. Text/number/link categories are computed in memory. Search is
  Unicode case-insensitive substring matching over the bounded active list.
- While the user edits a search query, visible QWERTY key callbacks are diverted to the panel's
  in-memory query before Rime/editor routing. Backspace and Enter edit/finish search; no search key
  creates an EditorOperation. Closing the panel or entering a restricted lifecycle destroys the
  query and rendered entry references.
- Clear all requires an explicit second confirmation in the active panel. The store remains
  excluded from cloud backup, device transfer, export, diagnostics and network paths.

Rejected alternatives: current-item-only rendering (the reported usability failure), plaintext
history, unbounded 300-item storage, background/global clipboard monitoring, saving app/timestamp
metadata, reusing dictation-history encryption keys, and an internal searchable `EditText` that
would need a second IME/editor authority.

## Consequences

The user can build a useful, searchable multi-item history by opening or refreshing the panel after
copying text. The product intentionally does not promise capture of copies that were never observed
during those explicit actions. History survives process restart, remains encrypted and backup-
excluded, and can be cleared locally.

The implementation adds one versioned private format and one Keystore alias. Keystore invalidation,
authenticated-payload corruption or unsupported future versions make history unavailable rather
than exposing plaintext or guessing a migration. A future incompatible format, listener policy or
cross-device sync requires a superseding ADR.

## Validation

Accepted on 2026-08-27 with the implementation evidence below:

- Focused JVM tests for `ClipboardHistory`, `ClipboardHistoryCodec`, `ClipboardPanelSnapshot` and
  `LocalClipboardCipher`: PASS 14/14. They cover MRU bounds, strict canonical decoding, Unicode,
  tamper/legacy-plaintext rejection and redacted diagnostics.
- Focused API35 ARM64 instrumentation for `ClipboardHistoryStore`, `KeyboardClipboardPanel` and
  `AndroidClipboardReader`: PASS 9/9. Raw preferences contain authenticated ciphertext rather than
  tested plaintext; restart loading, corrupt-payload recovery, future-version preservation,
  categories, search, clear confirmation and callback invalidation pass.
- System-selected IME Test Host clipboard scenario: PASS 1/1. Paste uses the editor transaction
  route, the OTP field and a `NO_PERSONALIZED_LEARNING` field expose no clipboard entry, and the
  panel closes across the restricted-field transition.
- `scripts/verify_android.sh preflight`: PASS with 121 repository tests, 11 Android checks, 285
  architecture contracts and 10 mobile-voice tests. The clipboard-specific architecture gate is
  PASS 13/13 and the sensitive-toolbar gate is PASS 10/10.
- The clean Gradle verification graph completed 191 tasks; app JVM tests are PASS 1095/1095. Exact
  product and test APK resource scans report zero violations and zero bundled Xiaohè resources.
- `AndroidManifest.xml` and `data_extraction_rules.xml` remain unchanged: no permission, exported
  component, backup, device-transfer, listener or network path was added.
- Manual process-restart and search checks on the API35 ARM64 emulator preserved two encrypted
  entries, filtered `exam` without editor insertion and rendered the revised back/clear controls.

## Rollback

Revert the `KBD-011` history commit. Older builds ignore the new preference file and alias, so the
original current-item panel resumes without migration. The encrypted file may be removed by the
rollback build or app-data clear; no other domain depends on it. A future version must ignore, not
reinterpret or overwrite, an unknown newer `format_version`.

## References

- Task: `KBD-011`
- Design: `docs/opentypeless_specs/02_ARCHITECTURE_DEVELOPMENT.md`,
  `06_SECURITY_PRIVACY.md`, `07_IMPLEMENTATION_BACKLOG.md`, `08_TEST_VALIDATION.md`
- Related ADR: [ADR-0013](0013-emoji-recents-private-format.md)
