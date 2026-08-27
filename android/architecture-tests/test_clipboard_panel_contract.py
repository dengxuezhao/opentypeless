from __future__ import annotations

from pathlib import Path
import shutil
import tempfile
import unittest

from clipboard_panel_contract import (
    ADR,
    CIPHER,
    CIPHER_TEST,
    CLIPBOARD_ROOT,
    CODEC_TEST,
    EXPECTED_FILES,
    HISTORY_TEST,
    HOST_TEST,
    PANEL_TEST,
    READER_TEST,
    SERVICE,
    SNAPSHOT_TEST,
    STORE_TEST,
    inspect_android,
)


class ClipboardPanelContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name) / "android"
        self.root.mkdir(parents=True)
        android = Path(__file__).resolve().parents[1]
        for name in EXPECTED_FILES:
            self.copy(android, CLIPBOARD_ROOT / name)
        for relative in (
            CIPHER,
            SERVICE,
            SNAPSHOT_TEST,
            HISTORY_TEST,
            CODEC_TEST,
            CIPHER_TEST,
            READER_TEST,
            STORE_TEST,
            PANEL_TEST,
            HOST_TEST,
        ):
            self.copy(android, relative)
        repository = android.parent
        adr_target = (self.root / ADR).resolve()
        adr_target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(repository / "docs/adr/0014-clipboard-history-encrypted-format.md", adr_target)

    def tearDown(self) -> None:
        self.temp.cleanup()

    def copy(self, android: Path, relative: Path) -> None:
        target = self.root / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(android / relative, target)

    def rules(self) -> set[str]:
        return {item.rule for item in inspect_android(self.root)}

    def mutate(self, relative: Path, old: str, new: str) -> None:
        path = (self.root / relative).resolve()
        source = path.read_text(encoding="utf-8")
        self.assertIn(old, source)
        path.write_text(source.replace(old, new, 1), encoding="utf-8")

    def test_current_contract_passes(self) -> None:
        self.assertEqual((), inspect_android(self.root))

    def test_rejects_background_clipboard_listener(self) -> None:
        reader = CLIPBOARD_ROOT / "SystemClipboardReader.java"
        path = self.root / reader
        path.write_text(
            path.read_text(encoding="utf-8")
            + "\nmanager.addPrimaryClipChangedListener(null);\n",
            encoding="utf-8",
        )
        self.assertIn("KBD011_EXPLICIT_READER", self.rules())

    def test_rejects_uri_coercion(self) -> None:
        reader = CLIPBOARD_ROOT / "SystemClipboardReader.java"
        self.mutate(reader, "clip.getItemAt(0).getText()", "clip.getItemAt(0).coerceToText(null)")
        self.assertIn("KBD011_EXPLICIT_READER", self.rules())

    def test_rejects_unbounded_history(self) -> None:
        history = CLIPBOARD_ROOT / "ClipboardHistory.java"
        self.mutate(history, "MAX_ENTRIES = 100", "MAX_ENTRIES = 1000")
        self.assertIn("KBD011_HISTORY_BOUNDARY", self.rules())

    def test_rejects_noncanonical_codec(self) -> None:
        codec = CLIPBOARD_ROOT / "ClipboardHistoryCodec.java"
        self.mutate(
            codec,
            "CodingErrorAction.REPORT",
            "CodingErrorAction.REPLACE",
        )
        self.assertIn("KBD011_CODEC_BOUNDARY", self.rules())

    def test_rejects_dictation_key_or_plaintext_fallback(self) -> None:
        self.mutate(
            CIPHER,
            'KEY_ALIAS = "opentypeless_clipboard_history_v1"',
            'KEY_ALIAS = "opentypeless_history_text_v1"',
        )
        self.assertIn("KBD011_CIPHER_DOMAIN", self.rules())

    def test_rejects_editor_writer_in_panel(self) -> None:
        panel = CLIPBOARD_ROOT / "KeyboardClipboardPanel.java"
        path = self.root / panel
        path.write_text(
            path.read_text(encoding="utf-8") + "\neditor.commitText(text, 1);\n",
            encoding="utf-8",
        )
        self.assertIn("KBD011_PANEL_CAPABILITY", self.rules())

    def test_rejects_storage_capability_in_panel(self) -> None:
        panel = CLIPBOARD_ROOT / "KeyboardClipboardPanel.java"
        path = self.root / panel
        path.write_text(
            path.read_text(encoding="utf-8") + "\nSharedPreferences history;\n",
            encoding="utf-8",
        )
        self.assertIn("KBD011_PANEL_CAPABILITY", self.rules())

    def test_rejects_missing_sensitive_projection(self) -> None:
        self.mutate(
            SERVICE,
            "if (!clipboardHistoryAllowed()) hideClipboardPanel();",
            "if (false) hideClipboardPanel();",
        )
        self.assertIn("KBD011_SERVICE_WIRING", self.rules())

    def test_rejects_no_learning_history_expansion(self) -> None:
        self.mutate(
            SERVICE,
            "&& currentLearningAllowed\n                && keyboardToolbarPrivacy.clipboardVisible();",
            "&& keyboardToolbarPrivacy.clipboardVisible();",
        )
        self.assertIn("KBD011_SERVICE_WIRING", self.rules())

    def test_rejects_search_that_reaches_rime_or_editor(self) -> None:
        self.mutate(
            SERVICE,
            "clipboard.appendSearchText(text);\n            return;",
            "clipboard.appendSearchText(text);",
        )
        self.assertIn("KBD011_SERVICE_WIRING", self.rules())

    def test_rejects_direct_paste_outside_typing_facade(self) -> None:
        self.mutate(
            SERVICE,
            "insertKeyboardText(snapshot.text());",
            "routeTypingText(snapshot.text());",
        )
        self.assertIn("KBD011_SERVICE_WIRING", self.rules())

    def test_rejects_proposed_or_drifted_persistence_decision(self) -> None:
        self.mutate(ADR, "## Status\n\nAccepted", "## Status\n\nProposed")
        self.assertIn("KBD011_ACCEPTED_ADR", self.rules())


if __name__ == "__main__":
    unittest.main()
