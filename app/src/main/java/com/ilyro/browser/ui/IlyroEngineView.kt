package com.ilyro.browser.ui

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.AttributeSet
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/**
 * Stable native Gecko host modeled after Firefox Android's GeckoEngineView lifecycle.
 *
 * GeckoView stays in a native Activity-owned FrameLayout. On Android 14+ its SurfaceView follows
 * attachment, which prevents an external-player handoff from destroying the compositor merely
 * because the browser temporarily loses foreground visibility.
 */
class IlyroEngineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var transitionColor: Int = Color.BLACK
    private var engineGeckoView: GeckoView = createGeckoView()
    private var renderedSession: GeckoSession? = null

    init {
        isNestedScrollingEnabled = true
        addView(engineGeckoView)
    }

    private fun createGeckoView(): GeckoView {
        return object : GeckoView(context) {
            override fun onDetachedFromWindow() {
                if (getSession() != null) runCatching { releaseSession() }
                super.onDetachedFromWindow()
            }
        }.apply {
            setBackgroundColor(transitionColor)
            coverUntilFirstPaint(transitionColor)
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            ViewCompat.setImportantForAutofill(this, IMPORTANT_FOR_AUTOFILL_YES)
        }
    }

    private fun configureSurfaceLifecycle(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && view is SurfaceView) {
            view.setSurfaceLifecycle(SurfaceView.SURFACE_LIFECYCLE_FOLLOWS_ATTACHMENT)
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) configureSurfaceLifecycle(view.getChildAt(index))
        }
    }

    @Synchronized
    fun render(session: GeckoSession) {
        if (renderedSession === session && engineGeckoView.getSession() === session) return
        if (engineGeckoView.getSession() != null) runCatching { engineGeckoView.releaseSession() }
        renderedSession = session
        engineGeckoView.setBackgroundColor(transitionColor)
        engineGeckoView.coverUntilFirstPaint(transitionColor)
        engineGeckoView.setSession(session)
        configureSurfaceLifecycle(engineGeckoView)
        engineGeckoView.post { configureSurfaceLifecycle(engineGeckoView) }
    }

    /** Recreate only Gecko's display view; keep the existing GeckoSession and page state intact. */
    @Synchronized
    fun recreateDisplay(): Boolean {
        val session = renderedSession ?: engineGeckoView.getSession() ?: return false
        val previous = engineGeckoView
        runCatching { previous.releaseSession() }
        removeView(previous)

        val replacement = createGeckoView()
        replacement.coverUntilFirstPaint(transitionColor)
        engineGeckoView = replacement
        addView(replacement)
        replacement.setSession(session)
        configureSurfaceLifecycle(replacement)
        replacement.post {
            configureSurfaceLifecycle(replacement)
            replacement.requestLayout()
            ViewCompat.requestApplyInsets(replacement)
            replacement.invalidate()
        }
        return true
    }

    @Synchronized
    fun setTransitionColor(color: Int) {
        transitionColor = color
        setBackgroundColor(color)
        engineGeckoView.setBackgroundColor(color)
    }

    @Synchronized
    fun coverUntilFirstPaint(session: GeckoSession? = null) {
        if (session != null && renderedSession !== session) return
        engineGeckoView.coverUntilFirstPaint(transitionColor)
    }

    @Synchronized
    fun release() {
        if (engineGeckoView.getSession() != null) runCatching { engineGeckoView.releaseSession() }
        renderedSession = null
    }

    fun geckoView(): GeckoView = engineGeckoView

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }
}
