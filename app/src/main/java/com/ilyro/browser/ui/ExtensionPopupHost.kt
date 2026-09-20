package com.ilyro.browser.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

@Composable
internal fun ExtensionPopupHost(
    session: GeckoSession,
    title: String,
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val popupWidthFraction = if (isTablet) 0.56f else 0.94f
    val popupHeightFraction = if (isTablet) 0.68f else 0.76f

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        IlyroSystemBarAppearance()
        Surface(
            modifier = Modifier
                .fillMaxWidth(popupWidthFraction)
                .widthIn(max = 480.dp)
                .fillMaxHeight(popupHeightFraction),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 8.dp, top = 6.dp, bottom = 4.dp)
                ) {
                    Text(
                        text = title.ifBlank { tr("Extension", "Расширение") },
                        modifier = Modifier.align(Alignment.CenterStart),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Text(tr("Close", "Закрыть"))
                    }
                }

                AndroidView(
                    factory = { context ->
                        GeckoView(context).apply {
                            setSession(session)
                        }
                    },
                    update = { view ->
                        if (view.session !== session) view.setSession(session)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .heightIn(min = 220.dp)
                )
            }
        }
    }
}
