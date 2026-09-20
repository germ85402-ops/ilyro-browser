package com.ilyro.browser.passwords

import java.net.URI
import java.util.Locale

internal data class PasswordCsvImportPreview(
    val credentials: List<PasswordCredential>,
    val sourceRows: Int,
    val skippedRows: Int,
    val duplicateRows: Int,
    val error: String? = null
) {
    val canImport: Boolean
        get() = error == null && credentials.isNotEmpty()
}

/**
 * Parses password CSV exports without ever writing the plaintext input to disk.
 *
 * Supported header shapes include Chromium-family exports (Chrome / Edge / Brave) and Firefox.
 * Origins are reduced to scheme + exact host + explicit non-default port before they enter the
 * vault, which keeps Gecko login matching scoped to the real origin instead of an arbitrary URL
 * path from the source browser.
 */
internal object PasswordCsvImporter {
    fun preview(rawCsv: String): PasswordCsvImportPreview {
        val rows = parseCsv(rawCsv)
        if (rows.isEmpty()) {
            return PasswordCsvImportPreview(emptyList(), 0, 0, 0, "CSV-файл пуст.")
        }

        val headers = rows.first().mapIndexed { index, value ->
            val cleaned = if (index == 0) value.removePrefix("\uFEFF") else value
            normalizeHeader(cleaned)
        }
        val originIndex = findHeader(headers, "url", "origin", "hostname")
        val usernameIndex = findHeader(headers, "username", "user", "loginusername")
        val passwordIndex = findHeader(headers, "password")
        val formActionIndex = findHeader(headers, "formactionorigin", "formaction", "formurl")
        val httpRealmIndex = findHeader(headers, "httprealm", "realm")

        if (originIndex < 0 || passwordIndex < 0) {
            return PasswordCsvImportPreview(
                credentials = emptyList(),
                sourceRows = rows.drop(1).count { row -> row.any { it.isNotBlank() } },
                skippedRows = 0,
                duplicateRows = 0,
                error = "Не найдены обязательные столбцы URL и password."
            )
        }

        val unique = LinkedHashMap<String, PasswordCredential>()
        var sourceRows = 0
        var skippedRows = 0
        var duplicateRows = 0

        rows.drop(1).forEach { row ->
            if (row.all { it.isBlank() }) return@forEach
            sourceRows += 1

            val origin = canonicalOrigin(row.valueAt(originIndex))
            val password = row.valueAt(passwordIndex)
            if (origin == null || password.isEmpty()) {
                skippedRows += 1
                return@forEach
            }

            val credential = PasswordCredential(
                guid = "",
                origin = origin,
                formActionOrigin = row.valueAt(formActionIndex)
                    .takeIf { it.isNotBlank() }
                    ?.let(::canonicalOrigin),
                httpRealm = row.valueAt(httpRealmIndex).trim().takeIf { it.isNotEmpty() },
                username = row.valueAt(usernameIndex),
                password = password
            )
            val identity = loginIdentity(credential)
            if (unique.containsKey(identity)) duplicateRows += 1
            unique[identity] = credential
        }

        return PasswordCsvImportPreview(
            credentials = unique.values.toList(),
            sourceRows = sourceRows,
            skippedRows = skippedRows,
            duplicateRows = duplicateRows
        )
    }

    private fun normalizeHeader(value: String): String = value
        .trim()
        .lowercase(Locale.ROOT)
        .filter { it.isLetterOrDigit() }

    private fun findHeader(headers: List<String>, vararg names: String): Int =
        headers.indexOfFirst { header -> names.any { it == header } }

    private fun List<String>.valueAt(index: Int): String =
        if (index in indices) this[index] else ""

    internal fun canonicalOrigin(raw: String): String? {
        val candidate = raw.trim()
        if (candidate.isEmpty()) return null
        return runCatching {
            val uri = URI(candidate)
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
                ?.takeIf { it == "http" || it == "https" }
                ?: return null
            val host = uri.host
                ?.trimEnd('.')
                ?.lowercase(Locale.ROOT)
                ?.takeIf { it.isNotBlank() }
                ?: return null
            val port = uri.port.takeIf { explicitPort ->
                explicitPort >= 0 &&
                    !((scheme == "https" && explicitPort == 443) ||
                        (scheme == "http" && explicitPort == 80))
            } ?: -1
            URI(scheme, null, host, port, null, null, null).toASCIIString()
        }.getOrNull()
    }

    internal fun parseCsv(raw: String): List<List<String>> {
        if (raw.isEmpty()) return emptyList()

        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var index = 0

        fun finishField() {
            row += field.toString()
            field.setLength(0)
        }

        fun finishRow() {
            finishField()
            rows += row
            row = mutableListOf()
        }

        while (index < raw.length) {
            val char = raw[index]
            when {
                char == '"' && inQuotes && index + 1 < raw.length && raw[index + 1] == '"' -> {
                    field.append('"')
                    index += 1
                }
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> finishField()
                (char == '\n' || char == '\r') && !inQuotes -> {
                    finishRow()
                    if (char == '\r' && index + 1 < raw.length && raw[index + 1] == '\n') {
                        index += 1
                    }
                }
                else -> field.append(char)
            }
            index += 1
        }

        if (field.isNotEmpty() || row.isNotEmpty()) finishRow()
        return rows
    }
}
