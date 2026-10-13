package app.teakadai.reader

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The shop's server address, shop name and owner code, encrypted with a key that lives in the phone's hardware-backed
 * Android Keystore and never leaves it. If the key cannot be used, the values are treated as missing; they are
 * never written in plain text.
 */
object Vault {
    private const val ALIAS = "tk_reader_vault_v1"
    private const val PREFS = "tk_vault"

    data class Settings(val serverUrl: String, val shopSlug: String, val ownerCode: String)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun seal(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP)
    }

    private fun open(sealed: String): String? = try {
        val (iv, ct) = sealed.split(":").let { Base64.decode(it[0], Base64.NO_WRAP) to Base64.decode(it[1], Base64.NO_WRAP) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(c.doFinal(ct), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }

    fun save(ctx: Context, s: Settings): Boolean = try {
        val blob = listOf(s.serverUrl, s.shopSlug, s.ownerCode).joinToString("\n")
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("s", seal(blob)).apply()
        true
    } catch (e: Exception) {
        false
    }

    fun load(ctx: Context): Settings? {
        val sealed = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("s", null) ?: return null
        val parts = open(sealed)?.split("\n") ?: return null
        return if (parts.size == 3) Settings(parts[0], parts[1], parts[2]) else null
    }

    /** The owner's "yes, read payment notifications" answer, with the time they gave it. */
    fun saveConsent(ctx: Context, granted: Boolean) {
        val e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (granted) e.putLong("consent_at", System.currentTimeMillis()) else e.remove("consent_at")
        e.apply()
    }

    fun hasConsent(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("consent_at", 0L) > 0L

    /** Forget this shop: deletes the settings, the consent and the key. */
    fun wipe(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        try {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS)
        } catch (_: Exception) {
        }
    }
}
