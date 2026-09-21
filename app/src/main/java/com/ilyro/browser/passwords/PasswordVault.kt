package com.ilyro.browser.passwords

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.UUID
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class PasswordCredential(
    val guid: String,
    val origin: String,
    val formActionOrigin: String?,
    val httpRealm: String?,
    val username: String,
    val password: String,
    val timesUsed: Int = 0,
    val lastUsedAt: Long = 0L
)

/**
 * A password vault read failed. The caller must not treat this as an empty vault: a failed
 * Keystore read or a damaged file must never silently replace saved credentials with a new store.
 */
internal class PasswordVaultReadException(
    val dataMayBeCorrupt: Boolean,
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)

private class PasswordVaultCorruptionException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Application-private password storage encrypted with an AES key that never leaves Android
 * Keystore. The encrypted vault file itself is also excluded from Android backup because ILYRO's
 * application has allowBackup=false.
 *
 * This is deliberately separate from browser settings / Drive sync. Password sync will only be
 * added later together with an explicit end-to-end-encryption design.
 */
internal class PasswordVault(context: Context) {
    private val appContext = context.applicationContext
    private val vaultFile = AtomicFile(
        File(appContext.filesDir, "passwords/ilyro-passwords.v1").also { file ->
            file.parentFile?.mkdirs()
        }
    )
    private val lock = Any()

    fun snapshot(): List<PasswordCredential> = synchronized(lock) {
        readCredentialsLocked()
    }

    fun upsert(entry: PasswordCredential): PasswordCredential = synchronized(lock) {
        require(entry.origin.isNotBlank()) { "Password origin must not be blank." }
        val existing = readCredentialsLocked().toMutableList()
        val index = existing.indexOfFirst { current ->
            current.guid == entry.guid || sameLoginIdentity(current, entry)
        }
        val normalized = entry.copy(
            guid = entry.guid.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            origin = entry.origin.trim()
        )
        if (index >= 0) {
            val previous = existing[index]
            existing[index] = normalized.copy(
                timesUsed = maxOf(previous.timesUsed, normalized.timesUsed),
                lastUsedAt = maxOf(previous.lastUsedAt, normalized.lastUsedAt)
            )
        } else {
            existing += normalized
        }
        writeCredentialsLocked(existing)
        normalized
    }

    fun replaceAll(entries: Collection<PasswordCredential>) = synchronized(lock) {
        val deduplicated = LinkedHashMap<String, PasswordCredential>()
        entries.forEach { entry ->
            if (entry.origin.isBlank()) return@forEach
            val normalized = entry.copy(
                guid = entry.guid.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                origin = entry.origin.trim()
            )
            val identity = loginIdentity(normalized)
            val previous = deduplicated[identity]
            deduplicated[identity] = if (previous == null) {
                normalized
            } else {
                normalized.copy(
                    timesUsed = maxOf(previous.timesUsed, normalized.timesUsed),
                    lastUsedAt = maxOf(previous.lastUsedAt, normalized.lastUsedAt)
                )
            }
        }
        writeCredentialsLocked(deduplicated.values.toList())
    }

    fun markUsed(guid: String?, origin: String, username: String) = synchronized(lock) {
        val existing = readCredentialsLocked().toMutableList()
        val index = existing.indexOfFirst { entry ->
            (!guid.isNullOrBlank() && entry.guid == guid) ||
                (entry.origin == origin && entry.username == username)
        }
        if (index < 0) return@synchronized
        val current = existing[index]
        existing[index] = current.copy(
            timesUsed = current.timesUsed + 1,
            lastUsedAt = System.currentTimeMillis()
        )
        writeCredentialsLocked(existing)
    }

    fun clear() = synchronized(lock) {
        vaultFile.delete()
    }

