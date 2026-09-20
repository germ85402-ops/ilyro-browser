package com.ilyro.browser.ui
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import com.ilyro.browser.R
internal val LocalAppIcon = staticCompositionLocalOf { AppIcon.DEER }
internal fun AppIcon.resource(): Int = when (this) {
 AppIcon.DEER -> R.mipmap.ic_launcher_deer
 AppIcon.CLASSIC -> R.mipmap.ic_launcher
 AppIcon.MONOCHROME -> R.mipmap.ic_launcher_monochrome
 AppIcon.PINK_LIGHT -> R.mipmap.ic_launcher_pink_light
 AppIcon.PINK_DARK -> R.mipmap.ic_launcher_pink_dark
}
@Composable
internal fun AppBrandIcon(modifier: Modifier = Modifier, icon: AppIcon = LocalAppIcon.current) {
 val context = LocalContext.current
 val configuration = androidx.compose.ui.platform.LocalConfiguration.current
 val bitmap = remember(icon, configuration) {
 ResourcesCompat.getDrawable(context.resources, icon.resource(), context.theme)!!.toBitmap(216,216).asImageBitmap()
 }
 Image(bitmap = bitmap, contentDescription = "ILYRO", modifier = modifier)
}
