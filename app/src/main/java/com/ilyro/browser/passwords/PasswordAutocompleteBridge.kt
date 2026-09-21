package com.ilyro.browser.passwords

import android.content.Context
import android.util.Log
import org.mozilla.geckoview.Autocomplete
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import java.net.URI
import java.util.Locale
import java.util.concurrent.Executors

internal object PasswordManagerService {
    @Volatile
    private var vault: PasswordVault? = null

    @Volatile
    private var delegate: PasswordAutocompleteStorageDelegate? = null

    @Volatile
    private var installedRuntime: GeckoRuntime? = null

    fun prepare(context: Context) {
        vault(context)
    }

    fun vault(context: Context): PasswordVault = vault ?: synchronized(this) {
        vault ?: PasswordVault(context.applicationContext).also { vault = it }
    }

    /**
     * Install into ILYRO's already-created Gecko runtime. The vault is prepared from MainActivity
     * first, so this never creates a second GeckoRuntime just to get a Context.
     */
    fun install(runtime: GeckoRuntime): Boolean {
        val preparedVault = vault ?: return false
        if (installedRuntime === runtime && delegate != null) return true

        val storageDelegate = delegate ?: synchronized(this) {
            delegate ?: PasswordAutocompleteStorageDelegate(preparedVault).also { delegate = it }
        }
        runtime.setAutocompleteStorageDelegate(storageDelegate)
        installedRuntime = runtime
        return true
    }

    fun isInstalled(): Boolean = installedRuntime != null
}

private class PasswordAutocompleteStorageDelegate(
    private val vault: PasswordVault
) : Autocomplete.StorageDelegate {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ilyro-password-vault").apply { isDaemon = true }
    }

    override fun onLoginFetch(domain: String): GeckoResult<Array<Autocomplete.LoginEntry>> {
        val result = GeckoResult<Array<Autocomplete.LoginEntry>>()
        executor.execute {
            runCatching {
                vault.snapshot()
                    .asSequence()
                    .filter { credential -> passwordDomainMatches(credential.origin, domain) }
                    .map(PasswordCredential::toAutocompleteLogin)
                    .toList()
                    .toTypedArray()
            }.onSuccess(result::complete)
                .onFailure { error ->
                    Log.w(TAG, "Unable to read saved passwords for $domain", error)
                    result.completeExceptionally(error)
                }
        }
        return result
    }

    override fun onLoginFetch(): GeckoResult<Array<Autocomplete.LoginEntry>> {
        val result = GeckoResult<Array<Autocomplete.LoginEntry>>()
        executor.execute {
            runCatching {
                vault.snapshot().map(PasswordCredential::toAutocompleteLogin).toTypedArray()
            }.onSuccess(result::complete)
                .onFailure { error ->
                    Log.w(TAG, "Unable to read saved passwords", error)
                    result.completeExceptionally(error)
                }
        }
        return result
    }

    override fun onLoginSave(login: Autocomplete.LoginEntry) {
        executor.execute {
            runCatching {
                vault.upsert(login.toPasswordCredential())
            }.onFailure { error ->
                Log.e(TAG, "Unable to save password", error)
            }
        }
    }

    override fun onLoginUsed(login: Autocomplete.LoginEntry, usedFields: Int) {
        executor.execute {
            runCatching {
                vault.markUsed(
                    guid = login.guid,
                    origin = login.origin,
                    username = login.username
                )
            }.onFailure { error ->
                Log.w(TAG, "Unable to update password usage metadata", error)
            }
        }
    }

    private companion object {
        const val TAG = "ILYRO.Passwords"
    }
}

internal fun passwordDomainMatches(origin: String, requestedDomain: String): Boolean {
    val originUri = parsePasswordOrigin(origin) ?: return false
    val requestedText = requestedDomain.trim()
    if (requestedText.isBlank()) return false
    val hasExplicitScheme = requestedText.contains("://")
    val requestedUri = parsePasswordOrigin(
        if (hasExplicitScheme) requestedText else "https://$requestedText"
    ) ?: return false

    if (originUri.host != requestedUri.host) return false
    if (hasExplicitScheme && originUri.scheme != requestedUri.scheme) return false

    // Gecko sometimes supplies only a hostname, so keep that legacy-compatible path. When the
    // API gives us a scheme or port, require the full origin instead of offering a credential to
    // an HTTP endpoint or a different service on the same host.
    if (hasExplicitScheme || requestedUri.port != -1) {
        return effectivePort(originUri) == effectivePort(requestedUri)
    }
    return true
}

private data class ParsedPasswordOrigin(
    val scheme: String,
    val host: String,
    val port: Int
)

private fun parsePasswordOrigin(raw: String): ParsedPasswordOrigin? = runCatching {
    val uri = URI(raw.trim())
    val scheme = uri.scheme?.lowercase(Locale.ROOT).orEmpty()
    val host = uri.host?.trimEnd('.')?.lowercase(Locale.ROOT).orEmpty()
    if (scheme.isBlank() || host.isBlank()) return@runCatching null
    ParsedPasswordOrigin(scheme, host, uri.port)
}.getOrNull()

private fun effectivePort(origin: ParsedPasswordOrigin): Int = when {
    origin.port != -1 -> origin.port
    origin.scheme == "http" -> 80
    origin.scheme == "https" -> 443
    else -> -1
}

private fun PasswordCredential.toAutocompleteLogin(): Autocomplete.LoginEntry =
    Autocomplete.LoginEntry.Builder()
        .guid(guid)
        .origin(origin)
        .formActionOrigin(formActionOrigin)
        .httpRealm(httpRealm)
        .username(username)
        .password(password)
        .build()

private fun Autocomplete.LoginEntry.toPasswordCredential(): PasswordCredential =
    PasswordCredential(
        guid = guid.orEmpty(),
        origin = origin,
        formActionOrigin = formActionOrigin,
        httpRealm = httpRealm,
        username = username,
        password = password
    )
