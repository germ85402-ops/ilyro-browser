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
                    result.complete(emptyArray())
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
                    result.complete(emptyArray())
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
    val originHost = hostFromOrigin(origin) ?: return false
    val requestedHost = hostFromDomain(requestedDomain) ?: return false
    return originHost == requestedHost
}

private fun hostFromOrigin(origin: String): String? = runCatching {
    URI(origin.trim()).host
        ?.trimEnd('.')
        ?.lowercase(Locale.ROOT)
        ?.takeIf { it.isNotBlank() }
}.getOrNull()

private fun hostFromDomain(domain: String): String? {
    val trimmed = domain.trim()
    if (trimmed.isBlank()) return null
    val candidate = if (trimmed.contains("://")) trimmed else "https://$trimmed"
    return runCatching {
        URI(candidate).host
            ?.trimEnd('.')
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()
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
