package com.example.ui.components

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import android.webkit.WebView

/**
 * Coordinates global swipe-down gesture detection across all screens in the app,
 * including Messages, Calls, Dialer/Keypad, Contacts, Clone Space hub, Browser,
 * and inside any active Cloned App (WebView).
 */
object GlobalSwipeDownTracker {
    @Volatile
    var isDrawerOpen: Boolean = false

    @Volatile
    var isSwipeEnabled: Boolean = true

    @Volatile
    var isWebViewScreenActive: Boolean = false

    @Volatile
    var didComposeChildScroll: Boolean = false

    @Volatile
    var activeWebViewCanScrollUp: Boolean = false

    @Volatile
    var jsDomCanScrollUp: Boolean = false

    var onOpenDrawer: (() -> Unit)? = null

    fun onGestureStart() {
        didComposeChildScroll = false
    }

    fun openDrawer() {
        if (!isDrawerOpen && isSwipeEnabled) {
            onOpenDrawer?.invoke()
        }
    }

    class XenoSwipeBridge {
        @JavascriptInterface
        fun setDomCanScrollUp(canScrollUp: Boolean) {
            jsDomCanScrollUp = canScrollUp
            if (canScrollUp) {
                activeWebViewCanScrollUp = true
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun attachToWebView(webView: WebView, disallowParentIntercept: Boolean = false) {
        try {
            webView.addJavascriptInterface(XenoSwipeBridge(), "XenoSwipeBridge")
        } catch (_: Exception) {}

        webView.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val canNativeScrollUp = v.scrollY > 4 || v.canScrollVertically(-1)
                    activeWebViewCanScrollUp = canNativeScrollUp || jsDomCanScrollUp
                }
                MotionEvent.ACTION_MOVE -> {
                    if (v.scrollY > 4 || v.canScrollVertically(-1) || jsDomCanScrollUp) {
                        activeWebViewCanScrollUp = true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    jsDomCanScrollUp = false
                }
            }
            if (disallowParentIntercept) {
                if (event.action == MotionEvent.ACTION_DOWN ||
                    event.action == MotionEvent.ACTION_MOVE
                ) {
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            false
        }
    }

    fun injectSwipeScrollObserver(webView: WebView?) {
        webView?.evaluateJavascript(
            """
            (function() {
                if (window.__xenoSwipeBridgeInstalled) return;
                window.__xenoSwipeBridgeInstalled = true;
                var checkCanScrollUp = function(target) {
                    try {
                        if ((window.scrollY || window.pageYOffset || 0) > 4) return true;
                        var path = (window.location && window.location.pathname) ? window.location.pathname.toLowerCase() : '';
                        if (path.indexOf('/reels') !== -1 || path.indexOf('/reel/') !== -1 || path.indexOf('/shorts') !== -1) {
                            return true;
                        }
                        var el = target;
                        while (el && el !== document.body && el !== document.documentElement) {
                            if (el.scrollTop && el.scrollTop > 4) {
                                return true;
                            }
                            el = el.parentElement;
                        }
                    } catch (e) {}
                    return false;
                };
                window.addEventListener('touchstart', function(e) {
                    if (!e.touches || e.touches.length !== 1) return;
                    var canUp = checkCanScrollUp(e.target);
                    if (window.XenoSwipeBridge && typeof window.XenoSwipeBridge.setDomCanScrollUp === 'function') {
                        window.XenoSwipeBridge.setDomCanScrollUp(Boolean(canUp));
                    }
                }, { passive: true, capture: true });
            })();
            """.trimIndent(),
            null
        )
    }
}
