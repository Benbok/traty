package ru.my.traty.data.secure

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Шифрует секреты (API-ключи ИИ) перед записью в настройки. */
interface SecretCipher {
    fun encrypt(plain: String): String
    fun decrypt(stored: String): String
}

/**
 * AES-256-GCM. Сохранённая строка: `v1:` + Base64(IV 12 байт + шифротекст с тегом).
 * Ключ берётся из [keyProvider]; в приложении это ключ Android Keystore, который
 * нельзя извлечь из телефона и который не попадает в резервные копии.
 */
class AesGcmSecretCipher(private val keyProvider: () -> SecretKey) : SecretCipher {
    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // IV генерирует сам шифр: Keystore требует случайный IV при шифровании
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
        val iv = cipher.iv
        check(iv.size == IV_BYTES) { "Неожиданная длина IV: ${iv.size}" }
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.getEncoder().encodeToString(iv + sealed)
    }

    override fun decrypt(stored: String): String {
        require(isEncrypted(stored)) { "Строка не зашифрована" }
        val bytes = Base64.getDecoder().decode(stored.removePrefix(PREFIX))
        require(bytes.size > IV_BYTES) { "Повреждённый секрет" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        return String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    }

    companion object {
        const val PREFIX = "v1:"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128

        fun isEncrypted(stored: String): Boolean = stored.startsWith(PREFIX)
    }
}

/**
 * Как секреты лежат в настройках. Пустая строка остаётся пустой.
 * Старые ключи, сохранённые открытым текстом, читаются как есть и шифруются при [needsUpgrade].
 * Если Keystore недоступен (тесты на JVM) — значение хранится как раньше, открытым текстом,
 * чтобы приложение не теряло ключ. Если ключ Keystore потерян (перенос на другой телефон) —
 * секрет читается пустым, и его нужно ввести заново.
 */
class SecretCodec(private val cipher: SecretCipher) {
    fun seal(plain: String): String {
        if (plain.isEmpty()) return ""
        return runCatching { cipher.encrypt(plain) }.getOrElse { plain }
    }

    fun open(stored: String?): String {
        if (stored.isNullOrEmpty()) return ""
        if (!AesGcmSecretCipher.isEncrypted(stored)) return stored
        return runCatching { cipher.decrypt(stored) }.getOrElse { "" }
    }

    /** Значение записано открытым текстом и его стоит перешифровать. */
    fun needsUpgrade(stored: String?): Boolean = !stored.isNullOrEmpty() && !AesGcmSecretCipher.isEncrypted(stored)
}
