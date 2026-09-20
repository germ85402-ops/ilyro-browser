package com.ilyro.browser.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

@Composable
internal fun OnboardingExtensionIcon(
    slug: String,
    fallback: String,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(slug) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(slug) {
        bitmap = fetchOnboardingAmoIcon(slug)
    }

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                ComposeImage(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.padding(4.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    text = fallback,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private suspend fun fetchOnboardingAmoIcon(slug: String): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val apiConnection = URL("https://addons.mozilla.org/api/v5/addons/addon/$slug/")
            .openConnection() as HttpURLConnection
        apiConnection.connectTimeout = 4_000
        apiConnection.readTimeout = 4_000
        apiConnection.setRequestProperty("Accept", "application/json")

        val response = try {
            if (apiConnection.responseCode !in 200..299) return@runCatching null
            apiConnection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            apiConnection.disconnect()
        }

        val iconUrl = JSONObject(response).optString("icon_url")
            .takeIf { it.startsWith("https://") }
            ?: return@runCatching null

        val imageConnection = URL(iconUrl).openConnection() as HttpURLConnection
        imageConnection.connectTimeout = 4_000
        imageConnection.readTimeout = 4_000
        try {
            if (imageConnection.responseCode !in 200..299) return@runCatching null
            imageConnection.inputStream.use(BitmapFactory::decodeStream)
        } finally {
            imageConnection.disconnect()
        }
    }.getOrNull()
}

