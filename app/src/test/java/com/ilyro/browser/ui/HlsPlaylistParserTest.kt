package com.ilyro.browser.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsPlaylistParserTest {

    @Test
    fun masterPlaylist_resolvesRelativeUrlsAndSortsBestQualityFirst() {
        val playlist = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=900000,RESOLUTION=854x480
            480/playlist.m3u8
            #EXT-X-STREAM-INF:AVERAGE-BANDWIDTH=3100000,BANDWIDTH=3500000,RESOLUTION=1920x1080
            1080/playlist.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=1700000,RESOLUTION=1280x720
            https://cdn.example.net/720.m3u8
        """.trimIndent()

        val variants = parseHlsMasterVariants(
            playlist,
            "https://media.example.com/video/master.m3u8"
        )

        assertEquals(3, variants.size)
        assertEquals(1920, variants[0].width)
        assertEquals(1080, variants[0].height)
        assertEquals(3_100_000L, variants[0].bandwidth)
        assertEquals(
            "https://media.example.com/video/1080/playlist.m3u8",
            variants[0].url
        )
        assertEquals("https://cdn.example.net/720.m3u8", variants[1].url)
    }

    @Test
    fun masterPlaylist_readsNamedQualityFrameRateAndCodecs() {
        val playlist = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=5400000,NAME="1080p",FRAME-RATE=59.94,CODECS="avc1.640028,mp4a.40.2"
            stream/1080/index.m3u8
        """.trimIndent()

        val variant = selectBestHlsVariant(
            playlist,
            "https://media.example.com/master.m3u8"
        )

        assertNotNull(variant)
        assertEquals(1080, variant!!.height)
        assertEquals("1080p", variant.name)
        assertEquals(59.94, variant.frameRate, 0.01)
        assertEquals("avc1.640028,mp4a.40.2", variant.codecs)
    }

    @Test
    fun masterPlaylist_marksSeparateAudioVariants() {
        val playlist = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=2200000,RESOLUTION=1280x720,AUDIO="audio-main"
            video/720.m3u8
        """.trimIndent()

        val variant = selectBestHlsVariant(
            playlist,
            "https://media.example.com/master.m3u8"
        )

        assertNotNull(variant)
        assertTrue(variant!!.hasSeparateAudio)
    }

    @Test
    fun mediaPlaylist_parsesInitSegmentByteRangesAndVodState() {
        val playlist = """
            #EXTM3U
            #EXT-X-VERSION:7
            #EXT-X-MAP:URI="init.mp4",BYTERANGE="720@0"
            #EXTINF:4.0,
            #EXT-X-BYTERANGE:1200@720
            stream.mp4
            #EXTINF:4.0,
            #EXT-X-BYTERANGE:1300
            stream.mp4
            #EXT-X-ENDLIST
        """.trimIndent()

        val parsed = parseHlsMediaPlaylist(
            playlist,
            "https://video.example.org/path/index.m3u8"
        )

        assertTrue(parsed.isVod)
        assertTrue(parsed.prefersMp4Container)
        assertFalse(parsed.hasUnsupportedEncryption)
        assertEquals("https://video.example.org/path/init.mp4", parsed.initSegment?.url)
        assertEquals(720L, parsed.initSegment?.byteRange?.length)
        assertEquals(0L, parsed.initSegment?.byteRange?.offset)
        assertEquals(2, parsed.segments.size)
        assertEquals(1200L, parsed.segments[0].byteRange?.length)
        assertEquals(720L, parsed.segments[0].byteRange?.offset)
        assertEquals(1300L, parsed.segments[1].byteRange?.length)
        assertNull(parsed.segments[1].byteRange?.offset)
    }

    @Test
    fun mediaPlaylist_detectsUnsupportedEncryption() {
        val playlist = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="key.bin"
            #EXTINF:6,
            segment001.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val parsed = parseHlsMediaPlaylist(
            playlist,
            "https://video.example.org/index.m3u8"
        )

        assertTrue(parsed.hasUnsupportedEncryption)
        assertTrue(parsed.isVod)
        assertFalse(parsed.prefersMp4Container)
    }

    @Test
    fun mediaPlaylist_keepsEncryptionFlagForEarlierSegmentsAfterKeyReset() {
        val playlist = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="key.bin"
            #EXTINF:4,
            encrypted.ts
            #EXT-X-KEY:METHOD=NONE
            #EXTINF:4,
            clear.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val parsed = parseHlsMediaPlaylist(
            playlist,
            "https://video.example.org/index.m3u8"
        )

        assertTrue(parsed.segments[0].encrypted)
        assertFalse(parsed.segments[1].encrypted)
        assertTrue(parsed.hasUnsupportedEncryption)
    }

    @Test(expected = IllegalArgumentException::class)
    fun mediaPlaylist_rejectsPlaylistWithoutSegments() {
        parseHlsMediaPlaylist(
            "#EXTM3U\n#EXT-X-ENDLIST",
            "https://video.example.org/index.m3u8"
        )
    }
}
