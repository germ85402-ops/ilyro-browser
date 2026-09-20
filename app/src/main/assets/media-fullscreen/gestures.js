(() => {
  // Google web sign-in uses a first-party completion marker. Consume it before the page renders
  // so the user lands on ILYRO Home while the Google cookies remain in Gecko's normal profile.
  try {
    const current = new URL(window.location.href);
    const googleHost = current.hostname === 'google.com' || current.hostname.endsWith('.google.com');
    if (googleHost && current.searchParams.get('ilyro_google_signin_complete') === '1') {
      window.location.replace('about:blank');
      return;
    }
  } catch (_) {}

  const policy = globalThis.ILYROGesturePolicy;
  if (!policy) return;

  let gesture = null;
  let port = null;
  function send(progress, refresh = false) {
    try {
      if (!port) {
        port = browser.runtime.connectNative('ilyro_gestures');
        port.onDisconnect.addListener(() => { port = null; });
      }
      port.postMessage({ progress, refresh });
    } catch (_) { port = null; }
  }
  const atTop = () => {
    const scrolling = document.scrollingElement;
    if (!scrolling) return false;
    return policy.isAtTop({
      scrollTop: scrolling.scrollTop,
      windowScrollY: window.scrollY,
      fullscreen: !!document.fullscreenElement,
      scale: window.visualViewport ? window.visualViewport.scale : 1
    });
  };
  function nestedScroll(path) {
    return path.some(node => {
      if (!(node instanceof Element)) return false;
      if (node.matches('input,textarea,select,video,iframe,[contenteditable="true"]')) return true;
      if (node === document.scrollingElement || node === document.body || node === document.documentElement) return false;

      const css = getComputedStyle(node);
      return policy.blocksNestedScroll({
        scrollHeight: node.scrollHeight,
        clientHeight: node.clientHeight,
        scrollTop: node.scrollTop,
        overflowY: css.overflowY,
        overscrollBehaviorY: css.overscrollBehaviorY,
        touchAction: css.touchAction
      });
    });
  }
  function cancel() { if (gesture) send(0); gesture = null; }
  document.addEventListener('touchstart', event => {
    cancel();
    if (event.touches.length !== 1 || !atTop() || nestedScroll(event.composedPath())) return;
    const touch = event.touches[0];
    gesture = { x: touch.clientX, y: touch.clientY, progress: 0 };
  }, { capture: true, passive: true });
  document.addEventListener('touchmove', event => {
    if (!gesture) return;
    // The scrollable ancestor check is intentionally done only on touchstart.
    // getComputedStyle() on every touchmove forces expensive style work on YouTube.
    if (event.touches.length !== 1 || !atTop()) { cancel(); return; }
    const dx = Math.abs(event.touches[0].clientX - gesture.x);
    const dy = event.touches[0].clientY - gesture.y;
    if (dy < -8 || (dx > 12 && dx >= Math.abs(dy))) { cancel(); return; }
    const progress = dy > dx * 1.5 ? Math.max(0, Math.min(1, (dy - 20) / 76)) : 0;
    if (Math.abs(progress - gesture.progress) > .025 || progress === 1) {
      gesture.progress = progress;
      send(progress);
    }
  }, { capture: true, passive: true });
  document.addEventListener('touchend', event => {
    if (!gesture) return;
    const refresh = event.touches.length === 0 && gesture.progress >= 1 && atTop();
    gesture = null;
    send(0, refresh);
  }, { capture: true, passive: true });
  document.addEventListener('touchcancel', cancel, { capture: true, passive: true });
  document.addEventListener('fullscreenchange', cancel);
  window.addEventListener('pagehide', cancel);
})();
