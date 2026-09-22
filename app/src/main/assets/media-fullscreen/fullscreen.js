(() => {
  const host = location.hostname.toLowerCase();
  const isYouTube = host === 'youtube.com' || host.endsWith('.youtube.com') || host === 'youtu.be';

  // Keep YouTube's fullscreen media box large enough to use the available surface.
  // object-fit: contain preserves the original aspect ratio without cropping or stretching.
  const style = document.createElement('style');
  style.textContent = `
    :fullscreen {
      background-color: #000 !important;
    }

    video:fullscreen,
    :fullscreen video {
      object-fit: contain !important;
      object-position: center center !important;
      background: #000 !important;
    }

    /*
     * YouTube Ambient Mode is painted behind the media layer. Keep the fullscreen
     * surface transparent so that glow remains visible around the centered video.
     */
    html.ilyro-youtube-fullscreen :fullscreen {
      background-color: transparent !important;
      position: fixed !important;
      inset: 0 !important;
      width: 100% !important;
      height: 100% !important;
      width: 100dvw !important;
      height: 100dvh !important;
      min-width: 100% !important;
      min-height: 100% !important;
      max-width: none !important;
      max-height: none !important;
      margin: 0 !important;
    }

    /* Use viewport units: 100% resolves against YouTube's intermediate player,
     * which can reserve 48px for controls (880x348 inside an 880x396 fullscreen).
     * Native Gecko bounds are already correct; do not resize or recreate its surface.
     */
    html.ilyro-youtube-fullscreen :fullscreen .html5-video-container {
      box-sizing: border-box !important;
      padding: 0 !important;
      position: absolute !important;
      inset: 0 !important;
      width: 100vw !important;
      width: 100dvw !important;
      height: 100vh !important;
      height: 100dvh !important;
      min-width: 100dvw !important;
      min-height: 100dvh !important;
      max-width: none !important;
      max-height: none !important;
      margin: 0 !important;
      overflow: hidden !important;
      display: flex !important;
      align-items: center !important;
      justify-content: center !important;
      background-color: transparent !important;
    }

    html.ilyro-youtube-fullscreen :fullscreen .html5-video-container video.html5-main-video,
    html.ilyro-youtube-fullscreen :fullscreen .html5-video-container video.video-stream {
      box-sizing: border-box !important;
      position: relative !important;
      inset: auto !important;
      top: auto !important;
      left: auto !important;
      right: auto !important;
      bottom: auto !important;
      display: block !important;
      width: 100% !important;
      height: 100% !important;
      min-width: 0 !important;
      min-height: 0 !important;
      max-width: 100% !important;
      max-height: 100% !important;
      margin: 0 !important;
      flex: 0 0 auto !important;
      transform: none !important;
      object-fit: contain !important;
      object-position: center center !important;
      background: transparent !important;
    }
  `;

  function install() {
    (document.head || document.documentElement)?.appendChild(style);
  }

  if (document.documentElement) install();
  else document.addEventListener('DOMContentLoaded', install, { once: true });

  if (!isYouTube) return;


  // Opt-in, geometry-only diagnostics; never collect page text, URLs or media sources.
  if (new URLSearchParams(location.search).get('ilyro_fs_debug') === '1') {
    let panel = null;
    let port = null;
    let nativeGeometry = 'native: waiting';
    let installedVersion = 'waiting';
    function box(element) {
      if (!element) return 'none';
      const r = element.getBoundingClientRect();
      return element.tagName + ' ' + [r.x, r.y, r.width, r.height].map(Math.round).join(',');
    }
    function sample() {
      const fs = document.fullscreenElement;
      if (!fs) {
        panel?.remove();
        panel = null;
        return;
      }
      if (!panel) {
        panel = document.createElement('pre');
        panel.style.cssText = 'position:fixed!important;left:4px!important;bottom:4px!important;z-index:2147483647!important;background:#000d!important;color:#fff!important;font:11px monospace!important;padding:6px!important;margin:0!important;pointer-events:none!important;white-space:pre-wrap!important;max-width:95vw!important;';
        (fs instanceof HTMLVideoElement ? document.documentElement : fs).appendChild(panel);
      }
      const video = fs instanceof HTMLVideoElement ? fs : fs.querySelector('video');
      const lines = [
        'ILYRO fullscreen diagnostic',
        'helper: script=' + browser.runtime.getManifest().version + ' native=' + installedVersion,
        nativeGeometry,
        'viewport: ' + innerWidth + 'x' + innerHeight + ' DPR ' + devicePixelRatio,
        'fullscreen: ' + box(fs),
        'video: ' + box(video),
        'parent: ' + box(video?.parentElement),
        'helper CSS: ' + Boolean(video?.matches(':fullscreen .html5-video-container video.html5-main-video, :fullscreen .html5-video-container video.video-stream')),
        'source size: ' + (video ? video.videoWidth + 'x' + video.videoHeight : 'none')
      ];
      panel.textContent = lines.join('\n');
      try {
        if (!port) {
          port = browser.runtime.connectNative('ilyro_gestures');
          port.onMessage.addListener(message => {
            if (message.type === 'fullscreenGeometry') {
              nativeGeometry = message.geometry;
              installedVersion = message.helperVersion || 'unreported';
            }
          });
          port.onDisconnect.addListener(() => { port = null; });
        }
        port.postMessage({ type: 'fullscreenGeometry' });
      } catch (_) { nativeGeometry = 'native: unavailable'; }
    }
    document.addEventListener('fullscreenchange', sample, true);
    window.addEventListener('resize', sample);
    // One bounded-rate sample while fullscreen; no video styles or focus changes.
    setInterval(() => { if (document.fullscreenElement) sample(); }, 1000);
  }

  function syncFullscreenState() {
    document.documentElement?.classList.toggle(
      'ilyro-youtube-fullscreen',
      Boolean(document.fullscreenElement)
    );
  }

  document.addEventListener('fullscreenchange', syncFullscreenState, true);
  syncFullscreenState();
})();
