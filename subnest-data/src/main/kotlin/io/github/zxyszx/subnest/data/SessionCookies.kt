package io.github.zxyszx.subnest.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface CookieStorage {
    fun read(): String
    fun write(value: String)
}

/** Session cookies never enter backup, logs or plaintext preferences. Passwords are not stored. */
class EncryptedCookieStorage(context: Context) : CookieStorage {
    private val file = File(context.noBackupFilesDir, "subnest-session")
    private val alias = "subnest-session-v1"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    override fun read(): String {
        if (!file.exists()) return ""
        return try {
            val bytes = Base64.decode(file.readText(), Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
        } catch (_: Exception) {
            file.delete()
            ""
        }
    }

    override fun write(value: String) {
        if (value.isEmpty()) {
            file.delete()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(value.toByteArray())
        file.writeText(Base64.encodeToString(bytes, Base64.NO_WRAP))
    }
}

class SessionCookies(private val origin: HttpUrl, private val storage: CookieStorage) : CookieJar {
    private var cookies = storage.read().takeIf { it.substringBefore('\n') == origin.toString() }
        .orEmpty().lineSequence().drop(1).mapNotNull { Cookie.parse(origin, it) }
        .filter { it.domain == origin.host && it.expiresAt > System.currentTimeMillis() }.toList()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (url.scheme != origin.scheme || url.host != origin.host || url.port != origin.port) return
        val accepted = cookies.filter { it.domain == origin.host }
        this.cookies = (this.cookies.filterNot { old -> accepted.any { it.name == old.name && it.path == old.path } } + accepted)
            .filter { it.expiresAt > System.currentTimeMillis() }
        storage.write(origin.toString() + "\n" + this.cookies.joinToString("\n"))
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        if (url.scheme == origin.scheme && url.host == origin.host && url.port == origin.port) {
            cookies.filter { it.matches(url) && it.expiresAt > System.currentTimeMillis() }
        } else {
            emptyList()
        }

    fun csrf(): String? = loadForRequest(origin).firstOrNull { it.name == "renewlet_csrf" }?.value

    @Synchronized
    fun clear() {
        cookies = emptyList()
        storage.write("")
    }
}
