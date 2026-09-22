package com.ilyro.browser

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.ilyro.browser.ui.IlyroEngineView
import java.lang.ref.WeakReference
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/**
 * Activity-level browser host.
 *
 * GeckoView is a direct native child of the Activity hierarchy. Compose is a sibling used only
 * for ILYRO chrome. Browser-page pointer input is routed to the native host only inside the
 * content rectangle reported by Compose.
 */
internal object NativeBrowserHost {
    fun install(
        activity: ComponentActivity,
        initialTransitionColor: Int = android.graphics.Color.BLACK,
        content: @Composable () -> Unit
    ) {
        val root = BrowserRootLayout(activity, initialTransitionColor)
        val engineHost = IlyroEngineView(activity).apply {
            setTransitionColor(initialTransitionColor)
            visibility = View.INVISIBLE
        }
        val composeView = ComposeView(activity).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setContent(content)
        }

        root.attachEngineHost(engineHost)
        root.addView(
            engineHost,
            FrameLayout.LayoutParams(1, 1)
        )
        root.addView(
            composeView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        activity.setContentView(root)
        NativeBrowserHostCoordinator.attach(root, engineHost)
    }
}

/**
 * Small bridge used by BrowserScreen to control the Activity-owned Gecko host.
 * Weak references prevent the singleton from retaining an old Activity after recreation.
 */
internal object NativeBrowserHostCoordinator {
    private var rootRef = WeakReference<BrowserRootLayout>(null)
    private var hostRef = WeakReference<IlyroEngineView>(null)
    private var externalMediaHandoffPending = false
    private var displayGeneration = 0L

    fun attach(root: BrowserRootLayout, host: IlyroEngineView) {
        rootRef = WeakReference(root)
        hostRef = WeakReference(host)
        externalMediaHandoffPending = false
        displayGeneration += 1
    }

    fun detach() {
        displayGeneration += 1
        hostRef.get()?.release()
        externalMediaHandoffPending = false
        rootRef.clear()
        hostRef.clear()
    }

    fun markExternalMediaHandoff() {
        externalMediaHandoffPending = true
    }

    fun isExternalMediaHandoffPending(): Boolean = externalMediaHandoffPending

    fun clearExternalMediaHandoff() {
        externalMediaHandoffPending = false
    }

    /**
     * Hide the current compositor before Compose changes the selected tab.
     *
     * GeckoView is intentionally reused between tabs. If the old native surface remains visible
     * until the next Compose frame, Android can present its last frame for one or two frames
     * before the new session is attached. This is especially visible on phones and on YouTube.
     */
    fun prepareForSessionSwitch() {
        displayGeneration += 1
        rootRef.get()?.setEngineVisible(false)
        hostRef.get()?.visibility = View.INVISIBLE
    }

    private fun revealWhenReady(host: IlyroEngineView, generation: Long) {
        host.postOnAnimation {
            if (generation != displayGeneration || hostRef.get() !== host) return@postOnAnimation
            host.visibility = View.VISIBLE
            rootRef.get()?.setEngineVisible(true)
        }
    }

    /** Restore input/display visibility after a transient Android focus change. */
    fun restoreVisible() {
        val host = hostRef.get() ?: return
        host.visibility = View.VISIBLE
        rootRef.get()?.setEngineVisible(true)
    }

    fun render(session: GeckoSession) {
        val host = hostRef.get() ?: return
        val changed = host.render(session)
        if (!changed) {
            // Re-rendering the already attached session is common after focus/lifecycle
            // callbacks. Do not hide the surface in that case: doing so can interrupt media
            // playback and creates an unnecessary black frame.
            host.visibility = View.VISIBLE
            rootRef.get()?.setEngineVisible(true)
            return
        }
        val generation = ++displayGeneration
        revealWhenReady(host, generation)
    }

    fun recreateDisplay() {
        val host = hostRef.get() ?: return
        displayGeneration += 1
        if (host.recreateDisplay()) {
            host.visibility = View.VISIBLE
            rootRef.get()?.setEngineVisible(true)
        }
    }

    fun release(hide: Boolean = false) {
        displayGeneration += 1
        // During an explicit external-player handoff, keep the existing native display attached.
        // MainActivity will recreate only the display on return if MIUI invalidated its surface.
        if (hide && externalMediaHandoffPending) return
        hostRef.get()?.release()
        if (hide) {
            hostRef.get()?.visibility = View.INVISIBLE
            rootRef.get()?.setEngineVisible(false)
        }
    }

    fun hideAndRelease() {
        release(hide = true)
    }

    fun geckoView(): GeckoView? = hostRef.get()?.geckoView()

    fun setTransitionColor(color: Int) {
        hostRef.get()?.setTransitionColor(color)
        rootRef.get()?.setTransitionColor(color)
    }

    fun coverUntilFirstPaint(session: GeckoSession? = null) {
        hostRef.get()?.coverUntilFirstPaint(session)
    }

    fun setInputExclusion(left: Int, top: Int, right: Int, bottom: Int) {
        rootRef.get()?.setEngineInputExclusion(left, top, right, bottom)
    }

