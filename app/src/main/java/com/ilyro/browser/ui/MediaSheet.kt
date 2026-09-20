package com.ilyro.browser.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaSheet(
    media: List<DetectedMedia>,
    resolvingQualities: Boolean,
    onDismiss: () -> Unit,
    onDownload: (DetectedMedia) -> Unit,
    onOpenExternal: (DetectedMedia) -> Unit
) {
    val metrics = rememberIlyroLayoutMetrics()
    val dense = LocalIlyroUiDensity.current == UiDensity.COMPACT
    val qualityKeys = remember(media) {
        media.map(::mediaQualityBucket)
            .distinct()
            .sortedWith(compareByDescending<String>(::qualitySortValue).thenBy { it })
    }
    val bestHeight = remember(media) { media.maxOfOrNull { it.height } ?: 0 }
    val bestBitrate = remember(media) { media.maxOfOrNull { it.bitrate } ?: 0L }
    var selectedQuality by remember(qualityKeys) { mutableStateOf("all") }
    val visibleMedia = remember(media, selectedQuality) {
        if (selectedQuality == "all") media
        else media.filter { mediaQualityBucket(it) == selectedQuality }
    }
    val bestLabel = when {
        bestHeight > 0 -> qualityDisplayName("${bestHeight}p")
        bestBitrate > 0L -> formatBitrate(bestBitrate)
        else -> null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(
            skipPartiallyExpanded = true
        ),
        sheetGesturesEnabled = true,
        dragHandle = {
            BottomSheetDefaults.DragHandle()
        },
        shape = RoundedCornerShape(
            topStart = IlyroVisualTokens.LargeRadius,
            topEnd = IlyroVisualTokens.LargeRadius
        )
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = if (dense) 680.dp else 760.dp)
                    .padding(horizontal = metrics.horizontalPadding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (dense) 8.dp else if (metrics.isCompact) 12.dp else 18.dp)
                ) {
                    Text(
                        text = tr("Media found", "Найденные видео"),
                        style = if (dense) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when {
                            resolvingQualities -> tr(
                                "${media.size} sources · checking stream qualities",
                                "Источников: ${media.size} · определяю качество потоков"
                            )
                            bestLabel != null -> tr(
                                "${media.size} sources · up to $bestLabel",
                                "Источников: ${media.size} · до $bestLabel"
                            )
                            else -> tr(
                                "${media.size} source${if (media.size == 1) "" else "s"} on this page",
                                "Источников на странице: ${media.size}"
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (resolvingQualities) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    )
                    Text(
                        text = tr(
                            "Reading HLS manifests and available variants…",
                            "Читаю HLS-потоки и доступные варианты качества…"
                        ),
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(metrics.sectionGap))

                if (
                    qualityKeys.size > 1 ||
                    qualityKeys.firstOrNull()?.let { it != "video" && it != "auto" } == true
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(metrics.itemGap)
                    ) {
                        QualityTab(
                            label = tr("All", "Все"),
                            selected = selectedQuality == "all",
                            onClick = { selectedQuality = "all" }
                        )
                        qualityKeys.forEach { key ->
                            QualityTab(
                                label = qualityDisplayName(key),
                                selected = selectedQuality == key,
                                onClick = { selectedQuality = key }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(metrics.sectionGap))
                }

                if (media.isEmpty() && !resolvingQualities) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 28.dp),
                        shape = RoundedCornerShape(IlyroVisualTokens.LargeRadius),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(
                                alpha = IlyroVisualTokens.SubtleBorderAlpha
                            )
                        ),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(
                                if (metrics.isNarrowPhone) 18.dp else 22.dp
                            )
                        ) {
                            Text(
                                text = tr(
                                    "No media detected yet",
                                    "Видео пока не найдено"
                                ),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = tr(
                                    "Start playback on the page. ILYRO also watches dynamically created players and stream manifests.",
                                    "Запустите видео на странице. ILYRO также отслеживает динамические плееры и потоковые манифесты."
                                ),
                                modifier = Modifier.padding(top = 4.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (visibleMedia.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.heightIn(
                            max = if (metrics.isCompact) 520.dp else 590.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(metrics.itemGap)
                    ) {
                        items(
                            visibleMedia,
                            key = { "${it.kind}:${it.qualityLabel}:${it.url}" }
                        ) { item ->
                            MediaCard(
                                item = item,
                                compact = dense || metrics.isCompact,
                                narrow = metrics.isNarrowPhone,
                                isBestQuality = item.height > 0 && item.height == bestHeight,
                                onDownload = { onDownload(item) },
                                onOpenExternal = {
                                    onDismiss()
                                    onOpenExternal(item)
                                }
                            )
                        }
                    }
                }

                if (media.any { it.kind == DetectedMediaKind.DASH || it.hlsHasSeparateAudio }) {
                    Text(
                        text = tr(
                            "Some adaptive streams keep video and audio separately. ILYRO shows them, but only enables Download when it can create a valid local file.",
                            "Некоторые адаптивные потоки хранят видео и звук отдельно. ILYRO показывает их, но включает «Скачать» только когда может создать корректный локальный файл."
                        ),
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun QualityTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(IlyroVisualTokens.PillRadius),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
        },
        border = if (!selected) {
            BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = IlyroVisualTokens.SubtleBorderAlpha)
            )
        } else null,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(
                horizontal = if (LocalIlyroUiDensity.current == UiDensity.COMPACT) 10.dp else 14.dp,
                vertical = if (LocalIlyroUiDensity.current == UiDensity.COMPACT) 6.dp else 9.dp
            ),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun MediaCard(
    item: DetectedMedia,
    compact: Boolean,
    narrow: Boolean,
    isBestQuality: Boolean,
    onDownload: () -> Unit,
    onOpenExternal: () -> Unit
) {
    val uri = runCatching { Uri.parse(item.url) }.getOrNull()
    val host = uri?.host.orEmpty().removePrefix("www.")
    val dimensions = if (item.width > 0 && item.height > 0) {
        "${item.width}×${item.height}"
    } else null
    val bitrate = item.bitrate.takeIf { it > 0L }?.let(::formatBitrate)
    val frameRate = item.frameRate.takeIf { it >= 1.0 }?.let {
        val rounded = kotlin.math.round(it).toInt()
        "${rounded} fps"
    }
    val codec = compactCodecName(item.codecs)
    val tier = qualityTierLabel(item.height)
    val title = item.title
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.take(140)
        ?.takeIf { it.isNotBlank() && !it.equals("undefined", true) }
        ?: tr("Video stream", "Видеопоток")

    val technical = listOfNotNull(
        mediaKindLabel(item.kind),
        tier,
        dimensions,
        bitrate,
        frameRate,
        codec
    ).distinct().joinToString(" · ")

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (compact) 16.dp else IlyroVisualTokens.CardRadius),
        color = MaterialTheme.colorScheme.surface.copy(
            alpha = if (compact) 0.86f else IlyroVisualTokens.StrongSurfaceAlpha
        ),
        border = BorderStroke(
            1.dp,
            if (isBestQuality) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.26f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = IlyroVisualTokens.SubtleBorderAlpha)
            }
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(if (compact) 10.dp else 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    shape = RoundedCornerShape(IlyroVisualTokens.SmallRadius),
                    color = if (isBestQuality) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
                    },
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Text(
                        text = item.qualityLabel,
                        modifier = Modifier.padding(
                            horizontal = if (compact) 8.dp else 11.dp,
                            vertical = if (compact) 5.dp else 7.dp
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isBestQuality) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = if (compact) 8.dp else 10.dp)
                ) {
                    Text(
                        text = title,
                        maxLines = if (narrow) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = technical,
                        modifier = Modifier.padding(top = 2.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (host.isNotBlank()) {
                        Text(
                            text = tr("Source: $host", "Источник: $host"),
                            modifier = Modifier.padding(top = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (isBestQuality && !narrow) {
                    Surface(
                        shape = RoundedCornerShape(IlyroVisualTokens.PillRadius),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = tr("Best", "Лучшее"),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (compact) 8.dp else 11.dp),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
            ) {
                if (item.canDownload) {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(IlyroVisualTokens.ControlRadius)
                    ) {
                        Text(tr("Download", "Скачать"))
                    }
                }
                OutlinedButton(
                    onClick = onOpenExternal,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(IlyroVisualTokens.ControlRadius)
                ) {
                    Text(tr("Open in player", "В плеер"))
                }
            }

            if (item.hlsHasSeparateAudio) {
                Text(
                    text = tr(
                        "Audio is stored separately for this quality",
                        "Для этого качества звук хранится отдельной дорожкой"
                    ),
                    modifier = Modifier.padding(top = 7.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (item.height <= 0 && item.kind == DetectedMediaKind.HLS) {
                Text(
                    text = tr(
                        "The server does not advertise a fixed resolution; the player chooses it automatically.",
                        "Сервер не сообщает фиксированное разрешение — плеер выбирает качество автоматически."
                    ),
                    modifier = Modifier.padding(top = 7.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun mediaQualityBucket(item: DetectedMedia): String = when {
    item.height > 0 -> "${item.height}p"
    item.kind == DetectedMediaKind.AUDIO -> "audio"
    item.kind == DetectedMediaKind.HLS || item.kind == DetectedMediaKind.DASH -> "auto"
    else -> "video"
}

private fun qualitySortValue(key: String): Int = when {
    key.endsWith('p') -> key.dropLast(1).toIntOrNull() ?: 0
    key == "auto" -> -1
    key == "video" -> -2
    key == "audio" -> -3
    else -> -4
}

@Composable
private fun qualityDisplayName(key: String): String = when (key) {
    "all" -> tr("All", "Все")
    "auto" -> tr("Auto", "Авто")
    "video" -> tr("Video", "Видео")
    "audio" -> tr("Audio", "Аудио")
    else -> key
}

@Composable
private fun qualityTierLabel(height: Int): String? = when {
    height >= 4320 -> "8K UHD"
    height >= 2160 -> "4K UHD"
    height >= 1440 -> "QHD"
    height >= 1080 -> "Full HD"
    height >= 720 -> "HD"
    height > 0 -> "SD"
    else -> null
}

private fun compactCodecName(codecs: String?): String? {
    val clean = codecs?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val videoCodec = clean.split(',').firstNotNullOfOrNull { token ->
        val value = token.trim().lowercase()
        when {
            value.startsWith("avc1") || value.startsWith("avc3") -> "H.264"
            value.startsWith("hvc1") || value.startsWith("hev1") -> "HEVC"
            value.startsWith("av01") -> "AV1"
            value.startsWith("vp09") || value.startsWith("vp9") -> "VP9"
            value.startsWith("vp08") || value.startsWith("vp8") -> "VP8"
            else -> null
        }
    }
    return videoCodec ?: clean.take(28)
}

private fun formatBitrate(bitsPerSecond: Long): String {
    return if (bitsPerSecond >= 1_000_000L) {
        String.format(java.util.Locale.US, "%.1f Mbps", bitsPerSecond / 1_000_000.0)
    } else {
        "${(bitsPerSecond / 1000L).coerceAtLeast(1L)} Kbps"
    }
}

@Composable
private fun mediaKindLabel(kind: DetectedMediaKind): String = when (kind) {
    DetectedMediaKind.VIDEO -> tr("Video", "Видео")
    DetectedMediaKind.AUDIO -> tr("Audio", "Аудио")
    DetectedMediaKind.HLS -> "HLS"
    DetectedMediaKind.DASH -> "DASH"
}
