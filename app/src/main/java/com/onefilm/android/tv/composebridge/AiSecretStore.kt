package com.onefilm.android.tv.composebridge

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.RequiresApi
import com.github.catvod.utils.Prefers
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AI 字幕翻译密钥的安全存储（移植自上游 `SecretStore`）。
 *
 * <p>API Key / MTranServer Token 用 AndroidKeyStore 的 AES-GCM 加密后，以 base64 存进 Prefers；
 * 明文只在内存里出现。**不再明文落盘**。
 *
 * <p>API 21/22 没有 AndroidKeyStore 的对称密钥支持，退化为明文存储（LITE 的 minSdk 是 21）。
 * 加密分支用 `@RequiresApi(M)` + 运行时版本判断隔离，避免 LITE lint 报 NewApi。
 */
object AiSecretStore {

    private const val STORE = "AndroidKeyStore"
    private const val ALIAS = "onefilm_ai_subtitle_api_key"
    private const val PREF_API_KEY = "ai_subtitle_api_key_cipher"
    private const val PREF_MTRAN_TOKEN = "ai_subtitle_mtran_token_cipher"
    private const val PLAIN_SUFFIX = "_plain"

    @Synchronized
    fun putApiKey(value: String) = put(PREF_API_KEY, value)

    @Synchronized
    fun getApiKey(): String = get(PREF_API_KEY)

    @Synchronized
    fun putMTranToken(value: String) = put(PREF_MTRAN_TOKEN, value)

    @Synchronized
    fun getMTranToken(): String = get(PREF_MTRAN_TOKEN)

    private fun put(pref: String, value: String) {
        if (value.isBlank()) {
            Prefers.remove(pref)
            Prefers.remove(pref + PLAIN_SUFFIX)
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Prefers.put(pref + PLAIN_SUFFIX, value.trim())
            return
        }
        putEncrypted(pref, value.trim())
    }

    private fun get(pref: String): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return Prefers.getString(pref + PLAIN_SUFFIX, "")
        }
        return getEncrypted(pref)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun putEncrypted(pref: String, value: String) {
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
            val iv = cipher.iv
            val payload = ByteArray(1 + iv.size + encrypted.size)
            payload[0] = iv.size.toByte()
            System.arraycopy(iv, 0, payload, 1, iv.size)
            System.arraycopy(encrypted, 0, payload, 1 + iv.size, encrypted.size)
            Prefers.put(pref, Base64.encodeToString(payload, Base64.NO_WRAP))
        } catch (t: Throwable) {
            Prefers.remove(pref)
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun getEncrypted(pref: String): String {
        val encoded = Prefers.getString(pref, "")
        if (encoded.isEmpty()) return ""
        return try {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            val ivLength = payload[0].toInt() and 0xff
            if (ivLength < 12 || payload.size <= ivLength + 1) return ""
            val iv = payload.copyOfRange(1, 1 + ivLength)
            val encrypted = payload.copyOfRange(1 + ivLength, payload.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        } catch (t: Throwable) {
            ""
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance(STORE)
        store.load(null)
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