    fun clearInputExclusion() {
        rootRef.get()?.clearEngineInputExclusion()
    }

    /**
     * Re-measure the native Gecko host after an Activity-handled configuration change.
     *
     * Fullscreen rotation keeps MainActivity alive via configChanges. The window changes size,
     * but the Compose content rectangle and the native host can otherwise retain the previous
     * portrait bounds for one or more frames. Request both passes without touching Gecko input
     * focus or recreating the media surface.
     */
    fun requestLayoutAfterConfigurationChange() {
        val root = rootRef.get() ?: return

        fun requestLayoutPass() {
            root.requestLayout()
            ViewCompat.requestApplyInsets(root)
            root.invalidate()

            hostRef.get()?.let { host ->
                host.requestLayout()
                ViewCompat.requestApplyInsets(host)
                host.invalidate()

                host.geckoView().let { view ->
                    view.requestLayout()
                    ViewCompat.requestApplyInsets(view)
                    view.invalidate()
                }
            }
        }

        root.post {
            requestLayoutPass()
            root.postOnAnimation { requestLayoutPass() }
        }
    }

    fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        rootRef.get()?.setEngineBounds(left, top, right, bottom)
    }

    fun setInputEnabled(enabled: Boolean) {
        rootRef.get()?.setEngineInputEnabled(enabled)
    }
}

internal class BrowserRootLayout(
    context: Context,
    initialTransitionColor: Int
) : FrameLayout(context) {
    private var transitionColor = initialTransitionColor
    private var engineHost: IlyroEngineView? = null
    private val engineBounds = Rect()
    private val engineInputExclusionBounds = Rect()
    private var engineVisible = false
    private var engineInputEnabled = false
    private var engineGestureActive = false
    private var engineGestureDownTime = 0L

    init {
        setBackgroundColor(transitionColor)
    }

    fun setTransitionColor(color: Int) {
        transitionColor = color
        setBackgroundColor(color)
        invalidate()
    }

    fun attachEngineHost(host: IlyroEngineView) {
        engineHost = host
    }

    fun setEngineVisible(visible: Boolean) {
        engineVisible = visible
        if (!visible) cancelEngineGesture()
    }

    fun setEngineInputEnabled(enabled: Boolean) {
        engineInputEnabled = enabled
        if (!enabled) cancelEngineGesture()
    }

    fun setEngineBounds(left: Int, top: Int, right: Int, bottom: Int) {
        val safeRight = right.coerceAtLeast(left + 1)
        val safeBottom = bottom.coerceAtLeast(top + 1)
        if (
            engineBounds.left == left &&
            engineBounds.top == top &&
            engineBounds.right == safeRight &&
            engineBounds.bottom == safeBottom
        ) return

        engineBounds.set(left, top, safeRight, safeBottom)
        val host = engineHost ?: return
        host.layoutParams = LayoutParams(engineBounds.width(), engineBounds.height()).apply {
            leftMargin = engineBounds.left
            topMargin = engineBounds.top
        }
        host.requestLayout()
    }

    fun setEngineInputExclusion(left: Int, top: Int, right: Int, bottom: Int) {
        engineInputExclusionBounds.set(
            left,
            top,
            right.coerceAtLeast(left),
            bottom.coerceAtLeast(top)
        )
    }

    fun clearEngineInputExclusion() {
        engineInputExclusionBounds.setEmpty()
    }

    private fun cancelEngineGesture() {
        if (!engineGestureActive) return
        val host = engineHost
        if (host != null) {
            val now = SystemClock.uptimeMillis()
            val cancel = MotionEvent.obtain(
                engineGestureDownTime.takeIf { it > 0L } ?: now,
                now,
                MotionEvent.ACTION_CANCEL,
                0f,
                0f,
                0
            )
            host.dispatchTouchEvent(cancel)
            cancel.recycle()
        }
        engineGestureActive = false
        engineGestureDownTime = 0L
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val host = engineHost
        if (host != null && engineVisible && engineInputEnabled) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                val x = event.x.toInt()
                val y = event.y.toInt()
                engineGestureActive =
                    engineBounds.contains(x, y) &&
                        !engineInputExclusionBounds.contains(x, y)
                engineGestureDownTime = if (engineGestureActive) event.downTime else 0L
            }

            if (engineGestureActive) {
                val copy = MotionEvent.obtain(event)
                copy.offsetLocation(-engineBounds.left.toFloat(), -engineBounds.top.toFloat())
                val handled = host.dispatchTouchEvent(copy)
                copy.recycle()

                if (event.actionMasked == MotionEvent.ACTION_DOWN && !handled) {
                    engineGestureActive = false
                    engineGestureDownTime = 0L
                } else if (
                    event.actionMasked == MotionEvent.ACTION_UP ||
                    event.actionMasked == MotionEvent.ACTION_CANCEL
                ) {
                    engineGestureActive = false
                    engineGestureDownTime = 0L
                }

                if (handled) return true
            }
        }

        return super.dispatchTouchEvent(event)
    }
}
