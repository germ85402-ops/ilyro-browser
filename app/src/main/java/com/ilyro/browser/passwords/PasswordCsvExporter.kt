package com.ilyro.browser.passwords

import java.net.URI

internal object PasswordCsvExporter {
    fun encode(credentials: Collection<PasswordCredential>): String = buildString {
        append("name,url,username,password\n")
        credentials.forEach { credential ->
            appendCsvField(displayName(credential.origin))
            append(',')
            appendCsvField(credential.origin)
            append(',')
            appendCsvField(credential.username)
            append(',')
            appendCsvField(credential.password)
            append('\n')
        }
    }

    private fun displayName(origin: String): String = runCatching {
        URI(origin).host?.takeIf { it.isNotBlank() }
    }.getOrNull() ?: origin

    private fun StringBuilder.appendCsvField(value: String) {
        val requiresQuotes = value.any { char ->
            char == ',' || char == '"' || char == '\n' || char == '\r'
        }
        if (!requiresQuotes) {
            append(value)
            return
        }

        append('"')
        value.forEach { char ->
            if (char == '"') append("\"\"") else append(char)
        }
        append('"')
    }
}
