              package com.ilyro.browser.ui

              import android.content.Context
              import android.content.pm.PackageManager
              import android.net.Uri
              import androidx.compose.foundation.layout.Arrangement
              import androidx.compose.foundation.layout.Box
              import androidx.compose.foundation.layout.Column
              import androidx.compose.foundation.layout.Row
              import androidx.compose.foundation.layout.fillMaxWidth
              import androidx.compose.foundation.layout.padding
              import androidx.compose.foundation.layout.size
              import androidx.compose.foundation.shape.RoundedCornerShape
              import androidx.compose.material.icons.Icons
              import androidx.compose.material.icons.rounded.Lock
              import androidx.compose.material3.AlertDialog
              import androidx.compose.material3.Icon
              import androidx.compose.material3.MaterialTheme
              import androidx.compose.material3.Surface
              import androidx.compose.material3.Text
              import androidx.compose.material3.TextButton
              import androidx.compose.runtime.Composable
              import androidx.compose.runtime.getValue
              import androidx.compose.runtime.mutableStateOf
              import androidx.compose.runtime.setValue
              import androidx.compose.ui.Alignment
              import androidx.compose.ui.Modifier
              import androidx.compose.ui.text.font.FontWeight
              import androidx.compose.ui.unit.dp
              import androidx.core.content.ContextCompat
              import org.mozilla.geckoview.GeckoResult
              import org.mozilla.geckoview.GeckoSession
              import java.util.ArrayDeque

              internal sealed interface PendingSitePermission {
                  val host: String

                  data class Content(
                      override val host: String,
                      val permission: Int,
                      val result: GeckoResult<Int>,
                      val onShown: () -> Unit
                  ) : PendingSitePermission

                  data class Media(
                      override val host: String,
                      val video: GeckoSession.PermissionDelegate.MediaSource?,
                      val audio: GeckoSession.PermissionDelegate.MediaSource?,
                      val requestedVideo: Boolean,
                      val requestedAudio: Boolean,
                      val callback: GeckoSession.PermissionDelegate.MediaCallback
                  ) : PendingSitePermission
              }

              @org.mozilla.geckoview.ExperimentalGeckoViewApi
              internal object SitePermissionCoordinator {
                  private data class AndroidPermissionRequest(
                      val permissions: Array<String>,
                      val callback: GeckoSession.PermissionDelegate.Callback
                  )

                  private var appContext: Context? = null
                  private var androidLauncher: ((Array<String>) -> Unit)? = null
                  private var activeAndroidRequest: AndroidPermissionRequest? = null
                  private val androidQueue = ArrayDeque<AndroidPermissionRequest>()
                  private val promptQueue = ArrayDeque<PendingSitePermission>()

                  var currentPrompt by mutableStateOf<PendingSitePermission?>(null)
                      private set

                  fun bindAndroidPermissionLauncher(
                      context: Context,
                      launcher: (Array<String>) -> Unit
                  ) {
                      appContext = context.applicationContext
                      androidLauncher = launcher
                      launchNextAndroidRequest()
                  }

                  fun unbindAndroidPermissionLauncher() {
                      androidLauncher = null
                      activeAndroidRequest?.callback?.reject()
                      activeAndroidRequest = null
                      while (androidQueue.isNotEmpty()) {
                          androidQueue.removeFirst().callback.reject()
                      }
                      denyAllPrompts()
                  }

                  fun requestAndroidPermissions(
                      permissions: Array<out String>?,
                      callback: GeckoSession.PermissionDelegate.Callback
                  ) {
                      val context = appContext
                      if (context == null) {
                          callback.reject()
                          return
                      }
                      val missing = permissions.orEmpty()
                          .distinct()
                          .filter {
                              ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                          }
                          .toTypedArray()
                      if (missing.isEmpty()) {
                          callback.grant()
                          return
                      }
                      val request = AndroidPermissionRequest(missing, callback)
                      if (activeAndroidRequest == null) {
                          activeAndroidRequest = request
                          launchNextAndroidRequest()
                      } else {
                          androidQueue.addLast(request)
                      }
                  }

                  fun onAndroidPermissionsResult(grants: Map<String, Boolean>) {
                      val request = activeAndroidRequest ?: return
                      val context = appContext
                      val granted = request.permissions.all { permission ->
                          grants[permission] == true ||
                              (context != null && ContextCompat.checkSelfPermission(
                                  context,
                                  permission
                              ) == PackageManager.PERMISSION_GRANTED)
                      }
                      activeAndroidRequest = null
                      if (granted) request.callback.grant() else request.callback.reject()
                      if (androidQueue.isNotEmpty()) {
                          activeAndroidRequest = androidQueue.removeFirst()
                          launchNextAndroidRequest()
                      }
                  }

                  fun requestContentPermission(
                      permission: GeckoSession.PermissionDelegate.ContentPermission
                  ): GeckoResult<Int> {
                      val value = permission.value
                      if (value == GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW ||
                          value == GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
                      ) {
                          return GeckoResult.fromValue(value)
                      }

                      return when (permission.permission) {
                          GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION,
                          GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION,
                          GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE,
                          GeckoSession.PermissionDelegate.PERMISSION_XR -> {
                              val result = GeckoResult<Int>()
                              enqueuePrompt(
                                  PendingSitePermission.Content(
                                      host = hostOf(permission.uri),
                                      permission = permission.permission,
                                      result = result,
                                      onShown = { permission.notifyShown() }
                                  )
                              )
                              result
                          }
                          GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE ->
                              GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
                          GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE ->
                              GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                          GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS ->
                              GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
                          else -> GeckoResult.fromValue(
                              GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
                          )
                      }
                  }

                  fun requestMediaPermission(
                      uri: String,
                      video: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
                      audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
                      callback: GeckoSession.PermissionDelegate.MediaCallback
                  ) {
                      val context = appContext
                      if (context == null) {
                          callback.reject()
                          return
                      }

                      val requestedVideo = !video.isNullOrEmpty()
                      val requestedAudio = !audio.isNullOrEmpty()

                      // GeckoView requests Android runtime camera/microphone permissions through
                      // onAndroidPermissionsRequest. Do not pre-filter these sources here: doing so
                      // rejects the web request before Android can show its permission dialog.
                      val selectedVideo = video?.firstOrNull()
                      val selectedAudio = audio?.firstOrNull()

                      if ((requestedVideo && selectedVideo == null) ||
                          (requestedAudio && selectedAudio == null)
                      ) {
                          callback.reject()
                          return
                      }

                      enqueuePrompt(
                          PendingSitePermission.Media(
                              host = hostOf(uri),
                              video = selectedVideo,
                              audio = selectedAudio,
                              requestedVideo = requestedVideo,
                              requestedAudio = requestedAudio,
                              callback = callback
                          )
                      )
                  }

                  fun allowCurrent() {
                      val prompt = currentPrompt ?: return
                      currentPrompt = null
                      when (prompt) {
                          is PendingSitePermission.Content -> prompt.result.complete(
                              GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                          )
                          is PendingSitePermission.Media -> prompt.callback.grant(prompt.video, prompt.audio)
                      }
                      activateNextPrompt()
                  }

                  fun denyCurrent() {
                      val prompt = currentPrompt ?: return
                      currentPrompt = null
                      deny(prompt)
                      activateNextPrompt()
                  }

                  private fun enqueuePrompt(prompt: PendingSitePermission) {
                      if (currentPrompt == null) {
                          currentPrompt = prompt
                          notifyShown(prompt)
                      } else {
                          promptQueue.addLast(prompt)
                      }
                  }

                  private fun activateNextPrompt() {
                      val next = if (promptQueue.isEmpty()) null else promptQueue.removeFirst()
                      currentPrompt = next
                      if (next != null) notifyShown(next)
                  }

                  private fun notifyShown(prompt: PendingSitePermission) {
                      if (prompt is PendingSitePermission.Content) prompt.onShown()
                  }

                  private fun denyAllPrompts() {
                      currentPrompt?.let(::deny)
                      currentPrompt = null
                      while (promptQueue.isNotEmpty()) deny(promptQueue.removeFirst())
                  }

                  private fun deny(prompt: PendingSitePermission) {
                      when (prompt) {
                          is PendingSitePermission.Content -> prompt.result.complete(
                              GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
                          )
                          is PendingSitePermission.Media -> prompt.callback.reject()
                      }
                  }

                  private fun launchNextAndroidRequest() {
                      val request = activeAndroidRequest ?: return
                      val launcher = androidLauncher ?: return
                      launcher(request.permissions)
                  }

                  private fun hostOf(uri: String): String = runCatching {
                      Uri.parse(uri).host
                  }.getOrNull()?.removePrefix("www.")?.takeIf { it.isNotBlank() } ?: uri
              }

              internal object IlyroPermissionDelegate : GeckoSession.PermissionDelegate {
                  override fun onAndroidPermissionsRequest(
                      session: GeckoSession,
                      permissions: Array<out String>?,
                      callback: GeckoSession.PermissionDelegate.Callback
                  ) {
                      SitePermissionCoordinator.requestAndroidPermissions(permissions, callback)
                  }

                  override fun onContentPermissionRequest(
                      session: GeckoSession,
                      perm: GeckoSession.PermissionDelegate.ContentPermission
                  ): GeckoResult<Int> = SitePermissionCoordinator.requestContentPermission(perm)

                  override fun onMediaPermissionRequest(
                      session: GeckoSession,
                      uri: String,
                      video: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
                      audio: Array<out GeckoSession.PermissionDelegate.MediaSource>?,
                      callback: GeckoSession.PermissionDelegate.MediaCallback
                  ) {
                      SitePermissionCoordinator.requestMediaPermission(uri, video, audio, callback)
                  }
              }

              @Composable
              internal fun SitePermissionPromptHost() {
                  val prompt = SitePermissionCoordinator.currentPrompt ?: return
                  val host = prompt.host.ifBlank { tr("This site", "Этот сайт") }
                  val requestText = when (prompt) {
                      is PendingSitePermission.Content -> when (prompt.permission) {
                          GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION ->
                              tr("your location", "вашу геопозицию")
                          GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION ->
                              tr("notifications", "уведомления")
                          GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE ->
                              tr("persistent site storage", "постоянное хранилище сайта")
                          GeckoSession.PermissionDelegate.PERMISSION_XR ->
                              tr("VR/AR devices", "устройства VR/AR")
                          else -> tr("this permission", "это разрешение")
                      }
                      is PendingSitePermission.Media -> {
                          val videoIsScreen = prompt.video?.source ==
                              GeckoSession.PermissionDelegate.MediaSource.SOURCE_SCREEN
                          when {
                              prompt.requestedVideo && prompt.requestedAudio && videoIsScreen ->
                                  tr("your screen and audio", "ваш экран и звук")
                              prompt.requestedVideo && prompt.requestedAudio ->
                                  tr("your camera and microphone", "вашу камеру и микрофон")
                              prompt.requestedVideo && videoIsScreen ->
                                  tr("your screen", "ваш экран")
                              prompt.requestedVideo -> tr("your camera", "вашу камеру")
                              prompt.requestedAudio -> tr("your microphone", "ваш микрофон")
                              else -> tr("media devices", "медиаустройства")
                          }
                      }
                  }

                  AlertDialog(
                      onDismissRequest = { SitePermissionCoordinator.denyCurrent() },
                      shape = RoundedCornerShape(24.dp),
                      containerColor = MaterialTheme.colorScheme.surface,
                      title = {
                          Row(
                              verticalAlignment = Alignment.CenterVertically,
                              horizontalArrangement = Arrangement.spacedBy(10.dp)
                          ) {
                              Surface(
                                  modifier = Modifier.size(40.dp),
                                  shape = RoundedCornerShape(13.dp),
                                  color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                              ) {
                                  Box(contentAlignment = Alignment.Center) {
                                      Icon(
                                          imageVector = Icons.Rounded.Lock,
                                          contentDescription = null,
                                          modifier = Modifier.size(20.dp),
                                          tint = MaterialTheme.colorScheme.onSurface
                                      )
                                  }
                              }
                              Column {
                                  Text(
                                      tr("Site permission", "Разрешение сайта"),
                                      fontWeight = FontWeight.SemiBold
                                  )
                                  Text(
                                      host,
                                      style = MaterialTheme.typography.bodySmall,
                                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                                      maxLines = 1
                                  )
                              }
                          }
                      },
                      text = {
                          Surface(
                              modifier = Modifier.fillMaxWidth(),
                              shape = RoundedCornerShape(16.dp),
                              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)
                          ) {
                              Text(
                                  text = tr(
                                      "Allow $host to access $requestText?",
                                      "Разрешить $host доступ: $requestText?"
                                  ),
                                  modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                  style = MaterialTheme.typography.bodyMedium
                              )
                          }
                      },
                      confirmButton = {
                          TextButton(onClick = { SitePermissionCoordinator.allowCurrent() }) {
                              Text(tr("Allow", "Разрешить"), fontWeight = FontWeight.SemiBold)
                          }
                      },
                      dismissButton = {
                          TextButton(onClick = { SitePermissionCoordinator.denyCurrent() }) {
                              Text(tr("Don't allow", "Не разрешать"))
                          }
                      }
                  )
              }
