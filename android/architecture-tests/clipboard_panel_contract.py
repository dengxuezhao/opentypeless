#!/usr/bin/env python3
"""KBD-011 fail-closed encrypted clipboard-history and search source boundary."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
from pathlib import Path
import re
import sys


CLIPBOARD_ROOT = Path("app/src/main/java/com/opentypeless/android/keyboard/clipboard")
CIPHER = Path("app/src/main/java/com/opentypeless/android/security/LocalClipboardCipher.java")
SERVICE = Path("app/src/main/java/com/opentypeless/android/ime/OpenTypelessImeService.java")
SNAPSHOT_TEST = Path("app/src/test/java/com/opentypeless/android/keyboard/clipboard/ClipboardPanelSnapshotTest.java")
HISTORY_TEST = Path("app/src/test/java/com/opentypeless/android/keyboard/clipboard/ClipboardHistoryTest.java")
CODEC_TEST = Path("app/src/test/java/com/opentypeless/android/keyboard/clipboard/ClipboardHistoryCodecTest.java")
CIPHER_TEST = Path("app/src/test/java/com/opentypeless/android/security/LocalClipboardCipherTest.java")
READER_TEST = Path("app/src/androidTest/java/com/opentypeless/android/keyboard/clipboard/SystemClipboardReaderInstrumentedTest.java")
STORE_TEST = Path("app/src/androidTest/java/com/opentypeless/android/keyboard/clipboard/ClipboardHistoryStoreInstrumentedTest.java")
PANEL_TEST = Path("app/src/androidTest/java/com/opentypeless/android/keyboard/clipboard/KeyboardClipboardPanelInstrumentedTest.java")
HOST_TEST = Path("test-host/src/androidTest/java/com/opentypeless/testhost/TestHostInstrumentedTest.java")
ADR = Path("../docs/adr/0014-clipboard-history-encrypted-format.md")
EXPECTED_FILES = {
    "ClipboardHistory.java",
    "ClipboardHistoryCodec.java",
    "ClipboardHistoryStore.java",
    "ClipboardPanelSnapshot.java",
    "KeyboardClipboardPanel.java",
    "SystemClipboardReader.java",
}
WRITER = re.compile(
    r"\.\s*(?:commitText|setComposingText|finishComposingText|"
    r"deleteSurroundingText(?:InCodePoints)?|sendKeyEvent|setSelection)\s*\("
)


@dataclass(frozen=True)
class Violation:
    rule: str
    detail: str


def _read(path: Path, rule: str, violations: list[Violation]) -> str:
    if not path.is_file() or path.is_symlink():
        violations.append(Violation(rule, str(path)))
        return ""
    try:
        return path.read_text(encoding="utf-8")
    except UnicodeError:
        violations.append(Violation(rule, f"invalid UTF-8: {path}"))
        return ""


def _compact(value: str) -> str:
    return re.sub(r"\s+", "", value)


def inspect_android(android_root: Path) -> tuple[Violation, ...]:
    root = android_root.resolve()
    violations: list[Violation] = []
    clipboard_root = root / CLIPBOARD_ROOT
    observed = {
        path.name for path in clipboard_root.iterdir()
        if path.is_file() and not path.is_symlink()
    } if clipboard_root.is_dir() and not clipboard_root.is_symlink() else set()
    if observed != EXPECTED_FILES:
        violations.append(Violation(
            "KBD011_SOURCE_SET",
            f"expected {sorted(EXPECTED_FILES)}, got {sorted(observed)}",
        ))

    sources = {
        name: _read(clipboard_root / name, "KBD011_SOURCE", violations)
        for name in EXPECTED_FILES
    }
    snapshot = sources.get("ClipboardPanelSnapshot.java", "")
    history = sources.get("ClipboardHistory.java", "")
    codec = sources.get("ClipboardHistoryCodec.java", "")
    store = sources.get("ClipboardHistoryStore.java", "")
    panel = sources.get("KeyboardClipboardPanel.java", "")
    reader = sources.get("SystemClipboardReader.java", "")
    cipher = _read(root / CIPHER, "KBD011_CIPHER_SOURCE", violations)
    service = _read(root / SERVICE, "KBD011_SERVICE", violations)
    tests = {
        path: _read(root / path, "KBD011_TEST_SOURCE", violations)
        for path in (
            SNAPSHOT_TEST, HISTORY_TEST, CODEC_TEST, CIPHER_TEST,
            READER_TEST, STORE_TEST, PANEL_TEST, HOST_TEST,
        )
    }
    adr = _read((root / ADR).resolve(), "KBD011_ACCEPTED_ADR", violations)

    pure_forbidden = (
        "import android.", "InputConnection", "ClipboardManager", "ClipData",
        "java.net.", "SharedPreferences", "Bundle", "Intent", "Log.",
    )
    if any(token in snapshot for token in pure_forbidden) or "catch (Exception" in snapshot:
        violations.append(Violation(
            "KBD011_SNAPSHOT_CAPABILITY",
            "clipboard snapshot must remain pure, bounded and content-redacted",
        ))
    snapshot_compact = _compact(snapshot)
    snapshot_tokens = (
        "MAX_TEXT_CODE_POINTS=EditorOperation.MAX_TEXT_CODE_POINTS",
        "EditorSessionLimits.requireWellFormedUtf16(text,\"clipboardText\")",
        "Character.isISOControl(codePoint)",
        "returntext.substring(0,end)+'\\u2026'",
        'return"ClipboardPanelSnapshot{state="+state',
    )
    if any(token not in snapshot_compact for token in snapshot_tokens):
        violations.append(Violation(
            "KBD011_SNAPSHOT_BOUNDARY",
            "snapshot must reject malformed/oversized text and redact diagnostics",
        ))

    history_forbidden = pure_forbidden + (
        "java.io.", "java.nio.", "Cipher", "KeyStore", "Executor", "Thread",
    )
    history_compact = _compact(history)
    history_tokens = (
        "MAX_ENTRIES=100;",
        "MAX_TOTAL_CODE_POINTS=120_000",
        "MAX_SEARCH_CODE_POINTS=64",
        "enumCategory{ALL,TEXT,NUMBER,LINK}",
        "updated.size()>=MAX_ENTRIES",
        "total+entry.codePoints()>MAX_TOTAL_CODE_POINTS",
        "entry.text().toLowerCase(Locale.ROOT).contains(needle)",
        'return"ClipboardHistory{entries="+entries.size()',
    )
    if (
        any(token in history for token in history_forbidden)
        or any(token not in history_compact for token in history_tokens)
    ):
        violations.append(Violation(
            "KBD011_HISTORY_BOUNDARY",
            "history must remain pure, bounded, searchable and content-redacted",
        ))

    codec_compact = _compact(codec)
    codec_tokens = (
        "FORMAT_VERSION=1",
        'MAGIC="opentypeless-clipboard-history"',
        "MAX_ENCODED_PAYLOAD_CHARS=700_000",
        "Base64.getUrlEncoder().withoutPadding()",
        "CodingErrorAction.REPORT",
        "ClipboardHistory.fromNewestFirst(texts)",
        "!encode(history).equals(encoded)",
    )
    if (
        "import android." in codec
        or WRITER.search(codec)
        or any(token not in codec_compact for token in codec_tokens)
        or codec.count("CodingErrorAction.REPORT") != 2
    ):
        violations.append(Violation(
            "KBD011_CODEC_BOUNDARY",
            "v1 codec must be canonical, bounded and strict UTF-8",
        ))

    cipher_compact = _compact(cipher)
    cipher_tokens = (
        'KEY_ALIAS="opentypeless_clipboard_history_v1"',
        'PREFIX="opentypeless-encrypted-clipboard:v1:"',
        '"OpenTypelessClipboard:v1".getBytes(StandardCharsets.UTF_8)',
        'Cipher.getInstance("AES/GCM/NoPadding")',
        ".setRandomizedEncryptionRequired(true)",
        'thrownewIllegalArgumentException("Unencryptedclipboardhistoryisnotaccepted")',
    )
    if any(token not in cipher_compact for token in cipher_tokens) or "decryptOrLegacy" in cipher:
        violations.append(Violation(
            "KBD011_CIPHER_DOMAIN",
            "clipboard needs a distinct authenticated domain with no plaintext fallback",
        ))

    store_forbidden = (
        "InputConnection", "com.opentypeless.android.editor", "java.net.",
        "addPrimaryClipChangedListener", "Executor", "Thread", "Log.",
    )
    store_compact = _compact(store)
    store_tokens = (
        'STORE="opentypeless_clipboard_history_v1"',
        'FORMAT_VERSION="format_version"',
        'ENCRYPTED_PAYLOAD="encrypted_payload"',
        "newLocalClipboardCipher()",
        ".putInt(FORMAT_VERSION,ClipboardHistoryCodec.FORMAT_VERSION)",
        ".putString(ENCRYPTED_PAYLOAD,encrypted)",
        ".commit()",
        "version!=ClipboardHistoryCodec.FORMAT_VERSION",
        "returnnewLoaded(ClipboardHistory.empty(),Status.FUTURE_VERSION)",
    )
    if (
        any(token in store for token in store_forbidden)
        or WRITER.search(store)
        or any(token not in store_compact for token in store_tokens)
    ):
        violations.append(Violation(
            "KBD011_STORE_BOUNDARY",
            "store must keep the accepted v1 cipher/format and no editor/thread capability",
        ))

    panel_forbidden = (
        "ClipboardManager", "ClipData", "InputConnection",
        "com.opentypeless.android.editor", "java.net.", "java.io.",
        "SharedPreferences", "LocalClipboardCipher", "System.loadLibrary", "Log.",
    )
    panel_compact = _compact(panel)
    panel_tokens = (
        "MINIMUM_TOUCH_TARGET_DP=48",
        "PANEL_MINIMUM_HEIGHT_DP=276",
        "SEARCH_OVERLAY_HEIGHT_DP=60",
        "voidonPaste(Stringtext)",
        "voidonRefresh()",
        "voidonSearchEditingChanged(booleanediting)",
        "voidonClearHistory()",
        "booleanappendSearchText(Stringtext)",
        "booleandeleteSearchCodePoint()",
        "booleanfinishSearchEditing()",
        "history=ClipboardHistory.empty()",
        "query=\"\"",
        "if(generation==renderGeneration)listener.onPaste(exactText)",
    )
    if (
        any(token in panel for token in panel_forbidden)
        or WRITER.search(panel)
        or any(token not in panel_compact for token in panel_tokens)
    ):
        violations.append(Violation(
            "KBD011_PANEL_CAPABILITY",
            "panel may own only bounded View/search state and callbacks",
        ))

    reader_compact = _compact(reader)
    reader_tokens = (
        "manager.getPrimaryClip()",
        "clip.getItemAt(0).getText()",
        "ClipboardPanelSnapshot.fromPrimaryText",
    )
    reader_forbidden = (
        "addPrimaryClipChangedListener", "removePrimaryClipChangedListener",
        "coerceToText", "coerceToStyledText", "getUri()", "getIntent()",
        "SharedPreferences", "java.io.", "java.net.", "Log.",
    )
    if (
        any(token not in reader_compact for token in reader_tokens)
        or any(token in reader for token in reader_forbidden)
        or reader.count("getPrimaryClip()") != 1
    ):
        violations.append(Violation(
            "KBD011_EXPLICIT_READER",
            "reader must perform one explicit plain-text read with no listener/coercion",
        ))

    service_compact = _compact(service)
    service_tokens = (
        "clipboardHistoryStore=newClipboardHistoryStore(this)",
        "caseMENU_CLIPBOARD->showClipboardPanel()",
        "ClipboardPanelSnapshotcurrent=SystemClipboardReader.readCurrentText(this)",
        "finallongrequest=++clipboardHistoryRequest",
        "finallongrequestEpoch=editorEpoch",
        "localIo.execute(()->",
        "request!=clipboardHistoryRequest||requestEpoch!=editorEpoch",
        "!sensitiveField&&currentLearningAllowed&&keyboardToolbarPrivacy.clipboardVisible()",
        "if(!clipboardHistoryAllowed())hideClipboardPanel()",
        "clipboard.appendSearchText(text);return",
        "clipboard.deleteSearchCodePoint();return",
        "clipboard.finishSearchEditing();return",
        "hideClipboardPanel();closeIdleRimeSession();insertKeyboardText(snapshot.text())",
        "clipboardHistoryRequest++;restoreClipboardSearchPadding()",
    )
    if any(token not in service_compact for token in service_tokens):
        violations.append(Violation(
            "KBD011_SERVICE_WIRING",
            "service must use generation-bound local I/O, SEC-005, search routing and ETM facade",
        ))
    if service.count("hideClipboardPanel();") < 8:
        violations.append(Violation(
            "KBD011_LIFECYCLE_CLEAR",
            "clipboard bodies and search must clear on all input lifecycles",
        ))

    required_tests = (
        (tests[SNAPSHOT_TEST], "unsupportedAndOversizedInputsNeverRetainPartialText"),
        (tests[HISTORY_TEST], "recordIsBoundedMostRecentFirstAndMovesDuplicates"),
        (tests[HISTORY_TEST], "searchAndCategoriesAreComputedWithoutStoredMetadata"),
        (tests[CODEC_TEST], "unknownMalformedDuplicateAndNonCanonicalPayloadsFailClosed"),
        (tests[CIPHER_TEST], "plaintextAndTamperedCiphertextFailClosed"),
        (tests[READER_TEST], "uriAndIntentItemsAreNotCoercedOrResolved"),
        (tests[STORE_TEST], "realKeystorePersistsMultipleEntriesWithoutPlaintextAtRest"),
        (tests[STORE_TEST], "corruptV1RecoversButUnknownFutureVersionIsNotOverwritten"),
        (tests[PANEL_TEST], "cardsRenderMultipleEntriesPasteExactTextAndInvalidateOldViews"),
        (tests[PANEL_TEST], "categoryAndQwertySearchFilterTheBoundedHistory"),
        (tests[PANEL_TEST], "clearRequiresTwoClicksAndLifecycleDropsEveryBodyAndQuery"),
        (tests[HOST_TEST], "selectedImeClipboardPastesCurrentTextAndHidesInSensitiveFieldWhenRequested"),
        (tests[HOST_TEST], "no-learning More menu exposed clipboard history"),
    )
    if any(token not in source for source, token in required_tests):
        violations.append(Violation(
            "KBD011_TEST_COVERAGE",
            "tests must cover bounds, crypto, canonical format, search, stale views and privacy",
        ))

    adr_compact = _compact(adr)
    adr_tokens = (
        "#ADR-0014:Clipboardhistoryencryptedformatandexplicit-captureboundary",
        "##StatusAccepted",
        "atmost100distinctentries",
        "atmost120,000Unicodecodepoints",
        "opentypeless_clipboard_history_v1",
        "OpenTypelessClipboard:v1",
        "Donotregister`OnPrimaryClipChangedListener`",
    )
    if any(token not in adr_compact for token in adr_tokens):
        violations.append(Violation(
            "KBD011_ACCEPTED_ADR",
            "ADR-0014 must remain Accepted and match the shipped format/privacy boundary",
        ))
    return tuple(violations)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--android-root", type=Path, default=Path(__file__).resolve().parents[1]
    )
    args = parser.parse_args()
    violations = inspect_android(args.android_root)
    if violations:
        for item in violations:
            print(f"{item.rule}: {item.detail}", file=sys.stderr)
        return 1
    print("KBD-011 encrypted clipboard-history source boundary passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
