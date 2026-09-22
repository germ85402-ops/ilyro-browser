package com.ilyro.browser.ui

import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.MediaSession

internal data class GeckoMediaPlaybackState(
    val active: Boolean = false,
    val playing: Boolean = false,
    val features: Long = 0L
)

internal object GeckoMediaSessionBridge {
    private val playbackBySession = mutableMapOf<GeckoSession, GeckoMediaPlaybackState>()
    private val controllerBySession = mutableMapOf<GeckoSession, MediaSession>()
    private var selectedSession: GeckoSession? = null

    fun bind(session: GeckoSession) {
        playbackBySession.putIfAbsent(session, GeckoMediaPlaybackState())
        session.setMediaSessionDelegate(object : MediaSession.Delegate {
            override fun onActivated(session: GeckoSession, mediaSession: MediaSession) {
                controllerBySession[session] = mediaSession
                update(session) { it.copy(active = true) }
            }

            override fun onDeactivated(session: GeckoSession, mediaSession: MediaSession) {
                controllerBySession.remove(session)
                update(session) { it.copy(active = false, playing = false) }
                restoreIdlePriority(session)
            }

            override fun onPlay(session: GeckoSession, mediaSession: MediaSession) {
                controllerBySession[session] = mediaSession
                update(session) { it.copy(active = true, playing = true) }
                session.setPriorityHint(GeckoSession.PRIORITY_HIGH)
            }

            override fun onPause(session: GeckoSession, mediaSession: MediaSession) {
                update(session) { it.copy(active = true, playing = false) }
                restoreIdlePriority(session)
            }

            override fun onStop(session: GeckoSession, mediaSession: MediaSession) {
                update(session) { it.copy(active = true, playing = false) }
                restoreIdlePriority(session)
            }

            override fun onFeatures(session: GeckoSession, mediaSession: MediaSession, features: Long) {
                controllerBySession[session] = mediaSession
                update(session) { it.copy(active = true, features = features) }
            }
        })
    }

    fun unbind(session: GeckoSession) {
        playbackBySession.remove(session)
        controllerBySession.remove(session)
        if (selectedSession === session) selectedSession = null
        session.setMediaSessionDelegate(null)
    }

    fun setSelectedSession(session: GeckoSession?) {
        val previous = selectedSession
        selectedSession = session
        if (previous != null && previous !== session && stateFor(previous).playing.not()) {
            runCatching { previous.setPriorityHint(GeckoSession.PRIORITY_DEFAULT) }
        }
        if (session != null) {
            runCatching { session.setPriorityHint(GeckoSession.PRIORITY_HIGH) }
        }
    }

    fun stateFor(session: GeckoSession): GeckoMediaPlaybackState =
        playbackBySession[session] ?: GeckoMediaPlaybackState()

    fun hasActivePlayback(): Boolean {
        val session = selectedSession ?: return false
        val state = playbackBySession[session] ?: return false
        return state.active || state.playing
    }

    fun playSelected(): Boolean {
        val controller = selectedSession?.let(controllerBySession::get) ?: return false
        if (!controller.isActive) return false
        controller.play()
        return true
    }

    fun pauseSelected(): Boolean {
        val controller = selectedSession?.let(controllerBySession::get) ?: return false
        if (!controller.isActive) return false
        controller.pause()
        return true
    }

    fun onPictureInPictureModeChanged(inPictureInPicture: Boolean) {
        val session = selectedSession ?: return
        runCatching { session.compositorController.onPipModeChanged(inPictureInPicture) }
        if (inPictureInPicture) {
            runCatching { session.setFocused(false) }
            runCatching { session.setActive(true) }
            runCatching { session.setPriorityHint(GeckoSession.PRIORITY_HIGH) }
        }
    }

    private fun restoreIdlePriority(session: GeckoSession) {
        runCatching {
            session.setPriorityHint(
                if (selectedSession === session) GeckoSession.PRIORITY_HIGH
                else GeckoSession.PRIORITY_DEFAULT
            )
        }
    }

    private fun update(
        session: GeckoSession,
        transform: (GeckoMediaPlaybackState) -> GeckoMediaPlaybackState
    ) {
        playbackBySession[session] = transform(
            playbackBySession[session] ?: GeckoMediaPlaybackState()
        )
    }
}
