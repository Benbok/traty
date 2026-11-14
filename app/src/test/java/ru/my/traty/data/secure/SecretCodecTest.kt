package ru.my.traty.data.secure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class SecretCodecTest {
    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val key = newKey()
    private val codec = SecretCodec(AesGcmSecretCipher { key })

    @Test
    fun sealedSecretIsNotPlainTextAndOpensBack() {
        val stored = codec.seal("sk-1234567890")
        assertTrue(stored.startsWith(AesGcmSecretCipher.PREFIX))
        assertFalse(stored.contains("sk-1234567890"))
        assertEquals("sk-1234567890", codec.open(stored))
    }

    @Test
    fun sameSecretGivesDifferentCiphertext() {
        assertNotEquals(codec.seal("abc"), codec.seal("abc"))
    }

    @Test
    fun emptyStaysEmpty() {
        assertEquals("", codec.seal(""))
        assertEquals("", codec.open(""))
        assertEquals("", codec.open(null))
    }

    @Test
    fun legacyPlainTextIsReadAndMarkedForUpgrade() {
        assertEquals("sk-old", codec.open("sk-old"))
        assertTrue(codec.needsUpgrade("sk-old"))
        assertFalse(codec.needsUpgrade(codec.seal("sk-new")))
        assertFalse(codec.needsUpgrade(""))
        assertFalse(codec.needsUpgrade(null))
    }

    @Test
    fun foreignKeyOrDamagedValueOpensEmpty() {
        val stored = codec.seal("sk-1")
        val other = SecretCodec(AesGcmSecretCipher { newKey() })
        assertEquals("", other.open(stored))
        assertEquals("", codec.open(AesGcmSecretCipher.PREFIX + "bm90LXJlYWw="))
    }

    @Test
    fun unavailableKeystoreKeepsPlainTextInsteadOfLosingKey() {
        val broken = SecretCodec(AesGcmSecretCipher { throw IllegalStateException("no keystore") })
        assertEquals("sk-1", broken.seal("sk-1"))
        assertEquals("sk-1", broken.open("sk-1"))
    }
}
