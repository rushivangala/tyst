package app.tyst.keyboard.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Supplies the AES key that protects learned data. */
interface KeyProvider {
    fun key(): SecretKey
    /** Destroys the key, which makes every file written with it unreadable. */
    fun destroy()
}

/**
 * An AES-256 key held in the hardware-backed Android Keystore. StrongBox is not used for this key:
 * it is far too slow for bulk encryption of the learned-data files.
 */
class KeystoreKeyProvider(private val alias: String) : KeyProvider {
    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    override fun key(): SecretKey {
        (keyStore().getKey(alias, null) as? SecretKey)?.let { return it }
        return generate()
    }

    private fun generate(): SecretKey {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(spec)
        return generator.generateKey()
    }

    override fun destroy() {
        runCatching { keyStore().deleteEntry(alias) }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}

/**
 * Small encrypted files: AES-GCM, a fresh 12-byte IV per write, written atomically.
 * File layout: "TYS1" magic, IV length, IV, ciphertext with tag.
 */
class SecureStore(private val dir: File, private val keys: KeyProvider) {
    private fun file(name: String) = File(dir, "$name.bin")

    @Synchronized
    fun write(name: String, text: String) {
        dir.mkdirs()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keys.key())
        val iv = cipher.iv
        val sealed = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        val tmp = File(dir, "$name.tmp")
        tmp.outputStream().use { out ->
            out.write(MAGIC)
            out.write(iv.size)
            out.write(iv)
            out.write(sealed)
        }
        if (!tmp.renameTo(file(name))) {
            tmp.copyTo(file(name), overwrite = true)
            tmp.delete()
        }
    }

    /** The stored text, or null if missing or unreadable (for example after the key was destroyed). */
    @Synchronized
    fun read(name: String): String? {
        val f = file(name)
        if (!f.exists()) return null
        return try {
            val bytes = f.readBytes()
            require(bytes.size > MAGIC.size + 1 && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "bad header" }
            val ivLen = bytes[MAGIC.size].toInt()
            val ivStart = MAGIC.size + 1
            val iv = bytes.copyOfRange(ivStart, ivStart + ivLen)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keys.key(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(bytes, ivStart + ivLen, bytes.size - ivStart - ivLen), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun sizeBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    /** Deletes every file and destroys the key. */
    @Synchronized
    fun deleteAll() {
        dir.listFiles()?.forEach { it.delete() }
        keys.destroy()
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        val MAGIC = "TYS1".toByteArray(Charsets.US_ASCII)
    }
}