    private fun readCredentialsLocked(): List<PasswordCredential> {
        if (!vaultFile.baseFile.exists()) return emptyList()
        val encrypted = try {
            vaultFile.openRead().use { input -> input.readBytes() }
        } catch (error: IOException) {
            throw PasswordVaultReadException(
                dataMayBeCorrupt = false,
                message = "Saved password storage could not be read. Existing data was preserved.",
                cause = error
            )
        } catch (error: Exception) {
            throw PasswordVaultReadException(
                dataMayBeCorrupt = false,
                message = "Saved password storage is temporarily unavailable. Existing data was preserved.",
                cause = error
            )
        }
        if (encrypted.isEmpty()) {
            val backup = preserveUnreadableVaultLocked()
            throw PasswordVaultReadException(
                dataMayBeCorrupt = true,
                message = unreadableMessage(backup),
                cause = IllegalStateException("Password vault is empty")
            )
        }

        return try {
            val plain = decrypt(encrypted)
            PasswordVaultCodec.decode(String(plain, StandardCharsets.UTF_8))
        } catch (error: PasswordVaultCorruptionException) {
            val backup = preserveUnreadableVaultLocked()
            Log.e(TAG, "Encrypted password vault is unreadable; original data was preserved.", error)
            throw PasswordVaultReadException(
                dataMayBeCorrupt = true,
                message = unreadableMessage(backup),
                cause = error
            )
        } catch (error: JSONException) {
            val backup = preserveUnreadableVaultLocked()
            Log.e(TAG, "Password vault JSON is unreadable; original data was preserved.", error)
            throw PasswordVaultReadException(
                dataMayBeCorrupt = true,
                message = unreadableMessage(backup),
                cause = error
            )
        } catch (error: IllegalArgumentException) {
            val backup = preserveUnreadableVaultLocked()
            Log.e(TAG, "Password vault format is unreadable; original data was preserved.", error)
            throw PasswordVaultReadException(
                dataMayBeCorrupt = true,
                message = unreadableMessage(backup),
                cause = error
            )
        } catch (error: Exception) {
            // Keystore and transient I/O/security failures must not be classified as corruption.
            // Retain the original file and let the caller offer a retry instead of resetting it.
            Log.e(TAG, "Encrypted password vault could not be opened; data was preserved.", error)
            throw PasswordVaultReadException(
                dataMayBeCorrupt = false,
                message = "Saved password storage is temporarily unavailable. Existing data was preserved.",
                cause = error
            )
        }
    }

    private fun preserveUnreadableVaultLocked(): File? {
        val source = vaultFile.baseFile
        if (!source.exists()) return null

        val parent = source.parentFile ?: return null
        val existing = parent.listFiles()
            .orEmpty()
            .firstOrNull { file ->
                file.name.startsWith("${source.name}.corrupt-") &&
                    file.length() == source.length()
            }
        if (existing != null) return existing

        val quarantine = File(
            parent,
            "${source.name}.corrupt-${System.currentTimeMillis()}"
        )
        return runCatching {
            source.copyTo(quarantine, overwrite = false)
            quarantine
        }.getOrNull().also { backup ->
            if (backup == null) {
                Log.w(TAG, "Could not preserve a copy of the unreadable password vault.")
            }
        }
    }

    private fun unreadableMessage(backup: File?): String = if (backup != null) {
        "Saved passwords could not be opened. The original encrypted vault was preserved."
    } else {
        "Saved passwords could not be opened. The original encrypted vault was preserved, but a recovery copy could not be created."
    }

    private fun writeCredentialsLocked(entries: List<PasswordCredential>) {
        val plain = PasswordVaultCodec.encode(entries).toByteArray(StandardCharsets.UTF_8)
        val encrypted = encrypt(plain)
        val output = vaultFile.startWrite()
        try {
            output.write(encrypted)
            output.flush()
            vaultFile.finishWrite(output)
        } catch (error: Throwable) {
            vaultFile.failWrite(output)
            throw error
        }
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        require(iv.size in 1..255) { "Unexpected AES-GCM IV length." }
        val ciphertext = cipher.doFinal(plain)
        return ByteArray(MAGIC.size + 1 + iv.size + ciphertext.size).also { output ->
            MAGIC.copyInto(output, destinationOffset = 0)
            output[MAGIC.size] = iv.size.toByte()
            iv.copyInto(output, destinationOffset = MAGIC.size + 1)
            ciphertext.copyInto(output, destinationOffset = MAGIC.size + 1 + iv.size)
        }
    }

