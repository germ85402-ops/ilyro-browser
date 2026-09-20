package com.ilyro.browser.ui
import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap

internal object AppIconManager {
 private val aliases = mapOf(AppIcon.DEER to "com.ilyro.browser.DeerIconAlias",
 AppIcon.CLASSIC to "com.ilyro.browser.ClassicIconAlias",
 AppIcon.MONOCHROME to "com.ilyro.browser.MonochromeIconAlias",
 AppIcon.PINK_LIGHT to "com.ilyro.browser.PinkLightIconAlias",
 AppIcon.PINK_DARK to "com.ilyro.browser.PinkDarkIconAlias")
 fun apply(context: Context, selected: AppIcon): Boolean {
 val pm = context.packageManager
 val ordered = listOf(selected) + AppIcon.entries.filter { it != selected }
 val success = runCatching {
 val updates = ordered.mapNotNull { icon ->
 val component = ComponentName(context.packageName, aliases.getValue(icon))
 val state = if (icon == selected) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
 if (pm.getComponentEnabledSetting(component) == state) null else component to state
 }
 if (Build.VERSION.SDK_INT >= 33) {
 if (updates.isNotEmpty()) pm.setComponentEnabledSettings(updates.map { (component, state) ->
 PackageManager.ComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
 })
 } else {
 // Enable the destination before disabling the previous launcher entry.
 updates.forEach { (component, state) -> pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP) }
 }
 }.isSuccess
 var current = context
 while (current is ContextWrapper && current !is Activity) current = current.baseContext
 val activity = current as? Activity
 if (success && activity != null) runCatching {
 val bitmap = ResourcesCompat.getDrawable(context.resources, selected.resource(), context.theme)!!.toBitmap(144,144)
 @Suppress("DEPRECATION")
 activity.setTaskDescription(ActivityManager.TaskDescription("ILYRO", bitmap))
 }
 return success
 }
}
