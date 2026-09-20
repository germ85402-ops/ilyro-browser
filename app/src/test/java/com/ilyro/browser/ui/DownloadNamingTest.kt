package com.ilyro.browser.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadNamingTest {
    @Test
    fun apkMimeReplacesInventedBinExtension() {
        assertEquals(
            "download.apk",
            resolveDownloadFileName("download.bin", null, APK_MIME_TYPE)
        )
    }

    @Test
    fun originalApkNameSurvivesOpaqueOctetStreamRedirect() {
        assertEquals(
            "ILYRO.apk",
            resolveDownloadFileName(
                guessedName = "payload.bin",
                suggestedName = "ILYRO.apk",
                mimeType = "application/octet-stream"
            )
        )
    }

    @Test
    fun genericMimeBecomesApkMimeWhenNameIsApk() {
        assertEquals(
            APK_MIME_TYPE,
            resolveDownloadMimeType("ILYRO.apk", "application/octet-stream")
        )
    }

    @Test
    fun concreteServerArchiveNameIsNotOverriddenByWeakHint() {
        assertEquals(
            "bundle.zip",
            resolveDownloadFileName(
                guessedName = "bundle.zip",
                suggestedName = "ILYRO.apk",
                mimeType = "application/octet-stream"
            )
        )
    }

    @Test
    fun ordinaryBinaryWithoutApkEvidenceStaysBin() {
        assertEquals(
            "payload.bin",
            resolveDownloadFileName(
                guessedName = "payload.bin",
                suggestedName = null,
                mimeType = "application/octet-stream"
            )
        )
    }
    @Test
    fun rfc5987ContentDispositionRecoversApkName() {
        assertEquals(
            "ILYRO 0.24.7.apk",
            contentDispositionFileName("attachment; filename*=UTF-8''ILYRO%200.24.7.apk")
        )
    }

    @Test
    fun quotedContentDispositionRecoversApkName() {
        assertEquals(
            "browser-release.apk",
            contentDispositionFileName("attachment; filename=\"browser-release.apk\"")
        )
    }

}