    private fun decrypt(encrypted: ByteArray): ByteArray {
        try {
            require(encrypted.size > MAGIC.size + 1) { "Encrypted password vault is truncated." }
            require(MAGIC.indices.all { encrypted[it] == MAGIC[it] }) {
                "Unsupported password vault format."
            }
            val ivLength = encrypted[MAGIC.size].toInt() and 0xff
            val ciphertextOffset = MAGIC.size + 1 + ivLength
            require(ivLength > 0 && ciphertextOffset < encrypted.size) {
                "Encrypted password vault has an invalid IV."
            }
            val iv = encrypted.copyOfRange(MAGIC.size + 1, ciphertextOffset)
            val ciphertext = encrypted.copyOfRange(ciphertextOffset, encrypted.size)
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            return cipher.doFinal(ciphertext)
        } catch (error: AEADBadTagException) {
            throw PasswordVaultCorruptionException("Encrypted password vault authentication failed.", error)
        } catch (error: javax.crypto.BadPaddingException) {
            throw PasswordVaultCorruptionException("Encrypted password vault authentication failed.", error)
        } catch (error: IllegalArgumentException) {
            throw PasswordVaultCorruptionException(error.message ?: "Invalid password vault format.", error)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val TAG = "ILYRO.Passwords"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "ilyro.passwords.aes.v1"
        const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        val MAGIC = byteArrayOf('I'.code.toByte(), 'L'.code.toByte(), 'P'.code.toByte(), '1'.code.toByte())
    }
}

internal object PasswordVaultCodec {
    private const val FORMAT_VERSION = 1

    fun encode(entries: Collection<PasswordCredential>): String {
        val credentials = JSONArray()
        entries.forEach { entry ->
            credentials.put(
                JSONObject()
                    .put("guid", entry.guid)
                    .put("origin", entry.origin)
                    .put("formActionOrigin", entry.formActionOrigin ?: JSONObject.NULL)
                    .put("httpRealm", entry.httpRealm ?: JSONObject.NULL)
                    .put("username", entry.username)
                    .put("password", entry.password)
                    .put("timesUsed", entry.timesUsed)
                    .put("lastUsedAt", entry.lastUsedAt)
            )
        }
        return JSONObject()
            .put("version", FORMAT_VERSION)
            .put("credentials", credentials)
            .toString()
    }

    fun decode(raw: String): List<PasswordCredential> {
        val root = JSONObject(raw)
        require(root.optInt("version", -1) == FORMAT_VERSION) {
            "Unsupported password vault version."
        }
        val credentials = root.optJSONArray("credentials") ?: JSONArray()
        return buildList {
            for (index in 0 until credentials.length()) {
                val item = credentials.optJSONObject(index) ?: continue
                val origin = item.optString("origin").trim()
                if (origin.isBlank()) continue
                add(
                    PasswordCredential(
                        guid = item.optString("guid").takeIf { it.isNotBlank() }
                            ?: UUID.randomUUID().toString(),
                        origin = origin,
                        formActionOrigin = item.nullableString("formActionOrigin"),
                        httpRealm = item.nullableString("httpRealm"),
                        username = item.optString("username"),
                        password = item.optString("password"),
                        timesUsed = item.optInt("timesUsed", 0).coerceAtLeast(0),
                        lastUsedAt = item.optLong("lastUsedAt", 0L).coerceAtLeast(0L)
                    )
                )
            }
        }
    }

    private fun JSONObject.nullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }
}

internal fun sameLoginIdentity(first: PasswordCredential, second: PasswordCredential): Boolean =
    loginIdentity(first) == loginIdentity(second)

internal fun loginIdentity(entry: PasswordCredential): String = buildString {
    append(entry.origin.trim().lowercase())
    append('\u0000')
    append(entry.formActionOrigin.orEmpty().trim().lowercase())
    append('\u0000')
    append(entry.httpRealm.orEmpty())
    append('\u0000')
    append(entry.username)
}
