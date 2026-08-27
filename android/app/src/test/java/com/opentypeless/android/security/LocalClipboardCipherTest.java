package com.opentypeless.android.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import org.junit.Test;

public final class LocalClipboardCipherTest {
    private static LocalClipboardCipher cipher() {
        byte[] key = "abcdef0123456789abcdef0123456789".getBytes(StandardCharsets.UTF_8);
        return new LocalClipboardCipher(new SecretKeySpec(key, "AES"));
    }

    @Test
    public void clipboardDomainEncryptsRandomlyAndRoundTripsUnicode() {
        LocalClipboardCipher cipher = cipher();
        String body = "clipboard 中文🙂\nsecond line";

        String first = cipher.encrypt(body);
        String second = cipher.encrypt(body);

        assertTrue(cipher.isEncrypted(first));
        assertNotEquals(first, second);
        assertFalse(first.contains(body));
        assertEquals(body, cipher.decrypt(first));
    }

    @Test
    public void plaintextAndTamperedCiphertextFailClosed() {
        LocalClipboardCipher cipher = cipher();
        String encrypted = cipher.encrypt("private clipboard");
        char replacement = encrypted.endsWith("A") ? 'B' : 'A';
        String tampered = encrypted.substring(0, encrypted.length() - 1) + replacement;

        assertThrows(IllegalArgumentException.class, () -> cipher.decrypt("legacy plaintext"));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(tampered));
        assertThrows(IllegalArgumentException.class, () -> cipher.encrypt(null));
    }
}
