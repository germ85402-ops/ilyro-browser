package com.ilyro.browser.ui

import org.json.JSONObject
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension

internal object PageGestureBridge {
    private var extension: WebExtension? = null
    private val callbacks = mutableMapOf<GeckoSession, (Float, Boolean) -> Unit>()

    fun attach(value: WebExtension) {
        extension = value
        callbacks.keys.toList().forEach { install(it, value) }
    }

    fun bind(session: GeckoSession, callback: (Float, Boolean) -> Unit) {
        callbacks[session] = callback
        extension?.let { install(session, it) }
    }

    fun unbind(session: GeckoSession) {
        callbacks.remove(session)
    }

    private fun install(session: GeckoSession, extension: WebExtension) {
        session.webExtensionController.setMessageDelegate(
            extension,
            object : WebExtension.MessageDelegate {
                override fun onConnect(port: WebExtension.Port) {
                    port.setDelegate(object : WebExtension.PortDelegate {
                        override fun onPortMessage(message: Any, port: WebExtension.Port) {
                            val json = message as? JSONObject ?: return
                            val progress = json.optDouble("progress", 0.0).toFloat()
                            if (!progress.isFinite()) return
                            callbacks[session]?.invoke(progress.coerceIn(0f, 1f), json.optBoolean("refresh", false))
                        }
                        override fun onDisconnect(port: WebExtension.Port) {
                            callbacks[session]?.invoke(0f, false)
                        }
                    })
                }
            },
            "ilyro_gestures"
        )
    }
}
