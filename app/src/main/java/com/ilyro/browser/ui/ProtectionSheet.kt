package com.ilyro.browser.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mozilla.geckoview.GeckoSession

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProtectionSheet(
    host: String,
    globalEnabled: Boolean,
    extensionReady: Boolean,
    @Suppress("UNUSED_PARAMETER") blockedBadge: String,
    permissions: List<GeckoSession.PermissionDelegate.ContentPermission>,
    isPrivate: Boolean,
    onDismiss: () -> Unit,
    onGlobalEnabledChange: (Boolean) -> Unit,
    onUpdateFilters: () -> Unit,
    onOpenUBlockSettings: () -> Unit,
    onOpenSiteControls: () -> Unit,
    onPermissionChange: (GeckoSession.PermissionDelegate.ContentPermission, Int) -> Unit,
    onResetPermissions: () -> Unit,
    onClearSiteData: () -> Unit
) {
    val metrics = rememberIlyroLayoutMetrics()
    val dense = LocalIlyroUiDensity.current == UiDensity.COMPACT
    val hasSite = host.isNotBlank()
    val siteUrl = if (hasSite) "https://$host/" else ""
    val bridgeReady = ProtectionBridge.bridgeReady
    val ready = globalEnabled && extensionReady && bridgeReady
    val siteEnabled = if (hasSite) ProtectionBridge.siteEnabled(siteUrl) else true
    val blockedRequests = if (ready && hasSite) ProtectionBridge.blockedRequestCount(siteUrl) else null
    val allowedRequests = if (ready && hasSite) ProtectionBridge.allowedRequestCount(siteUrl) else null
    val requestTotal = if (blockedRequests != null && allowedRequests != null) {
        blockedRequests + allowedRequests
    } else 0
    val blockedPercent = if (requestTotal > 0 && blockedRequests != null) {
        ((blockedRequests.toLong() * 100L) / requestTotal.toLong()).toInt()
    } else null

    val globalBlocked = ProtectionBridge.globalBlockedRequestCount
    val globalAllowed = ProtectionBridge.globalAllowedRequestCount
    val globalTotal = (globalBlocked ?: 0L) + (globalAllowed ?: 0L)
    val globalPercent = if (globalTotal > 0L && globalBlocked != null) {
        ((globalBlocked * 100L) / globalTotal).toInt()
    } else null
    val uBlockVersion = ProtectionBridge.uBlockVersion ?: "—"
    val updatingFilters = ProtectionBridge.filterUpdateInProgress
    val filterMessage = ProtectionBridge.filterUpdateMessage
    var confirmClear by remember(host) { mutableStateOf(false) }
    var showEngineDetails by remember { mutableStateOf(false) }
    var showSiteDetails by remember(host) { mutableStateOf(false) }

    LaunchedEffect(siteUrl, bridgeReady, globalEnabled) {
        if (bridgeReady) {
            ProtectionBridge.requestGlobalStats()
            if (hasSite) ProtectionBridge.requestSiteState(siteUrl)
        }
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
                    .stabilizeBottomSheetFling()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = metrics.horizontalPadding)
            ) {
                Row(
                    modifier = Modifier.padding(top = if (dense) 8.dp else if (metrics.isCompact) 12.dp else 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(if (dense) 40.dp else if (metrics.isNarrowPhone) 44.dp else 48.dp),
                        shape = RoundedCornerShape(IlyroVisualTokens.SmallRadius),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        ),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            IlyroShieldIcon(
                                active = ready,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(if (dense) 24.dp else 27.dp)
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = if (dense) 10.dp else 13.dp)
                    ) {
                        Text(
                            "ILYRO Shield",
                            style = if (dense) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            tr(
                                "Privacy and content blocking powered by uBlock Origin",
                                "Защита и блокировка контента на базе uBlock Origin"
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(metrics.sectionGap))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(IlyroVisualTokens.CardRadius),
                    color = if (globalEnabled) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                    },
                    border = BorderStroke(
                        1.dp,
                        if (globalEnabled) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(
                                alpha = IlyroVisualTokens.SubtleBorderAlpha
                            )
                        }
                    ),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = if (dense) 13.dp else 16.dp, vertical = if (dense) 11.dp else 15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (globalEnabled) {
                                    tr("Protection is on", "Защита включена")
                                } else {
                                    tr("Protection is off", "Защита выключена")
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (globalEnabled) {
                                    tr(
                                        "Block ads, trackers and unwanted network requests on all sites.",
                                        "Блокировать рекламу, трекеры и нежелательные сетевые запросы на всех сайтах."
                                    )
                                } else {
                                    tr(
                                        "uBlock Origin is paused globally.",
                                        "uBlock Origin приостановлен во всём браузере."
                                    )
                                },
                                modifier = Modifier.padding(top = 3.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = globalEnabled,
                            onCheckedChange = onGlobalEnabledChange
                        )
                    }
                }

                Spacer(modifier = Modifier.height(metrics.itemGap))

                if (metrics.isNarrowPhone) {
                    Column(verticalArrangement = Arrangement.spacedBy(metrics.itemGap)) {
                        ShieldMetricCard(
                            value = globalBlocked?.toString() ?: "—",
                            label = tr("Blocked overall", "Заблокировано всего"),
                            supporting = globalPercent?.let {
                                "$it% " + tr("of processed requests", "обработанных запросов")
                            } ?: tr("Lifetime uBlock statistics", "Общая статистика uBlock"),
                            modifier = Modifier.fillMaxWidth()
                        )
                        ShieldMetricCard(
                            value = globalAllowed?.toString() ?: "—",
                            label = tr("Allowed overall", "Разрешено всего"),
                            supporting = tr("Processed without blocking", "Обработано без блокировки"),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(metrics.itemGap)
                    ) {
                        ShieldMetricCard(
                            value = globalBlocked?.toString() ?: "—",
                            label = tr("Blocked overall", "Заблокировано всего"),
                            supporting = globalPercent?.let {
                                "$it% " + tr("of processed requests", "обработанных запросов")
                            } ?: tr("Lifetime uBlock statistics", "Общая статистика uBlock"),
                            modifier = Modifier.weight(1f)
                        )
                        ShieldMetricCard(
                            value = globalAllowed?.toString() ?: "—",
                            label = tr("Allowed overall", "Разрешено всего"),
                            supporting = tr("Processed without blocking", "Обработано без блокировки"),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(metrics.sectionGap))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(IlyroVisualTokens.CardRadius),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(
                            alpha = IlyroVisualTokens.SubtleBorderAlpha
                        )
                    ),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Column(modifier = Modifier.padding(horizontal = if (dense) 13.dp else 15.dp, vertical = if (dense) 11.dp else 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "uBlock Origin",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    tr("Engine version $uBlockVersion", "Версия движка $uBlockVersion"),
                                    modifier = Modifier.padding(top = 2.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                if (bridgeReady) tr("Ready", "Готов") else tr("Starting…", "Запуск…"),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (bridgeReady) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        TextButton(onClick = { showEngineDetails = !showEngineDetails }) {
                            Text(
                                if (showEngineDetails) {
                                    tr("Hide engine details", "Скрыть детали движка")
                                } else {
                                    tr("uBlock controls", "Управление uBlock")
                                }
                            )
                        }

                        if (showEngineDetails) {
                            Text(
                                when (filterMessage) {
                                    "updating" -> tr(
                                        "Updating filter lists…",
                                        "Обновляем списки фильтров…"
                                    )
                                    "updated" -> tr(
                                        "Filter lists are up to date.",
                                        "Списки фильтров обновлены."
                                    )
                                    "failed" -> tr(
                                        "Couldn't update filter lists.",
                                        "Не удалось обновить списки фильтров."
                                    )
                                    "not_ready" -> tr(
                                        "uBlock Origin is not ready yet.",
                                        "uBlock Origin ещё не готов."
                                    )
                                    else -> tr(
                                        "Filter lists update independently from the ILYRO app. The uBlock engine itself updates with ILYRO releases.",
                                        "Списки фильтров обновляются отдельно от приложения ILYRO. Сам движок uBlock обновляется вместе с версиями ILYRO."
                                    )
                                },
                                modifier = Modifier.padding(top = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
    
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 7.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TextButton(
                                    onClick = onUpdateFilters,
                                    enabled = globalEnabled && bridgeReady && !updatingFilters
                                ) {
                                    Text(
                                        if (updatingFilters) {
                                            tr("Updating…", "Обновление…")
                                        } else {
                                            tr("Update filters", "Обновить фильтры")
                                        }
                                    )
                                }
                                TextButton(
                                    onClick = onOpenUBlockSettings,
                                    enabled = globalEnabled && extensionReady
                                ) {
                                    Text(tr("Advanced settings", "Настройки uBlock"))
                                }
                            }
    
                        }
                    }
                }

                if (hasSite) {
                    Spacer(modifier = Modifier.height(metrics.sectionGap))

                    Text(
                        tr("Current site", "Текущий сайт"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        host,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(IlyroVisualTokens.CardRadius),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(
                                alpha = IlyroVisualTokens.SubtleBorderAlpha
                            )
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = if (dense) 13.dp else 15.dp, vertical = if (dense) 11.dp else 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (siteEnabled) {
                                        tr("Block on this site", "Блокировать на этом сайте")
                                    } else {
                                        tr("Allow on this site", "Разрешить на этом сайте")
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    blockedRequests?.let {
                                        tr(
                                            "$it requests blocked on this page",
                                            "Заблокировано запросов на странице: $it"
                                        )
                                    } ?: tr(
                                        "Per-site control for uBlock Origin",
                                        "Управление uBlock Origin для этого сайта"
                                    ),
                                    modifier = Modifier.padding(top = 2.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                // While uBO is warming up, keep the requested site state visible
                                // instead of flashing the switch off until the native bridge connects.
                                checked = globalEnabled && siteEnabled,
                                enabled = ready,
                                onCheckedChange = { enabled ->
                                    if (ProtectionBridge.setSiteEnabled(siteUrl, enabled)) {
                                        onOpenSiteControls()
                                    }
                                }
                            )
                        }
                    }

                    if (blockedPercent != null) {
                        Text(
                            "$blockedPercent% " + tr(
                                "of this site's requests were blocked",
                                "запросов этого сайта заблокировано"
                            ),
                            modifier = Modifier.padding(top = 7.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    TextButton(onClick = { showSiteDetails = !showSiteDetails }) {
                        Text(
                            if (showSiteDetails) {
                                tr("Hide site details", "Скрыть параметры сайта")
                            } else {
                                tr("Permissions & site data", "Разрешения и данные сайта")
                            }
                        )
                    }

                    if (showSiteDetails) {
                        Spacer(modifier = Modifier.height(metrics.itemGap))
    
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(IlyroVisualTokens.CardRadius),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(
                                    alpha = IlyroVisualTokens.SubtleBorderAlpha
                                )
                            )
                        ) {
                            Column(modifier = Modifier.padding(horizontal = if (dense) 12.dp else 14.dp, vertical = if (dense) 10.dp else 12.dp)) {
                                Text(
                                    tr("Site permissions", "Разрешения сайта"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (permissions.isEmpty()) {
                                    Text(
                                        tr(
                                            "No stored permissions for this site.",
                                            "Для этого сайта нет сохранённых разрешений."
                                        ),
                                        modifier = Modifier.padding(top = 6.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    permissions.forEach { permission ->
                                        PermissionRow(
                                            permission = permission,
                                            onPermissionChange = onPermissionChange
                                        )
                                    }
                                    TextButton(onClick = onResetPermissions) {
                                        Text(tr("Reset all permissions", "Сбросить все разрешения"))
                                    }
                                }
                            }
                        }
    
                        Spacer(modifier = Modifier.height(metrics.itemGap))
    
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(IlyroVisualTokens.CardRadius),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(
                                    alpha = IlyroVisualTokens.SubtleBorderAlpha
                                )
                            )
                        ) {
                            Column(modifier = Modifier.padding(horizontal = if (dense) 12.dp else 14.dp, vertical = if (dense) 10.dp else 12.dp)) {
                                Text(
                                    tr("Site data", "Данные сайта"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    tr(
                                        "Clear cookies, cache, local storage and stored permissions for this site.",
                                        "Удалить cookie, кэш, локальное хранилище и сохранённые разрешения этого сайта."
                                    ),
                                    modifier = Modifier.padding(top = 4.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(onClick = { confirmClear = true }) {
                                    Text(tr("Clear site data", "Очистить данные сайта"))
                                }
                            }
                        }
    
    
                    }

                    if (isPrivate) {
                        Text(
                            tr(
                                "Private tab data is isolated from normal browsing.",
                                "Данные приватной вкладки изолированы от обычного режима."
                            ),
                            modifier = Modifier.padding(top = 10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(metrics.sectionGap))
                    Text(
                        tr(
                            "Open a website to see per-site blocking, permissions and site data controls.",
                            "Откройте сайт, чтобы увидеть блокировку, разрешения и управление данными для него."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (confirmClear && hasSite) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(tr("Clear site data?", "Очистить данные сайта?")) },
            text = {
                Text(
                    tr(
                        "You may be signed out of $host and site permissions will be reset.",
                        "Вы можете выйти из аккаунта на $host, а разрешения сайта будут сброшены."
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClearSiteData()
                    }
                ) {
                    Text(tr("Clear", "Очистить"), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(tr("Cancel", "Отмена"))
                }
            }
        )
    }
}

@Composable
private fun PermissionRow(
    permission: GeckoSession.PermissionDelegate.ContentPermission,
    onPermissionChange: (GeckoSession.PermissionDelegate.ContentPermission, Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Text(
            text = permissionLabel(permission.permission),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = permissionValueLabel(permission.value),
            modifier = Modifier.padding(top = 1.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PermissionChoice(
                text = tr("Allow", "Разрешить"),
                selected = permission.value == GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW,
                modifier = Modifier.weight(1f),
                onClick = {
                    onPermissionChange(
                        permission,
                        GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                    )
                }
            )
            PermissionChoice(
                text = tr("Block", "Запретить"),
                selected = permission.value == GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY,
                modifier = Modifier.weight(1f),
                onClick = {
                    onPermissionChange(
                        permission,
                        GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
                    )
                }
            )
            PermissionChoice(
                text = tr("Ask", "Спрашивать"),
                selected = permission.value == GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT,
                modifier = Modifier.weight(1f),
                onClick = {
                    onPermissionChange(
                        permission,
                        GeckoSession.PermissionDelegate.ContentPermission.VALUE_PROMPT
                    )
                }
            )
        }
    }
}

@Composable
private fun PermissionChoice(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(IlyroVisualTokens.SmallRadius),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
        },
        border = if (selected) {
            BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = IlyroVisualTokens.SelectedBorderAlpha)
            )
        } else {
            BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.10f)
            )
        },
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 3.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun permissionLabel(permission: Int): String = when (permission) {
    GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> tr("Location", "Геопозиция")
    GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> tr("Notifications", "Уведомления")
    GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> tr(
        "Persistent storage",
        "Постоянное хранилище"
    )
    GeckoSession.PermissionDelegate.PERMISSION_XR -> tr("VR / AR", "VR / AR")
    GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE -> tr(
        "Autoplay with sound",
        "Автовоспроизведение со звуком"
    )
    GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE -> tr(
        "Silent autoplay",
        "Автовоспроизведение без звука"
    )
    GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> tr(
        "Protected media",
        "Защищённое медиа"
    )
    else -> tr("Site permission", "Разрешение сайта")
}

@Composable
private fun permissionValueLabel(value: Int): String = when (value) {
    GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW -> tr("Allowed", "Разрешено")
    GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY -> tr("Blocked", "Запрещено")
    else -> tr("Ask every time", "Спрашивать каждый раз")
}

@Composable
private fun ShieldMetricCard(
    value: String,
    label: String,
    supporting: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(IlyroVisualTokens.CardRadius),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = IlyroVisualTokens.SubtleBorderAlpha)
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                supporting,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
